**Roadmap ID:** `MEDIA-3` · **Priority:** P2 · **Relative size:** L

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

The first shared-audio milestone provides individual cues and one ambient stream, leaving track organization and scene transitions to macros.

## Proposed scope

Add GM playlist editing and scene-linked playback presets on top of shared cue state and the existing media player.

## Acceptance criteria

- [ ] GM can create named playlists, reorder tracks, select sequential or shuffled playback, and configure repeat behavior.
- [ ] Expose play, pause, skip, stop, and configurable fade transitions with current-track state visible to the GM.
- [ ] Allow an optional audio preset per map and an explicit choice before a map switch changes current playback.
- [ ] Persist playlist and preset data and restore the current ambient state for reconnecting players without replaying completed effects.
- [ ] Test track completion, unavailable media, empty playlists, transition cancellation, and local volume/mute behavior.

## Parent epic

{{MEDIA-EPIC}}

## Dependencies

- {{MEDIA-1}}

## Source evidence

- [src/main/java/net/rptools/maptool/client/functions/SoundFunctions.java:35](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/SoundFunctions.java#L35): Existing macro functions expose audio controls.
- [src/main/java/net/rptools/maptool/client/functions/MediaPlayerAdapter.java:261](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/MediaPlayerAdapter.java#L261): The media adapter already implements fade-out behavior.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
