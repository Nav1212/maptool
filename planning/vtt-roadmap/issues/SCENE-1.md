**Roadmap ID:** `SCENE-1` · **Priority:** P1 · **Relative size:** L

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

Preparing a door currently builds on wall editing, topology manipulation, or imported portal geometry. This proposal adds a native workflow; it is not a confirmed defect report.

## Proposed scope

Introduce identifiable doors attached to wall spans and initially controlled by the GM. Preserve independent movement, sight, light, and aura blocking settings, along with campaign and protocol compatibility.

## Acceptance criteria

- [ ] GM can create, select, label, open, close, and remove a door without editing raw topology.
- [ ] Opening disables the door's configured blocking channels; closing restores its configured closed behavior.
- [ ] State changes update connected clients and invalidate affected vision and pathfinding caches.
- [ ] Campaign and map persistence and protocol round trips preserve door identity, geometry, and state; older maps still load.
- [ ] Tests cover state transitions, topology effects, serialization, and reconnect state.

## Parent epic

{{SCENE-EPIC}}

## Dependencies

No prerequisite backlog issue. Existing behavior must remain compatible.

## Source evidence

- [src/main/java/net/rptools/maptool/model/topology/Wall.java:314](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/topology/Wall.java#L314): Wall.Data stores direction and blocking modifiers; it provides a foundation for explicit door state.
- [src/main/java/net/rptools/maptool/client/tool/WallTopologyTool.java:64](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/tool/WallTopologyTool.java#L64): Existing wall editing tool can host GM door creation and selection.
- [src/main/java/net/rptools/maptool/client/swing/walls/WallConfigurationController.java:29](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/swing/walls/WallConfigurationController.java#L29): Existing controls edit independent wall blocking settings.
- [src/main/java/net/rptools/maptool/client/utilities/DungeonDraftImporter.java:230](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/utilities/DungeonDraftImporter.java#L230): Imported door and window portals are translated into topology rather than an explicit interactive door entity.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
