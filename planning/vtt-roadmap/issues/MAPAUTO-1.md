**Roadmap ID:** `MAPAUTO-1` · **Priority:** P1 · **Relative size:** L

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

A tile-driven encounter needs one explicit active map context for its player tokens, automation definitions, NPCs, and controls. Existing multi-map campaigns should remain compatible.

## Proposed scope

Add an opt-in encounter workspace selecting one map and its required resources. Define a portable encounter package using existing archive/persistence infrastructure where practical.

## Acceptance criteria

- [ ] GM can start the workspace from a selected map and switch between edit and play modes without deleting other campaign maps or resources.
- [ ] Define which map, tokens, rules adapters, referenced assets/add-ons, and automation configuration belong to the encounter; validate unresolved references before play.
- [ ] All encounter actions carry the stable selected map ID and operate on that context rather than whichever map a client currently views.
- [ ] Export and reopen a reusable encounter with declared required resources, resolving or clearly reporting missing dependencies instead of silently dropping automation.
- [ ] Verify an existing campaign can enter and leave the workspace, save/reload, and host desktop players with automation disabled or enabled.

## Parent epic

{{MAPAUTO-EPIC}}

## Dependencies

No prerequisite backlog issue; reuse the existing map, token, and scripting infrastructure.

## Source evidence

- [src/main/java/net/rptools/maptool/model/Campaign.java:43](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/Campaign.java#L43): Campaign owns existing maps and campaign-wide data.
- [src/main/java/net/rptools/maptool/client/ui/MapToolFrame.java:1613](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/MapToolFrame.java#L1613): The desktop UI already tracks the current map renderer.
- [src/main/java/net/rptools/maptool/util/PersistenceUtil.java:284](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/util/PersistenceUtil.java#L284): Native map persistence is an existing portability boundary.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
