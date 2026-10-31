**Roadmap ID:** `NPC-2` · **Priority:** P1 · **Relative size:** L

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

Existing waypoints support path construction, but a reusable NPC patrol also needs a persistent route, progression rules, pauses, and a clear response to obstacles.

## Proposed scope

Add a GM patrol-route editor and route runner over NPC profiles, the shared scheduler, and the validated NPC movement executor. Support one-shot, loop, and ping-pong routes with waits measured by the map runtime clock. Do not introduce a separate region or pathfinding engine.

## Acceptance criteria

- [ ] GM can create, reorder, edit, and preview a route; choose one-shot, loop, or ping-pong progression; and configure per-waypoint waits.
- [ ] A route advances only after the accepted movement reaches its waypoint, with facing and arrival behavior defined independently of animation timing.
- [ ] Waits and route progression use the shared map clock and respect pause, save/reload, and turn-based execution policies.
- [ ] A blocked or invalidated route follows a configured bounded retry, wait, or return policy; it never teleports through obstacles or retries without a limit.
- [ ] Tests cover route completion, loops, ping-pong endpoints, mid-route obstacles, deleted waypoints, and consistent progression after reconnect.

## Parent epic

{{NPC-EPIC}}

## Dependencies

- {{NPC-1}}
- {{NPC-4}}
- {{NPC-5}}

## Source evidence

- [src/main/java/net/rptools/maptool/client/walker/ZoneWalker.java:34](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/walker/ZoneWalker.java#L34): The walker interface exposes setWaypoints and addWaypoints.
- [src/main/java/net/rptools/maptool/client/walker/AbstractZoneWalker.java:59](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/walker/AbstractZoneWalker.java#L59): Existing path construction divides routes into waypoint segments.
- [src/main/java/net/rptools/maptool/client/tool/PointerTool.java:1773](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/tool/PointerTool.java#L1773): Interactive movement already has explicit waypoint behavior to preserve when automated routes are committed.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
