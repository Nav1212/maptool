**Roadmap ID:** `DEV-1` · **Priority:** P1 · **Relative size:** M

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

The extension surface spans MTScript registration, GraalVM host exports, HTML5 bridges, and manifest files. Existing wiki extraction tooling shows documentation infrastructure; add a version-tied inventory and executable examples.

## Acceptance criteria

- [ ] Generate an inventory of registered MTScript names and exported JavaScript members for the built application version.
- [ ] Document parameters, return values, trust requirements, and a runnable example for the starter's supported API subset; link to existing wiki documentation.
- [ ] Provide a game-neutral .mtlib starter demonstrating JavaScript, MTScript interoperability, an HTML5 form, persistent data, and an event callback.
- [ ] Add a check that rebuilds the inventory and executes the starter's documented examples so API changes surface as reviewable differences.

## Parent epic

{{DEV-EPIC}}

## Dependencies

No prerequisite backlog issue. Existing behavior must remain compatible.

## Source evidence

- [src/main/java/net/rptools/maptool/client/MapToolExpressionParser.java:129](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/MapToolExpressionParser.java#L129): Exposes the registered macro function list.
- [src/main/java/net/rptools/maptool/client/script/javascript/api/JSAPIMapTool.java:20](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/script/javascript/api/JSAPIMapTool.java#L20): Declares top-level JavaScript objects and exported members.
- [build-resources/wikiDocExtraction/README.md:16](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/build-resources/wikiDocExtraction/README.md#L16): Existing tooling extracts macro documentation from the wiki.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
