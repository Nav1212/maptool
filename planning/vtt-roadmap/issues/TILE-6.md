**Roadmap ID:** `TILE-6` · **Priority:** P2 · **Relative size:** L

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

Tokens expose macro menus and map objects can carry properties, but the requested specialized map engine needs an accessible interaction workflow for a lever, chest, door-like prop, or pickup without asking players to locate framework macros.

## Proposed scope

Add an Interact action for eligible bound map objects and state-machine presets for toggle, locked/unlocked, and available/consumed behavior. Resolve the acting owned token and range through typed configuration, optionally perform a TILE-2 check, and apply TILE-3 handlers. Visual/state changes and any topology changes go through registered existing handlers or the scene subsystem; this ticket does not add a separate door/topology model.

## Acceptance criteria

- [ ] A GM can bind a token/object or drawing to named interaction choices and preview which players/actors may use them; players choose Interact and an eligible owned actor without editing code.
- [ ] Validate actor ownership, binding enabled/state, map membership, and configured reach on the authority before execution; unavailable choices display a clear reason without exposing hidden GM fields.
- [ ] Provide editable toggle, locked-container, and one-use pickup examples; a lock can select a configured check and branch to success/failure while object state controls later available actions.
- [ ] Simultaneous requests, repeated clicks, and stale-client requests resolve through TILE-7 receipts and state preconditions; test two players interacting with one consumable object and a remote client seeing the canonical final state.

## Parent epic

{{TILE-EPIC}}

## Dependencies

- {{TILE-1}}
- {{TILE-2}}
- {{TILE-3}}
- {{TILE-4}}
- {{TILE-7}}

## Source evidence

- [src/main/java/net/rptools/maptool/client/ui/TokenPopupMenu.java:83](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/TokenPopupMenu.java#L83): Existing owned-token menu exposes macro actions as an interaction entry point.
- [src/main/java/net/rptools/maptool/client/ui/TokenPopupMenu.java:139](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/TokenPopupMenu.java#L139): Macro menu currently builds choices from the token's available macros.
- [src/main/java/net/rptools/maptool/client/functions/TokenPropertyFunctions.java:287](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TokenPropertyFunctions.java#L287): Existing property functions expose token ownership for actor eligibility.
- [src/main/java/net/rptools/maptool/client/functions/TokenStateFunction.java:70](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TokenStateFunction.java#L70): Existing state setter can back preset state transitions through synchronized handlers.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
