**Roadmap ID:** `ACCESS-1` · **Priority:** P1 · **Relative size:** M

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

Hosts and players must interpret failures across configuration, connectivity, transport negotiation, and session authentication. A focused diagnostic flow can make those states actionable without changing the connection model.

## Proposed scope

Present transport-specific preflight and failure guidance using existing connection state. Provide a credential-free diagnostic report suitable for support.

## Acceptance criteria

- [ ] Distinguish local configuration errors, remote reachability failures, and authentication rejection.
- [ ] TCP guidance identifies the selected address and port and actual UPnP outcome without claiming Internet reachability from local checks.
- [ ] WebRTC guidance distinguishes signaling, ICE negotiation, and session-login failures.
- [ ] Copyable diagnostic reports omit passwords, keys, tokens, and other credentials.
- [ ] Automated tests exercise representative success, timeout, configuration, and authentication states without public infrastructure.

## Parent epic

{{ACCESS-EPIC}}

## Dependencies

No prerequisite backlog issue. Existing behavior must remain compatible.

## Source evidence

- [src/main/java/net/rptools/clientserver/ConnectionFactory.java:31](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/clientserver/ConnectionFactory.java#L31): Connection creation selects TCP or WebRTC transports.
- [src/main/java/net/rptools/maptool/client/ui/startserverdialog/StartServerDialog.java:37](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/startserverdialog/StartServerDialog.java#L37): Host setup already exposes transport, Easy Connect, and UPnP options.
- [src/main/java/net/rptools/maptool/client/ui/connecttoserverdialog/ConnectToServerDialog.java:53](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/connecttoserverdialog/ConnectToServerDialog.java#L53): Joining supports registry, LAN, and direct connections.
- [clientserver/src/main/java/net/rptools/clientserver/simple/connection/WebRTCConnection.java:32](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/clientserver/src/main/java/net/rptools/clientserver/simple/connection/WebRTCConnection.java#L32): The transport already records connection and negotiation state that diagnostics can expose.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
