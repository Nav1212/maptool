**Roadmap ID:** `MAPAUTO-4` · **Priority:** P1 · **Relative size:** L

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

Automatic checks and NPC movement need an understandable explanation and a quick intervention path during play. Macro logs and movement-path overlays alone are not an encounter control panel.

## Proposed scope

Expose the shared runtime state and action receipts in a GM console. Preview intended effects before committing and allow a queued action or NPC controller to be suspended.

## Acceptance criteria

- [ ] Show map play/pause status, pending work, recent completed actions, source tile/NPC, triggering event, and the reason a condition or action succeeded, failed, or was skipped.
- [ ] Preview an action using a snapshot without mutating live map state or consuming live random outcomes; identify any handler that cannot support preview.
- [ ] GM can single-step, cancel eligible queued actions, suspend one tile/NPC controller, and take manual control; already committed outcomes are not silently undone.
- [ ] Roll details and hidden NPC/tile configuration remain visible only to the intended audience, with a separate optional player-facing explanation.
- [ ] Verify pause/intervention races, handler failures, history retention limits, and reconnect behavior without depending on an external logging or AI service.

## Parent epic

{{MAPAUTO-EPIC}}

## Dependencies

- {{MAPAUTO-2}}

## Source evidence

- [src/main/java/net/rptools/maptool/client/functions/LogFunctions.java:34](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/LogFunctions.java#L34): Macro logging exists but is a different workflow from a map control console.
- [src/main/java/net/rptools/maptool/client/ui/ToolbarPanel.java:40](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/ToolbarPanel.java#L40): The desktop tabletop already exposes map tool controls.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
