**Roadmap ID:** `SCENE-3` · **Priority:** P1 · **Relative size:** L

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

Reusable scene automation benefits from named, persistent geometry rather than requiring each framework to encode areas in ad hoc drawings or macros. This is a proposed enhancement.

## Proposed scope

Add named rectangle and polygon regions as scene data, with stable IDs, enabled state, and GM editing. Regions become reusable geometry for later triggers; this issue does not execute actions.

## Acceptance criteria

- [ ] GM can create, rename, reshape, enable, duplicate, and delete regions.
- [ ] Regions retain stable identities through editing, saving, and network updates.
- [ ] Player views receive only region information required by the intended feature; GM editing overlays remain controlled.
- [ ] Invalid or degenerate geometry produces useful validation errors.
- [ ] Tests cover containment and boundaries, transformations, serialization, older maps, and network synchronization.

## Parent epic

{{SCENE-EPIC}}

## Dependencies

No prerequisite backlog issue. Existing behavior must remain compatible.

## Source evidence

- [src/main/java/net/rptools/maptool/model/Zone.java:1393](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/Zone.java#L1393): Zones already own map drawing elements and provide an integration point for region geometry.
- [src/main/java/net/rptools/maptool/client/ui/ToolbarPanel.java:275](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/ToolbarPanel.java#L275): Existing drawing tools support freehand and basic shapes.
- [messages/src/main/proto/data_transfer_objects.proto:610](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/messages/src/main/proto/data_transfer_objects.proto#L610): ZoneDto defines persisted and synchronized scene data.
- [src/main/java/net/rptools/maptool/util/PersistenceUtil.java:134](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/util/PersistenceUtil.java#L134): Campaign model versioning supplies the compatibility mechanism for new scene data.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
