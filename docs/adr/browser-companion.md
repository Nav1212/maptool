# ACCESS-2: browser companion boundary

Status: decision for the next integration experiment. The discovery and fixture experiment are complete; a live MapTool browser adapter is not implemented or verified by this change.

Related backlog: [ACCESS-2](../../planning/vtt-roadmap/issues/ACCESS-2.md), [ACCESS-6](../../planning/vtt-roadmap/issues/ACCESS-6.md), and [ACCESS-4](../../planning/vtt-roadmap/issues/ACCESS-4.md).

## Problem and decision

A browser cannot be implemented by exposing the current campaign protobuf as JSON or by loading desktop add-on HTML in a page. The existing transport, authentication, state, rendering, and macro execution contracts are application-specific.

Use a **host-owned, per-viewer read model with a narrow HTTP adapter** for the first browser milestone. Keep the MapTool host authoritative. Capture an immutable snapshot for an authenticated player, then expose only the supported browser contract. Initially serve a player-rendered map image and a small allowlist of visible token metadata. Leave native desktop transport unchanged.

The first adapter should run with the host rather than logging a separate gateway into the session as a GM. It can share the player's authorization and visibility computations without making a browser hold a GM session. Extract a reusable server/read-model boundary incrementally where the experiment identifies desktop dependencies. Do not undertake a general headless-server extraction before validating one read-only map.

The included Python program is a fixture for this contract, not that host adapter. It accepts synthetic, protobuf-JSON-shaped state plus explicitly supplied visibility grants and verifies authentication and projection behavior over a local HTTP listener. It does not connect to MapTool, derive line of sight, perform native password/key authentication, or render a map.

## Options considered

| Option | What can be reused | Concrete cost or constraint | Decision |
|---|---|---|---|
| Browser implements the native protocol directly | Generated protobuf types, existing WebRTC data channel, native authentication concepts | Must implement framing, Zstandard, chunk assembly, handshake cryptography and key encoding, signaling, asset transfer, compatibility, rendering, and allowed commands. Raw campaign DTOs are not a browser-facing visibility projection. Browsers cannot open the native TCP transport. | Do not use for the first read-only milestone. Reconsider only if a tested shared protocol/read model removes these costs. |
| Separate network gateway that joins as a desktop client | Can reuse a native Java client connection and translate HTTP/WebSocket traffic | A single privileged gateway connection would not supply per-player authorization. Per-player connections would still depend on desktop client initialization, credentials/key storage, asset loading, and reconnect state. | Keep as an optional deployment shape after a reusable authenticated read-model boundary exists; do not forward a GM client's DTOs. |
| Host-side adapter over a reusable read model | Existing player identity, campaign state, visibility logic, and asset cache; no new native transport implementation in JavaScript | Needs an explicit snapshot provider and session mapping, and must remove reliance on the currently selected renderer/player. Rendering may initially require the desktop host and virtual display. | Preferred next experiment and first browser milestone. |
| Extract a complete headless server first | Long-term separation of authoritative state, transports, and rendering | Broad changes to static `MapTool` access, Swing callbacks, host/client lifecycle, add-on execution, and rendering; unnecessary to prove one read-only view. | Incremental follow-up, not a prerequisite rewrite. |

## Observed implementation constraints

### Authentication and identity

- [`handshake.proto`](../../messages/src/main/proto/handshake.proto) defines `ClientInitMsg`, `UseAuthTypeMsg`, `ClientAuthMsg`, and `ConnectionSuccessfulMsg`. A name/version/public-key fingerprint is not itself a successful login. The server completes an authenticated `Player` only after its challenge response succeeds.
- [`ServerHandshake`](../../src/main/java/net/rptools/maptool/server/ServerHandshake.java) checks existing connections, application version outside development mode, blocked accounts, and shared-password or asymmetric-key authentication. Easy-connect approval calls Swing UI. A browser adapter needs an explicit alternative UI boundary, not a fake successful native handshake.
- [`CipherUtil`](../../common/src/main/java/net/rptools/lib/cipher/CipherUtil.java) supplies AES-GCM, PBKDF2 parameters, RSA-OAEP, and key encodings used by [`HandshakeChallenge`](../../src/main/java/net/rptools/maptool/server/HandshakeChallenge.java). A direct JavaScript implementation would need compatibility fixtures for the exact byte encodings and parameters; similarly named browser algorithms are not sufficient evidence of interoperability.
- The read API must obtain identity from an authenticated session, never from a `viewer`, role, token-owner, or map-ID query parameter. Begin with player-only, host-approved sessions mapped to an existing player identity. Define invite exchange, expiry, revocation, and host disconnect behavior before remote deployment. A native transport connection and a browser session must not be assumed to have interchangeable credentials.
- The fixture uses generated, one-hour local bearer sessions solely to exercise the proposed boundary. Those sessions are not accepted by MapTool and are not a proposed replacement for native credentials.

### Transport and signaling

