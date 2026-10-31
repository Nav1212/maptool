**Roadmap ID:** `MEDIA-1` · **Priority:** P2 · **Relative size:** L

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

Existing local macro playback is a foundation for sound, while a GM-facing session cue workflow and current-state synchronization would simplify ambient audio. This is an enhancement, not an assertion that audio is absent.

## Proposed scope

Add a small GM cue panel reusing existing playback, initially with one ambient stream and independent short sound effects. Defer positional audio, video, and sample-accurate synchronization.

## Acceptance criteria

- [ ] GM can select, start, and stop named cues for connected players.
- [ ] Each player retains local mute and volume controls and existing playback preferences.
- [ ] Joining and reconnecting players receive current ambient state without replaying expired effects.
- [ ] Unsupported or inaccessible media reports a useful error without blocking play.
- [ ] Tests cover state transitions, unauthorized control, reconnects, and local preferences.

## Parent epic

{{MEDIA-EPIC}}

## Dependencies

- {{ACCESS-6}}

## Source evidence

- [src/main/java/net/rptools/maptool/client/functions/SoundFunctions.java:36](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/SoundFunctions.java#L36): Existing macro functions support clips, streams, repeats, volume, positions, and fadeout.
- [src/main/java/net/rptools/maptool/client/functions/MediaPlayerAdapter.java:43](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/MediaPlayerAdapter.java#L43): JavaFX playback is already implemented and can be reused.
- [src/main/java/net/rptools/maptool/model/Asset.java:59](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/model/Asset.java#L59): Audio is already a supported asset type.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
