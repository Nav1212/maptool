**Roadmap ID:** `ACCESS-5` · **Priority:** P3 · **Relative size:** L

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

The current role model has GM and Player roles with detailed session policy switches. Groups that need an observer or a limited assistant GM lack an explicit role preset contract in the inspected source.

## Proposed scope

First define a capability-based preset model for new roles while preserving the effective behavior of existing GM/player campaigns.

## Acceptance criteria

- [ ] Document the supported capabilities for viewing maps, controlling owned tokens, editing scene data, managing initiative, and sharing campaign content.
- [ ] Provide Observer and Assistant GM presets with explicit defaults and GM-only management controls.
- [ ] Apply the same capability decisions at the server command boundary and in UI affordances, with tests for permitted and denied operations.
- [ ] Preserve old campaigns and accounts by mapping legacy roles to equivalent capabilities; saved presets and assignments survive reload and reconnect.

## Parent epic

{{ACCESS-EPIC}}

## Dependencies

- {{ACCESS-6}}

## Source evidence

- [src/main/java/net/rptools/maptool/model/player/Player.java:28](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/player/Player.java#L28): Core roles are GM and Player.
- [src/main/java/net/rptools/maptool/server/ServerPolicy.java:29](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/server/ServerPolicy.java#L29): Existing policies provide detailed session controls.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
