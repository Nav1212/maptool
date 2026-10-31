**Roadmap ID:** `SCENE-6` · **Priority:** P3 · **Relative size:** M

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

Floor-aware sessions need a deliberate choice between linked maps and multiple floors inside one zone, including visibility and compatibility rules. This is an architectural roadmap item, not a claim that existing topology is broken.

## Proposed scope

Produce an ADR and a minimal two-floor experiment comparing the two approaches. Define a practical first milestone without committing to a full 3D engine. T5's model contract can be used before its full implementation is complete.

## Acceptance criteria

- [ ] ADR compares linked maps and multiple floors in one zone against rendering, assets, topology, fog, ownership, and protocol constraints.
- [ ] Define player and GM floor selection and isolation of hidden floors, lights, and fog.
- [ ] Demonstrate a minimal two-floor fixture and token transition, or document a reproducible architectural blocker and the next experiment.
- [ ] Specify migration and version compatibility and treatment of stairs, landings, and token elevation.
- [ ] Produce an estimated implementation sequence with explicit exclusions such as general 3D geometry and vertical ray tracing.

## Parent epic

{{SCENE-EPIC}}

## Dependencies

- {{SCENE-5}}

## Source evidence

- [src/main/java/net/rptools/maptool/model/Campaign.java:355](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/Campaign.java#L355): Campaigns already contain multiple maps, allowing a separate-map floor approach.
- [src/main/java/net/rptools/maptool/model/Zone.java:307](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/Zone.java#L307): Zones provide planar topology categories, including hill, pit, and cover blocking.
- [src/main/java/net/rptools/maptool/model/topology/MaskTopology.java:93](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/topology/MaskTopology.java#L93): Current visibility semantics provide useful building blocks but do not establish a general stacked-floor model.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
