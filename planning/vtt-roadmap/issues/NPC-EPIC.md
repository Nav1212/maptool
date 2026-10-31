**Roadmap ID:** `NPC-EPIC` · **Priority:** P1 · **Relative size:** XL

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

The repository has initiative, pathfinding, token sight, and movement callbacks, but those primitives do not by themselves provide a coordinated NPC runtime. The requested single-map experience needs patrols and decisions that the GM can start, understand, and stop without every client independently executing them. This is roadmap work, not a confirmed defect.

## Proposed scope

Deliver NPC-1 through NPC-8 as a framework-neutral, rule-based NPC subsystem within the shared map workspace and runtime clock. One designated GM/session authority commits actions; stable event ordering, explicit observer contexts, and recorded randomness support reproducible decisions. Do not require external AI or an LLM.

## Acceptance criteria

- [ ] A GM can configure eligible NPCs on one map, start a patrol or behavior profile, and observe accepted movements on connected clients.
- [ ] Turn-based and real-time modes share the map clock and one action coordinator; players and reconnecting clients never independently execute NPC decisions.
- [ ] Movement, doors, perception, and rule-based actions use the same accepted scene state and authorization contracts as the rest of the session.
- [ ] GM can pause, step, inspect, and override automation; invalid profiles, unavailable targets, or exhausted work budgets produce visible wait/error outcomes rather than uncontrolled movement.
- [ ] The child-ticket integration fixture demonstrates reproducible patrol, detection, action, interruption, and replay behavior; no full 3D simulation or external AI service is required.

## Tracked deliverables

- [ ] {{NPC-1}}
- [ ] {{NPC-2}}
- [ ] {{NPC-3}}
- [ ] {{NPC-4}}
- [ ] {{NPC-5}}
- [ ] {{NPC-6}}
- [ ] {{NPC-7}}
- [ ] {{NPC-8}}
- [ ] {{NPC-9}}

## Dependencies

No prerequisite backlog issue; reuse the existing map, token, and scripting infrastructure.

## Source evidence

- [src/main/java/net/rptools/maptool/model/InitiativeList.java:289](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/InitiativeList.java#L289): Initiative transitions already have veto and commit event hooks that a coordinator can adapt.
- [src/main/java/net/rptools/maptool/client/walker/ZoneWalker.java:30](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/walker/ZoneWalker.java#L30): Walker interfaces already expose waypoint routing, paths, footprints, and distance.
- [src/main/java/net/rptools/maptool/client/ui/zone/ZoneView.java:634](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/zone/ZoneView.java#L634): Token visibility already incorporates observer context and lighting.
- [src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java:439](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java#L439): Existing movement-veto callbacks are integration points that automated moves must preserve.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
