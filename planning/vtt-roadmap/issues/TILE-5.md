**Roadmap ID:** `TILE-5` · **Priority:** P1 · **Relative size:** L

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

Area templates and movement-path helpers are available, but a GM currently has to supply the rules that turn a marked footprint into a hazard or occupancy-driven plate. Provide concrete automatic tile recipes on the shared region/event foundation.

## Proposed scope

Ship editable presets for an enter/cross hazard, an occupied-area turn/time pulse, and a pressure plate with empty/occupied transitions. Allow the effect footprint to reference a SCENE-3 region or existing radius/cone/line template and route checks/actions through TILE-2/TILE-3. Consume SCENE-4 committed movement occurrences and MAPAUTO-2 turn/time pulses. Initialize occupancy from the authoritative map snapshot and update it from accepted enter/exit events; reuse SCENE-3 containment geometry rather than introducing another spatial index.

## Acceptance criteria

- [ ] From a preset picker, configure a hazard's eligible actors, check, outcome effects, footprint, and repeat policy using forms; actor/resource/state names come from the lightweight adapter rather than hard-coded game rules.
- [ ] A pressure plate activates on an authoritative empty-to-occupied transition and releases on occupied-to-empty; provide an editable minimum actor count and optional adapter-resolved weight predicate.
- [ ] A persistent area applies its pulse on the selected actor-turn/shared-clock event, with explicit per-actor targeting and no duplicate pulse from client updates; moved/resized templates update the referenced footprint through SCENE-3.
- [ ] Test crossing a hazard in one long move, ending inside versus crossing through, two actors on one plate, exit order, and disabled/rearmed hazards using SCENE-4 fixtures and the current dice/property adapter.

## Parent epic

{{TILE-EPIC}}

## Dependencies

- {{TILE-2}}
- {{TILE-3}}
- {{TILE-4}}
- {{SCENE-3}}
- {{SCENE-4}}
- {{MAPAUTO-2}}

## Source evidence

- [src/main/java/net/rptools/maptool/client/functions/TemplateFunctions.java:69](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TemplateFunctions.java#L69): Templates include blast, burst, cone, line, radius, and wall variants.
- [src/main/java/net/rptools/maptool/model/drawing/Drawable.java:33](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/drawing/Drawable.java#L33): Template/drawing geometry exposes bounds and exact Area for a shared region adapter.
- [src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java:115](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java#L115): Existing movedOverPoints logic examines a token's movement path; generalized crossing detection remains SCENE-3/SCENE-4 work.
- [src/main/java/net/rptools/maptool/client/functions/TemplateFunctions.java:493](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TemplateFunctions.java#L493): Template creation is already synchronized through serverCommand().draw and returns a stable template ID.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
