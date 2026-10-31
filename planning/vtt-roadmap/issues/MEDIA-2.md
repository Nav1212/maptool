**Roadmap ID:** `MEDIA-2` · **Priority:** P2 · **Relative size:** L

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

Macro-controlled audio and shared cues do not provide a map editor for location-based ambience such as a waterfall or tavern.

## Proposed scope

Introduce optional sound-source entities with radius and volume falloff. Use a documented listener selection rule and retain local mute and volume preferences.

## Acceptance criteria

- [ ] GM can place, preview, move, enable, and remove a sound source with an asset, radius, loop setting, and volume.
- [ ] Define listener position for an owned token, multiple owned tokens, spectators, and GM preview; apply deterministic distance attenuation.
- [ ] Only sources on the relevant active map play, and changing maps or deleting sources stops their playback without leaving orphan players.
- [ ] Persist sources and synchronize changes and reconnect state; local mute preferences continue to override playback.
- [ ] Test overlapping sources, map switches, listener changes, invalid media, and save/reload; wall occlusion is explicitly deferred.

## Parent epic

{{MEDIA-EPIC}}

## Dependencies

- {{MEDIA-1}}

## Source evidence

- [src/main/java/net/rptools/maptool/client/functions/MediaPlayerAdapter.java:104](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/MediaPlayerAdapter.java#L104): Playback already supports per-stream volume and loop settings.
- [src/main/java/net/rptools/maptool/model/Zone.java:126](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/Zone.java#L126): Map-local scene state is owned by Zone.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
