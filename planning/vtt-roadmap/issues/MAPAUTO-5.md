**Roadmap ID:** `MAPAUTO-5` · **Priority:** P2 · **Relative size:** M

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

The specialized engine needs a reusable example showing map authors how tile rules, roll outcomes, and NPC movement fit together without hand-writing a complete framework.

## Proposed scope

Ship a game-neutral sample encounter with configured check tiles, a simple consequence, one patrol, and a short authoring/play guide. Demonstrate GM controls and reproducible reset.

## Acceptance criteria

- [ ] Opening the example identifies the required local resources and starts paused with a clear edit/play walkthrough.
- [ ] A player-owned token entering a configured tile causes one recorded configurable check and the configured pass/fail response, with correct result visibility.
- [ ] An NPC follows a short authored patrol using legal movement; the GM can pause, single-step, and take control through the automation console.
- [ ] Restore the baseline and replay the documented flow; saved/reconnected clients retain completed outcomes and do not create extra rolls or patrol jobs.
- [ ] Include a two-client acceptance scenario and authoring instructions for changing the tile check and route; no commercial rules content or external AI service is required.

## Parent epic

{{MAPAUTO-EPIC}}

## Dependencies

- {{MAPAUTO-3}}
- {{MAPAUTO-4}}
- {{TILE-2}}
- {{TILE-3}}
- {{NPC-1}}
- {{NPC-2}}

## Source evidence

- [src/main/java/net/rptools/maptool/util/PersistenceUtil.java:284](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/util/PersistenceUtil.java#L284): Map saves can support reusable encounter fixtures.
- [src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java:63](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java#L63): Existing movement hooks are a foundation for the worked example.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
