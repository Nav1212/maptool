**Roadmap ID:** `CONTENT-4` · **Priority:** P2 · **Relative size:** L

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

A searchable record browser is useful, but importing actors and adding items or spells to a character still needs a consistent interaction contract.

## Proposed scope

Let registered systems define record drop handlers for maps and registered character sheets, using stable compendium references and normal ownership checks.

## Acceptance criteria

- [ ] Drag a supported actor record onto a map to create a token through the registered framework handler, with a clear placement preview.
- [ ] Drop supported item or ability records onto an owned character sheet and invoke its declared handler with record identity and target context.
- [ ] Reject unsupported kinds, incompatible profiles, and unauthorized targets with actionable feedback and no partial state changes.
- [ ] An imported instance retains its source reference while campaign edits remain independent; test duplicate names, repeated drops, missing source packages, and multiplayer updates.

## Parent epic

{{SYS-EPIC}}

## Dependencies

- {{CONTENT-1}}
- {{SYS-2}}

## Source evidence

- [src/main/java/net/rptools/maptool/client/functions/DataFunctions.java:39](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/DataFunctions.java#L39): Generic data records can support framework-owned imported instances.
- [src/main/java/net/rptools/maptool/client/ui/htmlframe/HTMLWebViewManager.java:85](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/htmlframe/HTMLWebViewManager.java#L85): Existing HTML5 interface infrastructure can host sheet drop targets.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
