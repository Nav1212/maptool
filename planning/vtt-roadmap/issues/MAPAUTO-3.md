**Roadmap ID:** `MAPAUTO-3` · **Priority:** P2 · **Relative size:** L

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

A reusable automated map needs a way to resume a session and rerun an encounter with known tile and NPC state. Saving visible tokens alone would omit scheduled work and one-shot activation state.

## Proposed scope

Define a map-owned checkpoint contract and named baseline restore for simulation time, relevant scene state, recorded random outcomes, and extension-owned tile/NPC runtime data.

## Acceptance criteria

- [ ] Capture a named baseline/checkpoint containing the map-owned token state, initiative state, logical time, pending durable actions, and registered tile/NPC extension state.
- [ ] New random outcomes are recorded with their execution IDs; loading a checkpoint does not silently reroll completed actions or replay expired one-shot effects.
- [ ] GM can inspect the restore scope, pause the map, restore a selected checkpoint, and receive a completion/failure report; restore does not overwrite unrelated campaign maps or shared notes.
- [ ] Connected players receive a consistent refreshed snapshot after restore, and late-joining players observe the same restored state.
- [ ] Test save/load during a cooldown or paused patrol, partial restore failure handling, schema upgrades, and repeated baseline resets.

## Parent epic

{{MAPAUTO-EPIC}}

## Dependencies

- {{MAPAUTO-1}}
- {{MAPAUTO-2}}

## Source evidence

- [src/main/java/net/rptools/maptool/util/PersistenceUtil.java:387](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/util/PersistenceUtil.java#L387): Campaign saves already persist map and related campaign state.
- [src/main/java/net/rptools/maptool/model/gamedata/DataStoreManager.java:23](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/gamedata/DataStoreManager.java#L23): Namespaced data storage provides an existing extension-state foundation.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
