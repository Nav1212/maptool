"""Contract tests exercise a real local HTTP listener, not a live MapTool host."""

import copy
import json
from pathlib import Path
import threading
import time
import unittest
import urllib.error
import urllib.request

from companion_fixture import Session, SnapshotStore, make_server


class CompanionFixtureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = json.loads(Path(__file__).with_name("fixture.json").read_text())
        cls.store = SnapshotStore(cls.fixture)
        cls.server = make_server(cls.store, {
            "fixture-alice-token": Session("alice", time.time() + 3600),
            "fixture-bob-token": Session("bob", time.time() + 3600),
            "fixture-expired-token": Session("alice", time.time() - 1),
        })
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=2)

    def request(self, path, token="fixture-alice-token", method="GET"):
        headers = {} if token is None else {"Authorization": "Bearer " + token}
        request = urllib.request.Request(
            f"http://127.0.0.1:{self.server.server_port}" + path, headers=headers, method=method)
        try:
            response = urllib.request.urlopen(request, timeout=2)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return response.status, response.read(), response.headers

    def test_authentication_precedes_campaign_data(self):
        for token in (None, "wrong-fixture-token", "fixture-expired-token"):
            with self.subTest(token=token):
                status, body, _ = self.request("/v1/campaign", token)
                self.assertEqual(401, status)
                self.assertNotIn(b"fixture-campaign", body)

    def test_campaign_index_is_an_allowlist_projection(self):
        status, body, headers = self.request("/v1/campaign")
        self.assertEqual(200, status)
        result = json.loads(body)
        self.assertEqual([{"id": "courtyard", "name": "Courtyard"}], result["maps"])
        self.assertEqual({"id", "name"}, set(result["campaign"]))
        self.assertEqual("no-store", headers["Cache-Control"])
        self.assertNotIn(b"gmMacro", body)
        self.assertNotIn(b"gm-vault", body)

    def test_map_filters_hidden_gm_layer_and_other_owner_tokens(self):
        status, body, _ = self.request("/v1/maps/courtyard")
        self.assertEqual(200, status)
        result = json.loads(body)["map"]
        self.assertEqual(["hero", "guard"], [token["id"] for token in result["tokens"]])
        self.assertEqual(7, result["revision"])
        self.assertEqual("view-alice", result["rendered_asset_id"])
        for token in result["tokens"]:
            self.assertLessEqual(set(token), {"id", "name", "x", "y", "width", "height", "image_asset_id"})
        for private in (b"gmNotes", b"gmName", b"properties", b"macroProperties", b"topology", b"exposedArea", b"fixture-secret"):
            self.assertNotIn(private, body)

    def test_identity_is_bound_to_authenticated_session(self):
        status, body, _ = self.request("/v1/maps/courtyard", "fixture-bob-token")
        self.assertEqual(200, status)
        self.assertEqual(["guard", "bob-private"], [token["id"] for token in json.loads(body)["map"]["tokens"]])
        self.assertNotIn(b"hero-art", body)
        status, _, _ = self.request("/v1/maps/courtyard?viewer=bob")
        self.assertEqual(400, status)

    def test_private_and_missing_maps_have_same_result(self):
        private = self.request("/v1/maps/gm-vault")[:2]
        missing = self.request("/v1/maps/missing-map")[:2]
        self.assertEqual((404, b'{"error": "not_found"}'), private)
        self.assertEqual(private, missing)

    def test_asset_grants_are_per_viewer(self):
        status, body, _ = self.request("/v1/assets/hero-art")
        self.assertEqual((200, b"synthetic-fixture-hero-bytes"), (status, body))
        for path, token in (("/v1/assets/hero-art", "fixture-bob-token"),
                            ("/v1/assets/gm-private", "fixture-alice-token")):
            with self.subTest(path=path, token=token):
                self.assertEqual(404, self.request(path, token)[0])

    def test_unknown_fields_do_not_cross_projection_boundary(self):
        extended = copy.deepcopy(self.fixture)
        extended["campaign"]["futurePrivateField"] = "private-campaign-value"
        extended["campaign"]["zones"][0]["tokens"][0]["futurePrivateField"] = "private-token-value"
        result = SnapshotStore(extended).map_for("alice", "courtyard")
        self.assertNotIn("private-token-value", json.dumps(result))

    def test_write_methods_are_rejected_without_mutating_fixture(self):
        before = self.request("/v1/maps/courtyard")[1]
        for method in ("POST", "PUT", "PATCH", "DELETE"):
            with self.subTest(method=method):
                self.assertEqual(405, self.request("/v1/maps/courtyard", method=method)[0])
        self.assertEqual(before, self.request("/v1/maps/courtyard")[1])

    def test_paths_and_versions_are_not_generic_asset_or_file_access(self):
        for path in ("/v1/assets/../fixture.json", "/v1/assets/%2e%2e", "/v2/maps/courtyard", "/"):
            with self.subTest(path=path):
                self.assertEqual(404, self.request(path)[0])

    def test_incompatible_fixture_contract_fails_before_listening(self):
        fixture = copy.deepcopy(self.fixture)
        fixture["schema_version"] = 2
        with self.assertRaisesRegex(ValueError, "Unsupported fixture contract"):
            SnapshotStore(fixture)

    def test_script_assets_are_not_served_even_if_granted(self):
        fixture = copy.deepcopy(self.fixture)
        fixture["visibility"]["alice"]["courtyard"]["asset_ids"].append("script")
        self.assertIsNone(SnapshotStore(fixture).asset_for("alice", "script"))


if __name__ == "__main__":
    unittest.main()
