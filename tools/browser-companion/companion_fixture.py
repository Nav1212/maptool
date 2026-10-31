#!/usr/bin/env python3
"""Loopback-only ACCESS-2 contract experiment; not a MapTool server adapter."""

from __future__ import annotations

import argparse
import base64
import copy
import hmac
import json
import os
from pathlib import Path
import re
import secrets
import time
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit


CONTRACT_VERSION = 1
IDENTIFIER = re.compile(r"[A-Za-z0-9_-]{1,128}")
IMAGE_TYPES = {"image/png", "image/jpeg", "image/webp"}


@dataclass(frozen=True)
class Session:
    viewer: str
    expires_at: float


class SnapshotStore:
    """Project supplied protobuf-JSON-shaped state using supplied viewer grants.

    The grants stand in for an unimplemented MapTool-authoritative visibility
    provider. This class intentionally does not approximate fog or line of sight.
    """

    def __init__(self, fixture: dict):
        if fixture.get("schema_version") != CONTRACT_VERSION:
            raise ValueError("Unsupported fixture contract version")
        self.fixture = copy.deepcopy(fixture)
        self.campaign = self.fixture["campaign"]
        self.zones = {zone["id"]: zone for zone in self.campaign.get("zones", [])}
        self.visibility = self.fixture["visibility"]
        self.assets = self.fixture.get("assets", {})

    def campaign_for(self, viewer: str) -> dict:
        maps = []
        for zone_id in sorted(self.visibility.get(viewer, {})):
            zone = self.zones.get(zone_id)
            if zone and zone.get("isVisible") is True:
                maps.append({"id": zone_id, "name": zone.get("playerAlias") or zone["name"]})
        return {
            "schema_version": CONTRACT_VERSION,
            "campaign": {"id": self.campaign["id"], "name": self.campaign["name"]},
            "maps": maps,
        }

    def map_for(self, viewer: str, zone_id: str) -> dict | None:
        zone = self.zones.get(zone_id)
        grant = self.visibility.get(viewer, {}).get(zone_id)
        if not zone or not grant or zone.get("isVisible") is not True:
            return None
        visible = set(grant.get("visible_token_ids", []))
        assets = set(grant.get("asset_ids", []))
        tokens = []
        for token in zone.get("tokens", []):
            if token.get("id") not in visible or token.get("isVisible") is not True:
                continue
            if token.get("layer") != "TOKEN":
                continue
            owned = token.get("ownerType") == 1 or viewer in token.get("ownerList", [])
            if token.get("visibleOnlyToOwner") is True and not owned:
                continue
            public = {key: token[key] for key in ("id", "name", "x", "y", "width", "height") if key in token}
            image = token.get("currentImageAsset")
            if image in assets:
                public["image_asset_id"] = image
            tokens.append(public)
        result = {
            "schema_version": CONTRACT_VERSION,
            "map": {
                "id": zone_id,
                "name": zone.get("playerAlias") or zone["name"],
                "width": zone.get("width", 0),
                "height": zone.get("height", 0),
                "revision": grant["revision"],
                "tokens": tokens,
            },
        }
        rendered = grant.get("rendered_asset_id")
        if rendered in assets:
            result["map"]["rendered_asset_id"] = rendered
        return result

    def asset_for(self, viewer: str, asset_id: str) -> tuple[str, bytes] | None:
        grants = self.visibility.get(viewer, {})
        allowed = any(
            asset_id in grant.get("asset_ids", [])
            and self.zones.get(zone_id, {}).get("isVisible") is True
            for zone_id, grant in grants.items()
        )
        asset = self.assets.get(asset_id)
        if not allowed or not asset or asset.get("content_type") not in IMAGE_TYPES:
            return None
        return asset["content_type"], base64.b64decode(asset["data_base64"], validate=True)


def make_server(store: SnapshotStore, sessions: dict[str, Session], port: int = 0):
    class Handler(BaseHTTPRequestHandler):
        server_version = "MapToolContractFixture/1"
        sys_version = ""

        def log_message(self, *_args):
            # Never log request headers, session tokens, or private fixture data.
            pass

        def respond(self, status: int, value: dict | bytes, content_type="application/json"):
            data = value if isinstance(value, bytes) else json.dumps(value, sort_keys=True).encode()
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.end_headers()
            self.wfile.write(data)

        def viewer(self) -> str | None:
            authorization = self.headers.get("Authorization", "")
            if not authorization.startswith("Bearer "):
                return None
            supplied = authorization[7:]
            for token, session in sessions.items():
                if hmac.compare_digest(supplied.encode("utf-8"), token.encode("utf-8")) and time.time() < session.expires_at:
                    return session.viewer
            return None

        def do_GET(self):
            viewer = self.viewer()
            if viewer is None:
                self.respond(401, {"error": "authentication_required"})
                return
            request = urlsplit(self.path)
            if request.query or request.fragment:
                self.respond(400, {"error": "query_parameters_unsupported"})
                return
            if request.path == "/v1/campaign":
                self.respond(200, store.campaign_for(viewer))
                return
            for prefix, method in (("/v1/maps/", store.map_for), ("/v1/assets/", store.asset_for)):
                if request.path.startswith(prefix):
                    identifier = request.path[len(prefix):]
                    if not IDENTIFIER.fullmatch(identifier):
                        break
                    result = method(viewer, identifier)
                    if result is None:
                        break
                    if prefix == "/v1/assets/":
                        content_type, data = result
                        self.respond(200, data, content_type)
                    else:
                        self.respond(200, result)
                    return
            self.respond(404, {"error": "not_found"})

        def reject_write(self):
            self.respond(405, {"error": "read_only_fixture"})

        do_POST = reject_write
        do_PUT = reject_write
        do_PATCH = reject_write
        do_DELETE = reject_write
        do_HEAD = reject_write
        do_OPTIONS = reject_write

    return ThreadingHTTPServer(("127.0.0.1", port), Handler)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture", type=Path, default=Path(__file__).with_name("fixture.json"))
    parser.add_argument("--port", type=int, default=0)
    parser.add_argument("--session-file", type=Path, required=True,
                        help="New local file for generated fixture-only session tokens")
    args = parser.parse_args()
    store = SnapshotStore(json.loads(args.fixture.read_text()))
    sessions = {secrets.token_urlsafe(32): Session(viewer, time.time() + 3600)
                for viewer in store.visibility}
    descriptor = os.open(args.session_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w") as output:
        json.dump([dict(token=token, viewer=session.viewer, expires_at=session.expires_at)
                   for token, session in sessions.items()], output)
        output.write("\n")
    server = make_server(store, sessions, args.port)
    print(f"Fixture-only read API listening on loopback port {server.server_port}; sessions: {args.session_file}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
