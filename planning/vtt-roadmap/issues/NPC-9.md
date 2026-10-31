**Roadmap ID:** `NPC-9` · **Priority:** P2 · **Relative size:** M

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

A basic patrol can wait or replan at blocked topology, but an authored guard or civilian route may require opening and closing native doors.

## Proposed scope

Integrate the NPC movement/action coordinator with the native door and lock model. Configure door-use capability and costs explicitly, using accepted scene commands rather than modifying topology directly.

## Acceptance criteria

- [ ] An eligible NPC can request an open/close operation for an encountered native door and continue its path only after the accepted door state allows movement.
- [ ] GM configures whether a profile may use doors and its interaction cost/range; locked or forbidden doors cause the configured wait/replan outcome.
- [ ] Competing NPCs, player actions, and stale door state are revalidated at execution without duplicate spending or forced movement through a blocked span.
- [ ] Existing GM/player door controls continue to work; test locked/unlocked, closed/open, simultaneous users, vetoed operations, and reconnect/save state.

## Parent epic

{{NPC-EPIC}}

## Dependencies

- {{NPC-5}}
- {{NPC-6}}
- {{SCENE-1}}
- {{SCENE-2}}
- {{ACCESS-6}}

## Source evidence

- [src/main/java/net/rptools/maptool/model/topology/Wall.java:142](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/topology/Wall.java#L142): Native wall direction/blocking data is the foundation for planned door state.
- [src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java:439](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java#L439): Accepted automatic movement must preserve existing veto integration.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
