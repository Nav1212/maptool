**Roadmap ID:** `NPC-4` · **Priority:** P1 · **Relative size:** L

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

Initiative callbacks, direct initiative setters, and client timers have different execution contexts. NPC actions need one coordinator to prevent duplicate decisions, reentrant turns, or paused-world activity.

## Proposed scope

Adapt accepted initiative transitions and the MAPAUTO-2 runtime clock into NPC work items on its existing authoritative map queue. Use explicit map/token/event/turn identifiers and stable ordering. Do not create a second independently scheduled queue, independent per-NPC wall-clock timers, or automatic advancement of player turns.

## Acceptance criteria

- [ ] Only the designated map authority schedules and commits NPC actions; duplicate event delivery, reconnects, and authority loss cannot execute an action twice.
- [ ] Turn mode consumes accepted old/new initiative data and defines behavior for holds, backward navigation, direct slot changes, vetoed changes, and round transitions.
- [ ] Real-time mode uses shared runtime ticks and bounded per-tick work; pause, map deactivation, and a GM cancellation stop future decisions without uncontrolled catch-up.
- [ ] Switching turn/real-time modes is explicit and reconciles queued work and budgets; player initiative advances only under the selected GM policy.
- [ ] Fake-clock tests cover event ordering, callback reentrancy, repeated turn notifications, pause/resume, mode switches, and stale queued actions.

## Parent epic

{{NPC-EPIC}}

## Dependencies

- {{NPC-1}}
- {{MAPAUTO-2}}
- {{ACCESS-6}}

## Source evidence

- [src/main/java/net/rptools/maptool/model/InitiativeList.java:289](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/InitiativeList.java#L289): nextInitiative invokes veto/commit callbacks before assigning the new current index and round; an adapter must consume explicit event data rather than assume setters have run.
- [src/main/java/net/rptools/maptool/model/InitiativeList.java:278](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/InitiativeList.java#L278): Direct current-index changes use a setter path distinct from the legacy commit macro hook.
- [src/main/java/net/rptools/maptool/model/InitiativeList.java:695](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/InitiativeList.java#L695): Committed initiative events include old/new transition data and direction.
- [src/main/java/net/rptools/maptool/model/InitiativeList.java:348](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/InitiativeList.java#L348): Round setters also emit model changes independently of nextInitiative's callback path.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
