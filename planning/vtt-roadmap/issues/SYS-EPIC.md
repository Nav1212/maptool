**Roadmap ID:** `SYS-EPIC` · **Priority:** P1 · **Relative size:** XL

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

MapTool provides framework primitives, generic properties, HTML interfaces, data namespaces, notes, and handouts. Add standard user workflows that assemble those primitives into reusable systems and campaign content.

## Acceptance criteria

- [ ] Complete the linked system-profile, interactive-sheet, compendium, journal, map-pin, and record-drop workflows.
- [ ] Provide a redistributable, game-neutral example demonstrating the complete workflow.
- [ ] Keep existing campaigns, community macro frameworks, token notes, and handouts usable.

## Tracked deliverables

- [ ] {{SYS-1}}
- [ ] {{SYS-2}}
- [ ] {{CONTENT-1}}
- [ ] {{CONTENT-2}}
- [ ] {{CONTENT-3}}
- [ ] {{CONTENT-4}}

## Dependencies

No prerequisite backlog issue. Existing behavior must remain compatible.

## Source evidence

- [src/main/java/net/rptools/maptool/client/ui/AppMenuBar.java:401](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/AppMenuBar.java#L401): Help links to the community Frameworks wiki.
- [src/main/java/net/rptools/maptool/client/functions/MacroDialogFunctions.java:60](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/MacroDialogFunctions.java#L60): Registers HTML/HTML5 frames, dialogs, and overlays.
- [src/main/java/net/rptools/maptool/client/functions/DataFunctions.java:39](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/DataFunctions.java#L39): Exposes generic namespaced data functions.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
