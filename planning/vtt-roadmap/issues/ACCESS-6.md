**Roadmap ID:** `ACCESS-6` · **Priority:** P1 · **Relative size:** L

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

Doors, scene-region edits, shared audio control, and browser reads introduce new server operations. Their allowed actors and resulting client-visible state need an explicit contract using the existing GM/player roles and token ownership model. This proposal does not assert a vulnerability in current commands.

## Proposed scope

Specify and exercise the command boundary for the new roadmap features. Keep this milestone scoped to their operations and supporting multiplayer fixtures.

## Acceptance criteria

- [ ] Define allowed actors and policy switches for door interaction, region editing, shared audio control, and browser snapshot/movement requests.
- [ ] Provide a server-boundary fixture that submits representative requests as GM, token owner, non-owner, and disconnected identity.
- [ ] Verify denied requests leave state unchanged and do not produce accepted broadcasts; verify permitted requests preserve normal play.
- [ ] Cover concurrent requests and reconnect snapshots for the new command types as their implementations land; avoid extending this issue into an unrelated application-wide audit.

## Parent epic

{{ACCESS-EPIC}}

## Dependencies

No prerequisite backlog issue. Existing behavior must remain compatible.

## Source evidence

- [src/main/java/net/rptools/maptool/server/ServerPolicy.java:29](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/server/ServerPolicy.java#L29): Server policies define session restrictions for tokens, movement, vision, and UI.
- [src/main/java/net/rptools/maptool/model/player/Player.java:27](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/player/Player.java#L27): The core role model distinguishes GM and Player.
- [src/main/java/net/rptools/maptool/model/Token.java:1122](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/Token.java#L1122): Ownership is a core authorization input.
- [src/main/java/net/rptools/maptool/server/ServerMessageHandler.java:60](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/server/ServerMessageHandler.java#L60): The server command dispatcher is the relevant boundary for state mutation and rebroadcast.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
