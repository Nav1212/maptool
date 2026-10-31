**Roadmap ID:** `NPC-3` · **Priority:** P2 · **Relative size:** L

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

NPC decisions must use what that NPC can perceive, rather than the GM's visible-token list or whichever player view happens to be active. Patrol-to-pursuit behavior also needs explicit memory and transition rules.

## Proposed scope

Build per-NPC perception snapshots using explicit map, token, sight, lighting, and topology context. Add a small configurable state machine such as idle, patrol, investigate, pursue, and return, with clock-based last-seen memory and optional references to existing scene regions for a leash or territory. Do not add a new region editor.

## Acceptance criteria

- [ ] Perception evaluates the configured NPC observer and senses, documents treatment of hidden/owner-only tokens, and never substitutes unrestricted GM or current-player knowledge.
- [ ] GM can configure state transitions, target eligibility, last-seen memory duration, and an optional existing region or distance limit for pursuit.
- [ ] Transitions consume one immutable snapshot per decision event; ties and competing transitions use documented stable ordering.
- [ ] Sight, darkness, door/topology changes, target removal, and map changes invalidate affected observations; memory expires through the shared runtime clock.
- [ ] Tests cover occlusion, facing/range, darkness, hidden targets, target loss, memory expiry, simultaneous candidates, and return-to-patrol behavior.

## Parent epic

{{NPC-EPIC}}

## Dependencies

- {{NPC-1}}
- {{MAPAUTO-2}}
- {{SCENE-3}}

## Source evidence

- [src/main/java/net/rptools/maptool/client/functions/TokenSightFunctions.java:92](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TokenSightFunctions.java#L92): canSeeToken uses the renderer's current PlayerView and additionally checks target visibility and ownership, so it is not an authority-independent NPC perception API.
- [src/main/java/net/rptools/maptool/client/ui/zone/ZoneView.java:600](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/zone/ZoneView.java#L600): Token sight geometry already applies topology and can underpin explicit NPC observer calculations.
- [src/main/java/net/rptools/maptool/client/ui/zone/ZoneView.java:634](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/zone/ZoneView.java#L634): Lighting is combined with a single-token player view, making observer context an explicit integration concern.
- [src/main/java/net/rptools/maptool/model/Token.java:571](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/Token.java#L571): NPC sight must be configured; existing PC type behavior does not supply a universal NPC sight default.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
