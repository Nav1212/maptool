**Roadmap ID:** `PKG-EPIC` · **Priority:** P1 · **Relative size:** XL

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

MapTool already imports and distributes add-on bundles, but the inspected manager centers on selecting local files. Establish a discoverable package lifecycle around the existing format.

## Acceptance criteria

- [ ] Complete PKG-1, PKG-2, and PKG-3.
- [ ] Demonstrate discovery, compatible installation, update, and rollback using maintained example packages.
- [ ] Retain local .mtlib import and embedded campaign bundles as supported workflows.

## Tracked deliverables

- [ ] {{PKG-1}}
- [ ] {{PKG-2}}
- [ ] {{PKG-3}}

## Dependencies

No prerequisite backlog issue. Existing behavior must remain compatible.

## Source evidence

- [src/main/java/net/rptools/maptool/client/ui/addon/AddOnLibrariesDialogController.java:321](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/addon/AddOnLibrariesDialogController.java#L321): Add-on installation uses a local file chooser and can replace an existing namespace.
- [messages/src/main/proto/library.proto:10](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/messages/src/main/proto/library.proto#L10): Bundle metadata includes version and descriptive fields.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
