**Roadmap ID:** `SCENE-5` · **Priority:** P2 · **Relative size:** L

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

Frameworks can store an Elevation property, but a typed, interoperable elevation contract is needed for consistent editing, measurements, and future floor support. This proposal does not imply a defect in current planar movement.

## Proposed scope

Introduce numeric elevation in map units, editing and macro access, and optional elevation-aware ruler or distance output. Keep rendering order separate. Do not add 3D vision in this milestone.

## Acceptance criteria

- [ ] Tokens expose validated elevation with a documented zero default, units, and positive and negative values.
- [ ] Elevation survives token, map, and campaign persistence and multiplayer synchronization.
- [ ] Existing arbitrary Elevation properties and frameworks retain their behavior; any migration is explicit and documented.
- [ ] Measurement can show planar distance, elevation difference, and spatial distance without silently changing established planar movement rules.
- [ ] Tests cover units, rounding, missing and invalid values, serialization, macros, and separation from draw order.

## Parent epic

{{SCENE-EPIC}}

## Dependencies

No prerequisite backlog issue. Existing behavior must remain compatible.

## Source evidence

- [src/main/java/net/rptools/maptool/model/CampaignProperties.java:621](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/CampaignProperties.java#L621): Elevation currently appears as a generic campaign token property.
- [src/main/java/net/rptools/maptool/client/functions/TokenLocationFunctions.java:255](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TokenLocationFunctions.java#L255): The existing z coordinate refers to draw order, which must remain separate from elevation.
- [src/main/java/net/rptools/maptool/client/functions/TokenLocationFunctions.java:273](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TokenLocationFunctions.java#L273): Existing token distance logic is planar and documents its handling of terrain and topology.
- [messages/src/main/proto/data_transfer_objects.proto:382](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/messages/src/main/proto/data_transfer_objects.proto#L382): TokenDto is the protocol integration point for typed elevation data.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
