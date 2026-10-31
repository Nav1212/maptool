**Roadmap ID:** `MEDIA-4` · **Priority:** P3 · **Relative size:** M

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

The source audit did not establish a native video-map playback workflow. Selecting a rendering path requires evidence about JavaFX/Swing integration, resource use, and platform codecs.

## Proposed scope

Evaluate a bounded animated background layer before choosing production formats or promising general cinematic effects.

## Acceptance criteria

- [ ] Document the supported codecs and rendering options on Windows, macOS, and Linux, including how decoding integrates with the map renderer.
- [ ] Build a reproducible local fixture showing an animated background with token layers, pan/zoom, lighting, and fog, or record the concrete blocker and next experiment.
- [ ] Measure frame rate and memory/CPU use on an explicit reference setup and demonstrate pause/unload and a static fallback.
- [ ] Define asset transfer, persistence, reconnect behavior, and a follow-up implementation plan with explicit format and size limits.

## Parent epic

{{MEDIA-EPIC}}

## Dependencies

No prerequisite backlog issue. Existing behavior must remain compatible.

## Source evidence

- [src/main/java/net/rptools/maptool/client/functions/MediaPlayerAdapter.java:35](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/MediaPlayerAdapter.java#L35): JavaFX media is already used for streams, but that is not proof of integrated video-map rendering.
- [src/main/java/net/rptools/maptool/client/ui/zone/renderer/ZoneRenderer.java:90](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/ui/zone/renderer/ZoneRenderer.java#L90): Map rendering integrates multiple tabletop layers and visibility.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
