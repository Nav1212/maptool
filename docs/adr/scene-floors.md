# SCENE-6: represent floors as linked maps

Status: accepted for the first floor-aware milestone. This resolves the design
discovery, not the production floor feature. Native desktop validation remains
blocked as described below.

## Decision

Use one existing `Zone` per floor, grouped by optional campaign metadata, with
explicit directed links between stair/landing locations. Keep each floor's assets,
tokens, vision, fog, lights, topology, and initiative in that zone. Do not use the
four rendering layers as floors and do not add a vertical dimension to the current
visibility engine for this milestone.

This fits the existing model: `Campaign.getZones()` holds maps; `ZoneRenderer`
constructs its own `ZoneView`, lighting renderer, and fog renderer. Existing
`moveTokenToMap`/`moveTokenFromMap` macros already preserve a token object across
maps, but currently publish a destination `putToken` and source `removeToken`
separately. The production stair operation needs an authoritative atomic transfer
contract, rather than treating those two calls as a transaction.

Source anchors:

- `src/main/java/net/rptools/maptool/model/Campaign.java:355`
- `src/main/java/net/rptools/maptool/model/Zone.java:126` and `:307`
- `src/main/java/net/rptools/maptool/client/ui/zone/renderer/ZoneRenderer.java:162`
- `src/main/java/net/rptools/maptool/client/functions/TokenLocationFunctions.java:164`
- `messages/src/main/proto/data_transfer_objects.proto:610`

## Alternatives and consequences

| Concern | Separate maps plus links: selected | Stacked floors within one zone: deferred |
| --- | --- | --- |
| Rendering | Reuse each map's existing grid, board, renderers, and viewport. Only one selected floor is rendered to a player. | Add floor membership, active-floor projection, and overlap/roof rules to token, drawable, light, and board rendering. |
| Assets | Existing asset IDs can be shared across maps; grouping does not require copying image bytes. Each board retains its own scale/origin. | Asset sharing still works, but board transforms and compositing must become floor-aware. |
| Topology | Walls and wall/hill/pit/cover masks remain local to one map. No cross-floor rays. | Every wall/mask query needs a floor/height context; planar hill/pit behavior does not supply general wall heights. |
| Fog and light | Reuse independent zone exposure, per-token fog metadata, and light caches. Source fog is never copied to the destination. | Split fog history and every illumination/visibility cache by floor, and define vertical apertures and light propagation. |
| Ownership and visibility | Preserve token owners; validate the destination before transfer. Filter floor selection with map visibility and GM grants. Existing map visibility is not proof that hidden content is absent from every network payload. | Introduce per-floor visibility/permission boundaries inside a zone that currently exposes one map identity. Legacy floor-unaware clients need explicit rejection. |
| Protocol and persistence | Keep ordinary `ZoneDto` maps. Add versioned grouping/link metadata and one transfer request/result contract. Existing map export remains useful. | Add a floor dimension to many DTOs, mutations, model migrations, and reconnect paths; an absent floor field could collapse unrelated floors. |
| Migration and delivery | Existing campaigns are unchanged until a GM creates a floor set. Bounded follow-up work can ship incrementally. | Broad migration and compatibility work must precede a usable first feature. Relative size: XL with unresolved rendering/vision decisions. |

`TOKEN`, `GM`, `OBJECT`, and `BACKGROUND` layers encode interaction/rendering roles,
not height. For example, `Zone.Layer.supportsVision()` applies to the token layer.
Repurposing these layers would change existing ownership, marker, and fog behavior.

## Floor selection and isolation

The GM can inspect any floor through the existing map selector plus a grouped
floor list. Selecting a GM floor does not move players or reveal it. A separate
explicit command may focus a consenting/authorized player's view on an already
permitted floor.

Players see their current floor and other floors explicitly made available by the
GM. A hidden destination's label and landing coordinates must not appear in the
player link controls. Split parties retain independent selected maps; moving one
owned token must not switch every client's map. Follow the selected owned token
only for its controlling player, with a documented choice when they own tokens on
several floors. `PlayerZoneListener` already records a player's selected zone.

