"""Offline contract tests for the roadmap publisher; GitHub is always mocked."""

import contextlib
import copy
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("roadmap_publish", Path(__file__).with_name("publish.py"))
publisher = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(publisher)


def remote_issue(number, title, body="", state="open"):
    return {"number": number, "title": title, "body": body, "state": state,
            "html_url": f"https://github.com/Nav1212/maptool/issues/{number}"}


class FakeGitHub:
    def __init__(self, issues=()):
        self.issues = copy.deepcopy(list(issues))
        self.calls = []
        self.fail_after_create = False

    def pages(self, resource):
        self.calls.append(("GET", resource))
        if resource == "issues?state=all":
            return copy.deepcopy(self.issues)
        if resource == "labels":
            return [{"name": "feature"}, {"name": "epic"}]
        raise AssertionError(resource)

    def api(self, endpoint, method="GET", payload=None):
        self.calls.append((method, endpoint, copy.deepcopy(payload)))
        if method == "GET":
            return {"full_name": publisher.REPOSITORY, "has_issues": True,
                    "archived": False, "permissions": {"push": True}}
        if method == "POST":
            number = max([issue["number"] for issue in self.issues] + [100]) + 1
            issue = remote_issue(number, payload["title"], payload["body"])
            self.issues.append(issue)
            if self.fail_after_create:
                self.fail_after_create = False
                raise publisher.BacklogError("Simulated lost response after successful POST")
            return copy.deepcopy(issue)
        if method == "PATCH":
            number = int(endpoint.rsplit("/", 1)[1])
            issue = next(issue for issue in self.issues if issue["number"] == number)
            issue.update(payload)
            return copy.deepcopy(issue)
        raise AssertionError((endpoint, method))


class PublisherTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / "issues").mkdir()
        self.manifest = self.root / "issues.json"
        self.data = {"repository": publisher.REPOSITORY, "source_commit": "a" * 40,
                     "issues": [
                         {"id": "EP", "title": "Epic: Improve the shared campaign experience",
                          "type": "epic", "priority": "P1", "size": "XL", "tracks": ["A", "B"],
                          "body_file": "issues/EP.md"},
                         {"id": "B", "title": "Preserve ambient playback across player reconnects",
                          "type": "feature", "priority": "P2", "size": "M", "epic": "EP",
                          "depends_on": ["A"], "body_file": "issues/B.md"},
                         {"id": "A", "title": "Author named ambient audio cues in the GM panel",
                          "type": "feature", "priority": "P1", "size": "L", "epic": "EP",
                          "depends_on": [], "body_file": "issues/A.md"}]}
        (self.root / "issues/EP.md").write_text("Track {{A}} and {{B}}.\n")
        (self.root / "issues/A.md").write_text("Parent: {{EP}}.\n")
        (self.root / "issues/B.md").write_text("Parent: {{EP}}. Requires {{A}}.\n")
        self.write_manifest()

    def write_manifest(self):
        self.manifest.write_text(json.dumps(self.data))

    def roadmap(self):
        self.write_manifest()
        return publisher.Roadmap(self.manifest)

    def publish(self, api, roadmap=None):
        with contextlib.redirect_stdout(io.StringIO()):
            publisher.publish(roadmap or self.roadmap(), api)

    def test_default_is_offline_and_dependency_order_is_valid(self):
        with patch.object(publisher.subprocess, "run") as process, contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(publisher.main(["--manifest", str(self.manifest)]), 0)
        process.assert_not_called()
        self.assertEqual(self.roadmap().order, ["EP", "A", "B"])

    def test_rejects_unknown_dependency_and_body_reference(self):
        self.data["issues"][1]["depends_on"] = ["MISSING"]
        with self.assertRaises(publisher.BacklogError):
            self.roadmap()
        self.data["issues"][1]["depends_on"] = ["A"]
        (self.root / "issues/A.md").write_text("Requires {{MISSING}}")
        with self.assertRaises(publisher.BacklogError):
            self.roadmap()

    def test_rejects_dependency_cycle_and_feature_dependency_of_epic(self):
        self.data["issues"][2]["depends_on"] = ["B"]
        with self.assertRaisesRegex(publisher.BacklogError, "cycle"):
            self.roadmap()
        self.data["issues"][2]["depends_on"] = []
        self.data["issues"][0]["depends_on"] = ["A"]
        with self.assertRaisesRegex(publisher.BacklogError, "epics cannot"):
            self.roadmap()

    def test_rejects_path_escape_and_symlink_escape(self):
        self.data["issues"][2]["body_file"] = "../outside.md"
        with self.assertRaises(publisher.BacklogError):
            self.roadmap()
        (self.root / "issues/A.md").unlink()
        (self.root / "issues/A.md").symlink_to("/etc/hosts")
        self.data["issues"][2]["body_file"] = "issues/A.md"
        with self.assertRaises(publisher.BacklogError):
            self.roadmap()

    def test_rejects_malformed_placeholders_and_duplicate_titles(self):
        (self.root / "issues/A.md").write_text("Requires {{A}")
        with self.assertRaisesRegex(publisher.BacklogError, "malformed"):
            self.roadmap()
        (self.root / "issues/A.md").write_text("Requires {{EP}}")
        self.data["issues"][2]["title"] = self.data["issues"][1]["title"]
        with self.assertRaisesRegex(publisher.BacklogError, "duplicate normalized title"):
            self.roadmap()

    def test_duplicate_detection_happens_before_any_write(self):
        api = FakeGitHub([remote_issue(9, self.data["issues"][2]["title"], "Unowned issue")])
        with self.assertRaisesRegex(publisher.BacklogError, "Review possible existing"):
            self.publish(api)
        self.assertFalse(any(call[0] in {"POST", "PATCH"} for call in api.calls))

    def test_explicit_reuse_never_overwrites_unowned_issue(self):
        self.data["issues"][2]["existing_number"] = 9
        api = FakeGitHub([remote_issue(9, self.data["issues"][2]["title"], "Keep manual content", "closed")])
        self.publish(api)
        self.assertEqual(api.issues[0]["body"], "Keep manual content")
        self.assertEqual(api.issues[0]["state"], "closed")
        self.assertFalse(any(call[0] == "PATCH" and call[1].endswith("/9") for call in api.calls))

    def test_rejects_missing_existing_number_and_duplicate_remote_markers(self):
        self.data["issues"][2]["existing_number"] = 999
        with self.assertRaisesRegex(publisher.BacklogError, "does not exist"):
            publisher.inspect_remote(self.roadmap(), [])
        del self.data["issues"][2]["existing_number"]
        issues = [remote_issue(1, "Old", "<!-- maptool-vtt-roadmap:A -->", "closed"),
                  remote_issue(2, "New", "<!-- maptool-vtt-roadmap:A -->")]
        with self.assertRaisesRegex(publisher.BacklogError, "Multiple remote issues"):
            publisher.inspect_remote(self.roadmap(), issues)

    def test_render_forward_reference_then_resolved_link(self):
        roadmap = self.roadmap()
        initial = roadmap.render("EP", {})
        self.assertIn("A —", initial)
        self.assertNotIn("{{", initial)
        linked = roadmap.render("EP", {"A": remote_issue(5, "A"), "B": remote_issue(6, "B")})
        self.assertIn("[#5](https://github.com/Nav1212/maptool/issues/5)", linked)
        self.assertIn("<!-- maptool-vtt-roadmap:EP -->", linked)

    def test_lost_response_recovers_from_remote_marker_without_duplicates(self):
        api = FakeGitHub()
        api.fail_after_create = True
        with self.assertRaisesRegex(publisher.BacklogError, "lost response"):
            self.publish(api)
        self.assertEqual(len(api.issues), 1)
        self.publish(api)
        self.assertEqual(len(api.issues), 3)
        self.publish(api)
        self.assertEqual(len(api.issues), 3)
        self.assertTrue(all("publication pending" not in issue["body"] for issue in api.issues))
        published = json.loads((self.root / "publication.json").read_text())
        self.assertEqual(len(published["issues"]), 3)

    def test_explicit_distinct_decision_allows_similar_title(self):
        candidate = remote_issue(9, self.data["issues"][2]["title"] + " extension")
        self.data["duplicate_decisions"] = {"A": [9]}
        api = FakeGitHub([candidate])
        self.publish(api)
        self.assertEqual(len(api.issues), 4)
        self.assertEqual(api.issues[0]["body"], "")

    def test_pull_requests_are_excluded_from_inventory(self):
        issue = remote_issue(10, "Example pull request")
        issue["pull_request"] = {"url": "unused"}
        _, issues = publisher.remote_inventory(self.roadmap(), FakeGitHub([issue]))
        self.assertEqual(issues, [])


if __name__ == "__main__":
    unittest.main()
