**Roadmap ID:** `PKG-3` · **Priority:** P2 · **Relative size:** L

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

The current import flow replaces a registered namespace. Build a deliberate update workflow that distinguishes package code/assets from campaign-owned add-on data.

## Acceptance criteria

- [ ] Show available updates with installed/target versions, compatibility results, dependency changes, and release notes when supplied.
- [ ] Stage and validate the selected update before replacing the active bundle.
- [ ] Preserve existing namespaced campaign data unless an explicit, tested migration is supplied; retain the previous bundle and a data snapshot.
- [ ] Provide rollback to the previous bundle/data snapshot and verify save/reload and connected-client consistency.

## Parent epic

{{PKG-EPIC}}

## Dependencies

- {{PKG-1}}
- {{PKG-2}}

## Source evidence

- [src/main/java/net/rptools/maptool/client/ui/addon/AddOnLibrariesDialogController.java:335](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/addon/AddOnLibrariesDialogController.java#L335): Existing namespace replacement is confirmed and then re-registered.
- [src/main/java/net/rptools/maptool/model/library/addon/AddOnLibraryData.java:196](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/library/addon/AddOnLibraryData.java#L196): Initializes persistent namespaced data only when needed.
- [src/main/java/net/rptools/maptool/util/PersistenceUtil.java:874](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/util/PersistenceUtil.java#L874): Campaign persistence embeds installed bundle assets.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
