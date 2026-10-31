**Roadmap ID:** `NPC-5` · **Priority:** P1 · **Relative size:** L

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

A calculated path or direct XY macro update is not sufficient to guarantee a legal automated move. NPC movement must account for the live scene, configured movement costs, other actors, and door state at commit time.

## Proposed scope

Add an NPC movement actuator that consumes existing walker output, clips it to the configured budget, revalidates each committed segment, and uses the accepted movement/region pipeline. Blocked passages cause wait/replan behavior; active opening of native doors is a separate NPC-9 integration, so a basic patrol does not require the entire player-door feature.

## Acceptance criteria

- [ ] Respect configured movement budgets, map distance metrics, terrain costs, token footprints, topology, and an explicit actor-collision policy; ties and reservations use stable ordering.
- [ ] Honor movement-blocking topology and wait or replan at blocked passages. Opening a door is not a direct coordinate update or a bypass of its blocking state; native door interaction is deferred to NPC-9.
- [ ] Revalidate before committing movement after scene changes; no-path, canceled, unsupported-grid, and work-budget outcomes wait or report rather than teleport.
- [ ] Accepted segments preserve movement vetoes, committed path data, and existing region enter/exit/crossing delivery exactly once; rejected moves do not spend movement.
- [ ] Tests cover terrain, large footprints, competing NPCs, door changes, stale routes, partial budgets, vetoes, and region-trigger integration.

## Parent epic

{{NPC-EPIC}}

## Dependencies

- {{NPC-1}}
- {{SCENE-4}}
- {{ACCESS-6}}

## Source evidence

- [src/main/java/net/rptools/maptool/client/walker/astar/AbstractAStarWalker.java:213](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/walker/astar/AbstractAStarWalker.java#L213): The current pathfinder incorporates map walls, masks, and fog-related constraints.
- [src/main/java/net/rptools/maptool/client/walker/astar/AbstractAStarWalker.java:455](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/walker/astar/AbstractAStarWalker.java#L455): Movement validation checks token footprints against blocking topology.
- [src/main/java/net/rptools/maptool/model/Token.java:298](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/Token.java#L298): Terrain cost operations already include multiply, add, block, and free.
- [src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java:439](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java#L439): Existing movement veto handlers must remain effective for accepted automated movement.
- [src/main/java/net/rptools/maptool/client/functions/TokenLocationFunctions.java:553](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TokenLocationFunctions.java#L553): moveToken ultimately submits an XY property update, so blindly calling it is not a replacement for a validated movement actuator.
- [src/main/java/net/rptools/maptool/model/GridlessGrid.java:35](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/GridlessGrid.java#L35): Gridless mode explicitly disables automatic pathing and needs an explicit supported/unsupported automation policy.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
