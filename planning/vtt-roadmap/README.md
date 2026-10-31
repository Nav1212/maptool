# MapTool VTT development backlog

Target: [Nav1212/maptool](https://github.com/Nav1212/maptool). Inspected source: `ee0f937c736b6dfe7c14ad2a950e818b05cf3b76`.

This backlog contains **59 proposed issues**: 9 tracking epics, 44 feature deliverables, 4 bounded discovery tasks, and 2 supporting engineering tasks. It covers the VTT comparison and the requested single-map automation focus. It does not claim to enumerate every possible defect.

Existing lighting, topology, macro scripting, JavaScript, HTML interfaces, add-on bundles, resource libraries, notes, audio, and networking are foundations to extend. These proposals should not be interpreted as claims those capabilities are absent.

## Publication status

**Prepared locally; not published as GitHub issues.** GitHub API reads now work and the all-state inventory is empty. Issues is disabled in the repository: issue creation returns HTTP 410, and enabling it through the integration returns HTTP 403. Enable **Settings → General → Features → Issues** in [repository settings](https://github.com/Nav1212/maptool/settings), then run the publisher. No new token is requested.

See [implementation progress](IMPLEMENTATION.md) for code and validation work. No ticket has been marked resolved; component tests do not establish unfinished application/editor/multiplayer acceptance criteria. Full application validation currently needs the two JetBrains redirect domains already added to the saved environment draft.

## Scope and priorities

- **P1:** workflow foundations, the single-map automation slice, and joining improvements to consider first.
- **P2:** capability expansion built on those foundations.
- **P3:** optional or architecture-dependent follow-up.
- **S/M/L/XL:** relative scope, not delivery dates. XL epics track child issues; the browser MVP is intentionally a large, bounded deliverable.

Foundry and other vendor sites were inaccessible during comparison. Competitor positioning therefore reflects established capabilities rather than a current-release audit. The requirements here are supported by the linked MapTool source and are proposed product decisions.

## Current focus: single-map automation

The new [single-map roadmap](SINGLE_MAP.md) adds 25 drafts for a focused encounter workspace, a shared simulation clock, automatic tile checks and consequences, NPC patrol/behavior controls, and reusable encounter state. Start with this vertical slice before the broader ecosystem work. The first playable version uses current token properties and MTScript and does not require a new game-system marketplace or external AI service.

## Broader VTT sequence

1. Establish package compatibility, scripting examples, searchable records, and joining diagnostics.
2. Add system profiles, interactive sheets, journals, native doors, scene regions, and shared audio cues.
3. Build workflows on top: package updates, record drops, journal pins, player doors, region triggers, spatial audio, and playlists.
4. Decide floor/elevation, animated media, role presets, scene import, and browser architecture using bounded prototypes. Implement the browser MVP only after the architecture decision.

## Issue index

| ID | Proposed issue | Priority | Size | Dependencies |
|---|---|---|---|---|
| PKG-EPIC | [[Epic]: Discover, install, and maintain compatible add-on packages](issues/PKG-EPIC.md) | P1 | XL | — |
| PKG-1 | [[Feature]: Browse and install add-ons from a versioned package catalog](issues/PKG-1.md) | P1 | L | — |
| PKG-2 | [[Feature]: Validate add-on compatibility and dependencies before activation](issues/PKG-2.md) | P1 | L | — |
| PKG-3 | [[Feature]: Preview add-on updates and restore the previous installed version](issues/PKG-3.md) | P2 | L | PKG-1, PKG-2 |
| SYS-EPIC | [[Epic]: Make rules frameworks, sheets, and campaign content discoverable](issues/SYS-EPIC.md) | P1 | XL | — |
| CONTENT-1 | [[Feature]: Browse searchable rules compendiums supplied by add-ons](issues/CONTENT-1.md) | P1 | L | — |
| SYS-1 | [[Feature]: Create campaigns from installed game-system profiles](issues/SYS-1.md) | P1 | L | PKG-2 |
| CONTENT-2 | [[Feature]: Organize standalone campaign journals and share selected entries](issues/CONTENT-2.md) | P2 | L | — |
| CONTENT-3 | [[Feature]: Link journal entries to permission-aware map pins](issues/CONTENT-3.md) | P2 | L | CONTENT-2 |
| CONTENT-4 | [[Feature]: Support framework-defined compendium drag-and-drop actions](issues/CONTENT-4.md) | P2 | L | CONTENT-1, SYS-2 |
| SYS-2 | [[Feature]: Open registered interactive character sheets from tokens](issues/SYS-2.md) | P2 | L | SYS-1 |
| DEV-EPIC | [[Epic]: Provide a repeatable add-on authoring and validation workflow](issues/DEV-EPIC.md) | P1 | XL | — |
| DEV-1 | [[Feature]: Generate a versioned scripting inventory and publish a runnable add-on starter](issues/DEV-1.md) | P1 | M | — |
| DEV-2 | [[Feature]: Validate and reload a local add-on with actionable diagnostics](issues/DEV-2.md) | P2 | M | DEV-1 |
| DEV-3 | [[Discovery]: Evaluate a bounded Foundry scene import path](issues/DEV-3.md) | P3 | M | — |
| SCENE-EPIC | [[Epic]: Build interactive scenes with doors, regions, and elevation](issues/SCENE-EPIC.md) | P1 | XL | — |
| SCENE-1 | [[Feature]: Add native doors with GM open and close controls](issues/SCENE-1.md) | P1 | L | — |
| SCENE-3 | [[Feature]: Add persistent named scene regions and GM authoring tools](issues/SCENE-3.md) | P1 | L | — |
| SCENE-2 | [[Feature]: Add player door interactions and GM-controlled locks](issues/SCENE-2.md) | P2 | M | SCENE-1, ACCESS-6 |
| SCENE-4 | [[Feature]: Execute trusted region enter, exit, and crossing triggers](issues/SCENE-4.md) | P1 | L | SCENE-3, ACCESS-6 |
| SCENE-5 | [[Feature]: Add typed token elevation and optional elevation-aware measurement](issues/SCENE-5.md) | P2 | L | — |
| SCENE-6 | [[Discovery]: Define floor-aware maps and visibility with a bounded design prototype](issues/SCENE-6.md) | P3 | M | SCENE-5 |
| MEDIA-EPIC | [[Epic]: Provide integrated session audio and evaluate animated maps](issues/MEDIA-EPIC.md) | P2 | XL | — |
| MEDIA-1 | [[Feature]: Add GM ambient audio cues with shared playback state](issues/MEDIA-1.md) | P2 | L | ACCESS-6 |
| MEDIA-2 | [[Feature]: Add map-local ambient sound sources with distance attenuation](issues/MEDIA-2.md) | P2 | L | MEDIA-1 |
| MEDIA-3 | [[Feature]: Manage reusable playlists and scene audio transitions](issues/MEDIA-3.md) | P2 | L | MEDIA-1 |
| MEDIA-4 | [[Discovery]: Prototype animated map backgrounds and document supported media limits](issues/MEDIA-4.md) | P3 | M | — |
| ACCESS-EPIC | [[Epic]: Simplify joining and establish a browser player path](issues/ACCESS-EPIC.md) | P1 | XL | — |
| ACCESS-1 | [[Feature]: Add connection preflight diagnostics and actionable join guidance](issues/ACCESS-1.md) | P1 | M | — |
| ACCESS-6 | [[Engineering]: Define authorization contracts for new scene, audio, and browser commands](issues/ACCESS-6.md) | P1 | L | — |
| ACCESS-3 | [[Feature]: Guide new GMs and players through their first successful session](issues/ACCESS-3.md) | P2 | M | ACCESS-1 |
| ACCESS-2 | [[Discovery]: Define a browser companion architecture and validate a minimal protocol slice](issues/ACCESS-2.md) | P3 | M | ACCESS-6 |
| ACCESS-4 | [[Feature]: Deliver a bounded browser player client for map viewing and owned-token movement](issues/ACCESS-4.md) | P3 | XL | ACCESS-2, ACCESS-6 |
| ACCESS-5 | [[Feature]: Define configurable session role presets for observers and assistant GMs](issues/ACCESS-5.md) | P3 | L | ACCESS-6 |
| MAPAUTO-EPIC | [[Epic]: Specialize the engine for a reusable single-map automated encounter](issues/MAPAUTO-EPIC.md) | P1 | XL | — |
| MAPAUTO-1 | [[Feature]: Add a focused single-map encounter workspace and reusable encounter package](issues/MAPAUTO-1.md) | P1 | L | — |
| MAPAUTO-2 | [[Feature]: Add an authoritative map clock and deterministic automation queue](issues/MAPAUTO-2.md) | P1 | L | MAPAUTO-1, ACCESS-6 |
| MAPAUTO-3 | [[Feature]: Save encounter checkpoints and reset map automation to a chosen baseline](issues/MAPAUTO-3.md) | P2 | L | MAPAUTO-1, MAPAUTO-2 |
| MAPAUTO-4 | [[Feature]: Give the GM a map automation console with preview, pause, and action history](issues/MAPAUTO-4.md) | P1 | L | MAPAUTO-2 |
| TILE-EPIC | [[Epic]: Build editable interactive tiles with automatic checks and outcomes](issues/TILE-EPIC.md) | P1 | XL | — |
| TILE-1 | [[Feature]: Bind interactive tiles to map regions and objects through a visual inspector](issues/TILE-1.md) | P1 | L | SCENE-3, SCENE-4, MAPAUTO-1 |
| TILE-2 | [[Feature]: Automatically roll tile dice, skill checks, and saves using a configurable rules adapter](issues/TILE-2.md) | P1 | L | TILE-1, TILE-7 |
| TILE-3 | [[Feature]: Configure ordered tile actions for check success, failure, and other outcomes](issues/TILE-3.md) | P1 | L | TILE-2, TILE-7 |
| TILE-4 | [[Feature]: Control tile repeat, cooldown, and per-token activation policies](issues/TILE-4.md) | P1 | M | TILE-1, TILE-7, MAPAUTO-2 |
| TILE-5 | [[Feature]: Add editable hazard, pressure-plate, and template-area tile presets](issues/TILE-5.md) | P1 | L | TILE-2, TILE-3, TILE-4, SCENE-3, SCENE-4, MAPAUTO-2 |
| TILE-6 | [[Feature]: Operate map objects through code-free interact, lock, and state presets](issues/TILE-6.md) | P2 | L | TILE-1, TILE-2, TILE-3, TILE-4, TILE-7 |
| TILE-7 | [[Feature]: Record authoritative tile rolls and idempotent outcome steps across clients](issues/TILE-7.md) | P1 | L | TILE-1, SCENE-4, MAPAUTO-2 |
| TILE-8 | [[Feature]: Save reusable tile recipes and extend the starter scenario with tile traces](issues/TILE-8.md) | P2 | M | TILE-2, TILE-3, TILE-4, TILE-5, TILE-6, TILE-7, MAPAUTO-4, MAPAUTO-5 |
| NPC-EPIC | [[Epic]: Run configurable NPC automation inside one authoritative map runtime](issues/NPC-EPIC.md) | P1 | XL | — |
| NPC-1 | [[Feature]: Add versioned NPC automation profiles and explicit per-map activation](issues/NPC-1.md) | P1 | M | MAPAUTO-1, ACCESS-6 |
| NPC-2 | [[Feature]: Author NPC waypoint patrols with loops, waits, and blocked-route behavior](issues/NPC-2.md) | P1 | L | NPC-1, NPC-4, NPC-5 |
| NPC-3 | [[Feature]: Add observer-specific NPC perception and deterministic behavior states](issues/NPC-3.md) | P2 | L | NPC-1, MAPAUTO-2, SCENE-3 |
| NPC-4 | [[Feature]: Schedule NPC turns and real-time decisions through the shared map clock](issues/NPC-4.md) | P1 | L | NPC-1, MAPAUTO-2, ACCESS-6 |
| NPC-5 | [[Feature]: Execute NPC movement within budgets, collisions, and live map topology](issues/NPC-5.md) | P1 | L | NPC-1, SCENE-4, ACCESS-6 |
| NPC-6 | [[Feature]: Choose and execute NPC actions with configurable rules and recorded rolls](issues/NPC-6.md) | P2 | L | NPC-1, NPC-3, NPC-4, NPC-5, ACCESS-6, TILE-2 |
| NPC-7 | [[Feature]: Extend the GM runtime console with NPC pause, step, override, and decision inspection](issues/NPC-7.md) | P2 | M | MAPAUTO-4, NPC-2, NPC-3, NPC-4, NPC-6, ACCESS-6 |
| NPC-8 | [[Engineering]: Add deterministic NPC scenario replay and bounded-work regression fixtures](issues/NPC-8.md) | P2 | M | NPC-2, NPC-3, NPC-5, NPC-6, NPC-7, MAPAUTO-2 |
| NPC-9 | [[Feature]: Let eligible NPCs operate unlocked doors along an authored route](issues/NPC-9.md) | P2 | M | NPC-5, NPC-6, SCENE-1, SCENE-2, ACCESS-6 |
| MAPAUTO-5 | [[Feature]: Ship a playable single-map starter with tile checks and patrolling NPCs](issues/MAPAUTO-5.md) | P2 | M | MAPAUTO-3, MAPAUTO-4, TILE-2, TILE-3, NPC-1, NPC-2 |

## Publishing and recovery

The Markdown files under `issues/` are the issue bodies; `issues.json` supplies repository, grouping, order, and stable IDs. `{{ID}}` placeholders are resolved to GitHub issue links by the publisher. Existing issue labels are reused where available; no milestones, assignees, or dates are imposed.

Run from this directory:

```bash
python3 publish.py --dry-run
python3 publish.py --check-remote
# Inspect remote-issues.json and the duplicate report, reusing or dispositioning matches.
python3 publish.py --apply
```

The apply command is authorized by the request to create this backlog. It inventories existing issues, preserves marker-owned publication state, creates missing issues, then links epics and dependencies. It must stop on unresolved duplicate candidates or unavailable access. An exact-title issue without a roadmap marker is reused only through an explicit `existing_number` mapping in its manifest entry. `publication.json` records confirmed issue numbers and URLs; remote markers allow recovery when a previous API response was lost.

Future issue bodies should describe a reproducible defect when labeled as a bug. Unconfirmed gaps and research questions remain features or discovery work.
