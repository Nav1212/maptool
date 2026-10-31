# NPC automation core

The `common` module contains a Java 21, GUI-independent NPC coordinator in
`net.rptools.maptool.automation.npc`. It provides opt-in profiles, patrol routes,
observer-specific pursuit, logical-time waits, approved action dispatch, inspection
receipts, and snapshot/restore. The desktop/server adapter supplies the actual world.
It does not start threads, create another clock, call an external AI service, or
select a game system.

## Integration

Create an engine, assign profiles, and explicitly enable the selected map:

```java
var engine = new NpcEngine();
var patrol = NpcProfile.patrol(
    List.of(
        new NpcProfile.Waypoint(new NpcWorld.Point(10, 10), 2),
        new NpcProfile.Waypoint(new NpcWorld.Point(20, 10), 0)),
    NpcProfile.PatrolMode.PING_PONG,
    5);
engine.assign(mapId, npcId, patrol);
engine.setMapEnabled(mapId, true);

// Use the shared map clock and one stable ID for this turn/tick occurrence.
List<NpcResult> receipts = engine.tick(runId, eventId, mapId, logicalTick, world);
```

`NpcWorld` is an explicit map/observer adapter. It must:

- Affirm the current session authority through `hasAuthority`.
- Return immutable token snapshots and only observations permitted to that NPC.
- Plan legal movement, optionally returning a partial path within the supplied
  budget. `MovePlan.ready` and `MovePlan.blocked` are construction helpers.
- Revalidate actor/world revisions, collisions, door policies, permissions, and
  movement callbacks when committing a move. A rejection leaves the world unchanged.
- Revalidate action approval, target, resource costs, and macro trust before
  executing an approved action. The default action adapter rejects execution.
- Deduplicate command keys at the actual mutation boundary. The engine's receipt
  is inspection data and must never be reapplied by the caller.

Adapter reads and planning must be side-effect-free. A world mutation can emit
events, but reentrant coordinator calls are deferred without dispatch. Queue those
events in the shared runtime and submit them after the outer `tick` completes.

Positions, action range, and pursuit radius use one adapter-defined coordinate
unit. Action range and pursuit radius use Euclidean distance in those coordinates;
movement cost comes from the world's legal path/terrain calculation. A profile's
movement and action budgets apply once per unique decision event. The caller must
use one event identity per intended NPC turn/tick, rather than create new IDs for
animation frames or duplicate notifications. Each event performs at most one move
or one approved action per NPC. Submitted IDs do not create a scheduler.

## Behavior and controls

Patrols support one-shot, looping, and ping-pong routes. Arrival starts the
waypoint's logical-time wait; progression occurs on a later decision event.
Blocked paths wait and return a reason instead of teleporting or spinning retries.

Profiles can opt into visible-target pursuit, configure last-seen memory and a
home-centered pursuit radius, and name an approved in-range action. Target ties
resolve by distance and then stable token ID. The built-in states are `IDLE`,
`PATROL`, `PURSUE`, `INVESTIGATE`, and `RETURN`. Lost targets can be investigated at
their last-seen position but cannot be attacked from memory. After memory expires,
the NPC returns to its initial home position and resumes its route.

Register a pure `NpcRule` by ID for configurable game-specific selection. Its
context contains copied observations, the actor, profile, behavior, and memory.
It returns a wait, destination, or approved action request. Missing registrations
wait safely after restoring a snapshot. Rules must be bounded and deterministic;
they must not call mutating macros or consume shared random state during selection.
Action outcomes contain recorded rolls/outcomes supplied once by the authority.
This package never changes the dice library's global seed.

`setMapPaused`, `setNpcPaused`, and `setManualOverride` stop future mutations.
Pausing or changing an assignment while planning invalidates the pending commit.
`setTargetOverride` still requires a perceived target. `preview` runs selection on
a copied actor state without planning/committing movement or executing actions;
`inspect` returns immutable state for a GM inspector. The UI and server must enforce
who is allowed to invoke configuration and GM control methods.

## Persistence and event identity

`snapshot()` returns nested records/lists suitable for JSON serialization;
`NpcEngine.fromSnapshot(snapshot)` validates and restores them. Persist the world
and engine at the same accepted-event boundary. Rule implementations are not
serialized and must be registered again. Snapshotting during an in-flight event
is rejected to avoid saving inconsistent state.

The first submitted run binds a map. `resetRun(mapId, newRunId)` explicitly starts
a different run, resets transient behavior, and retires the old identifier; run
IDs cannot be reused on that map. A tuple of run, event, map, and NPC identifies a
receipt independently of later clock or configuration changes.

The engine returns retained duplicate receipts with `duplicate=true`. It retains
2,048 receipts by default. When older receipts expire, their logical ticks become
a rejection floor: expired/older events never dispatch again, including after
snapshot/restore. New events at an expired tick are rejected too; advance the
shared logical clock rather than reuse that tick indefinitely. A separate run
requires an explicit new run ID.

An adapter exception is a terminal `ERROR` receipt because an effect might already
have happened. The NPC pauses until the GM resolves the error and explicitly
unpauses it; a later clock tick cannot automatically retry the uncertain effect.
Exactly-once effects
across a process crash additionally require a durable adapter command journal or
an atomic world/receipt transaction; an in-memory receipt alone cannot establish
that guarantee. Replaying captured outcomes belongs in that adapter, not in a
second random roll.

Default limits are 256 assigned NPCs per map, 1,024 observations per NPC, and 8,192
points per move plan. The constructor can change NPC/receipt limits. Rule execution
and pathfinding also need the shared runtime's bounded-work policy; this package
cannot preempt arbitrary trusted Java callbacks.

## Validation and remaining integration

Run the deterministic scenario suite with:

```sh
./gradlew :common:test --tests '*NpcEngineScenarioTest'
```

The core is deliberately separate from desktop controls, native door/collision
rules, actual token mutation, region execution, multiplayer command transport,
and campaign persistence wiring. Those adapters and user workflows must be
validated before claiming the complete NPC roadmap is delivered. Browser clients,
elevation/floors, multi-action combat plans, and game-specific attack rules are
outside this core implementation.
