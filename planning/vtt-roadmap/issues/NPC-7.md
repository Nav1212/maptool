**Roadmap ID:** `NPC-7` · **Priority:** P2 · **Relative size:** M

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

A GM must be able to explain and interrupt automation before trusting it during a session. Existing AI debug switches display pathfinding details rather than NPC decisions.

## Proposed scope

Extend the planned MAPAUTO-4 GM console with selected-NPC state, route, perception, candidate-action explanations, and committed action/roll records. Provide per-NPC and global pause, one-action stepping, and explicit return to manual control; do not create a second general runtime console.

## Acceptance criteria

- [ ] GM inspector shows state, route/next waypoint, observer-specific perception inputs, candidate and rejected actions, selected target, costs, and recorded roll outcomes.
- [ ] GM can pause globally or per NPC and step one pending action; queued work checks cancellation and pause before mutation.
- [ ] GM can override target/action, drag a token under a documented manual-control policy, or disable its automation; execution still revalidates current state and permissions.
- [ ] Preview/dry-run generates explanations without moving tokens, spending resources, rolling new dice, advancing initiative, or executing mutating macros.
- [ ] Tests verify GM-only access, no decision-debug broadcasts containing hidden information, pause/step/override transitions, and deterministic inspection of captured records.

## Parent epic

{{NPC-EPIC}}

## Dependencies

- {{MAPAUTO-4}}
- {{NPC-2}}
- {{NPC-3}}
- {{NPC-4}}
- {{NPC-6}}
- {{ACCESS-6}}

## Source evidence

- [src/main/java/net/rptools/maptool/client/DeveloperOptions.java:40](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/DeveloperOptions.java#L40): ShowAiDebugging is an existing debug toggle, but its actual consumers visualize A* rather than NPC decisions.
- [src/main/java/net/rptools/maptool/client/walker/astar/AbstractAStarWalker.java:650](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/walker/astar/AbstractAStarWalker.java#L650): Existing AI debug output shows pathfinder scores and nodes.
- [src/main/java/net/rptools/maptool/client/ui/tokenpanel/InitiativePanel.java:457](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/tokenpanel/InitiativePanel.java#L457): Existing GM permission checks provide a model for privileged session controls.
- [src/main/java/net/rptools/maptool/client/functions/LogFunctions.java:65](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/LogFunctions.java#L65): Macro logging controls are already restricted to trusted contexts.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