- [`ConnectionFactory`](../../src/main/java/net/rptools/clientserver/ConnectionFactory.java) selects TCP or WebRTC; it does not supply an HTTP or browser client.
- [`AbstractConnection`](../../clientserver/src/main/java/net/rptools/clientserver/simple/connection/AbstractConnection.java) Zstandard-compresses each application message. [`SocketConnection`](../../clientserver/src/main/java/net/rptools/clientserver/simple/connection/SocketConnection.java) sends a four-byte length before the compressed bytes.
- [`WebRTCConnection`](../../clientserver/src/main/java/net/rptools/clientserver/simple/connection/WebRTCConnection.java) also prefixes the compressed message length, then sends chunks up to 16 KiB over `myDataChannel`. A browser peer must reassemble and decompress these chunks before parsing protobuf; a received data-channel chunk is not necessarily a complete protobuf message.
- [`WebRTCServer`](../../clientserver/src/main/java/net/rptools/clientserver/simple/server/WebRTCServer.java) currently points at an external `ws://` signaling service, and the connection configures STUN/TURN endpoints. Neither signaling reachability nor mixed-content compatibility with an HTTPS browser page was verified. The first local HTTP/read-model experiment does not require these services.

### State, visibility, and assets

- [`MapToolServer.addRemoteConnection`](../../src/main/java/net/rptools/maptool/server/MapToolServer.java) sends `campaign.toDto()` after native authentication. That is a native-client contract, not a browser API authorization decision.
- [`CampaignDto`, `ZoneDto`, and `TokenDto`](../../messages/src/main/proto/data_transfer_objects.proto) contain campaign/GM macros, GM notes and names, arbitrary properties, topology, exposed areas, and token ownership. Protocol field presence or `isVisible` alone does not compute a player's complete visible map.
- [`PlayerView`](../../src/main/java/net/rptools/maptool/client/ui/zone/PlayerView.java) represents role plus optional token selection. [`ZoneView.getVisibility`](../../src/main/java/net/rptools/maptool/client/ui/zone/ZoneView.java) and [`ZoneRenderer.renderZone`](../../src/main/java/net/rptools/maptool/client/ui/zone/renderer/ZoneRenderer.java) use this context. An adapter must construct the authenticated player's view and apply server policies; it must not reuse a cached GM view or change the GM's current selection to answer a request.
- The initial snapshot provider should render or capture an immutable player-visible image on the appropriate application/rendering thread, then release it to the HTTP thread. It must define image coordinates, zoom-independent map coordinates, revision, invalidation, and how player-specific fog is represented. This rendering integration is unimplemented in the fixture.
- [`AssetManager`](../../src/main/java/net/rptools/maptool/model/AssetManager.java) caches assets, while [`ServerMessageHandler.getAsset`](../../src/main/java/net/rptools/maptool/server/ServerMessageHandler.java) starts the native asset transfer. Native asset identifiers and cache paths do not become unrestricted HTTP file access. Grant only assets referenced by the player's current snapshot, return opaque IDs, and revoke access with the snapshot/session. For the first milestone, serve image assets only, not add-on HTML or scripts.

### Desktop and macro execution

- [`ClientHandshake`](../../src/main/java/net/rptools/maptool/server/ClientHandshake.java) initializes toolbar visibility, asset panels, campaign data, and add-ons through `MapTool` and Swing. Reusing it as a headless gateway requires more than instantiating a network connection.
- [`HTMLWebViewManager`](../../src/main/java/net/rptools/maptool/client/ui/htmlframe/HTMLWebViewManager.java), [`MacroJavaScriptBridge`](../../src/main/java/net/rptools/maptool/client/functions/MacroJavaScriptBridge.java), and the JavaScript/MTScript bridge execute within desktop token, map, trust, and UI contexts. A browser page does not acquire these contexts by loading the same HTML.
- The read-only API has no macro execution, raw protocol-forwarding, token-update, map-editing, or arbitrary asset-upload endpoint. Existing server command authorization work in ACCESS-6 remains the boundary for later interactive milestones; read access does not imply permission to send native `Message` variants.

## Reproducible experiment and observed result

