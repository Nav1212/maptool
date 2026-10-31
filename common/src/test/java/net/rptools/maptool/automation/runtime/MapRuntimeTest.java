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
package net.rptools.maptool.automation.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MapRuntimeTest {
  private MapRuntime.Event event(String id, long tick) {
    return new MapRuntime.Event("run", id, "map", "actor", "move", tick, Map.of());
  }

  private MapRuntime runtime() {
    var runtime = new MapRuntime("run", "map", "gm");
    runtime.register("gm", "move", event -> MapRuntime.Result.of(event.id()));
    return runtime;
  }

  @Test
  void advancesInTickAndAcceptanceOrderAndLeavesFutureWorkQueued() {
    var runtime = runtime();
    runtime.enqueue("gm", event("later", 10));
    runtime.enqueue("gm", event("first", 2));
    runtime.enqueue("gm", event("second", 2));
    runtime.resume("gm");
    assertEquals(
        List.of("first", "second"),
        runtime.advanceTo("gm", 2).stream().map(MapRuntime.Receipt::eventId).toList());
    assertEquals("later", runtime.snapshot().pending().getFirst().event().id());
    assertEquals(2, runtime.snapshot().tick());
  }

  @Test
  void duplicateOccurrenceDoesNotExecuteAgainEvenAfterReload() {
    var calls = new AtomicInteger();
    var runtime = runtime();
    runtime.register(
        "gm", "move", event -> MapRuntime.Result.of("calls " + calls.incrementAndGet()));
    assertTrue(runtime.enqueue("gm", event("same", 0)));
    assertFalse(runtime.enqueue("gm", event("same", 0)));
    runtime.step("gm");
    var restored = MapRuntime.fromJson(runtime.toJson(), "map", "gm");
    restored.register(
        "gm",
        "move",
        event -> {
          calls.incrementAndGet();
          return MapRuntime.Result.of("unexpected");
        });
    assertFalse(restored.enqueue("gm", event("same", 0)));
    assertNull(restored.step("gm"));
    assertEquals(1, calls.get());
  }

  @Test
  void pauseAndStepDoNotRunUnrequestedActions() {
    var runtime = runtime();
    runtime.enqueue("gm", event("one", 5));
    runtime.enqueue("gm", event("two", 5));
    assertTrue(runtime.advanceTo("gm", 100).isEmpty());
    assertEquals(0, runtime.snapshot().tick());
    assertEquals("one", runtime.step("gm").eventId());
    assertEquals(5, runtime.snapshot().tick());
    assertTrue(runtime.snapshot().paused());
    assertEquals(1, runtime.snapshot().pending().size());
  }

  @Test
  void rejectsWrongAuthorityMapAndRunWithoutChangingState() {
    var runtime = runtime();
    assertThrows(SecurityException.class, () -> runtime.enqueue("player", event("one", 0)));
    assertThrows(SecurityException.class, () -> runtime.resume("player"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtime.enqueue(
                "gm", new MapRuntime.Event("other", "x", "map", "actor", "move", 0, Map.of())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            runtime.enqueue(
                "gm", new MapRuntime.Event("run", "x", "other", "actor", "move", 0, Map.of())));
    assertTrue(runtime.snapshot().pending().isEmpty());
  }

  @Test
  void boundsWorkAndQueueCapacityWithoutDroppingAcceptedEvents() {
    var runtime = new MapRuntime("run", "map", "gm", 3, 2);
    runtime.register("gm", "move", event -> MapRuntime.Result.of("ok"));
    for (int i = 0; i < 3; i++) runtime.enqueue("gm", event("e" + i, 0));
    assertThrows(IllegalStateException.class, () -> runtime.enqueue("gm", event("overflow", 0)));
    runtime.resume("gm");
    assertEquals(2, runtime.advanceTo("gm", 0).size());
    assertEquals(1, runtime.advanceTo("gm", 0).size());
    assertEquals(3, runtime.snapshot().receipts().size());
  }

  @Test
  void aFailureDoesNotAutomaticallyRepeatItsEffectOrBlockUnrelatedWork() {
    var runtime = runtime();
    var calls = new AtomicInteger();
    runtime.register(
        "gm",
        "fail",
        event -> {
          calls.incrementAndGet();
          throw new IllegalStateException("adapter failed");
        });
    var bad = new MapRuntime.Event("run", "bad", "map", "actor", "fail", 0, Map.of());
    runtime.enqueue("gm", bad);
    runtime.enqueue("gm", event("good", 0));
    runtime.resume("gm");
    var results = runtime.advanceTo("gm", 0);
    assertEquals(
        List.of(MapRuntime.State.FAILED, MapRuntime.State.COMPLETED),
        results.stream().map(MapRuntime.Receipt::state).toList());
    assertFalse(runtime.enqueue("gm", bad));
    assertEquals(1, calls.get());
  }

  @Test
  void restoringInFlightReservationRequiresGmResolutionWithoutAutomaticReplay() {
    var runtime = runtime();
    var durable = new ArrayList<MapRuntime.Snapshot>();
    runtime.persistWith("gm", durable::add);
    runtime.enqueue("gm", event("one", 1));
    runtime.step("gm");
    var reservation =
        durable.stream()
            .filter(s -> s.receipts().stream().anyMatch(r -> r.state() == MapRuntime.State.RUNNING))
            .findFirst()
            .orElseThrow();
    var restored = MapRuntime.restore(reservation, "map", "gm");
    assertEquals(MapRuntime.State.INTERRUPTED, restored.snapshot().receipts().getFirst().state());
    assertTrue(restored.snapshot().paused());
    assertFalse(restored.enqueue("gm", event("one", 1)));
  }

  @Test
  void durabilityFailureBeforeEffectPreventsExecution() {
    var runtime = runtime();
    var calls = new AtomicInteger();
    runtime.register(
        "gm",
        "move",
        event -> {
          calls.incrementAndGet();
          return MapRuntime.Result.of("ok");
        });
    runtime.enqueue("gm", event("one", 0));
    runtime.persistWith(
        "gm",
        snapshot -> {
          throw new IllegalStateException("disk full");
        });
    assertEquals(MapRuntime.State.INTERRUPTED, runtime.step("gm").state());
    assertEquals(0, calls.get());
    assertTrue(runtime.snapshot().paused());
  }

  @Test
  void restoresPendingWorkPausedAndRequiresLocalHandlerRegistration() {
    var runtime = runtime();
    runtime.enqueue("gm", event("one", 10));
    runtime.resume("gm");
    var restored = MapRuntime.fromJson(runtime.toJson(), "map", "gm");
    assertTrue(restored.snapshot().paused());
    assertEquals(MapRuntime.State.FAILED, restored.step("gm").state());
    assertThrows(
        SecurityException.class,
        () -> MapRuntime.fromJson(runtime.toJson(), "map", "different-gm"));
    assertThrows(
        IllegalArgumentException.class,
        () -> MapRuntime.fromJson(runtime.toJson(), "different-map", "gm"));
  }

  @Test
  void cancellationAndBackwardTimeAreExplicit() {
    var runtime = runtime();
    runtime.enqueue("gm", event("one", 0));
    assertTrue(runtime.cancel("gm", "one"));
    assertFalse(runtime.cancel("gm", "one"));
    assertEquals(MapRuntime.State.CANCELLED, runtime.snapshot().receipts().getFirst().state());
    runtime.resume("gm");
    runtime.advanceTo("gm", 10);
    assertThrows(IllegalArgumentException.class, () -> runtime.advanceTo("gm", 9));
  }

  @Test
  void recursiveClockAdvanceIsRejectedButFollowUpWorkCanBeQueued() {
    var runtime = runtime();
    runtime.register(
        "gm",
        "move",
        event -> {
          assertThrows(IllegalStateException.class, () -> runtime.advanceTo("gm", 100));
          runtime.enqueue(
              "gm", new MapRuntime.Event("run", "followup", "map", "actor", "later", 1, Map.of()));
          return MapRuntime.Result.of("ok");
        });
    runtime.enqueue("gm", event("first", 0));
    runtime.step("gm");
    assertEquals("followup", runtime.snapshot().pending().getFirst().event().id());
    assertEquals(0, runtime.snapshot().tick());
  }

  @Test
  void catchUpEventsObserveScheduledTimeAndWorkLimitsDoNotSkipTheRemainingHorizon() {
    var runtime = new MapRuntime("run", "map", "gm", 10, 1);
    var seen = new ArrayList<Long>();
    runtime.register(
        "gm",
        "move",
        event -> {
          seen.add(runtime.tick());
          return MapRuntime.Result.of("ok");
        });
    runtime.enqueue("gm", event("one", 2));
    runtime.enqueue("gm", event("two", 5));
    runtime.resume("gm");
    runtime.advanceTo("gm", 10);
    assertEquals(2, runtime.tick());
    runtime.advanceTo("gm", 10);
    assertEquals(List.of(2L, 5L), seen);
    assertEquals(10, runtime.tick());
  }

  @Test
  void batchEnqueueChecksCapacityBeforeAcceptingAnyMember() {
    var runtime = new MapRuntime("run", "map", "gm", 2, 2);
    runtime.enqueue("gm", event("existing", 10));
    assertThrows(
        IllegalStateException.class,
        () -> runtime.enqueueAll("gm", List.of(event("a", 0), event("b", 0))));
    assertFalse(runtime.hasEvent("a"));
    assertFalse(runtime.hasEvent("b"));
    assertEquals(1, runtime.snapshot().pending().size());
    runtime.cancel("gm", "existing");
    assertEquals(
        List.of("a", "b"), runtime.enqueueAll("gm", List.of(event("a", 0), event("b", 0))));
  }

  @Test
  void lostExecutionAuthorityPausesWithoutDequeuingRemainingWork() {
    var runtime = runtime();
    var permitted = new java.util.concurrent.atomic.AtomicBoolean(true);
    runtime.guardExecutionWith("gm", permitted::get);
    runtime.register(
        "gm",
        "move",
        event -> {
          permitted.set(false);
          return MapRuntime.Result.of("first accepted");
        });
    runtime.enqueue("gm", event("one", 1));
    runtime.enqueue("gm", event("two", 2));
    runtime.resume("gm");
    assertThrows(SecurityException.class, () -> runtime.advanceTo("gm", 10));
    assertTrue(runtime.snapshot().paused());
    assertEquals("two", runtime.snapshot().pending().getFirst().event().id());
    assertEquals(MapRuntime.State.QUEUED, runtime.snapshot().receipts().getLast().state());
    assertEquals(1, runtime.tick());
  }

  @Test
  void rejectsMalformedAndInconsistentSnapshots() {
    var runtime = runtime();
    runtime.enqueue("gm", event("one", 0));
    String json = runtime.toJson();
    assertThrows(
        IllegalArgumentException.class,
        () -> MapRuntime.fromJson(json.replace("\"version\":1", "\"version\":2"), "map", "gm"));
    var saved = runtime.snapshot();
    var invalid =
        new MapRuntime.Snapshot(
            1, "run", "map", "gm", 0, 1, true, 100, 10, List.of(), saved.receipts());
    assertThrows(IllegalArgumentException.class, () -> MapRuntime.restore(invalid, "map", "gm"));
  }
}
