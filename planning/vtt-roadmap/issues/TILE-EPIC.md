**Roadmap ID:** `TILE-EPIC` · **Priority:** P1 · **Relative size:** XL

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

MapTool already exposes movement callbacks, dice expressions, token properties, drawings, templates, and trusted macros. A GM specializing one map still needs to assemble these primitives to make an area roll a check, resolve a hazard, or operate an object.

## Proposed scope

Build a form-based interactive-tile layer over SCENE-3 regions and SCENE-4 trigger occurrences. Bind map objects to editable check/action presets, execute checks on the authoritative host, and persist activation state. The first milestone uses current token properties and MTScript through a small map-local rules adapter; a future system profile can supply that adapter but is not required. This epic consumes the shared dispatcher, clock, checkpoints, and GM console rather than creating replacements. No external API, package catalog, or LLM is required.

## Acceptance criteria

- [ ] Complete TILE-1 through TILE-8; keep geometry/event dispatch in SCENE-3/SCENE-4 and shared runtime controls in MAPAUTO-*.
- [ ] Without writing macro code, a GM can bind an area to an automatic property-based dice check and configure different success/failure actions on the active map.
- [ ] Provide an executable one-map demo containing a hazard, pressure plate, clickable object, and template-shaped effect, including automatic checks and once-only results seen by two clients.
- [ ] Existing map art, tokens, hand-written macros, and campaigns without tile automation continue to behave as before.

## Tracked deliverables

- [ ] {{TILE-1}}
- [ ] {{TILE-2}}
- [ ] {{TILE-3}}
- [ ] {{TILE-4}}
- [ ] {{TILE-5}}
- [ ] {{TILE-6}}
- [ ] {{TILE-7}}
- [ ] {{TILE-8}}

## Dependencies

No prerequisite backlog issue; reuse the existing map, token, and scripting infrastructure.

## Source evidence

- [src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java:63](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java#L63): Exposes token-move callbacks and path-related macro functions.
- [dicelib/src/main/java/net/rptools/dicelib/expression/ExpressionParser.java:276](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/dicelib/src/main/java/net/rptools/dicelib/expression/ExpressionParser.java#L276): Registers multiple dice mechanics rather than one fixed game system.
- [src/main/java/net/rptools/maptool/client/functions/TemplateFunctions.java:43](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TemplateFunctions.java#L43): Existing macros create, inspect, and move area templates.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
