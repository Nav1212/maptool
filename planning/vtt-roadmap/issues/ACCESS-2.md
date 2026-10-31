**Roadmap ID:** `ACCESS-2` · **Priority:** P3 · **Relative size:** M

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

A browser companion needs an architectural boundary across authentication, transport, assets, serialization, and desktop APIs. The existing WebRTC transport alone does not establish a browser client.

## Proposed scope

Evaluate direct protocol compatibility, a gateway or companion service, and a reusable server boundary. Validate a small read-only experiment without promising a full client rewrite; estimate production implementation separately.

## Acceptance criteria

- [ ] ADR compares direct protocol compatibility, a gateway or companion service, and extraction of a reusable server boundary.
- [ ] Inventory constraints involving authentication, signaling, campaign serialization, assets, desktop APIs, and macro execution.
- [ ] Demonstrate a local authenticated read-only campaign or map snapshot, or provide a reproducible blocker and the next experiment.
- [ ] Define the first usable browser milestone, initially excluding editing and privileged macros.
- [ ] Document deployment, authorization boundaries, version compatibility, and estimated follow-up work.

## Parent epic

{{ACCESS-EPIC}}

## Dependencies

- {{ACCESS-6}}

## Source evidence

- [build.gradle:125](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/build.gradle#L125): The application plugin launches the existing desktop application and configures JVM startup.
- [messages/src/main/proto/message.proto:17](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/messages/src/main/proto/message.proto#L17): Protocol envelopes provide a possible browser interoperability boundary.
- [messages/src/main/proto/data_transfer_objects.proto:610](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/messages/src/main/proto/data_transfer_objects.proto#L610): Campaign and map data transfer objects are candidates for a read-only protocol experiment.
- [src/main/java/net/rptools/maptool/server/ServerHandshake.java:67](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/server/ServerHandshake.java#L67): Authentication behavior must be included in any browser architecture.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
