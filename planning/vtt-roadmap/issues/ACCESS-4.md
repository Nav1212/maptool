**Roadmap ID:** `ACCESS-4` · **Priority:** P3 · **Relative size:** XL

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

The desktop client requirement is a substantial joining barrier for players. Embedded HTML interfaces and WebRTC data transport do not supply a complete browser client.

## Proposed scope

Implement the first usable player milestone using the architecture selected by ACCESS-2: authenticated map viewing, assets, visibility, and owned-token movement while the existing host remains authoritative.

## Acceptance criteria

- [ ] A player can join through a supported desktop browser, authenticate, receive permitted assets, and view the active map without installing Java.
- [ ] Render the player-visible map and owned tokens while preserving the existing visibility and fog contract; private campaign state is excluded from delivered snapshots.
- [ ] Submit owned-token movement to the host and display accepted updates; reject disallowed movement using the same policies as the desktop session.
- [ ] Handle reconnects and incompatible protocol versions with useful status and no duplicated operations.
- [ ] Document the tested browser/deployment matrix and exercise a host plus desktop and browser players; map editing, privileged macros, full sheet compatibility, and a GM browser client are out of scope.

## Parent epic

{{ACCESS-EPIC}}

## Dependencies

- {{ACCESS-2}}
- {{ACCESS-6}}

## Source evidence

- [src/main/java/net/rptools/maptool/server/ServerHandshake.java:451](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/server/ServerHandshake.java#L451): Session login is part of the existing server protocol.
- [messages/src/main/proto/data_transfer_objects.proto:1](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/messages/src/main/proto/data_transfer_objects.proto#L1): Existing protocol messages describe campaign/map state.
- [src/main/java/net/rptools/maptool/client/ui/htmlframe/HTMLWebViewManager.java:85](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/htmlframe/HTMLWebViewManager.java#L85): Embedded HTML is a desktop UI surface rather than a full browser player.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
