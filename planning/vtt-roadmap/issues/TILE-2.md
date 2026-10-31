**Roadmap ID:** `TILE-2` · **Priority:** P1 · **Relative size:** L

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

The dice parser and token-property API can already evaluate many mechanics. Tile authors need a code-free way to select the acting token, roll once, and obtain a structured result rather than embedding game-specific assumptions in each trigger.

## Proposed scope

Define a small map-local check-adapter contract and provide a reference adapter built from current token properties, dice expressions, and trusted MTScript handlers. An adapter describes available checks/saves, input fields, modifier/property lookup, comparison/outcome rules, and its trusted handler. Tile forms select a check or plain roll and edit numeric/enum parameters. Future SYS profiles may supply adapters without being a prerequisite.

## Acceptance criteria

- [ ] Provide a working reference adapter supporting a plain dice roll and a property-modified check against a configurable target/comparator; demonstrate at least two dice expressions without assuming d20, ability names, HP, or a particular ruleset.
- [ ] The tile inspector lets a GM select the check/save label, actor selection, target/modifiers, and public/owner/GM result visibility without editing macro code; auto execution is available for movement/occupancy triggers.
- [ ] Return a versioned result containing activation/check IDs, adapter identity, actor, rolled components, total, target when present, outcome, and resolved inputs; use TILE-7's authoritative receipt rather than rerolling on observing clients.
- [ ] Missing actor properties, unavailable adapters, invalid dice input, or handler errors produce an actionable paused/failed check result rather than silently substituting a value or applying an outcome.
- [ ] Tests exercise property lookup, successful/failed checks, a plain roll, visibility, and a missing-property failure through existing MTScript/dicelib; the milestone runs without SYS-1 or a package catalog.

## Parent epic

{{TILE-EPIC}}

## Dependencies

- {{TILE-1}}
- {{TILE-7}}

## Source evidence

- [dicelib/src/main/java/net/rptools/dicelib/expression/ExpressionParser.java:276](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/dicelib/src/main/java/net/rptools/dicelib/expression/ExpressionParser.java#L276): Registers standard, exploding, success-counting, Fudge, and other dice mechanics.
- [src/main/java/net/rptools/maptool/client/functions/TokenPropertyFunctions.java:368](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TokenPropertyFunctions.java#L368): getProperty resolves the token's evaluated property value.
- [src/main/java/net/rptools/maptool/client/functions/LastRolledFunction.java:43](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/LastRolledFunction.java#L43): The parser exposes rolled components through lastRolled/getRolled/getNewRolls.
- [src/main/java/net/rptools/maptool/client/functions/DefineMacroFunction.java:48](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/DefineMacroFunction.java#L48): Trusted macros can register reusable user-defined functions.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
