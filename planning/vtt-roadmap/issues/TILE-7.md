**Roadmap ID:** `TILE-7` · **Priority:** P1 · **Relative size:** L

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

Tile checks and mutations must produce one canonical result when the same accepted interaction is observed by multiple clients. Existing event handlers run macro code and data changes are broadcast, but this does not establish a tile-specific roll/effect receipt protocol.

## Proposed scope

Consume SCENE-4's accepted occurrence ID and authority decision to create a tile activation key containing binding revision, actor, and check/step IDs. Only the authority executes the trusted check, records its result, and executes registered idempotent effect steps. Observers render receipts. Persist completed/interrupted records with map automation state and reuse the shared checkpoint integration. This is a roll/action consumer protocol, not a second generic event dispatcher or host-election system. Reuse the shared map queue and execution ledger; include the encounter-run ID in receipt identity so a baseline reset starts a distinct activation namespace without duplicating a resumed run.

## Acceptance criteria

- [ ] A check/activation key is reserved on the current authority; duplicate or reentrant requests for that key return its existing pending/completed result instead of invoking the dice handler again.
- [ ] Broadcast one canonical roll result with actor and configured visibility, and let reconnecting/late clients retrieve that result without rolling locally; a distinct accepted occurrence can legitimately produce a new roll.
- [ ] Pass stable activation/step idempotency keys to effect handlers and record step completion; built-in state/property actions reject stale preconditions and duplicate application.
- [ ] Persist results and pending/completed/interrupted steps; after an interruption with an ambiguous non-idempotent trusted macro, pause for GM resolution instead of claiming exactly-once arbitrary macro execution or automatically replaying it.
- [ ] Integration tests send duplicate/reordered requests from two clients, reconnect an observer, and save/reload receipts; assert one dice invocation and one supported effect per key, plus clear handling of an interrupted unsupported custom effect.

## Parent epic

{{TILE-EPIC}}

## Dependencies

- {{TILE-1}}
- {{SCENE-4}}
- {{MAPAUTO-2}}

## Source evidence

- [src/main/java/net/rptools/maptool/util/EventMacroUtil.java:163](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/util/EventMacroUtil.java#L163): Event handling invokes macros with token context and variables; tile-specific receipts can wrap this execution.
- [src/main/java/net/rptools/maptool/model/gamedata/DataStoreUpdateClientsProxy.java:95](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/gamedata/DataStoreUpdateClientsProxy.java#L95): Existing data-store updates are propagated with serverCommand().updateData.
- [src/main/java/net/rptools/maptool/client/functions/TokenPropertyFunctions.java:332](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TokenPropertyFunctions.java#L332): Token-property effects use a synchronized server-command update path.
- [src/main/java/net/rptools/maptool/client/functions/LastRolledFunction.java:43](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/LastRolledFunction.java#L43): Roll components are available from parser-local roll history; observers should consume an activation result rather than rerun that history's producer.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
