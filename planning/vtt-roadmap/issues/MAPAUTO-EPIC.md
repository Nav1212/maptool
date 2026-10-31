**Roadmap ID:** `MAPAUTO-EPIC` · **Priority:** P1 · **Relative size:** XL

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

Running an encounter on one map currently combines map, token, initiative, macro, and persistence workflows. Tile rules and autonomous NPCs need a shared map-level runtime and an accessible control surface.

## Proposed scope

Provide an opt-in single-map encounter workspace with map-scoped simulation, reusable state, and GM controls. Build on the current tabletop engine and preserve existing campaign workflows.

## Acceptance criteria

- [ ] Complete the linked workspace, shared-clock, checkpoint, control-console, and starter-scenario deliverables.
- [ ] Demonstrate a player entering a configured tile, a recorded check and consequence, and an NPC moving along a route under one map authority.
- [ ] GM can pause or single-step automation, inspect outcomes, and resume from saved state while connected players see only permitted information.
- [ ] Existing campaigns remain usable with automation disabled; the first milestone uses current properties and MTScript without requiring a marketplace, browser rewrite, or external AI service.

## Tracked deliverables

- [ ] {{MAPAUTO-1}}
- [ ] {{MAPAUTO-2}}
- [ ] {{MAPAUTO-3}}
- [ ] {{MAPAUTO-4}}
- [ ] {{MAPAUTO-5}}

## Dependencies

No prerequisite backlog issue; reuse the existing map, token, and scripting infrastructure.

## Source evidence

- [src/main/java/net/rptools/maptool/model/Zone.java:84](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/Zone.java#L84): Zone is the existing map state boundary.
- [src/main/java/net/rptools/maptool/model/InitiativeList.java:104](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/InitiativeList.java#L104): Initiative changes already expose macro event hooks.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
