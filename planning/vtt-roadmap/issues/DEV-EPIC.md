**Roadmap ID:** `DEV-EPIC` · **Priority:** P1 · **Relative size:** XL

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

MapTool has several extension surfaces and wiki documentation. Bring discovery, examples, local iteration, and validation into a documented workflow tied to the actual application version.

## Acceptance criteria

- [ ] Complete DEV-1 and DEV-2, and record the bounded scene-import feasibility decision from DEV-3.
- [ ] Document a reproducible path from starter package to local validation, installation, and catalog submission.
- [ ] Keep documentation and examples scoped to supported public APIs and versioned package contracts.

## Tracked deliverables

- [ ] {{DEV-1}}
- [ ] {{DEV-2}}
- [ ] {{DEV-3}}

## Dependencies

No prerequisite backlog issue. Existing behavior must remain compatible.

## Source evidence

- [src/main/java/net/rptools/maptool/client/MapToolExpressionParser.java:78](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/MapToolExpressionParser.java#L78): Registers the MTScript function surface.
- [src/main/java/net/rptools/maptool/client/script/javascript/api/JSAPIMTScript.java:32](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/script/javascript/api/JSAPIMTScript.java#L32): JavaScript-to-MTScript bridge supports custom macro registration.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
