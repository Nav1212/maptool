**Roadmap ID:** `CONTENT-2` · **Priority:** P2 · **Relative size:** L

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

Token notes, GM notes, image handouts, and Markdown rendering exist. Provide a campaign-level notebook for information that should not require a map token.

## Acceptance criteria

- [ ] Add campaign-owned journal entries with stable IDs, folders/tags, title, Markdown body, and referenced campaign assets.
- [ ] Provide create/edit/delete/search and links between entries.
- [ ] Let the GM share an entry with all or selected players and revoke access; enforce visibility in network payloads as well as the UI.
- [ ] Persist journals and sharing settings in campaign files, and test save/reload, late-joining players, revocation, and missing linked assets.

## Parent epic

{{SYS-EPIC}}

## Dependencies

No prerequisite backlog issue. Existing behavior must remain compatible.

## Source evidence

- [src/main/java/net/rptools/maptool/model/Token.java:349](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/Token.java#L349): Token model stores player-facing and GM notes.
- [src/main/java/net/rptools/maptool/client/ui/AbstractTokenPopupMenu.java:77](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/AbstractTokenPopupMenu.java#L77): Displays token-associated image handouts.
- [src/main/java/net/rptools/maptool/client/functions/MarkDownFunctions.java:59](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/MarkDownFunctions.java#L59): Existing Markdown-to-HTML renderer.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
