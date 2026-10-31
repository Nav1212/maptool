**Roadmap ID:** `DEV-2` · **Priority:** P2 · **Relative size:** M

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

Authors can reimport bundles, and the engine already initializes add-on JavaScript contexts. Provide an explicit development workflow with validation, reload, and contextual errors rather than requiring repeated file selection.

## Acceptance criteria

- [ ] Add an explicit local development source setting and Reload action for an add-on; automatic file watching is optional.
- [ ] Validate manifest fields, referenced entry files, event handlers, and sheet registrations before activation.
- [ ] On successful reload, replace the bundle's contexts/registrations without accumulating duplicate handlers and preserve existing campaign-owned data; on validation failure, retain the active bundle.
- [ ] Provide diagnostics containing namespace, source path, operation, and line when available, plus a reusable test harness covering initialization, a macro call, an event, and reload.

## Parent epic

{{DEV-EPIC}}

## Dependencies

- {{DEV-1}}

## Source evidence

- [src/main/java/net/rptools/maptool/client/ui/addon/AddOnLibrariesDialogController.java:321](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/addon/AddOnLibrariesDialogController.java#L321): Current bundle replacement begins with a local file chooser.
- [src/main/java/net/rptools/maptool/model/library/addon/AddOnLibrary.java:609](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/library/addon/AddOnLibrary.java#L609): Initialization removes and recreates a bundle's JavaScript context.
- [src/main/java/net/rptools/maptool/client/functions/MacroJavaScriptBridge.java:185](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/MacroJavaScriptBridge.java#L185): Converts JavaScript exceptions into parser errors with available source-line information.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
