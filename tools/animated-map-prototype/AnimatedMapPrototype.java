/*
 * This software Copyright by the RPTools.net development team, and
 * licensed under the Affero GPL Version 3 or, at your option, any later
 * version.
 *
 * MapTool Source Code is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public
 * License * along with this source Code.  If not, please visit
 * <https://www.gnu.org/licenses/> and specifically the Affero license
 * text at <https://www.gnu.org/licenses/agpl.html>.
 */

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Bounded rendering experiment, deliberately separate from the actual ZoneRenderer. */
public final class AnimatedMapPrototype {
  private static final int WIDTH = 640;
  private static final int HEIGHT = 360;
  private static final long SECOND = 1_000_000_000L;
  private final AtomicReference<BufferedImage> latest = new AtomicReference<>();
  private final BufferedImage fallback =
      new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
  private final CountDownLatch finished = new CountDownLatch(1);
  private final Set<Long> videoHashes = new HashSet<>();
  private final Set<Long> pausedHashes = new HashSet<>();
  private final Map<String, Object> metrics = new LinkedHashMap<>();
  private final Path clip;
  private MediaPlayer player;
  private MediaView view;
  private JFrame frame;
  private javax.swing.Timer repaintTimer;
  private Board board;
  private long started;
  private long cpuStarted;
  private long snapshots;
  private long paints;
  private long peakRssKiB;
  private long heapPeak;
  private boolean ready;
  private boolean paused;
  private boolean unloaded;
  private boolean disposed;
  private boolean playerCreated;
  private String nativeError = "";

  private AnimatedMapPrototype(Path clip) {
    this.clip = clip;
    var g = fallback.createGraphics();
    g.setColor(new Color(50, 70, 90));
    g.fillRect(0, 0, WIDTH, HEIGHT);
    g.setColor(Color.WHITE);
    g.drawString("Static map fallback", 20, 30);
    g.dispose();
  }

  public static void main(String[] args) throws Exception {
    var prototype = new AnimatedMapPrototype(Path.of(args[0]));
    prototype.started = System.nanoTime();
    prototype.cpuStarted = prototype.cpuNanos();
    SwingUtilities.invokeAndWait(prototype::startSwing);
    Platform.runLater(prototype::startVideo);
    if (!prototype.finished.await(20, TimeUnit.SECONDS))
      throw new IllegalStateException("Fixture timeout");
    SwingUtilities.invokeAndWait(
        () -> {
          prototype.verifyComposition();
          prototype.repaintTimer.stop();
          prototype.metrics.put("repaint_timer_stopped", !prototype.repaintTimer.isRunning());
          prototype.frame.dispose();
        });
    Platform.exit();
    Files.writeString(Path.of(args[1]), prototype.json());
  }

  private void startSwing() {
    // Starts the toolkit; the decoder view remains off the Swing board to avoid overlay leakage.
    new JFXPanel();
    Platform.setImplicitExit(false);
    board = new Board();
    frame = new JFrame("Animated map discovery fixture");
    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    frame.setContentPane(board);
    frame.pack();
    frame.setVisible(true);
    repaintTimer =
        new javax.swing.Timer(
            67,
            event -> {
              board.phase = (System.nanoTime() - started) / (double) SECOND;
              board.repaint();
              sampleMemory();
            });
    repaintTimer.start();
    metrics.put(
        "rendering",
        "JavaFX22 MediaView snapshot -> bounded latest BufferedImage -> Swing Graphics2D, software"
            + " Prism");
    metrics.put("width", WIDTH);
    metrics.put("height", HEIGHT);
    metrics.put("source_fps", 15);
  }

  private void startVideo() {
    try {
      var media = new Media(clip.toUri().toString());
      media.setOnError(() -> nativeFailure(media.getError().toString()));
      player = new MediaPlayer(media);
      playerCreated = true;
      player.setMute(true);
      player.setCycleCount(MediaPlayer.INDEFINITE);
      player.setOnError(() -> nativeFailure(player.getError().toString()));
      view = new MediaView(player);
      view.setFitWidth(WIDTH);
      view.setFitHeight(HEIGHT);
      view.setPreserveRatio(false);
      new Scene(new Group(view), WIDTH, HEIGHT);
      player.setOnReady(
          () -> {
            ready = true;
            player.play();
          });
    } catch (RuntimeException error) {
      nativeFailure(error.toString());
    }
    new AnimationTimer() {
      private long previous;
      private WritableImage writable = new WritableImage(WIDTH, HEIGHT);

      @Override
      public void handle(long now) {
        if (now - previous < SECOND / 15) return;
        previous = now;
        double elapsed = (System.nanoTime() - started) / (double) SECOND;
        if (elapsed >= 6 && !paused) {
          if (player != null && ready) player.pause();
          paused = true;
          metrics.put("paint_count_at_pause", paints);
        }
        if (elapsed >= 8 && !unloaded) {
          unload();
          metrics.put("paint_count_at_unload", paints);
        }
        if (elapsed >= 10) {
          stop();
          metrics.put("elapsed_seconds", elapsed);
          metrics.put(
              "process_cpu_percent_one_core",
              100.0 * (cpuNanos() - cpuStarted) / (System.nanoTime() - started));
          metrics.put("painted_frames", paints);
          metrics.put("swing_paint_fps", paints / elapsed);
          metrics.put("snapshot_frames", snapshots);
          metrics.put("distinct_video_frames", videoHashes.size());
          metrics.put("paused_distinct_source_frames_after_settle", pausedHashes.size());
          metrics.put("decoded_video_verified", ready && videoHashes.size() >= 3);
          metrics.put("media_ready", ready);
          metrics.put("native_player_created", playerCreated);
          metrics.put("native_pause_verified", ready && pausedHashes.size() == 1);
          metrics.put("native_error", nativeError);
          metrics.put("disposed", disposed);
          metrics.put("static_fallback_after_unload", latest.get() == null && unloaded);
          metrics.put("peak_rss_kib", peakRssKiB);
          metrics.put("final_rss_kib", rssKiB());
          metrics.put("sampled_peak_heap_bytes", heapPeak);
          metrics.put(
              "metrics_scope",
              ready && videoHashes.size() >= 3
                  ? "native decoded snapshot fixture"
                  : "fallback/compositor only; native decode unverified");
          finished.countDown();
          return;
        }
        if (ready && !unloaded && nativeError.isEmpty()) {
          try {
            writable = view.snapshot(new SnapshotParameters(), writable);
            // A new immutable published frame; only one pending latest reference is retained.
            BufferedImage copy = SwingFXUtils.fromFXImage(writable, null);
            long hash = hash(copy);
            if (!paused) videoHashes.add(hash);
            else if (elapsed >= 7) pausedHashes.add(hash);
            latest.set(copy);
            snapshots++;
          } catch (RuntimeException error) {
            nativeFailure(error.toString());
          }
        }
      }
    }.start();
  }