Vision, lights, movement blocking, and fog are computed only from the selected
zone. No upstairs token/light participates in a downstairs query. Entering a floor
uses that floor's existing reveal policy and any previously saved exposure for
that token. Do not import the source floor's exposed area or replace destination
fog. Selecting another floor does not grant ownership of tokens there.

Server authorization and player-safe payload filtering remain part of ACCESS-6;
this ADR does not claim secrecy merely from hiding a floor in the UI.

## Links, elevation, and transfer rules

Proposed additive metadata, version 1:

```text
FloorSet: id, label, ordered floors[], directed links[]
Floor: zoneId, label, baseElevation, elevationUnit
Link: id, sourceZoneId, entryAnchor, destinationZoneId, exitAnchor,
      arrivalRelativeElevation, enabled, interactionPolicy
```

Zone GUIDs are identities; map names are labels. A link is directed. A reverse
stair is a second explicit link, not an inferred coordinate transform. Initial
landing coordinates are explicit points in each map's coordinate system; map
scale, grid origin, or image size do not imply alignment. Version 1 rejects
incompatible elevation units unless an explicit tested conversion is supplied.

SCENE-5 supplies typed token elevation relative to its floor. Global reference
height is `floor.baseElevation + token.relativeElevation`. A stair sets the
destination's configured arrival-relative elevation; its rise is the difference
between the two landing reference heights. This does not turn an arbitrary legacy
`Elevation` property into a physics field, and does not silently modify framework
properties. Flying/retained-height behavior needs an explicit later policy.

The host validates, before mutation: occurrence ID, actor revision, source map,
entry proximity, ownership/GM permission, enabled link, allowed destination, and a
legal unoccupied landing for the token's footprint. A successful transaction
preserves token GUID, properties, ownership, portrait, and other token data while
changing map, position, and configured relative elevation. A failure leaves both
maps unchanged. Repeated occurrence IDs return the same result without copying
the token or moving it again.

Publish the removal/insertion as one accepted transfer with a durable receipt and
a reconnect snapshot that cannot contain the token on both floors or neither.
Source-exit and destination-entry region events use that occurrence ID and
defined ordering. A reverse link must rearm only after leaving its entry region;
arrival alone must not create an infinite stair loop.

Each zone currently has its own initiative list and the new encounter runtime is
explicitly map-scoped. The first milestone requires a GM decision when transferring
an actor in initiative: leave/remove its source entry and explicitly enroll it at
the destination. It does not automatically merge rounds, advance another floor's
clock, resume an NPC profile there, or keep an NPC chasing across floors.

## Persistence and compatibility

Existing campaigns load as ordinary unrelated maps. Creating a floor set adds
metadata; it does not rewrite zone topology, change token draw order, or reinterpret
existing properties. Exporting a single `.rpmap` remains valid, but its external
link targets must be reported as unresolved on import until a GM relinks them by
GUID. Missing destinations disable links with a diagnostic rather than selecting
another map by a coincidentally equal name.

The production implementation must register a campaign model version and feature
capability for floor metadata and atomic transfer. A floor-unaware client can use
ordinary visible maps only when compatibility policy permits; it cannot submit
automatic stair interactions. Saving with a version unable to retain floor links
must warn/block that loss or require an explicit metadata-free export. Unknown
metadata must not be silently discarded. No existing protobuf field number is
reused; upgrade and downgrade behavior needs round-trip tests.

## Experiment, evidence, and remaining blocker

`common/src/test/java/net/rptools/maptool/automation/prototype/LinkedMapFloorPrototypeTest.java`
is a minimal two-floor **contract prototype** using the existing GUI-independent
`NpcWorld.Token` and `Point` records. Its fixture checks stable identity/ownership,
explicit landing elevation, map-local state isolation, rejected transfers,
duplicate occurrence replay after JSON restore, and source-map NPC nonexecution
after transfer.
The fixture's transfer method is experimental test code, not a new production
floor API and not an execution of native `Campaign`/`Zone` rendering or networking.

