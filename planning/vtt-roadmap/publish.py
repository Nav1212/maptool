#!/usr/bin/env python3
"""Validate or publish the MapTool roadmap. The default is entirely offline."""

from __future__ import annotations

import argparse
import difflib
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path


REPOSITORY = "Nav1212/maptool"
MARKER = re.compile(r"<!-- maptool-vtt-roadmap:([A-Z][A-Z0-9-]*) -->")
PLACEHOLDER = re.compile(r"\{\{([^{}]+)\}\}")
ID = re.compile(r"[A-Z][A-Z0-9-]*")


class BacklogError(Exception):
    pass


def save_json(path, value):
    path = Path(path)
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=path.parent,
                                     prefix=".roadmap-", delete=False) as stream:
        temporary = Path(stream.name)
        json.dump(value, stream, indent=2, ensure_ascii=False)
        stream.write("\n")
    temporary.replace(path)


def title_key(title):
    title = re.sub(r"^\[?(?:feature|epic|engineering|discovery)\]?\s*:\s*", "",
                   title.strip(), flags=re.I)
    return " ".join(re.findall(r"\w+", title.casefold()))


class Roadmap:
    def __init__(self, manifest):
        self.path = Path(manifest).resolve()
        self.root = self.path.parent
        try:
            self.data = json.loads(self.path.read_text(encoding="utf-8"))
        except (OSError, ValueError) as error:
            raise BacklogError(f"Cannot read manifest: {error}") from error
        if self.data.get("repository") != REPOSITORY:
            raise BacklogError(f"Manifest repository must be {REPOSITORY}")
        if not re.fullmatch(r"[a-fA-F0-9]{40}", self.data.get("source_commit", "")):
            raise BacklogError("source_commit must be the full 40-character source SHA")
        issues = self.data.get("issues")
        if not isinstance(issues, list) or not issues:
            raise BacklogError("Manifest must contain a nonempty issues array")
        self.items = {}
        self.bodies = {}
        titles, numbers = set(), set()
        for item in issues:
            key = item.get("id", "")
            if not isinstance(key, str) or not ID.fullmatch(key) or key in self.items:
                raise BacklogError(f"Invalid or duplicate issue ID: {key!r}")
            title = item.get("title")
            if not isinstance(title, str) or not title.strip() or len(title) > 256:
                raise BacklogError(f"{key}: title must contain 1–256 characters")
            normalized = title_key(title)
            if normalized in titles:
                raise BacklogError(f"{key}: duplicate normalized title")
            titles.add(normalized)
            if item.get("type") not in {"epic", "feature", "discovery", "engineering"}:
                raise BacklogError(f"{key}: unsupported issue type")
            if item.get("priority") not in {"P1", "P2", "P3"}:
                raise BacklogError(f"{key}: priority must be P1, P2 or P3")
            if item.get("size") not in {"S", "M", "L", "XL"}:
                raise BacklogError(f"{key}: invalid relative size")
            for field in ("depends_on", "tracks"):
                values = item.get(field, [])
                if not isinstance(values, list) or any(not isinstance(v, str) for v in values):
                    raise BacklogError(f"{key}: {field} must be an array of IDs")
                if len(set(values)) != len(values):
                    raise BacklogError(f"{key}: duplicate {field} references")
            body_name = item.get("body_file")
            if not isinstance(body_name, str) or Path(body_name).is_absolute():
                raise BacklogError(f"{key}: body_file must be a relative file path")
            body_path = (self.root / body_name).resolve()
            if not body_path.is_relative_to(self.root) or not body_path.is_file():
                raise BacklogError(f"{key}: body_file is missing or escapes the roadmap directory")
            self.bodies[key] = body_path.read_text(encoding="utf-8")
            if MARKER.search(self.bodies[key]):
                raise BacklogError(f"{key}: body must not contain a publisher ownership marker")
            number = item.get("existing_number")
            if number is not None:
                if type(number) is not int or number <= 0 or number in numbers:
                    raise BacklogError(f"{key}: existing_number must be a unique positive issue number")
                numbers.add(number)
            self.items[key] = item
        for key, item in self.items.items():
            refs = item.get("depends_on", []) + item.get("tracks", [])
            if item.get("epic"):
                refs.append(item["epic"])
            for ref in refs:
                if ref not in self.items or ref == key:
                    raise BacklogError(f"{key}: unknown or self reference {ref}")
            if item.get("epic") and self.items[item["epic"]]["type"] != "epic":
                raise BacklogError(f"{key}: parent must be an epic")
            if item.get("tracks") and item["type"] != "epic":
                raise BacklogError(f"{key}: only epics may track children")
            if item["type"] == "epic" and any(self.items[d]["type"] != "epic"
                                                for d in item.get("depends_on", [])):
                raise BacklogError(f"{key}: epics cannot depend on implementation issues")
            body = self.bodies[key]
            for ref in PLACEHOLDER.findall(body):
                if ref not in self.items:
                    raise BacklogError(f"{key}: unknown body reference {{{{{ref}}}}}")
            stripped = PLACEHOLDER.sub("", body)
            if "{{" in stripped or "}}" in stripped:
                raise BacklogError(f"{key}: malformed reference placeholder")
        self.order = self._ordered()
        self.decisions = self.data.get("duplicate_decisions", {})
        if not isinstance(self.decisions, dict):
            raise BacklogError("duplicate_decisions must map local IDs to reviewed issue numbers")
        for key, numbers in self.decisions.items():
            if key not in self.items or not isinstance(numbers, list) or any(
                    type(n) is not int or n <= 0 for n in numbers):
                raise BacklogError(f"Invalid duplicate_decisions entry: {key}")

    def _ordered(self):
        result, pending = [], set(self.items)
        while pending:
            available = [key for key in pending
                         if all(dep in result for dep in self.items[key].get("depends_on", []))]
            if not available:
                raise BacklogError("depends_on contains a cycle")
            available.sort(key=lambda key: (self.items[key]["type"] != "epic",
                                            list(self.items).index(key)))
            key = available[0]
            result.append(key)
            pending.remove(key)
        return result

    def render(self, key, resolved):
        def replace(match):
            ref = match.group(1)
            if ref in resolved:
                issue = resolved[ref]
                return f"[#{issue['number']}]({issue['html_url']})"
            return f"{ref} — {self.items[ref]['title']} (publication pending)"

        return PLACEHOLDER.sub(replace, self.bodies[key]).rstrip() + f"\n\n<!-- maptool-vtt-roadmap:{key} -->\n"


