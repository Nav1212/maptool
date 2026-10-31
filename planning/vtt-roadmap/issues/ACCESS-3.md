**Roadmap ID:** `ACCESS-3` · **Priority:** P2 · **Relative size:** M

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

Existing server, join, resource, and campaign dialogs expose the necessary controls, but a new group must assemble these steps into a working session.

## Proposed scope

Provide a skippable first-session guide that opens existing workflows and explains their results without duplicating connection or campaign logic.

## Acceptance criteria

- [ ] Offer separate host and player paths, with an explicit skip/reopen option and a saved completion preference.
- [ ] The host path creates or opens a campaign, places a sample token, assigns player ownership, and opens the existing hosting flow.
- [ ] The player path reaches the existing join flow and explains authentication, owned-token selection, and basic movement after a successful connection.
- [ ] Use transport-specific diagnostics from ACCESS-1 on failure, preserving entered non-secret settings and avoiding a claim of success before a session handshake.
- [ ] Validate the guide with a repeatable two-client sample session and an offline/local-only path.

## Parent epic

{{ACCESS-EPIC}}

## Dependencies

- {{ACCESS-1}}

## Source evidence

- [src/main/java/net/rptools/maptool/client/ui/startserverdialog/StartServerDialog.java:40](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/startserverdialog/StartServerDialog.java#L40): A host configuration workflow already exists.
- [src/main/java/net/rptools/maptool/client/ui/connecttoserverdialog/ConnectToServerDialog.java:282](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/connecttoserverdialog/ConnectToServerDialog.java#L282): Existing join paths cover direct, LAN, and registry connections.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
