**Roadmap ID:** `CONTENT-3` · **Priority:** P2 · **Relative size:** L

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

Standalone journals need a way to associate information with a location without creating a character or object token solely to hold notes.

## Proposed scope

Add map pins referencing campaign journal entry IDs, with GM editing and player visibility derived from entry access and the documented pin-visibility policy.

## Acceptance criteria

- [ ] GM can place, move, label, link, and delete pins on a map; selecting a visible pin opens its journal entry.
- [ ] Define pin visibility for GM-only, explicitly shared, and fog-hidden locations; clients without access do not receive private labels or entry bodies.
- [ ] Journal deletion, access revocation, and missing links produce useful feedback without corrupting map data.
- [ ] Persist pin identity and links through map/campaign saves and synchronize placement and access changes to connected and late-joining clients.

## Parent epic

{{SYS-EPIC}}

## Dependencies

- {{CONTENT-2}}

## Source evidence

- [src/main/java/net/rptools/maptool/model/Zone.java:126](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/Zone.java#L126): Zone owns map layers and map-local entities.
- [src/main/java/net/rptools/maptool/model/Token.java:623](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/Token.java#L623): Existing token notes provide a different, token-associated information workflow.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
