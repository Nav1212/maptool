**Roadmap ID:** `NPC-8` · **Priority:** P2 · **Relative size:** M

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

NPC automation crosses initiative, pathfinding, visibility, callbacks, and network state. Isolated unit checks cannot establish that a complete patrol-to-action sequence executes once, stays responsive, and can be replayed.

## Proposed scope

Create a small offline scenario harness using the shared map clock, recorded commands and roll outcomes, and real geometry/rule adapters. Exercise one-map NPC workflows without public services. Add a documented workload fixture and budget-exhaustion behavior rather than promising a universal NPC capacity.

## Acceptance criteria

- [ ] A fixture covers patrol, occlusion, target detection/loss, an eligible rule action, a door obstruction, a region callback, and GM interruption.
- [ ] Replaying the same initial scene, event sequence, and recorded randomness produces the same ordered decisions and accepted state changes without mutating the shared dice RNG.
- [ ] Duplicate/reordered delivery, reconnect snapshots, canceled movement, removed tokens, and stale targets do not produce extra actions or resource spending.
- [ ] Document a repeatable multi-NPC workload, measure decision/pathfinding work, and assert configured per-tick work limits defer remaining work without losing state or blocking UI processing.
- [ ] Run the harness with fake time and local fixtures in CI; distinguish unsupported geometry/configuration from failed assertions and keep existing tests intact.

## Parent epic

{{NPC-EPIC}}

## Dependencies

- {{NPC-2}}
- {{NPC-3}}
- {{NPC-5}}
- {{NPC-6}}
- {{NPC-7}}
- {{MAPAUTO-2}}

## Source evidence

- [src/main/java/net/rptools/maptool/client/walker/astar/AbstractAStarWalker.java:205](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/walker/astar/AbstractAStarWalker.java#L205): Current pathfinding has a wall-time fail-safe; multi-NPC scheduling needs explicit bounded-work outcomes and replay treatment.
- [src/test/java/net/rptools/maptool/model/PathTest.java:1](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/test/java/net/rptools/maptool/model/PathTest.java#L1): Existing path tests provide local fixture patterns for movement data.
- [src/test/java/net/rptools/maptool/model/SquareGridTest.java:1](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/test/java/net/rptools/maptool/model/SquareGridTest.java#L1): Existing grid tests can supply real geometry cases rather than trivial NPC mocks.
- [dicelib/src/test/java/net/rptools/dicelib/expression/ExpressionParserWithMockRollsTest.java:1](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/dicelib/src/test/java/net/rptools/dicelib/expression/ExpressionParserWithMockRollsTest.java#L1): Existing expression tests demonstrate controlled roll outcomes suitable for deterministic action scenarios.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
