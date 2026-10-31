**Roadmap ID:** `PKG-1` · **Priority:** P1 · **Relative size:** L

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

The current add-on dialog imports local files. The separate resource catalog downloads art packs; it does not establish an add-on discovery workflow.

## Acceptance criteria

- [ ] Define and document a versioned catalog format containing namespace, version, description, authors, license, download URL, and SHA-256.
- [ ] Add a searchable catalog view showing package details and installed status.
- [ ] Download a selected bundle, verify its digest and declared identity, then install it through the existing importer.
- [ ] Show actionable catalog/download errors and retain local import when the catalog is unavailable.

## Parent epic

{{PKG-EPIC}}

## Dependencies

No prerequisite backlog issue. Existing behavior must remain compatible.

## Source evidence

- [src/main/java/net/rptools/maptool/client/ui/addon/AddOnLibrariesDialogController.java:321](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/addon/AddOnLibrariesDialogController.java#L321): Imports .mtlib files through JFileChooser.
- [src/main/java/net/rptools/maptool/util/library/LibraryUtils.java:44](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/util/library/LibraryUtils.java#L44): Existing remote resource catalog uses the listArtPacks endpoint.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
