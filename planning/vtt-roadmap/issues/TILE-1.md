**Roadmap ID:** `TILE-1` · **Priority:** P1 · **Relative size:** L

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

Drawings and tokens already have identities and geometry, and movement macros receive path information. These primitives do not themselves provide the requested form-based tile configuration workflow.

## Proposed scope

Add a map-scoped automation binding with a stable ID, label, enabled state, source reference, trigger selection, and preset parameters. Its source is a SCENE-3 region, an object/token, or an existing drawing/template whose area is resolved by SCENE-3. Expose the binding in the selected-map inspector and subscribe it to SCENE-4 occurrences; do not introduce another collision detector or event dispatcher.

## Acceptance criteria

- [ ] A GM can select a region, object/token, drawing, or template on the active map and create/edit/delete/disable its tile binding using forms and a highlighted footprint.
- [ ] The inspector offers supported movement events from SCENE-4, such as enter/exit/crossing; turn or occupancy pulses are supplied by the shared map clock, and explicit interactions by the object interaction contract. Each option identifies its actor/source context rather than implying the region dispatcher already supplies every event type.
- [ ] Persist bindings with map/source stable IDs; copying a source makes an independent binding and remaps internal references, while missing/deleted sources become inert with a visible repair message.
- [ ] Players can see permitted interaction affordances but cannot edit the binding or hidden GM configuration; tests cover save/reload, source movement, duplication, and disabled bindings.

## Parent epic

{{TILE-EPIC}}

## Dependencies

- {{SCENE-3}}
- {{SCENE-4}}
- {{MAPAUTO-1}}

## Source evidence

- [src/main/java/net/rptools/maptool/model/drawing/Drawable.java:33](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/drawing/Drawable.java#L33): Drawables expose bounds, exact Area geometry, GUID identity, and layer.
- [src/main/java/net/rptools/maptool/model/drawing/AbstractDrawing.java:35](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/drawing/AbstractDrawing.java#L35): Drawing IDs are intended to remain stable, with copies receiving new IDs.
- [src/main/java/net/rptools/maptool/client/functions/DrawingGetterFunctions.java:36](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/DrawingGetterFunctions.java#L36): Existing macro API reads drawable identity-related information, name, coordinates, and dimensions.
- [src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java:427](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java#L427): Movement handlers already receive token/path context; generalized region dispatch belongs in the planned SCENE runtime.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
