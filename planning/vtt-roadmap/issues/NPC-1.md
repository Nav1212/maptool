**Roadmap ID:** `NPC-1` · **Priority:** P1 · **Relative size:** M

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

NPC automation needs an explicit configuration and lifecycle rather than treating every NPC token as automatically controlled or hiding behavior in unrelated token properties.

## Proposed scope

Add a versioned behavior-profile model scoped to the selected map, with stable token/profile identifiers, enabled state, movement and decision policy references, and separate runtime state. Provide a small GM assignment workflow and validation; leave patrol, perception, scheduling, and action execution to their own tickets.

## Acceptance criteria

- [ ] GM can create, validate, assign, disable, and remove a profile for selected NPC tokens; player tokens are never enrolled implicitly.
- [ ] Persist configuration and stable identifiers with the map/campaign while preserving older files and existing framework token properties.
- [ ] Separate configuration from transient execution state; loading a campaign does not silently restart actions before the GM activates the map runtime.
- [ ] Deleting, copying, or moving a token to another map produces a documented reconciliation result without orphaned actions or accidental control of the copy.
- [ ] Server rejects unauthorized profile changes; schema, lifecycle, persistence, and assignment tests cover invalid and missing references.

## Parent epic

{{NPC-EPIC}}

## Dependencies

- {{MAPAUTO-1}}
- {{ACCESS-6}}

## Source evidence

- [src/main/java/net/rptools/maptool/model/Token.java:812](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/Token.java#L812): Tokens already distinguish PC/NPC type; the existing type alone should not imply automation enrollment.
- [messages/src/main/proto/data_transfer_objects.proto:382](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/messages/src/main/proto/data_transfer_objects.proto#L382): TokenDto provides the current token synchronization contract that new profile references must coexist with.
- [src/main/java/net/rptools/maptool/model/Zone.java:1742](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/Zone.java#L1742): Zones already expose filtered token collections for map-scoped assignment.
- [src/main/java/net/rptools/maptool/util/PersistenceUtil.java:134](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/util/PersistenceUtil.java#L134): Campaign model versioning supplies compatibility mechanisms for new configuration.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