Run it with the configured Java 21 environment:

```sh
./gradlew :common:test --tests '*LinkedMapFloorPrototypeTest' --no-daemon --max-workers=2
```

Validation in this task: the fixture compiled with Java 21 and executed through
the cached JUnit Platform 6.1.3 launcher against the current `common` classes.
All four tests started and passed; none failed, aborted, or skipped. This isolated
launcher run avoided modifying another agent's concurrent Gradle result files.

The native desktop experiment is blocked by dependency access, not by a proven
floor-model incompatibility. The current-instance baseline captured in
`/workspace/.maptool-cloud/baseline-current.log` fails while resolving
`com.jetbrains.intellij.java:java-gui-forms-rt:241.12019`: the official JetBrains
repository redirects to `d2cico3c979uwg.cloudfront.net`, and the dependency cache
redirects to `artifacts-caching-proxy.aws.intellij.net`; both return HTTP 403.
The recorded failure is:

```text
Could not resolve com.jetbrains.intellij.java:java-gui-forms-rt:241.12019
Could not GET https://d2cico3c979uwg.cloudfront.net/com/jetbrains/intellij/java/java-gui-forms-rt/241.12019/java-gui-forms-rt-241.12019.pom
Received status code 403 from server: Forbidden
Could not GET https://artifacts-caching-proxy.aws.intellij.net/intellij-dependencies/com/jetbrains/intellij/java/java-gui-forms-rt/241.12019/java-gui-forms-rt-241.12019.pom
Received status code 403 from server: Forbidden
```

Reproduce the prerequisite check with:

```sh
./gradlew :shadowJar --no-daemon --max-workers=2
```

No dependency versions, checksums, TLS checks, or source behavior were weakened to
work around that failure. The prototype result does not establish desktop or
multiplayer floor readiness.

Next native experiment after the approved dependency destinations work: build
`installDist`; under Xvfb create two real zones with distinct fog, walls and lights,
place one owned token, transfer it with the existing cross-map workflow, and
inspect both zone models and views. Save/reload, reconnect a player, and verify one
token identity, preserved ownership/properties, independent exposure/light caches,
and no forced view change for a second player. Observe the existing two-command
transfer's intermediate state before implementing the atomic replacement. Retain
this experiment as the integration acceptance fixture for the feature follow-up.

## Implementation sequence

| Step | Relative size | Concrete deliverable |
| --- | --- | --- |
| 1 | M | Versioned floor-set/link metadata, GUID resolution, validation, and campaign/map import/export round trips. Finalize SCENE-5 units and relative-height contract. |
| 2 | L | Authorized atomic cross-map transfer with occurrence receipts, legal landing checks, region event ordering, reconnect recovery, and rejected-operation tests. |
| 3 | M | GM floor grouping/link authoring and filtered player selection/interaction, including split-party behavior and unresolved-link diagnostics. |
| 4 | M | Stair/landing region binding using SCENE-3/SCENE-4, rearm policy, explicit initiative/NPC handoff controls, and bounded transition costs. |
| 5 | M | Run the native two-zone experiment above against representative campaigns; add remote-update, reconnect, save/reload, migration, and downgrade checks. |

Excluded: general 3D geometry, wall heights, vertical ray tracing, light or vision
through floor apertures, automatic roofs, simultaneously rendered stacked floors,
cross-floor pathfinding, automatic flying-height conservation, and unified combat
across floors. A future stacked-floor proposal must supply its own rendering and
visibility experiments rather than treating this decision as an implementation.

SCENE-6's discovery acceptance is satisfied by the comparison, selection/isolation
contract, bounded prototype plus reproducible native-experiment blocker, migration
rules, and sized follow-up sequence above. Production floor behavior remains a
separate implementation and validation milestone.
