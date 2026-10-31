**Roadmap ID:** `SYS-1` · **Priority:** P1 · **Relative size:** L

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

The application exposes framework links and customizable campaign properties. Introduce an explicit profile contract and selection flow so users can start with an installed framework without manually assembling its campaign defaults.

## Acceptance criteria

- [ ] Define optional system-profile metadata for identity/version, required add-ons, property types, states/bars, macros, and sheet registrations.
- [ ] Offer installed profiles when creating a campaign, with an explicit generic/blank choice.
- [ ] Persist the selected profile identity/version and initialize its defaults only in the newly created campaign.
- [ ] Ship a small game-neutral example and test creation, save/reload, and opening a campaign whose profile package is unavailable.

## Parent epic

{{SYS-EPIC}}

## Dependencies

- {{PKG-2}}

## Source evidence

- [src/main/java/net/rptools/maptool/model/CampaignProperties.java:590](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/CampaignProperties.java#L590): Initializes generic token properties and related defaults.
- [src/main/java/net/rptools/maptool/model/library/addon/AddOnLibraryImporter.java:45](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/library/addon/AddOnLibraryImporter.java#L45): Existing bundle format provides an extension point for profile metadata.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
