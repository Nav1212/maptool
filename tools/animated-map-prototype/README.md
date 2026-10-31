# Animated background discovery fixture

This isolated experiment uses the repository's JavaFX **22** pin, a generated
640×360/15 fps silent H.264 Baseline MP4, and a Swing board. It does not modify
`ZoneRenderer` or claim production video-map support.

```sh
source /workspace/.maptool-cloud/activate.sh  # this cloud machine's JDK/Xvfb activation
python3 tools/animated-map-prototype/run.py \
  --cache /workspace/.maptool-cloud/animated-prototype \
  --output tools/animated-map-prototype/results/debian13-javafx22.json
```

On another Linux x86_64 machine supply JDK 21, Python 3, ffmpeg with libx264, and
an X display or `xvfb-run`. The activation script is specific to this cloud
machine and is not needed when those tools are already on PATH. The runner uses
only pinned Maven Central JavaFX native jars and checks their SHA-256 on every
run. Digests were recorded after TLS-verified downloads checked against Maven's
published SHA-1 metadata; no checksum/TLS checks were disabled. Generated media,
classes, downloaded jars, and JavaFX/user caches stay in `--cache`.

The Java experiment runs for ten seconds. It requests 15 snapshots/second on
the FX thread, publishes only a bounded latest `BufferedImage`, and paints that
image below illustrative stamps/grid, darkness, tokens, and opaque fog. Pan and
zoom run on the Swing compositor. Pixel assertions check that the visible token
is above the background/darkness and that opaque fog hides another token.
These are representative layers, **not the actual ZoneView light/FoW algorithm**.

At six seconds it requests pause; after a settling interval it counts distinct
source snapshots. At eight seconds it disposes a player if one was created,
detaches the view, and clears the latest frame to show the static fallback.
At ten seconds it stops both timers and exits. The JSON records painted versus
snapshot frames, changing video-frame hashes, process CPU time, RSS and sampled
heap, and separate readiness/pause/disposal flags. CPU percent is normalized to
one logical core; RSS is Linux `/proc/self/status`, sampled rather than an
allocator-accurate native-memory attribution.

A successful process exit means compositor assertions and teardown completed,
not that native video decoded. Check `decoded_video_verified`,
`native_pause_verified`, `native_player_created`, and `native_error` separately.
On this Debian 13 host JavaFX22 could not create the native H.264 player; the
saved result therefore labels all FPS/CPU/memory measurements as **fallback/
compositor only**. It does not establish decoded playback performance or native
pause/dispose behavior. See [the ADR](../../docs/adr/animated-maps.md) for the
native-library evidence and next experiment.
