# Single-map runtime foundation

The common module now contains a caller-driven map clock and a composition layer for the tile
and NPC engines. The dice module supplies a working check adapter using MapTool's existing dice
parser. This is engine infrastructure; it is not yet an automatically enabled desktop play mode.

## Composition

`EncounterSession` owns one `MapRuntime`, `TileEngine`, and `NpcEngine` for an explicit map/run
identity. Its `World` adapter supplies authenticated authority, actual map state, token properties,
observer-specific perception, legal movement, and approved effects. No component consults a
globally selected map, starts a background timer, or requires an external AI service.

The host registers check/action handlers, configures regions and NPC profiles, then submits
accepted movements and scheduled decisions. Clients must render filtered results rather than
execute the session again. The default clock is paused; its authority can resume, single-step, or
advance to a logical horizon. Catch-up work observes scheduled time and is bounded per call.

The adapters described in [tile integration](single-map-tiles.md) and
[NPC integration](single-map-npcs.md) remain responsible for real movement vetoes, collision and
door rules, permissions, and filtering private data before network transmission.

## Dice checks

`net.rptools.dicelib.automation.DiceCheckAdapter` implements the tile roll-adapter contract. Its
parameters are:

| Parameter | Meaning |
|---|---|
| `expression` | Numeric arithmetic with standard `NdS` dice, such as `2d6 + 1`. |
| `property` | Optional numeric property captured for the triggering actor. Missing values fail. |
| `modifier` | Optional numeric adjustment, default zero. |
| `target` | Optional numeric comparison target. Without it the outcome is `ROLLED`. |
| `comparison` | `gte` (default), `gt`, `lte`, `lt`, or `eq`. |

A checked result is `SUCCESS` or `FAILURE` and includes rolled components, total, target and
resolved inputs. This intentionally bounded reference adapter does not implement game-specific
damage, resources, saves or every advanced dicelib syntax. Frameworks can register other adapters.
It never resets the global random generator to reproduce a result. Numeric inputs are bounded
before rolling: at most 128 characters, 64 digits of precision, and absolute decimal scale 32.

## Persistence and intervention

Full encounter snapshots contain the world adapter's state, clock, tile state/results, and NPC
state. Treat these as GM-owned checkpoints, not player payloads. Restoration checks map/run and
authority identities before changing world state, starts paused, and requires handlers to be
registered again. The world adapter must validate and restore its state atomically.

The clock's synchronous durability hook reserves execution before effects. A reservation observed
after interruption is not retried automatically. Arbitrary macro side effects are not guaranteed
transactionally exactly once; interrupted operations require GM resolution. NPC movement callbacks
are buffered until the decision batch completes so durability snapshots cannot capture an
inconsistent, in-flight NPC controller. Accepted segments remain in a durable outbox until an
entire notification batch is reserved. Queue saturation pauses the encounter and retains that
outbox for explicit recovery; it does not discard movement that already happened. An authority
loss pauses the clock before dequeuing more work.

## Executed checks

From the checkout with JDK 21 and the Gradle wrapper:

```bash
./gradlew :common:test :dicelib:test :clientserver:test :messages:test --no-daemon --max-workers=2
```

`MapRuntimeTest` exercises authorization, chronological ordering, bounded work, queue capacity,
pause/step/cancel, duplicate events, durability failure, and snapshot validation.
`EncounterSessionTest` runs a real dicelib check, applies a configured outcome, moves a patrol NPC,
restores a checkpoint, and verifies no repeated roll/effect. It also covers missing properties,
authority loss, and synchronous movement notifications during durable NPC decisions.

These tests exercise the engine and adapter contracts. They do not establish desktop editor
integration, actual two-client play, or complete acceptance of the larger roadmap tickets.
