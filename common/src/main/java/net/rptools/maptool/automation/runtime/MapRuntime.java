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

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A host-owned logical clock and bounded action queue for one map encounter.
 *
 * <p>It never creates wall-clock timers or performs a client-side election. The caller establishes
 * the authenticated authority and supplies accepted events. Check, NPC, and movement adapters are
 * registered locally and are deliberately excluded from saved state.
 */
public final class MapRuntime {
  public enum State {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    INTERRUPTED
  }

  public record Event(
      String runId,
      String id,
      String mapId,
      String actorId,
      String type,
      long tick,
      Map<String, String> arguments) {
    public Event {
      requireText(runId, "runId");
      requireText(id, "event id");
      requireText(mapId, "mapId");
      requireText(type, "event type");
      actorId = Objects.requireNonNull(actorId, "actorId");
      if (tick < 0) throw new IllegalArgumentException("Negative event tick");
      arguments = Map.copyOf(arguments);
    }
  }

  public record Result(String description, Map<String, String> values) {
    public Result {
      description = Objects.requireNonNull(description);
      values = Map.copyOf(values);
    }

    public static Result of(String description) {
      return new Result(description, Map.of());
    }
  }

  public record Receipt(
      String eventId,
      State state,
      long completedTick,
      String description,
      Map<String, String> values) {
    public Receipt {
      requireText(eventId, "receipt event id");
      Objects.requireNonNull(state);
      if (completedTick < 0) throw new IllegalArgumentException("Negative receipt tick");
      description = Objects.requireNonNull(description);
      values = Map.copyOf(values);
    }
  }

  public record Pending(long sequence, Event event) {
    public Pending {
      if (sequence < 0) throw new IllegalArgumentException("Negative sequence");
      Objects.requireNonNull(event);
    }
  }

  public record Snapshot(
      int version,
      String runId,
      String mapId,
      String authorityId,
      long tick,
      long nextSequence,
      boolean paused,
      int queueLimit,
      int workLimit,
      List<Pending> pending,
      List<Receipt> receipts) {
    public Snapshot {
      pending = List.copyOf(pending);
      receipts = List.copyOf(receipts);
    }
  }

  @FunctionalInterface
  public interface Handler {
    Result execute(Event event) throws Exception;
  }

  private static final Comparator<Pending> ORDER =
      Comparator.comparingLong((Pending p) -> p.event().tick())
          .thenComparingLong(Pending::sequence);
  private static final Gson GSON = new Gson();
  private final String runId;
  private final String mapId;
  private final String authorityId;
  private final int queueLimit;
  private final int workLimit;
  private final PriorityQueue<Pending> pending = new PriorityQueue<>(ORDER);
  private final Map<String, Receipt> receipts = new LinkedHashMap<>();
  private final Map<String, Handler> handlers = new HashMap<>();
  private Consumer<Snapshot> persistence = ignored -> {};
  private BooleanSupplier executionGuard = () -> true;
  private long tick;
  private long nextSequence;
  private boolean paused = true;
  private boolean executing;

  public MapRuntime(String runId, String mapId, String authorityId) {
    this(runId, mapId, authorityId, 1000, 100);
  }

  public MapRuntime(String runId, String mapId, String authorityId, int queueLimit, int workLimit) {
    requireText(runId, "runId");
    requireText(mapId, "mapId");
    requireText(authorityId, "authorityId");
    if (queueLimit <= 0 || workLimit <= 0)
      throw new IllegalArgumentException("Limits must be positive");
    this.runId = runId;
    this.mapId = mapId;
    this.authorityId = authorityId;
    this.queueLimit = queueLimit;
    this.workLimit = workLimit;
  }

  public synchronized void register(String caller, String type, Handler handler) {
    authorize(caller);
    requireText(type, "handler type");
    handlers.put(type, Objects.requireNonNull(handler));
  }

  /** A synchronous durability hook: a running receipt is saved before an effect is attempted. */
  public synchronized void persistWith(String caller, Consumer<Snapshot> persistence) {
    authorize(caller);
    this.persistence = Objects.requireNonNull(persistence);
  }

  public synchronized long tick() {
    return tick;
  }

  public synchronized boolean hasEvent(String eventId) {
    return receipts.containsKey(eventId);
  }