  private void nativeFailure(String error) {
    if (nativeError.isEmpty()) nativeError = error;
    latest.set(null);
  }

  private void unload() {
    if (player != null) {
      player.setOnReady(null);
      player.setOnError(null);
      player.pause();
      player.dispose();
      disposed = player.getStatus() == MediaPlayer.Status.DISPOSED;
    }
    if (view != null) view.setMediaPlayer(null);
    player = null;
    view = null;
    latest.set(null);
    unloaded = true;
  }

  private final class Board extends JPanel {
    private double phase;

    Board() {
      setPreferredSize(new Dimension(WIDTH, HEIGHT));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      var g = (Graphics2D) graphics.create();
      var background = latest.get();
      compose(g, background == null ? fallback : background, phase);
      g.dispose();
      paints++;
    }
  }

  private void compose(Graphics2D g, BufferedImage image, double phase) {
    g.setColor(Color.BLACK);
    g.fillRect(0, 0, WIDTH, HEIGHT);
    AffineTransform original = g.getTransform();
    g.translate(12 * Math.sin(phase), 6 * Math.cos(phase));
    g.scale(1.05 + 0.04 * Math.sin(phase), 1.05 + 0.04 * Math.sin(phase));
    g.drawImage(image, 0, 0, null);
    g.setColor(new Color(100, 100, 110));
    g.fillRect(110, 80, 110, 70); // background/object stamp
    g.setColor(new Color(255, 255, 255, 55));
    for (int x = 0; x < WIDTH; x += 32) g.drawLine(x, 0, x, HEIGHT);
    for (int y = 0; y < HEIGHT; y += 32) g.drawLine(0, y, WIDTH, y);
    g.setPaint(
        new RadialGradientPaint(
            300,
            180,
            320,
            new float[] {0, 1},
            new Color[] {new Color(0, 0, 0, 0), new Color(0, 0, 0, 185)}));
    g.fillRect(0, 0, WIDTH, HEIGHT); // illustrative light/darkness, not ZoneView computation
    g.setColor(Color.RED);
    g.fillOval(280, 160, 30, 30); // token above light/darkness
    g.setColor(Color.CYAN);
    g.fillOval(510, 70, 30, 30); // this token must be hidden by the later fog mask
    g.setTransform(original);
    g.setColor(Color.BLACK);
    g.fillRect(460, 0, WIDTH - 460, 150); // opaque fog above map and tokens
  }

  private void verifyComposition() {
    var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    var g = image.createGraphics();
    compose(g, fallback, 0);
    g.dispose();
    if (image.getRGB(530, 90) != Color.BLACK.getRGB())
      throw new IllegalStateException("Fog did not cover token/background");
    if (image.getRGB(310, 185) != Color.RED.getRGB())
      throw new IllegalStateException("Token did not cover background/light");
    metrics.put("composition_pixel_checks_passed", true);
  }

  private static long hash(BufferedImage image) {
    long result = 1;
    for (int y = 0; y < image.getHeight(); y += 17)
      for (int x = 0; x < image.getWidth(); x += 17) result = 31 * result + image.getRGB(x, y);
    return result;
  }

  private long cpuNanos() {
    return ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
        .getProcessCpuTime();
  }

  private long rssKiB() {
    try {
      return Files.readAllLines(Path.of("/proc/self/status")).stream()
          .filter(line -> line.startsWith("VmRSS:"))
          .mapToLong(line -> Long.parseLong(line.split("\\s+")[1]))
          .findFirst()
          .orElse(-1);
    } catch (Exception ignored) {
      return -1;
    }
  }

  private void sampleMemory() {
    peakRssKiB = Math.max(peakRssKiB, rssKiB());
    heapPeak =
        Math.max(heapPeak, ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
  }

  private String json() {
    var out = new StringBuilder("{\n");
    int index = 0;
    for (var entry : metrics.entrySet()) {
      if (index++ > 0) out.append(",\n");
      out.append("  ").append(quote(entry.getKey())).append(": ");
      Object value = entry.getValue();
      out.append(value instanceof String ? quote(value.toString()) : value.toString());
    }
    return out.append("\n}\n").toString();
  }

  private static String quote(String text) {
    return "\""
        + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
        + "\"";
  }
}
