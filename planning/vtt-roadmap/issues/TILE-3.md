**Roadmap ID:** `TILE-3` · **Priority:** P1 · **Relative size:** L

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

Rolling a check does not yet define what the map should do with its result. MapTool already has token state/property updates, chat, and macro invocation that can back editable outcome presets.

## Proposed scope

Add a form-based outcome action list referencing TILE-2 result fields and registered trusted action handlers. Ship bounded primitives for messages, changing a configured token property/resource, setting a token state, changing an interactive object's state, and arming/disarming a tile. Resolve selected actor/source/target IDs explicitly. A rules adapter owns game-specific damage/resource interpretation. This consumes SCENE-4 dispatch safeguards rather than creating its own trigger engine.

## Acceptance criteria

- [ ] A GM can configure ordered actions for success/failure and adapter-declared additional outcomes, plus an always branch; check-error/cancelled results do not enter success/failure branches.
- [ ] Each preset exposes typed fields, validates references before enabling the binding, and calls registered trusted MTScript handlers with a structured context rather than composing player-supplied macro source.
- [ ] Apply actions through existing synchronized update paths and TILE-7 step receipts; stop on an action error and record completed/pending steps without automatically repeating an ambiguous effect.
- [ ] Demonstrate different success/failure effects, a profile-owned resource change, a message with configured visibility, and arming another binding; test unknown outcome/handler and deleted-target cases.

## Parent epic

{{TILE-EPIC}}

## Dependencies

- {{TILE-2}}
- {{TILE-7}}

## Source evidence

- [src/main/java/net/rptools/maptool/client/functions/TokenPropertyFunctions.java:332](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TokenPropertyFunctions.java#L332): setProperty updates token properties through MapTool.serverCommand().updateTokenProperty.
- [src/main/java/net/rptools/maptool/client/functions/TokenStateFunction.java:51](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TokenStateFunction.java#L51): Existing API reads/sets named token states.
- [src/main/java/net/rptools/maptool/client/functions/CallFunction.java:47](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/CallFunction.java#L47): Existing macro invocation supports structured argument arrays and deferred calls.
- [src/main/java/net/rptools/maptool/util/EventMacroUtil.java:163](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/util/EventMacroUtil.java#L163): Event handler invocation already supplies a token context, variables, and chat-output control.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
