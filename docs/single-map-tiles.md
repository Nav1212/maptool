# Tile engine foundation

`common` contains a headless, game-neutral tile consumer in
`net.rptools.maptool.automation.tile`. It operates on committed caller-supplied
events. It does not own a queue, clock, host election, network transport, UI,
random generator, or MTScript trust policy.

## Definitions and rules adapters

`TileRegion` describes a named rectangle or simple polygon with primitive point
records. Boundaries are outside: touching an edge or travelling along it does not
activate an area. Segment tracing detects pass-through and concave re-entry,
rather than comparing only the two endpoint occupancies. Zero-length moves do
not enter, exit, or cross. Geometry definitions can remain invalid while being
edited; `validationErrors()` explains why they cannot execute. Engine insertion
rejects nonfinite coordinates so every retained draft remains JSON-serializable.

`TileDefinition` holds an identity, map, optional region, enabled flag, trigger
set, optional check, activation policy, outcome branches, and always actions.
`ENTER`, `EXIT`, and `CROSS` consume movement; `INTERACT` consumes an explicit
interaction; `PULSE` consumes a caller-supplied time/turn pulse for an actor
currently inside the region. `CROSS` means any positive interior traversal,
including movement entirely inside. A definition matching multiple movement
triggers executes once per occurrence/actor, with ENTER, EXIT, CROSS precedence.

Register a `RollAdapter` under a check's adapter ID. Its request contains the
selected check ID and parameters plus immutable actor properties and event
context. The adapter returns a structured `CheckResult` with an outcome name,
display, optional numeric total, rolled components, and details. No die size,
skill name, success threshold, or damage resource is built into the engine.
Adapters may bridge trusted MTScript or an installed rules profile. Plain
actions without a check use outcome `ACTIVATED`.

Register action handlers by ID. The matching outcome's actions execute in list
order, followed by always actions. Each receives the canonical check result,
context, typed parameter map, and stable step key. A result with no matching
branch performs only always actions. Game-specific resource changes belong to
the injected handler, not the engine.

```java
var tiles = new TileEngine("campaign-run-id");
tiles.putRegion(TileRegion.rectangle("plate", "Pressure plate", "map-id", 0, 0, 64, 64));
tiles.registerRollAdapter("rules", request -> myTrustedCheck(request));
tiles.registerActionHandler("apply", request -> myIdempotentAction(request));
tiles.putTile(new TileDefinition(
    "trap", "Trap", "map-id", "plate", true,
    Set.of(TileDefinition.Trigger.ENTER),
    new TileDefinition.Check("rules", "agility", Map.of("target", "9")),
    new TileDefinition.Policy(
        TileDefinition.Repeat.ONCE_PER_ENTRY, TileDefinition.Scope.ACTOR, 0),
    Map.of("FAILURE", List.of(new TileDefinition.Action("apply", Map.of("state", "alert")))),
    List.of()));
var results = tiles.onMove(
    "accepted-segment-id", "map-id", "actor-id",
    new TileRegion.Point(-10, 32), new TileRegion.Point(32, 32),
    10, "turn-id", Map.of("agility", "3"));
```

The example bridge methods are integration points, not included implementations.
The caller must validate actual player ownership, interaction reach, trusted
macro access, and audience filtering before accepting events or sending results.
The check's `Visibility` is metadata; the engine's canonical receipt contains the
complete result and must not be broadcast indiscriminately.

## Policies and committed movement

Policies support ALWAYS, ONCE, ONCE_PER_ENTRY, ONCE_PER_TURN, and COOLDOWN.
ONCE and COOLDOWN may apply to the tile globally or separately to each actor;
ONCE_PER_ENTRY requires actor scope. A completed failure outcome consumes the
policy just as a completed success does. Skipped/invalid events do not consume
it. Turn policies require a nonblank caller-provided turn identity. Cooldowns
use only the nonnegative tick supplied by the caller; no wall-clock timer runs.

Entry generations advance on committed segment entries, including outside-to-
outside crossings. Repeated position notifications and duplicate occurrences do
not reset them. Feed each accepted straight segment once with a stable distinct
occurrence ID. A multi-segment path needs distinct segment occurrence IDs if its
segments are delivered separately. This API does not infer a complete path from
the endpoints of a bent route.

`pulse` includes the current position. `interact` selects a tile by ID and does
not enforce distance itself. `regionState(regionId, actorId)` exposes observed
occupancy and entry generation. `validationErrors(tileId)`, `definitions()`,
`regions()`, `policyStates()`, and `receipts()` support inspectors.

## Receipts, interruption, and persistence

The activation key includes run, occurrence, tile, and actor. External IDs are
length-prefixed to avoid delimiter collisions. Duplicate, concurrent, and
reentrant requests receive the existing receipt rather than rolling again.
Handlers run synchronously under the engine's lock: they must be short and must
not wait for another thread to call the same engine. The shared runtime remains
responsible for scheduling and event recursion limits.

A check or action exception produces INTERRUPTED, retains completed action-step
receipts, and blocks a new activation for that policy scope. This avoids
automatically repeating an effect that may have happened before a connection or
handler failed. The engine does not promise transactional or exactly-once
execution of arbitrary external macro side effects. Integrations should honor
the supplied step keys and use idempotent/precondition-checked handlers.

`resetTileState(tileId, actorId)` explicitly clears pending/repeat/cooldown state;
passing null for actor resets all scopes for that tile. It retains historical
receipts, so replaying an old event never rerolls. A GM must resolve ambiguous
effects before resetting. No rollback is attempted by this library.

`snapshot()` or `snapshotJson()` captures versioned definitions, occupancy,
policies, event mappings, canonical results, and step receipts.
`TileEngine.restore(snapshot)` or `TileEngine.fromJson(json)` restores them.
Runtime handlers are not serialized: register them again before new events.
RUNNING receipts found on restore become INTERRUPTED and require resolution.
History is retained without automatic pruning; the integrating runtime must
bound run lifetime and archive old runs deliberately, preserving deduplication
for any events it may still replay.

## Validation and current limits

The targeted tests are `./gradlew :common:test --tests '*tile*'`. They exercise
geometry, policy semantics, outcome ordering, validation, concurrent/reentrant
duplicates, and JSON restoration. Main-app integration must additionally test
committed movement, permissions, audience filtering, and client synchronization.

This foundation does not by itself finish visual authoring, pressure-plate actor
aggregation, click affordances, recipe pickers, or multi-client transport. Those
features should consume these definitions and receipts through the shared map
runtime rather than introduce another scheduler or assume that this library
alone closes the corresponding UI/network backlog items.
