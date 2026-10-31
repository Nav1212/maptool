# Implementation progress

Two bounded discovery items are completed locally: SCENE-6 and ACCESS-2. No GitHub issue has been created or closed. Feature acceptance requiring application compilation, desktop editors or actual multiplayer remains open.

Implementation draft: [https://github.com/Nav1212/maptool/pull/2](https://github.com/Nav1212/maptool/pull/2). Roadmap draft: [https://github.com/Nav1212/maptool/pull/1](https://github.com/Nav1212/maptool/pull/1).

## Validation boundary

A fresh combined run passed 247 Java tests with zero failures/errors/skips, plus all formatting checks. The browser fixture passed 11 HTTP tests and the publisher passed 12 offline tests. Full application compilation and desktop launch are blocked by JetBrains artifact redirect domains. See `docs/single-map-runtime.md`, `docs/single-map-tiles.md`, `docs/single-map-npcs.md`, and `docs/addon-compatibility.md` for implemented contracts and limitations.

GitHub API reads now work, but Issues is disabled and the integration cannot change that setting. Enabling it requires [repository settings](https://github.com/Nav1212/maptool/settings). No issues were created or closed.

## Ticket status

| ID | State | Implemented scope and remaining work |
|---|---|---|
| [PKG-EPIC](issues/PKG-EPIC.md) | in_progress | Tracking epic; child acceptance criteria remain open. |
| [PKG-1](issues/PKG-1.md) | not_started | No implementation acceptance claimed yet. |
| [PKG-2](issues/PKG-2.md) | partial | Compatibility planner, optional metadata, preflight activation and batch ordering implemented. Application compile/integration tests and UI validation are blocked by JetBrains downloads. |
| [PKG-3](issues/PKG-3.md) | not_started | No implementation acceptance claimed yet. |
| [SYS-EPIC](issues/SYS-EPIC.md) | in_progress | Tracking epic; child acceptance criteria remain open. |
| [CONTENT-1](issues/CONTENT-1.md) | partial | Typed/searchable compendium model, campaign-owned copies and workspace UI implemented; actual application/protocol validation pending. |
| [SYS-1](issues/SYS-1.md) | not_started | No implementation acceptance claimed yet. |
| [CONTENT-2](issues/CONTENT-2.md) | partial | Journal model, sharing, campaign persistence, content workspace and targeted sync code implemented; actual application/two-client validation pending. |
| [CONTENT-3](issues/CONTENT-3.md) | partial | Stable map-pin model and visibility-filtered storage implemented. Map renderer overlays and a verified fog policy remain incomplete. |
| [CONTENT-4](issues/CONTENT-4.md) | not_started | No implementation acceptance claimed yet. |
| [SYS-2](issues/SYS-2.md) | not_started | No implementation acceptance claimed yet. |
| [DEV-EPIC](issues/DEV-EPIC.md) | not_started | Tracking epic; child acceptance criteria remain open. |
| [DEV-1](issues/DEV-1.md) | not_started | No implementation acceptance claimed yet. |
| [DEV-2](issues/DEV-2.md) | not_started | No implementation acceptance claimed yet. |
| [DEV-3](issues/DEV-3.md) | not_started | No implementation acceptance claimed yet. |
| [SCENE-EPIC](issues/SCENE-EPIC.md) | in_progress | Tracking epic; child acceptance criteria remain open. |
| [SCENE-1](issues/SCENE-1.md) | not_started | No implementation acceptance claimed yet. |
| [SCENE-3](issues/SCENE-3.md) | partial | Named polygon/rectangle geometry and snapshot definitions tested. Native map editor/protocol integration remains incomplete. |
| [SCENE-2](issues/SCENE-2.md) | not_started | No implementation acceptance claimed yet. |
| [SCENE-4](issues/SCENE-4.md) | partial | Committed segment enter/exit/crossing consumption and activation receipts tested. Desktop movement/event integration remains incomplete. |
| [SCENE-5](issues/SCENE-5.md) | not_started | No implementation acceptance claimed yet. |
| [SCENE-6](issues/SCENE-6.md) | completed_discovery | Accepted linked-map floor architecture; 4 prototype tests passed. Native floors remain a separate production milestone; current blocker and next native experiment are recorded in docs/adr/scene-floors.md. |
| [MEDIA-EPIC](issues/MEDIA-EPIC.md) | not_started | Tracking epic; child acceptance criteria remain open. |
| [MEDIA-1](issues/MEDIA-1.md) | not_started | No implementation acceptance claimed yet. |
| [MEDIA-2](issues/MEDIA-2.md) | not_started | No implementation acceptance claimed yet. |
| [MEDIA-3](issues/MEDIA-3.md) | not_started | No implementation acceptance claimed yet. |
| [MEDIA-4](issues/MEDIA-4.md) | partial_discovery | Pinned JavaFX evidence, renderer ADR and measured static-fallback fixture completed. Native video decode/pause/disposal metrics remain unverified because this host has FFmpeg ABI 61 and JavaFX22 plugins support through ABI 60. |
| [ACCESS-EPIC](issues/ACCESS-EPIC.md) | not_started | Tracking epic; child acceptance criteria remain open. |
| [ACCESS-1](issues/ACCESS-1.md) | not_started | No implementation acceptance claimed yet. |
| [ACCESS-6](issues/ACCESS-6.md) | not_started | No implementation acceptance claimed yet. |
| [ACCESS-3](issues/ACCESS-3.md) | not_started | No implementation acceptance claimed yet. |
| [ACCESS-2](issues/ACCESS-2.md) | completed_discovery | Browser boundary ADR and authenticated read-only projection fixture completed; 11 HTTP contract tests passed. Live MapTool interoperability is not claimed; exact next experiment documented. |
| [ACCESS-4](issues/ACCESS-4.md) | not_started | No implementation acceptance claimed yet. |
| [ACCESS-5](issues/ACCESS-5.md) | not_started | No implementation acceptance claimed yet. |
| [MAPAUTO-EPIC](issues/MAPAUTO-EPIC.md) | in_progress | Tracking epic; child acceptance criteria remain open. |
| [MAPAUTO-1](issues/MAPAUTO-1.md) | partial | EncounterSession pins map/run/authority explicitly. Desktop workspace and portable actual-map package remain incomplete. |
| [MAPAUTO-2](issues/MAPAUTO-2.md) | partial | Shared logical clock, bounded queue, pause/step/cancel, durability hooks and identity validation tested. Production host event adapter remains incomplete. |
| [MAPAUTO-3](issues/MAPAUTO-3.md) | partial | Composed clock/tile/NPC/world checkpoints validate and restore paused in integration tests. Actual-map atomic restore and GM controls remain incomplete. |
| [MAPAUTO-4](issues/MAPAUTO-4.md) | partial | Pause/step/history and injectable durability APIs implemented. The desktop GM console and preview workflow remain incomplete. |
| [TILE-EPIC](issues/TILE-EPIC.md) | in_progress | Tracking epic; child acceptance criteria remain open. |
| [TILE-1](issues/TILE-1.md) | partial | JSON-friendly region/tile definitions and validation implemented. Visual binding editor remains incomplete. |
| [TILE-2](issues/TILE-2.md) | partial | Reference dice/property/comparator adapter produces recorded components and structured outcomes; form-based authoring and production host binding remain incomplete. |
| [TILE-3](issues/TILE-3.md) | partial | Ordered outcome/always actions and interruption receipts tested. Form-based preset authoring and production handlers remain incomplete. |
| [TILE-4](issues/TILE-4.md) | partial | Actor/global once, per-entry/per-turn and cooldown policies tested. Desktop policy authoring and real host turn events remain incomplete. |
| [TILE-5](issues/TILE-5.md) | not_started | No implementation acceptance claimed yet. |
| [TILE-6](issues/TILE-6.md) | partial | Explicit interaction API exists. Object lock/state presets, ownership/reach checks and player controls remain incomplete. |
| [TILE-7](issues/TILE-7.md) | partial | Canonical activation/step receipts, duplicate/reentrant handling and interruption handling tested. Actual client transport and durable production effect adapter remain incomplete. |
| [TILE-8](issues/TILE-8.md) | not_started | No implementation acceptance claimed yet. |
| [NPC-EPIC](issues/NPC-EPIC.md) | in_progress | Tracking epic; child acceptance criteria remain open. |
| [NPC-1](issues/NPC-1.md) | partial | Versioned profiles, map opt-in, assignments and JSON snapshots implemented. GM assignment UI and map lifecycle wiring remain incomplete. |
| [NPC-2](issues/NPC-2.md) | partial | Patrol loops, ping-pong, waits and blocked-route outcomes tested. Route authoring UI and actual map path adapter remain incomplete. |
| [NPC-3](issues/NPC-3.md) | partial | Observer-specific snapshots and deterministic behavior states tested. Actual map vision/perception adapter remains incomplete. |
| [NPC-4](issues/NPC-4.md) | partial | Caller-driven NPC events composed with shared logical clock. Live initiative/exploration host hooks remain incomplete. |
| [NPC-5](issues/NPC-5.md) | partial | Budget, stale route, blocked and unsupported outcomes tested through adapter contract. Actual topology/collision/veto adapter remains incomplete. |
| [NPC-6](issues/NPC-6.md) | partial | Deterministic action selection, approved dispatch and recorded outcomes tested. Production framework/macro bridge remains incomplete. |
| [NPC-7](issues/NPC-7.md) | partial | Pure preview, inspection, pause/manual/target overrides implemented. Desktop GM console remains incomplete. |
| [NPC-8](issues/NPC-8.md) | partial | Scenario regression fixtures exercise lifecycle, duplicate events, receipt expiry, pause/error and snapshot replay. Real scene adapters/workload validation remain incomplete. |
| [NPC-9](issues/NPC-9.md) | not_started | No implementation acceptance claimed yet. |
| [MAPAUTO-5](issues/MAPAUTO-5.md) | partial | Real dicelib/tile/NPC composition exercised by integration fixtures. A playable desktop starter map and two-client walkthrough remain incomplete. |

## Next iteration

1. Apply the saved JetBrains network additions and enable GitHub Issues.
2. Compile the actual application, run new application integration tests, then verify desktop and two-client workflows.
3. Finish the host/world adapters, tile/route editors, GM console and playable encounter before resolving the single-map tickets.
4. Continue remaining feature/discovery tickets in dependency order, with closure based on acceptance evidence.
