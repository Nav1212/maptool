**Roadmap ID:** `CONTENT-1` · **Priority:** P1 · **Relative size:** L

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

Generic JSON/data namespaces and static bundle content exist, but the inspected source does not establish a standard rules-record browser. Add a reusable catalog of named records without hard-coding a particular game.

## Acceptance criteria

- [ ] Document a versioned compendium format with stable record ID, namespace, kind, name, tags, description, arbitrary system data, and source/license metadata.
- [ ] Register read-only compendiums from add-ons and browse/search/filter their records by text, kind, and tags.
- [ ] Preview record descriptions and assets, and copy a record into campaign-owned data without modifying its source package.
- [ ] Expose stable record references and a framework import callback, demonstrated by creating a token from an example actor record; verify persistence and duplicate-ID handling.

## Parent epic

{{SYS-EPIC}}

## Dependencies

No prerequisite backlog issue. Existing behavior must remain compatible.

## Source evidence

- [messages/src/main/proto/gamedata.proto:9](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/messages/src/main/proto/gamedata.proto#L9): Supports JSON, scalar values, assets, types, and namespaces.
- [src/main/java/net/rptools/maptool/model/library/addon/AddOnLibraryData.java:127](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/library/addon/AddOnLibraryData.java#L127): Supports static bundle data access.
- [src/main/java/net/rptools/maptool/client/functions/DataFunctions.java:39](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/DataFunctions.java#L39): Provides generic data lookup and listing.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