  /** Rebound by the authenticated host adapter after restore; it is never serialized. */
  public synchronized void guardExecutionWith(String caller, BooleanSupplier guard) {
    authorize(caller);
    executionGuard = Objects.requireNonNull(guard);
  }

  /** Returns false for an already accepted occurrence, including failed or interrupted work. */
  public synchronized boolean enqueue(String caller, Event event) {
    return !enqueueAll(caller, List.of(event)).isEmpty();
  }

  /** Capacity and identity checks happen before any part of a batch is accepted. */
  public synchronized List<String> enqueueAll(String caller, List<Event> events) {
    authorize(caller);
    var accepted = new LinkedHashMap<String, Event>();
    for (Event event : List.copyOf(events)) {
      validate(event);
      if (receipts.containsKey(event.id())) continue;
      Event previous = accepted.putIfAbsent(event.id(), event);
      if (previous != null && !previous.equals(event))
        throw new IllegalArgumentException("Conflicting batch occurrence identity");
    }
    if (accepted.isEmpty()) return List.of();
    if (accepted.size() > queueLimit - pending.size())
      throw new IllegalStateException("Map action queue is full");
    if (accepted.size() > Long.MAX_VALUE - nextSequence)
      throw new IllegalStateException("Sequence exhausted");
    for (Event event : accepted.values()) {
      pending.add(new Pending(nextSequence++, event));
      receipts.put(event.id(), new Receipt(event.id(), State.QUEUED, tick, "Queued", Map.of()));
    }
    persistence.accept(snapshot());
    return List.copyOf(accepted.keySet());
  }

  public synchronized void pause(String caller) {
    authorize(caller);
    paused = true;
    persistence.accept(snapshot());
  }

  public synchronized void resume(String caller) {
    authorize(caller);
    paused = false;
    persistence.accept(snapshot());
  }

  public synchronized boolean cancel(String caller, String eventId) {
    authorize(caller);
    boolean removed = pending.removeIf(p -> p.event().id().equals(eventId));
    if (removed) {
      receipts.put(
          eventId, new Receipt(eventId, State.CANCELLED, tick, "Cancelled by GM", Map.of()));
      persistence.accept(snapshot());
    }
    return removed;
  }

  /** Explicit single stepping is available while paused; it advances to the next scheduled tick. */
  public synchronized Receipt step(String caller) {
    authorize(caller);
    if (executing)
      throw new IllegalStateException("An action cannot recursively advance the clock");
    if (pending.isEmpty()) return null;
    return executeNext();
  }

  /**
   * Work beyond the per-call limit remains queued. The clock can stop short of targetTick when
   * paused or work-limited; the caller may request the same horizon again. Due events observe their
   * scheduled logical time rather than all seeing the end of a catch-up interval.
   */
  public synchronized List<Receipt> advanceTo(String caller, long targetTick) {
    authorize(caller);
    if (executing)
      throw new IllegalStateException("An action cannot recursively advance the clock");
    if (targetTick < tick) throw new IllegalArgumentException("Logical time cannot run backwards");
    if (paused) return List.of();
    var completed = new ArrayList<Receipt>();
    while (!paused
        && !pending.isEmpty()
        && pending.peek().event().tick() <= targetTick
        && completed.size() < workLimit) {
      completed.add(executeNext());
    }
    requireExecutionPermit();
    if (!paused && (pending.isEmpty() || pending.peek().event().tick() > targetTick)) {
      tick = targetTick;
    }
    persistence.accept(snapshot());
    return List.copyOf(completed);
  }

  public synchronized Snapshot snapshot() {
    return new Snapshot(
        1,
        runId,
        mapId,
        authorityId,
        tick,
        nextSequence,
        paused,
        queueLimit,
        workLimit,
        pending.stream().sorted(ORDER).toList(),
        List.copyOf(receipts.values()));
  }

  public synchronized String toJson() {
    return GSON.toJson(snapshot());
  }

  public static MapRuntime fromJson(String json, String expectedMapId, String authorityId) {
    Snapshot snapshot = GSON.fromJson(Objects.requireNonNull(json), Snapshot.class);
    return restore(snapshot, expectedMapId, authorityId);
  }

