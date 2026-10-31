**Roadmap ID:** `NPC-6` · **Priority:** P2 · **Relative size:** L

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

Automatic NPC turns need a framework-neutral way to select legal actions and targets without hardcoding one game's attack rules or granting arbitrary macros extra trust.

## Proposed scope

Add an ordered rule-based action selector over the NPC's perception/state snapshot and remaining budgets. Actions reference GM-approved macros or supported movement operations, with configurable target filters, range, resource predicates, roll formulas, and wait fallback. Record host-resolved randomness for replay; do not use external AI. Reuse the map-local check adapter and structured result contract from TILE-2 for NPC skill/action rolls; do not introduce a second incompatible rule-resolution service.

## Acceptance criteria

- [ ] GM configures action candidates, eligibility predicates, stable priorities or explicit weighted choice, target selection, costs, and a no-action fallback; invalid rules report useful validation errors.
- [ ] Candidate evaluation uses only the supplied NPC snapshot and cannot execute mutating macros, spend resources, or reveal unrestricted GM knowledge.
- [ ] Before execution, revalidate actor, target, map, permissions, and costs; approved macro execution retains existing trust boundaries and explicit token/map context.
- [ ] Action identifiers prevent duplicate execution; roll outcomes are resolved once by the authority and recorded, with deterministic replay that never reseeds the shared global dice RNG.
- [ ] Tests cover competing actions, missing targets, range/resources, failed macros, stale decisions, duplicate delivery, deterministic tie-breaking, and replayed roll outcomes.

## Parent epic

{{NPC-EPIC}}

## Dependencies

- {{NPC-1}}
- {{NPC-3}}
- {{NPC-4}}
- {{NPC-5}}
- {{ACCESS-6}}
- {{TILE-2}}

## Source evidence

- [src/main/java/net/rptools/maptool/model/MacroButtonProperties.java:467](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/MacroButtonProperties.java#L467): Existing macro execution can target supplied tokens, while later execution paths depend on current renderer and trust context.
- [src/main/java/net/rptools/maptool/client/MapToolLineParser.java:1064](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/MapToolLineParser.java#L1064): The parser already evaluates expressions with an explicit variable resolver.
- [src/main/java/net/rptools/maptool/client/MapToolLineParser.java:1712](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/MapToolLineParser.java#L1712): Macro trust checks must remain intact when actions run automatically.
- [dicelib/src/main/java/net/rptools/dicelib/expression/RunData.java:25](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/dicelib/src/main/java/net/rptools/dicelib/expression/RunData.java#L25): Dice execution uses a shared RNG; its global seed setter must not be repurposed for per-NPC replay.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
