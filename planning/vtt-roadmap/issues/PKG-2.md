**Roadmap ID:** `PKG-2` · **Priority:** P1 · **Relative size:** L

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

The current manifest contains version metadata but no declared MapTool compatibility range or package dependency fields. Add explicit metadata and preflight validation for packages that opt into it.

## Acceptance criteria

- [ ] Add optional manifest fields for supported MapTool versions and required/optional add-on namespace/version ranges.
- [ ] Produce an installation plan identifying missing dependencies, incompatible versions, duplicate identities, and dependency cycles.
- [ ] For manifests declaring requirements, prevent activation of an unsatisfied plan and show specific resolution steps.
- [ ] Keep legacy bundles without the new fields loadable, clearly display unknown compatibility, and test old/new campaign round trips.

## Parent epic

{{PKG-EPIC}}

## Dependencies

No prerequisite backlog issue. Existing behavior must remain compatible.

## Source evidence

- [messages/src/main/proto/library.proto:10](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/messages/src/main/proto/library.proto#L10): AddOnLibraryDto includes version, namespace, authors, license, and descriptions.
- [src/main/java/net/rptools/maptool/model/library/addon/AddOnLibraryImporter.java:145](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/library/addon/AddOnLibraryImporter.java#L145): Reads library.json and constructs bundle metadata.
- [src/main/java/net/rptools/maptool/model/library/LibraryManager.java:167](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/library/LibraryManager.java#L167): Registers bundles and distributes them while hosting.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