  /** Restore is always paused. Ambiguous in-flight effects require an explicit GM decision. */
  public static MapRuntime restore(Snapshot saved, String expectedMapId, String authorityId) {
    Objects.requireNonNull(saved, "snapshot");
    if (saved.version() != 1)
      throw new IllegalArgumentException("Unsupported runtime snapshot version");
    if (!saved.mapId().equals(expectedMapId))
      throw new IllegalArgumentException("Snapshot map mismatch");
    if (!saved.authorityId().equals(authorityId))
      throw new SecurityException("Snapshot authority mismatch");
    if (saved.tick() < 0 || saved.nextSequence() < 0)
      throw new IllegalArgumentException("Invalid saved clock");
    var runtime =
        new MapRuntime(
            saved.runId(), saved.mapId(), authorityId, saved.queueLimit(), saved.workLimit());
    runtime.tick = saved.tick();
    runtime.nextSequence = saved.nextSequence();
    for (Receipt receipt : saved.receipts()) {
      if (runtime.receipts.putIfAbsent(receipt.eventId(), receipt) != null) {
        throw new IllegalArgumentException("Duplicate saved receipt");
      }
      if (receipt.state() == State.RUNNING) {
        runtime.receipts.put(
            receipt.eventId(),
            new Receipt(
                receipt.eventId(),
                State.INTERRUPTED,
                saved.tick(),
                "Interrupted effect; GM resolution required",
                receipt.values()));
      }
    }
    var queuedIds = new HashSet<String>();
    var sequences = new HashSet<Long>();
    for (Pending work : saved.pending()) {
      runtime.validate(work.event());
      Receipt receipt = runtime.receipts.get(work.event().id());
      if (!queuedIds.add(work.event().id())
          || !sequences.add(work.sequence())
          || work.sequence() >= saved.nextSequence()
          || receipt == null
          || receipt.state() != State.QUEUED) {
        throw new IllegalArgumentException("Inconsistent saved work queue");
      }
      runtime.pending.add(work);
    }
    if (runtime.pending.size() > runtime.queueLimit
        || runtime.receipts.values().stream()
            .anyMatch(r -> r.state() == State.QUEUED && !queuedIds.contains(r.eventId()))) {
      throw new IllegalArgumentException("Incomplete or oversized saved work queue");
    }
    return runtime;
  }

  private Receipt executeNext() {
    requireExecutionPermit();
    var work = pending.remove();
    var event = work.event();
    tick = Math.max(tick, event.tick());
    var reserved = new Receipt(event.id(), State.RUNNING, tick, "Running", Map.of());
    receipts.put(event.id(), reserved);
    try {
      persistence.accept(snapshot());
    } catch (RuntimeException failure) {
      receipts.put(
          event.id(),
          new Receipt(
              event.id(),
              State.INTERRUPTED,
              tick,
              "Could not persist execution reservation",
              Map.of()));
      paused = true;
      return receipts.get(event.id());
    }
    executing = true;
    Receipt receipt;
    try {
      Handler handler = handlers.get(event.type());
      if (handler == null) throw new IllegalStateException("No handler for " + event.type());
      Result result = Objects.requireNonNull(handler.execute(event), "Handler returned no result");
      receipt =
          new Receipt(event.id(), State.COMPLETED, tick, result.description(), result.values());
    } catch (Exception failure) {
      receipt =
          new Receipt(
              event.id(),
              State.FAILED,
              tick,
              failure.getMessage() == null
                  ? failure.getClass().getSimpleName()
                  : failure.getMessage(),
              Map.of());
    } finally {
      executing = false;
    }
    receipts.put(event.id(), receipt);
    try {
      persistence.accept(snapshot());
    } catch (RuntimeException failure) {
      // The effect may have committed. Never retry it automatically after a failed durability
      // write.
      receipts.put(
          event.id(),
          new Receipt(
              event.id(),
              State.INTERRUPTED,
              tick,
              "Effect finished but its result could not be persisted; GM resolution required",
              receipt.values()));
      paused = true;
    }
    return receipts.get(event.id());
  }

  private void validate(Event event) {
    Objects.requireNonNull(event);
    if (!runId.equals(event.runId()) || !mapId.equals(event.mapId())) {
      throw new IllegalArgumentException("Event belongs to a different map or encounter run");
    }
  }

  private void authorize(String caller) {
    if (!authorityId.equals(caller))
      throw new SecurityException("Only the map authority may change the runtime");
  }

  private void requireExecutionPermit() {
    if (!executionGuard.getAsBoolean()) {
      paused = true;
      persistence.accept(snapshot());
      throw new SecurityException("Map authority is unavailable; queued work has been preserved");
    }
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
  }
}
