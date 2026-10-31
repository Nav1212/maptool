# Animated map backgrounds: bounded discovery (MEDIA-4)

Status: bounded experiment completed with concrete native-codec and application-build
blockers. MEDIA-4 remains partially validated because native decoding, playback
performance, and pause/disposal checks could not execute; production support remains
unverified.

Decision: retain the static map as the reliable baseline. Evaluate a bounded
JavaFX-to-Graphics2D frame bridge at the board layer before changing MapTool's
asset model or promising video maps. Do not put a live `JFXPanel`/`MediaView`
above the existing map canvas: it would bypass its token/light/fog composition.
Do not mark the broader MEDIA epic or production video support complete from
this experiment.

## Pinned evidence and platform matrix

`build.gradle:154` pins `javafx.version = '22'`. The official OpenJFX `22-ga`
tag resolves to commit **3b925780ab87c4987bd35a0d5dbcb66e8a2a5b31** (`22+30`).
The following URLs pin that commit rather than following current documentation:

- [Media package encoding/container table](https://github.com/openjdk/jfx/blob/3b925780ab87c4987bd35a0d5dbcb66e8a2a5b31/modules/javafx.media/src/main/java/javafx/scene/media/package-info.java#L43):
  H.264/AVC and H.265/HEVC video; MP4 paired with AAC; HLS variants are listed.
  Audio-only AAC/MP3/PCM and their containers do not make an animated map.
- [Windows/Linux GStreamer platform MIME table](https://github.com/openjdk/jfx/blob/3b925780ab87c4987bd35a0d5dbcb66e8a2a5b31/modules/javafx.media/src/main/java/com/sun/media/jfxmediaimpl/platform/gstreamer/GSTPlatform.java#L42):
  explicitly labels Windows and Linux; accepts `video/mp4`, `video/x-m4v`, and
  HLS MIME types. Container acceptance alone does not establish successful
  decoding of every profile or system codec installation.
- [macOS AVFoundation platform](https://github.com/openjdk/jfx/blob/3b925780ab87c4987bd35a0d5dbcb66e8a2a5b31/modules/javafx.media/src/main/java/com/sun/media/jfxmediaimpl/platform/osx/OSXPlatform.java#L38):
  uses AVFoundation and lists MP4/M4V/HLS; its native library must load.
- [Platform selection](https://github.com/openjdk/jfx/blob/3b925780ab87c4987bd35a0d5dbcb66e8a2a5b31/modules/javafx.media/src/main/java/com/sun/media/jfxmediaimpl/platform/PlatformManager.java#L99):
  selects GStreamer on non-iOS platforms and adds the macOS platform.
- [READY and asynchronous initialization](https://github.com/openjdk/jfx/blob/3b925780ab87c4987bd35a0d5dbcb66e8a2a5b31/modules/javafx.media/src/main/java/javafx/scene/media/MediaPlayer.java#L121)
  and [synchronous/asynchronous errors](https://github.com/openjdk/jfx/blob/3b925780ab87c4987bd35a0d5dbcb66e8a2a5b31/modules/javafx.media/src/main/java/javafx/scene/media/package-info.java#L238).
- [MediaPlayer.dispose](https://github.com/openjdk/jfx/blob/3b925780ab87c4987bd35a0d5dbcb66e8a2a5b31/modules/javafx.media/src/main/java/javafx/scene/media/MediaPlayer.java#L2362)
  frees player resources and moves to terminal DISPOSED; pause is not disposal.
- [Node.snapshot threading](https://github.com/openjdk/jfx/blob/3b925780ab87c4987bd35a0d5dbcb66e8a2a5b31/modules/javafx.graphics/src/main/java/javafx/scene/Node.java#L2186):
  may use a Scene without a Stage; synchronous snapshot must run on the FX
  Application Thread. It must not run inside `ZoneRenderer` on the Swing EDT.

| Platform | Source-supported scope in JavaFX22 | This discovery actually verified |
| --- | --- | --- |
| Windows | GStreamer media backend accepts MP4/M4V; API lists H.264 and H.265. Actual native decoder/profile availability must be probed on the packaged runtime. | Not run; no Windows decoding/performance claim. |
| macOS Intel/ARM | AVFoundation backend accepts MP4/M4V/HLS; API lists H.264 and H.265. OS/native decoder capabilities still apply. | Not run; no macOS decoding/performance claim. |
| Linux x86_64 | GStreamer backend accepts MP4/M4V; published JavaFX22 Linux jar supplies libav plugin builds through FFmpeg ABI60. Compatible external libav libraries remain necessary. | FX/Swing initialization, fallback composition and shutdown work; H.264 MediaPlayer creation fails on Debian13's ABI61-only codec installation. |

WebM/VP8/VP9, AV1, alpha video, animated WebP/GIF, 10-bit HDR, network live
streams, and seamless/sample-accurate looping are not validated production
formats here. They are not included in the initial proposal. The API's codec
list is evidence of declared support, not a guarantee about every distribution,
bitstream, patent license, or bundled decoder. No codec redistribution/licensing
decision is made by this ADR.

## Reproducible fixture and measured result

[The standalone fixture](../../tools/animated-map-prototype/README.md) downloads
SHA-256-pinned JavaFX22 Linux artifacts from Maven Central and generates a
four-second silent H.264 Baseline, yuv420p, 640×360, 15 fps MP4 with ffmpeg.
Its `MediaView` snapshot path publishes a bounded latest frame to a Swing
`Graphics2D` compositor. The synthetic board includes a background/object stamp,
grid, illustrative radial darkness, token layer, opaque fog, and continuous
pan/zoom. Pixel assertions verify token/light ordering and fog occlusion. It
does not instantiate `ZoneRenderer` or claim equivalent tabletop visibility.

Reference setup: Debian 13 trixie, x86_64, Linux 6.18.57, 5 logical CPUs,
33 GiB reported host memory, Temurin 21.0.9+10, JavaFX22+30 Linux Maven natives,
Xvfb 1280×1024×24, `-Dprism.order=sw`, `-Xms64m -Xmx256m`, ffmpeg
7.1.5 with libx264. Only a 640×360 board was drawn. CPU includes JVM/toolkit
startup and is measured as process CPU time / elapsed wall time (100% is one
core); RSS/heap are sampled. Xvfb CPU and total system CPU are not included.

The committed [result JSON](../../tools/animated-map-prototype/results/debian13-javafx22.json)
is the authoritative measurement for this run. Its native outcome is:

```text
MediaException: UNKNOWN ... Could not create player!
decoded_video_verified = false
media_ready = false
snapshot_frames = 0
```

FPS, CPU, RSS and heap values in that file measure the **static fallback and
illustrative compositor only**. They are not decoded-video performance numbers.

| Measured fallback/compositor value | This recorded run |
| --- | --- |
| Elapsed / painted frames | 10.079 s / 101 |
| Swing paint rate, including toolkit startup | 10.02 fps |
| Process CPU, one-core normalization | 11.61% |
| Sampled peak RSS / sampled peak heap | 146,544 KiB / 24,403,632 bytes |
| Decoded/snapshot video frames | 0 |
| Static fallback and composition assertions | Passed |
| Native player creation / native pause / native disposal | Not verified; player creation failed |

The harness requests pause at six seconds and unload at eight seconds, but
native pause/dispose cannot be claimed when no player was created. Static
fallback, cleared playback references, timer shutdown, and composition pixel
checks are separately recorded. This is a useful failure-path experiment, not a
passing native decoder benchmark.

Concrete codec evidence: `javafx-media-22-linux.jar` contains
`libavplugin-ffmpeg-58.so`, `-59.so`, and `-60.so` (plus older variants), not a
`-61.so` plugin. `ldd` on the shipped `-60.so` reports missing
`libavcodec.so.60` and `libavformat.so.60`; the host supplies
`libavcodec.so.61` and `libavformat.so.61`. The player error plus this ABI
mismatch prevents a native decoding-readiness claim. We did not disable TLS,
checksums, rename incompatible libraries, or install an unverified decoder.

There is also a separately captured main-application build blocker: the root
build attempt could not resolve `com.jetbrains.intellij.java:java-gui-forms-rt:241.12019`.
The JetBrains repository redirects to
`d2cico3c979uwg.cloudfront.net/...` and
`artifacts-caching-proxy.aws.intellij.net/...`; both return **403 Forbidden**
in this environment. Existing captured output is
`/workspace/.maptool-cloud/baseline-current.log`. That attempt prevented actual
map-renderer smoke testing even though the isolated fixture compiles and runs.
No extra environment domains were requested for this bounded discovery.

## Actual renderer constraints and options

The source path is
`src/main/java/net/rptools/maptool/client/ui/zone/renderer/ZoneRenderer.java`:

- `paintComponent` around lines 657–702 renders a complete map into a pooled
  AWT buffer. The Graphics2D path is conditional on
  `!viewModel.isUsingGdxRenderer()`. A future GDX path needs a separate texture
  implementation; use the static poster there until tested.
- `renderZone` around lines 836–944 orders board, background drawings/tokens,
  object drawings, grid, object tokens, lights/lumens/auras, darkness, token/GM
  drawings and tokens, labels, then fog. Later visibility overlays and selected
  always-visible tokens also matter. A movie must be rendered at the **board**
  stage, so the existing later passes still cover it.
- `renderBoard` around lines 1058–1113 caches a viewport-sized `backBuffer` and
  draws the static `ImageManager` map image using zone scale/offset, board
  origin, and independent X/Y image scale. Its cache refreshes on board/scale
  changes; swapping decoded frames alone would not animate that cache.

| Option | Result and cost | Disposition |
| --- | --- | --- |
| MediaView/JFXPanel visually stacked over Swing | Bypasses AWT light/fog masking and layer order; independent transforms and focus/HiDPI behavior. | Reject as production composition. |
| FX MediaView snapshot → bounded AWT frame → existing board pass | Keeps AWT masking and transforms; incurs FX snapshot/readback plus conversion/copy and repeated board paints. | Candidate for the next bounded test; not yet a production decision. |
| Native/FFmpeg decoder → reusable pixel buffers → AWT/GDX | More control over pixels; additional dependency, distribution, lifecycle, codec and native packaging work. | Separate alternative if pinned JavaFX cannot satisfy platforms/budgets. |
| Static poster | Existing rendering/cache semantics and reliable fallback. | Required baseline on decode, platform, budget, or download failure. |

For the candidate path, keep static texture/noise caching separate from the
changing movie frame. Apply the same zone-to-screen transform and clipping as
the current board image; do not recompute vision geometry on each video frame.
Run decoder/snapshot work on the FX thread and AWT painting on the EDT. Publish
immutable or explicitly owned double/triple buffers; keep at most one pending
latest frame and drop obsolete frames. Never wait synchronously across FX and
EDT, enqueue unlimited images, or mutate a frame while EDT painting uses it.
Measure existing repaint debouncing and rendering costs before selecting a
frame cap. Video is cosmetic; it must not drive tile/initiative/game logic.

## Proposed initial envelope (limits to test, not verified support)

- One visible animated **board background** per client; hidden/inactive maps
  keep a poster and have no active decoder.
- Silent local/file-backed MP4, H.264 Baseline/Main, 8-bit yuv420p, fixed
  dimensions, no alpha; at most **1280×720**, **24 fps**, **30 seconds**, and
  **32 MiB encoded** per clip. No auto-transcode or remote streaming in v1.
- Require an authored static poster with matching aspect ratio and explicit
  board bounds/scale. Prefer `faststart` MP4; byte limit must be checked before
  decoding and metadata limits before enabling playback.
- Runtime target for evaluation: at most 24 frame publications/second, one
  pending latest frame, and at most three application-owned RGBA frames
  (about **10.6 MiB at 720p**, excluding native decoder, AWT viewport buffers,
  and encoded assets). Do not advertise a total memory budget from that math.
  Measure a provisional **256 MiB incremental RSS** ceiling and a sustained
  compositor/video CPU budget on each reference platform before promotion.
- Media construction/READY/error gets a five-second bounded startup attempt;
  oversized/unsupported/late media keeps the poster and explains why. These
  values are proposed product bounds and require the experiments below.

## Assets, persistence, reconnect, and teardown plan

Current `Asset.Type` (`model/Asset.java:59`) has IMAGE/AUDIO/DATA but no native
video type; its MIME switch around line 162 does not classify `video/*` into a
playable type. `messages/src/main/proto/data_transfer_objects.proto:569` likewise
has no VIDEO variant. `Zone.java:401,2265,2328` stores a static `mapAsset` ID;
`PersistenceUtil.java:961` writes entire `asset.getData()` byte arrays into the
campaign archive. `AssetProducer.java:29` already streams file-backed chunks,
but that alone does not remove other byte-array copies in asset loading/saving.

Before production integration, add a versioned optional board-media descriptor
and a file-backed video asset path. Preserve existing static board IDs and older
campaign behavior. Include media ID, poster ID, declared metadata/byte size,
content digest, loop mode, mute, and persisted playback intent; never persist
MediaPlayer/native handles or machine-local absolute paths. Track both movie
and poster during campaign asset enumeration. Keep existing addressing
compatible while verifying the new media manifest's SHA-256/length before use.

Transfer compressed bytes through the existing bounded chunk/cache machinery,
not decoded frames. Validate 32 MiB admission before allocation/download,
download to a temporary file, verify, then atomically promote to cache. Profile
save/reload and concurrent clients so a 32 MiB clip does not silently cause
several full-memory copies. Missing/failed transfers keep the poster; a player
without a decoder can still use the map.

On reconnect, transfer/reuse immutable assets and the authoritative playback
intent: playing/paused, loop, reference shared-clock position and revision.
Clients independently decode and seek to the expected modulo-duration position;
late joins do not restart everyone. Treat a proposed 250 ms resync tolerance as
an experiment, not sample-accurate synchronization. Never gate game state on
video readiness. A local low-motion/disabled setting selects the poster.

On pause keep the selected frame only if the decoder is ready. On map switch,
disconnect, failure, or unload: stop frame publication, cancel pending callbacks
using a generation ID, pause/dispose on FX, detach MediaView/listeners, clear
queued/current frame references and timers, then show poster. Dispose before
loading another map. Repeated load/unload must demonstrate bounded RSS and no
continuing decode CPU; the current failed-player run cannot establish native
resource reclamation.

## Exact next experiments and follow-up gates

1. Run the checked-in fixture unchanged on **Ubuntu 22.04 x86_64 with the
   distribution's libavcodec58/libavformat58**, JDK21, and pinned JavaFX22.
   Confirm an actual compatible native codec is present using `ldd` on the
   shipped `libavplugin-ffmpeg-58.so`; do not alias ABI61 to ABI58. Run first
   with software Prism/Xvfb, then a real desktop GPU. Require READY, at least
   three changing video-frame hashes, no changing source frames after pause,
   a created player's DISPOSED state, and the static fallback after unload.
   If unsupported there, capture native loader/media diagnostics before
   considering a toolchain upgrade or a different decoder.
2. Repeat on supported packaged **Windows**, **macOS Intel**, and **macOS ARM**
   runtime artifacts using the same encoded fixture. The provided runner pins
   Linux only; adapt native artifact classifiers deliberately and record new
   SHA-256s. Record codec/profile success independently from container labels.
3. After the JetBrains dependency access/build blocker is resolved, integrate
   the bounded bridge behind an experimental flag at `renderBoard`, preserving
   the poster and GDX fallback. Test the **actual** ZoneRenderer with player
   vision, individual/GM fog, lights/darkness, background/object/token layers,
   pan/zoom/HiDPI, export/thumbnail capture, and rapid map switching.
4. Benchmark 640×360/15 and proposed 1280×720/24 with decoded FPS, dropped frames,
   CPU, peak/steady RSS, FX/EDT latency, and 20 load/pause/unload cycles. Require
   bounded queues, readable UI input, and stable idle CPU/RSS before selecting
   support limits. Keep native-codec and fallback measurements distinct.
5. Only then implement/version asset descriptors, limits, file-backed transfer,
   campaign compatibility, late-join/play/pause synchronization, local poster
   preference, and failure tests. This is follow-up feature work, not delivery
   implied by this discovery.

MEDIA-4's documented-blocker alternative is exercised: pinned source evidence,
a runnable bounded fixture, measured fallback behavior, the exact native and
main-app blockers, and concrete next experiments are present. Native playback,
actual map-renderer integration, cross-platform performance, and multiplayer
media behavior remain explicitly unverified.
