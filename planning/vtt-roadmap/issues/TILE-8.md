**Roadmap ID:** `TILE-8` · **Priority:** P2 · **Relative size:** M

Proposed single-map automation work based on source inspection at `ee0f937c73`. Existing MTScript, dice, topology, vision, initiative, and movement features are foundations to extend. This draft describes planned behavior.

## Describe the problem

Reusable presets need a practical authoring/test workflow so a GM can apply a working recipe to another object, inspect why an automatic roll happened, and diagnose a skipped activation without reading macro source.

## Proposed scope

Add a versioned recipe export/import format containing declarative bindings, check/action IDs, editable parameter defaults, and required handler identities. Separate recipe definitions from runtime counters/receipts. Provide a dry-run validator and publish tile traces into MAPAUTO-4's GM console rather than building another console. Extend the MAPAUTO-5 starter fixture with richer tile examples using the lightweight adapter, with no dependency on a catalog or future system package.

## Acceptance criteria

- [ ] Save a configured binding as a reusable recipe and apply it to another source with fresh binding IDs and explicit target/reference remapping; imports validate versions and required handlers before activation.
- [ ] Recipes retain editable defaults and descriptive labels but do not clone activation receipts, spent/cooldown state, or map-specific actor IDs into a newly applied instance.
- [ ] Offer validation/dry-run with a selected actor and injected check result; it reports eligibility, resolved inputs, selected outcome branch, and intended actions without consuming random rolls, cooldowns, or changing the live map.
- [ ] Emit tile-specific trace fields to the shared GM console: occurrence/binding/actor IDs, eligibility or suppression reason, adapter result, action steps, and pending/error state; preserve configured hidden-roll visibility.
- [ ] Extend the documented MAPAUTO-5 starter map and its repeatable checks to cover hazard success/failure, plate enter/release, a locked object, template pulses, duplicate-client requests, and reset/save/reload.

## Parent epic

{{TILE-EPIC}}

## Dependencies

- {{TILE-2}}
- {{TILE-3}}
- {{TILE-4}}
- {{TILE-5}}
- {{TILE-6}}
- {{TILE-7}}
- {{MAPAUTO-4}}
- {{MAPAUTO-5}}

## Source evidence

- [src/main/java/net/rptools/maptool/model/library/addon/AddOnLibraryImporter.java:45](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/library/addon/AddOnLibraryImporter.java#L45): Existing .mtlib format can carry optional reusable assets/handlers, while the first recipe workflow can remain local and independent of a catalog.
- [src/main/java/net/rptools/maptool/client/functions/CallFunction.java:77](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/CallFunction.java#L77): Structured argument arrays support reusable parameterized handlers.
- [src/main/java/net/rptools/maptool/client/functions/DefineMacroFunction.java:34](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/DefineMacroFunction.java#L34): User-defined function registration/introspection provides an existing handler-validation integration point.
- [src/main/java/net/rptools/maptool/model/gamedata/DataStoreManager.java:34](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/gamedata/DataStoreManager.java#L34): Persistent synchronized data primitives can store definitions separately from per-instance runtime state.

## Validation

Exercise the stated behavior with automation enabled and disabled, with the relevant map context, and with a host plus two clients where actions are synchronized. Persistence changes must cover save/reload and reconnect. Check that manual GM control remains usable and failures do not duplicate committed rolls or movement.
