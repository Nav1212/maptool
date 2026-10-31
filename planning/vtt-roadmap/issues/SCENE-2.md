**Roadmap ID:** `SCENE-2` · **Priority:** P2 · **Relative size:** M

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

A native door model needs a controlled player interaction workflow so opening a door does not require granting general topology-editing privileges. This is roadmap work, not a verified authorization defect.

## Proposed scope

Allow eligible players to operate unlocked doors using an owned token. Add GM-controlled locked state and configurable interaction distance. Defer game-specific keys, inventory integration, and skill checks.

## Acceptance criteria

- [ ] GM can enable or disable player door interaction and configure interaction distance.
- [ ] Server validates actor identity, token ownership, eligibility, current door state, and distance.
- [ ] Locked doors reject player opening requests with useful feedback; GM can override.
- [ ] Player controls do not expose hidden door labels or bypass normal visibility rules.
- [ ] Tests cover GM, owner, non-owner, locked, distant, stale, and simultaneous requests.

## Parent epic

{{SCENE-EPIC}}

## Dependencies

- {{SCENE-1}}
- {{ACCESS-6}}

## Source evidence

- [src/main/java/net/rptools/maptool/model/player/Player.java:27](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/player/Player.java#L27): Core roles distinguish GM and Player.
- [src/main/java/net/rptools/maptool/model/Token.java:1122](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/Token.java#L1122): Token ownership supplies the actor eligibility model for player interaction.
- [src/main/java/net/rptools/maptool/server/ServerPolicy.java:29](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/server/ServerPolicy.java#L29): Existing server policies govern session restrictions and should govern new interactions.
- [src/main/java/net/rptools/maptool/server/ServerMessageHandler.java:740](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/server/ServerMessageHandler.java#L740): Wall changes already cross a server command boundary; door requests should be separately validated there.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
