**Roadmap ID:** `SYS-2` · **Priority:** P2 · **Relative size:** L

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

HTML5 interfaces, editable token properties, and hover stat sheets already exist. Add a common registration and launch contract for interactive character sheets so frameworks need not invent their own entry point.

## Acceptance criteria

- [ ] Let a system/add-on register sheet identity, HTML5 entry URI, supported token property types, and display name.
- [ ] Expose Open Character Sheet from token actions and allow a default sheet per supported property type.
- [ ] Supply the selected token context; synchronize supported edits through existing permission-checked token update paths and refresh after remote changes.
- [ ] Provide an example editable sheet with a roll action; test owner, GM, and unauthorized-player behavior.

## Parent epic

{{SYS-EPIC}}

## Dependencies

- {{SYS-1}}

## Source evidence

- [src/main/java/net/rptools/maptool/client/functions/MacroDialogFunctions.java:63](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/MacroDialogFunctions.java#L63): HTML5 frame/dialog/overlay functions are registered.
- [src/main/java/net/rptools/maptool/model/library/addon/AddOnLibraryImporter.java:60](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/library/addon/AddOnLibraryImporter.java#L60): Bundles currently register stat_sheets.json.
- [src/main/java/net/rptools/maptool/client/ui/htmlframe/HTMLFrameFactory.java:227](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/htmlframe/HTMLFrameFactory.java#L227): Existing HTML interfaces receive token-change notifications.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
