**Roadmap ID:** `MAPAUTO-2` · **Priority:** P1 · **Relative size:** L

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

Automatic tile rolls, cooldowns, patrol movement, and NPC turns must share ordering and time semantics. Running independent client timers would not define a consistent encounter state.

## Proposed scope

Introduce one host-owned map runtime that consumes committed events and schedules bounded work against logical time. Support manual stepping and initiative phases first, with an explicitly selected paced exploration mode.

## Acceptance criteria

- [ ] Define immutable event IDs, encounter-run IDs, map IDs, ordering keys, actor context, and logical timestamps; clients submit intents while one map authority commits resulting work. A new baseline run gets a distinct execution namespace.
- [ ] Provide pause, resume, one-step, and initiative-phase advancement; a paced exploration mode uses the same logical queue rather than a timer per NPC.
- [ ] Committed region occurrences can enter the queue through an adapter to SCENE-4 without reimplementing its enter/exit/crossing detection.
- [ ] Bound queued work, recursive actions, and catch-up after pauses or reconnects; report a blocked or over-budget action without freezing rendering or accepting duplicate results.
- [ ] Persist logical time, pending durable work, and completed execution receipts; verify event order, duplicate intents, authority reconnect/stop, canceled moves, and large wall-clock jumps.

## Parent epic

{{MAPAUTO-EPIC}}

## Dependencies

- {{MAPAUTO-1}}
- {{ACCESS-6}}

## Source evidence

- [src/main/java/net/rptools/maptool/model/InitiativeList.java:104](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/InitiativeList.java#L104): Initiative hooks can supply explicit turn/round event payloads.
- [src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java:63](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java#L63): Movement callbacks provide a committed movement integration point.
- [src/main/java/net/rptools/maptool/server/ServerMessageHandler.java:60](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/server/ServerMessageHandler.java#L60): The current server dispatch boundary can receive and authorize runtime intents.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
