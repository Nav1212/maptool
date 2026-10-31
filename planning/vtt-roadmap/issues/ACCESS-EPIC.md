**Roadmap ID:** `ACCESS-EPIC` · **Priority:** P1 · **Relative size:** XL

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

The existing desktop client supports hosting and multiple transports. Improve joining guidance and define the protocol boundary for a usable browser player.

## Acceptance criteria

- [ ] Complete or explicitly disposition the linked deliverables; discovery issues must produce a reproducible result and implementation decision.
- [ ] Demonstrate the completed user workflows with existing campaign compatibility and a documented support boundary.

## Tracked deliverables

- [ ] {{ACCESS-1}}
- [ ] {{ACCESS-2}}
- [ ] {{ACCESS-6}}
- [ ] {{ACCESS-3}}
- [ ] {{ACCESS-4}}
- [ ] {{ACCESS-5}}

## Dependencies

No prerequisite backlog issue. Existing behavior must remain compatible.

## Source evidence

- [src/main/java/net/rptools/maptool/server/ServerPolicy.java:29](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/server/ServerPolicy.java#L29): Existing application infrastructure is the foundation for this roadmap area.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