Run the isolated fixture checks from the repository root:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/browser-companion -p 'test_*.py' -v
```

Result: **11 tests passed** against a real loopback HTTP listener. They cover missing/wrong/expired sessions, identity binding, player-specific token and asset grants, GM/hidden/owner-only token filtering, unknown/private field exclusion, version rejection, and rejected writes without state mutation. No public server or credential is needed. The fixture's image payloads are synthetic byte strings, not rendered MapTool images.

The source fixture mirrors relevant protobuf JSON field names and deliberately includes private fields. It is not produced by a running MapTool host. The visibility grant data is supplied separately and trusted by the prototype; the experiment therefore proves the projection contract and read-only HTTP behavior, **not** correct MapTool fog calculation, native login, or live server interoperability.

The live application experiment was blocked before application compilation by JetBrains dependency redirects. The baseline command and setup remain reproducible:

```bash
source /workspace/.maptool-cloud/activate.sh
cd /workspace/maptool
xvfb-run -a ./gradlew build --continue --no-daemon --console=plain --max-workers=2
```

The observed failure was resolution of `com.jetbrains.intellij.java:java-gui-forms-rt:241.12019`; repository redirects to `d2cico3c979uwg.cloudfront.net` and `artifacts-caching-proxy.aws.intellij.net` returned proxy 403. The onboarding draft contains additive domain requirements; saving a draft does not apply runtime access. The captured instance log is `/workspace/.maptool-cloud/baseline-current.log`. Independent common/clientserver/dicelib tests and message generation passed; that does not establish that the desktop host can be launched.

### Exact next live-host experiment

1. Apply the required dependency access, then run the build above and the desktop smoke check. Resolve any actual compilation or startup errors before claiming native interoperability.
2. Construct one host map with two separately authenticated player identities, one owned token each, a third visible NPC, a hidden token, a GM-layer marker, a GM-only map, and distinct explored fog. Include a known GM note and macro marker to assert their exclusion from output.
3. Implement an in-process `SnapshotProvider` seam receiving an authenticated `Player` and explicit map ID. Capture an immutable player view using the existing policy/visibility/rendering code. Do not substitute the fixture's manually supplied visibility grants for this step.
4. Bind a short-lived browser session to that accepted player identity and adapt the real snapshot to the prototype's `/v1` projection. Compare the returned image/token set with that player's desktop view, including ownership, individual fog, lighting, hidden maps, and revoked map access.
5. Repeat from two sessions, switch the GM's selected map while requests run, revoke one session, and reconnect. A session must not see the other player's snapshot or cause a stale GM-view cache to be reused. Record the actual host/app version, support matrix, and results.

Steps 3–5 are the next experiment, not completed implementation. This concrete build blocker and experiment sequence satisfy the discovery alternative in ACCESS-2; they do not close ACCESS-4 or any production browser feature.

## First usable browser milestone

The first production milestone is **authenticated read-only viewing of the active map in a supported desktop browser**:

- Host enables the companion and approves a player session; browser presents campaign label, permitted map list, connection state, and supported contract version.
- Browser displays a player-rendered map image with pan/zoom and a minimal visible-token overlay; no GM/private state is delivered to implement client-side hiding.
- Map/image/token snapshots have one coherent revision. Reconnect refreshes that snapshot and invalidates old asset grants. Start with bounded polling; streaming deltas can follow after snapshot correctness.
- Map access, individual fog/lighting behavior, session expiry/revocation, and supported browser versions are documented and exercised against a real host plus two distinct players.
- Editing, movement, privileged macros, arbitrary add-on HTML/JavaScript, uploads, voice/video, and a browser GM client are excluded. ACCESS-4 can add owned-token movement later under the same server authorization boundary.

## Deployment and version boundary

The fixture binds only to `127.0.0.1`; it has no public deployment mode and is not started by the normal application. Its generated session file is created with mode `0600` and never overwrites an existing file. Tokens are not logged. Stop the fixture when its local experiment ends.

A production adapter should serve its browser bundle and API from one origin, use HTTPS for remote sessions, and obtain sessions through the host-owned identity/approval boundary. Keep it disabled unless the host enables it. Document the additional listener and firewall requirements separately from native TCP/WebRTC rather than assuming the native port exposes HTTP. External hosting and TLS termination are deployment choices, not services configured by this discovery task.

Version the browser contract independently as `/v1`, include `schema_version` plus snapshot revision, and negotiate supported host capability/version before showing state. Fail explicitly for unsupported major versions. Do not use the native development-mode version exception as a browser compatibility promise. Optional protobuf additions do not automatically become browser response fields: the projection is an allowlist, so future native private fields remain absent until deliberately added to the browser contract.

## Sized follow-up work

| Work | Relative scope | Exit evidence |
|---|---|---|
| Remove current dependency/startup blocker | S setup, then investigate any resulting source failures | Full root build and desktop startup complete |
| Extract immutable per-player snapshot/render provider | L | Real host fixture matches two player views without using GM selection |
| Integrate host-approved browser sessions and map/asset grants | M | Native player identity, expiry, revocation, and role/map checks exercised |
| Build a read-only browser map viewer and bounded snapshot refresh | M | Pan/zoom, coherent image/token revision, reconnect, supported browsers |
| Package an opt-in listener and document remote deployment | M | Disabled-by-default lifecycle, listener shutdown, same-origin HTTPS path |
| Implement owned-token movement under ACCESS-4 | L/XL follow-up | Server accepts legal intents and rejects disallowed/stale moves; desktop compatibility verified |
| General headless host or direct browser-native protocol | Separate architecture work | Measured need and tests demonstrating benefit over the read-model adapter |

These are relative estimates, not dates. The Python fixture and ADR are complete artifacts for the discovery ticket; none of the unimplemented follow-up rows should be marked solved by their existence.
