# Browser companion contract fixture

This ACCESS-2 experiment provides an authenticated, read-only **fixture API** using Python's standard library. It is not a MapTool server/client adapter and does not demonstrate native protocol authentication, live visibility calculation, map rendering, or browser gameplay. See the [architecture decision](../../docs/adr/browser-companion.md) for the observed constraints, build blocker, and next live-host experiment.

`fixture.json` contains invented protobuf-JSON-shaped campaign data and separately supplied visibility grants. Those grants deliberately stand in for the real host snapshot provider that still needs implementation. Image payloads are synthetic bytes used for access tests; they are not rendered map images.

## Run the checks

From the repository root:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/browser-companion -p 'test_*.py' -v
```

The tests start their own loopback listener, send authenticated and unauthenticated requests, assert the projection and write rejection, then stop the listener. Test tokens are invented fixture identifiers and are not accepted by MapTool.

## Inspect the fixture manually

Start a local listener with a fresh session file:

```bash
python3 tools/browser-companion/companion_fixture.py \
  --session-file /tmp/maptool-browser-fixture-sessions.json
```

The program selects a loopback port and reports that port plus the session-file path. It generates one-hour sessions for the supplied fixture viewers, creates the file with mode `0600`, and refuses to overwrite an existing file. It does not print tokens. The only routes are:

| Route | Result |
|---|---|
| `GET /v1/campaign` | Campaign label and permitted visible map list |
| `GET /v1/maps/<map-id>` | Allowlisted map/token fields for the authenticated viewer |
| `GET /v1/assets/<asset-id>` | Supplied image bytes granted to that viewer |

Requests use an `Authorization: Bearer` session header. Identity comes from the server's session mapping; query parameters cannot select another viewer. Mutating HTTP methods return 405. Private and unknown resources both return 404. Responses are not cached.

Use the automated test as the reproducible example of constructing requests without printing session values. Stop a manually started listener with Ctrl-C. Generated sessions have no connection to native MapTool passwords, keys, or the platform's injected credentials. There is no public-host binding option, browser frontend, production TLS setup, or application startup registration in this experiment.