class GitHub:
    def __init__(self, repository=REPOSITORY):
        self.repository = repository

    def api(self, endpoint, method="GET", payload=None):
        command = ["gh", "api", "--hostname", "github.com", "--method", method, endpoint]
        try:
            if payload is None:
                response = subprocess.run(command, text=True, capture_output=True, check=False)
            else:
                with tempfile.NamedTemporaryFile("w+", encoding="utf-8") as stream:
                    json.dump(payload, stream, ensure_ascii=False)
                    stream.flush()
                    response = subprocess.run(command + ["--input", stream.name], text=True,
                                              capture_output=True, check=False)
        except OSError as error:
            raise BacklogError(f"Cannot run GitHub CLI: {error}") from error
        if response.returncode:
            suffix = " A write may have succeeded; rerun only after inspecting remote markers." if method != "GET" else ""
            raise BacklogError(f"GitHub {method} failed: {response.stderr.strip() or response.stdout.strip()}.{suffix}")
        try:
            return json.loads(response.stdout)
        except ValueError as error:
            raise BacklogError(f"GitHub returned invalid JSON for {method} {endpoint}; inspect remote state before retrying writes") from error

    def pages(self, resource):
        result = []
        for page in range(1, 1001):
            separator = "&" if "?" in resource else "?"
            batch = self.api(f"repos/{self.repository}/{resource}{separator}per_page=100&page={page}")
            if not isinstance(batch, list):
                raise BacklogError("Unexpected paginated GitHub response")
            result.extend(batch)
            if len(batch) < 100:
                return result
        raise BacklogError("GitHub inventory exceeds the pagination limit; no writes performed")


def remote_inventory(roadmap, api):
    repository = api.api(f"repos/{REPOSITORY}")
    if repository.get("full_name", "").casefold() != REPOSITORY.casefold():
        raise BacklogError("GitHub returned a different repository identity")
    if not repository.get("has_issues") or repository.get("archived"):
        raise BacklogError("The repository is archived or GitHub Issues is disabled")
    issues = [issue for issue in api.pages("issues?state=all") if "pull_request" not in issue]
    clean = [{field: issue.get(field) for field in
              ("number", "title", "body", "html_url", "state")} for issue in issues]
    save_json(roadmap.root / "remote-issues.json", {
        "repository": REPOSITORY,
        "permissions": repository.get("permissions"),
        "issues": clean,
    })
    return repository, clean


