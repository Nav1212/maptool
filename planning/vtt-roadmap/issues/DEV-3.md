**Roadmap ID:** `DEV-3` · **Priority:** P3 · **Relative size:** M

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

Native campaign/map/token formats and Universal VTT imports exist, but the audit did not establish a direct Foundry scene conversion workflow.

## Proposed scope

Assess a scene-only importer using user-supplied exports and the existing Universal VTT path as an alternative. Produce a compatibility report before promising whole-campaign migration.

## Acceptance criteria

- [ ] Document a tested source export version/schema and map its image, grid, walls, doors, lights, and token placement fields to existing MapTool concepts.
- [ ] Compare direct conversion with an intermediate Universal VTT export and identify information each route loses.
- [ ] Demonstrate a small redistributable scene fixture or record a reproducible blocker; list unsupported fields rather than silently dropping them.
- [ ] Produce an implementation plan and explicit exclusions for game-system automation, marketplace packages, journals, and whole-world migration.

## Parent epic

{{DEV-EPIC}}

## Dependencies

No prerequisite backlog issue. Existing behavior must remain compatible.

## Source evidence

- [src/main/java/net/rptools/maptool/client/utilities/DungeonDraftImporter.java:133](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/utilities/DungeonDraftImporter.java#L133): Existing Universal VTT importer provides a conversion boundary.
- [src/main/java/net/rptools/maptool/util/PersistenceUtil.java:1](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/util/PersistenceUtil.java#L1): Native MapTool formats have their own persistence model.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
