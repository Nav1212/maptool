**Roadmap ID:** `TILE-4` · **Priority:** P1 · **Relative size:** M

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

An automatic check needs a clear policy for repeated entries, overlapping actors, turn pulses, and reactivation. Generic property storage and initiative events exist, but the requested tile workflow needs explicit, editable activation semantics.

## Proposed scope

Store each binding's armed/disabled/spent state and an activation-policy record with scope and reset rules. Support once-ever, once-per-token, once-per-entry, once-per-turn, and cooldown policies. Use MAPAUTO-2's authoritative clock for time/turn deadlines, SCENE-4 occurrence IDs for event identity, and TILE-7 receipts for actual executions; no client wall-clock timers.

## Acceptance criteria

- [ ] The tile form offers once-ever, once-per-token, once-per-entry, once-per-turn, and cooldown with global/per-token scope, and documents when consumption occurs for success, failure, cancelled, and errored checks.
- [ ] Entry resets require a committed exit/re-entry occurrence; drawing redraws, repeated position sync, pause/resume, and client reconnect do not reset or consume the policy.
- [ ] Cooldown and turn reset deadlines derive from the shared clock; paused time does not advance a game-time cooldown, and any supported real-time mode is explicitly labelled.
- [ ] Persist counters, spent states, and deadlines with the map; support a GM reset action and verify two-token independence, simultaneous activation, save/reload, and reset behavior.

## Parent epic

{{TILE-EPIC}}

## Dependencies

- {{TILE-1}}
- {{TILE-7}}
- {{MAPAUTO-2}}

## Source evidence

- [src/main/java/net/rptools/maptool/model/InitiativeList.java:104](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/InitiativeList.java#L104): Existing initiative-change request/commit callbacks provide a turn integration point.
- [src/main/java/net/rptools/maptool/model/gamedata/DataStoreManager.java:34](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/gamedata/DataStoreManager.java#L34): The default namespaced data store propagates updates to other clients.
- [src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java:66](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java#L66): Both individual and multiple-token movement callback forms already exist.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