def inspect_remote(roadmap, issues):
    by_number = {issue["number"]: issue for issue in issues}
    markers, owned, resolved = {}, set(), {}
    for issue in issues:
        local_ids = set(MARKER.findall(issue.get("body") or ""))
        if len(local_ids) > 1 and local_ids.intersection(roadmap.items):
            raise BacklogError(f"Remote issue #{issue['number']} contains multiple roadmap IDs")
        for key in local_ids.intersection(roadmap.items):
            if key in markers:
                raise BacklogError(f"Multiple remote issues carry ownership marker {key}")
            markers[key] = issue
    for key, item in roadmap.items.items():
        explicit = item.get("existing_number")
        if explicit is not None:
            if explicit not in by_number:
                raise BacklogError(f"{key}: mapped issue #{explicit} does not exist in the issue inventory")
            remote_marks = set(MARKER.findall(by_number[explicit].get("body") or ""))
            if remote_marks and remote_marks != {key}:
                raise BacklogError(f"{key}: mapped issue belongs to a different roadmap ID")
            if key in markers and markers[key]["number"] != explicit:
                raise BacklogError(f"{key}: existing_number conflicts with its remote marker")
            resolved[key] = by_number[explicit]
        elif key in markers:
            resolved[key] = markers[key]
        if key in resolved and MARKER.search(resolved[key].get("body") or ""):
            owned.add(key)
    candidates = {}
    for key, item in roadmap.items.items():
        if key in resolved:
            continue
        normalized = title_key(item["title"])
        for issue in issues:
            if issue["number"] in roadmap.decisions.get(key, []):
                continue
            other = title_key(issue.get("title") or "")
            ratio = difflib.SequenceMatcher(None, normalized, other).ratio()
            ours, theirs = set(normalized.split()), set(other.split())
            overlap = len(ours & theirs) / max(len(ours | theirs), 1)
            if normalized == other or ratio >= 0.88 or (len(ours & theirs) >= 5 and overlap >= 0.8):
                candidates.setdefault(key, []).append(issue)
    return resolved, owned, candidates


def describe_candidates(candidates):
    return "\n".join(f"{key}: #{issue['number']} ({issue['state']}) {issue['title']}"
                     for key, issues in candidates.items() for issue in issues)


def persist_publication(roadmap, resolved):
    save_json(roadmap.root / "publication.json", {
        "repository": REPOSITORY,
        "source_commit": roadmap.data["source_commit"],
        "issues": {key: {field: issue.get(field) for field in ("number", "html_url", "state")}
                   for key, issue in resolved.items()},
    })


def publish(roadmap, api):
    repository, issues = remote_inventory(roadmap, api)
    resolved, owned, candidates = inspect_remote(roadmap, issues)
    if candidates:
        raise BacklogError("Review possible existing issues before publishing:\n" + describe_candidates(candidates)
                           + "\nReuse with item.existing_number, or record distinct candidates in duplicate_decisions.")
    permissions = repository.get("permissions") or {}
    print("Repository permissions: " + json.dumps(permissions, sort_keys=True))
    labels = {label["name"] for label in api.pages("labels")}
    persist_publication(roadmap, resolved)
    for key in roadmap.order:
        if key in resolved:
            print(f"Reuse {key}: {resolved[key]['html_url']} ({resolved[key]['state']})")
            continue
        item = roadmap.items[key]
        label = "epic" if item["type"] == "epic" else (
            "code-maintenance" if item["type"] == "engineering" else "feature")
        payload = {"title": item["title"], "body": roadmap.render(key, resolved)}
        if label in labels:
            payload["labels"] = [label]
        issue = api.api(f"repos/{REPOSITORY}/issues", "POST", payload)
        if not issue.get("number") or not issue.get("html_url"):
            raise BacklogError("Issue creation returned incomplete state; inspect remote markers before retrying")
        resolved[key] = issue
        owned.add(key)
        persist_publication(roadmap, resolved)
        print(f"Created {key}: {issue['html_url']}")
    # Resolve epic child links and any forward references only in publisher-owned bodies.
    for key in roadmap.order:
        if key not in owned:
            continue
        rendered = roadmap.render(key, resolved)
        if rendered != (resolved[key].get("body") or ""):
            issue = api.api(f"repos/{REPOSITORY}/issues/{resolved[key]['number']}",
                            "PATCH", {"body": rendered})
            resolved[key] = issue
            persist_publication(roadmap, resolved)
    print(f"Published/reused {len(resolved)} issues; links are in {roadmap.root / 'publication.json'}")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=Path(__file__).with_name("issues.json"))
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--dry-run", action="store_true", help="Validate locally; no network (default)")
    modes.add_argument("--check-remote", action="store_true", help="Read issues and identify duplicate candidates")
    modes.add_argument("--apply", action="store_true", help="Create authorized issues and resolve their links")
    args = parser.parse_args(argv)
    try:
        roadmap = Roadmap(args.manifest)
        if args.apply:
            publish(roadmap, GitHub())
        elif args.check_remote:
            repository, issues = remote_inventory(roadmap, GitHub())
            resolved, _, candidates = inspect_remote(roadmap, issues)
            print(f"Read {len(issues)} issues; {len(resolved)} roadmap matches.")
            print("Repository permissions: " + json.dumps(repository.get("permissions"), sort_keys=True))
            if candidates:
                print("Review these possible duplicates before publication:\n" + describe_candidates(candidates))
                return 2
            print("No exact or strong title-similarity candidates found; inspect remote-issues.json for semantic duplicates.")
        else:
            print(f"Validated {len(roadmap.items)} issues for {REPOSITORY}; no network calls or GitHub changes.")
            for key in roadmap.order:
                item = roadmap.items[key]
                print(f"{key}\t{item['priority']}\t{item['type']}\t{item['title']}")
        return 0
    except (BacklogError, OSError, KeyError, TypeError, ValueError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
