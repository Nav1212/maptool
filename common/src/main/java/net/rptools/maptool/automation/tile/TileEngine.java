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
package net.rptools.maptool.automation.tile;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.rptools.maptool.automation.tile.TileDefinition.Action;
import net.rptools.maptool.automation.tile.TileDefinition.Check;
import net.rptools.maptool.automation.tile.TileDefinition.Repeat;
import net.rptools.maptool.automation.tile.TileDefinition.Scope;
import net.rptools.maptool.automation.tile.TileDefinition.Trigger;
import net.rptools.maptool.automation.tile.TileRegion.Point;

/**
 * Authoritative tile consumer. The caller supplies committed events and clock/turn identities. This
 * class owns no scheduler, network transport, random generator, or game-specific dice rules.
 * Handlers execute synchronously and receive stable idempotency keys.
 */
public final class TileEngine {
  private static final int SNAPSHOT_VERSION = 1;
  private final String runId;
  private final Map<String, TileRegion> regions = new LinkedHashMap<>();
  private final Map<String, TileDefinition> tiles = new LinkedHashMap<>();
  private final Map<String, RollAdapter> rollAdapters = new LinkedHashMap<>();
  private final Map<String, ActionHandler> actionHandlers = new LinkedHashMap<>();
  private final Map<String, ActivationReceipt> receipts = new LinkedHashMap<>();
  private final Map<String, PolicyState> policyStates = new LinkedHashMap<>();
  private final Map<String, RegionState> regionStates = new LinkedHashMap<>();
  private final Map<String, List<String>> eventReceipts = new LinkedHashMap<>();

  public TileEngine(String runId) {
    requireId(runId, "run ID");
    this.runId = runId;
  }

  @FunctionalInterface
  public interface RollAdapter {
    CheckResult roll(CheckRequest request) throws Exception;
  }

  @FunctionalInterface
  public interface ActionHandler {
    void execute(ActionRequest request) throws Exception;
  }

  public enum Status {
    RUNNING,
    COMPLETED,
    SKIPPED,
    FAILED,
    INTERRUPTED
  }

  public record Context(
      String runId,
      String occurrenceId,
      String mapId,
      String tileId,
      String actorId,
      Trigger trigger,
      long tick,
      String turnId,
      Map<String, String> actorProperties,
      String activationKey) {
    public Context {
      actorProperties = actorProperties == null ? Map.of() : Map.copyOf(actorProperties);
    }
  }

  public record CheckRequest(Context context, Check check) {}

  public record CheckResult(
      String outcome,
      String display,
      Double total,
      List<Double> dice,
      Map<String, String> details) {
    public CheckResult {
      dice = dice == null ? List.of() : List.copyOf(dice);
      details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static CheckResult outcome(String outcome) {
      return new CheckResult(outcome, outcome, null, List.of(), Map.of());
    }
  }

  public record ActionRequest(String stepKey, Context context, Action action, CheckResult result) {}

  public record StepReceipt(String key, String handlerId, Status status, String error) {}

  public record ActivationReceipt(
      String key,
      Context context,
      Status status,
      CheckResult result,
      List<StepReceipt> steps,
      String reason) {
    public ActivationReceipt {
      steps = List.copyOf(steps);
    }
  }

  public record PolicyState(
      boolean consumed, long lastEntry, String lastTurn, long eligibleTick, String pendingKey) {}

  public record RegionState(long entry, boolean inside) {}

  public record Snapshot(
      int version,
      String runId,
      List<TileRegion> regions,
      List<TileDefinition> tiles,
      Map<String, ActivationReceipt> receipts,
      Map<String, PolicyState> policyStates,
      Map<String, RegionState> regionStates,
      Map<String, List<String>> eventReceipts) {}

  public synchronized void registerRollAdapter(String id, RollAdapter adapter) {
    requireId(id, "adapter ID");
    rollAdapters.put(id, Objects.requireNonNull(adapter));
  }

  public synchronized void registerActionHandler(String id, ActionHandler handler) {
    requireId(id, "handler ID");
    actionHandlers.put(id, Objects.requireNonNull(handler));
  }

  /** Invalid definitions may be saved for editing, but cannot execute. */
  public synchronized void putRegion(TileRegion region) {
    requireId(region.id(), "region ID");
    for (var point : region.vertices()) {
      if (point != null && (!Double.isFinite(point.x()) || !Double.isFinite(point.y()))) {
        throw new IllegalArgumentException(
            "Region coordinates must be finite for JSON persistence");
      }
    }
    regions.put(region.id(), region);
  }

  public synchronized void putTile(TileDefinition tile) {
    requireId(tile.id(), "tile ID");
    tiles.put(tile.id(), tile);
  }

  public synchronized void removeTile(String id) {
    tiles.remove(id);
  }

  public synchronized void removeRegion(String id) {
    regions.remove(id);
  }

  public synchronized List<TileRegion> regions() {
    return List.copyOf(regions.values());
  }

  public synchronized List<TileDefinition> definitions() {
    return List.copyOf(tiles.values());
  }

  public synchronized Map<String, ActivationReceipt> receipts() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(receipts));
  }

  public synchronized Map<String, PolicyState> policyStates() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(policyStates));
  }

  public String runId() {
    return runId;
  }

  public synchronized RegionState regionState(String regionId, String actorId) {
    return regionStates.getOrDefault(key(regionId, actorId), new RegionState(0, false));
  }

  public synchronized List<String> validationErrors(String tileId) {
    var tile = tiles.get(tileId);
    if (tile == null) return List.of("Unknown tile: " + tileId);
    var errors = new ArrayList<>(tile.validationErrors());
    if (!TileDefinition.blank(tile.regionId())) {
      var region = regions.get(tile.regionId());
      if (region == null) errors.add("Missing region: " + tile.regionId());
      else {
        errors.addAll(region.validationErrors());
        if (!Objects.equals(region.mapId(), tile.mapId())) errors.add("Region is on another map");
      }
    }
    if (tile.check() != null && !rollAdapters.containsKey(tile.check().adapterId())) {
      errors.add("Unavailable roll adapter: " + tile.check().adapterId());
    }
    var actions = new ArrayList<>(tile.alwaysActions());
    tile.outcomes().values().forEach(actions::addAll);
    for (var action : actions) {
      if (!actionHandlers.containsKey(action.handlerId())) {
        errors.add("Unavailable action handler: " + action.handlerId());
      }
    }
    return List.copyOf(errors);
  }

  /**
   * Consume a committed straight movement segment. CROSS means positive interior traversal;
   * ENTER/EXIT also detect outside-to-outside and concave pass-through paths. A binding executes at
   * most once for one occurrence/actor, even if it subscribes to several matched triggers.
   */
  public synchronized List<ActivationReceipt> onMove(
      String occurrenceId,
      String mapId,
      String actorId,
      Point from,
      Point to,
      long tick,
      String turnId,
      Map<String, String> actorProperties) {
    validateEvent(occurrenceId, mapId, actorId, tick);
    validatePoint(from);
    validatePoint(to);
    if (!Double.isFinite(to.x() - from.x()) || !Double.isFinite(to.y() - from.y())) {
      throw new IllegalArgumentException("Movement segment length must be finite");
    }
    String eventKey = key("move", occurrenceId, mapId, actorId);
    if (eventReceipts.containsKey(eventKey)) return eventResults(eventKey);
    actorProperties = copyProperties(actorProperties);
    eventReceipts.put(eventKey, new ArrayList<>());
    var traversals = new LinkedHashMap<String, TileRegion.Traversal>();
    for (var region : regions.values()) {
      if (Objects.equals(region.mapId(), mapId) && region.validationErrors().isEmpty()) {
        var traversal = region.trace(from, to);
        traversals.put(region.id(), traversal);
        updateOccupancy(region.id(), actorId, traversal.entered(), traversal.endsInside());
      }
    }
    for (var tile : List.copyOf(tiles.values())) {
      if (!Objects.equals(mapId, tile.mapId())) continue;
      var traversal = traversals.get(tile.regionId());
      Trigger trigger = null;
      if (traversal != null) {
        if (traversal.entered() && tile.triggers().contains(Trigger.ENTER)) trigger = Trigger.ENTER;
        else if (traversal.exited() && tile.triggers().contains(Trigger.EXIT))
          trigger = Trigger.EXIT;
        else if (traversal.crossed() && tile.triggers().contains(Trigger.CROSS))
          trigger = Trigger.CROSS;
      } else if (tile.triggers().stream()
          .anyMatch(t -> t != Trigger.INTERACT && t != Trigger.PULSE)) {
        // Invalid/missing geometry is visible as a failed activation rather than silently ignored.
        trigger = Trigger.ENTER;
      }
      if (trigger != null) {
        activate(
            eventKey, tile, occurrenceId, mapId, actorId, trigger, tick, turnId, actorProperties);
      }
    }
    return eventResults(eventKey);
  }

  public synchronized List<ActivationReceipt> interact(
      String occurrenceId,
      String mapId,
      String tileId,
      String actorId,
      long tick,
      String turnId,
      Map<String, String> actorProperties) {
    validateEvent(occurrenceId, mapId, actorId, tick);
    String eventKey = key("interact", occurrenceId, mapId, actorId, tileId);
    if (eventReceipts.containsKey(eventKey)) return eventResults(eventKey);
    var tile = tiles.get(tileId);
    if (tile == null) throw new IllegalArgumentException("Unknown tile: " + tileId);
    if (!Objects.equals(mapId, tile.mapId()))
      throw new IllegalArgumentException("Tile is on another map");
    actorProperties = copyProperties(actorProperties);
    eventReceipts.put(eventKey, new ArrayList<>());
    if (tile.triggers().contains(Trigger.INTERACT)) {
      activate(
          eventKey,
          tile,
          occurrenceId,
          mapId,
          actorId,
          Trigger.INTERACT,
          tick,
          turnId,
          actorProperties);
    }
    return eventResults(eventKey);
  }

  /** Evaluate PULSE bindings containing the actor's current position; the caller owns timing. */
  public synchronized List<ActivationReceipt> pulse(
      String occurrenceId,
      String mapId,
      String actorId,
      Point position,
      long tick,
      String turnId,
      Map<String, String> actorProperties) {
    validateEvent(occurrenceId, mapId, actorId, tick);
    validatePoint(position);
    String eventKey = key("pulse", occurrenceId, mapId, actorId);
    if (eventReceipts.containsKey(eventKey)) return eventResults(eventKey);
    actorProperties = copyProperties(actorProperties);
    eventReceipts.put(eventKey, new ArrayList<>());
    for (var tile : List.copyOf(tiles.values())) {
      if (!Objects.equals(mapId, tile.mapId()) || !tile.triggers().contains(Trigger.PULSE))
        continue;
      var region = regions.get(tile.regionId());
      if (region == null || !region.validationErrors().isEmpty()) {
        activate(
            eventKey,
            tile,
            occurrenceId,
            mapId,
            actorId,
            Trigger.PULSE,
            tick,
            turnId,
            actorProperties);
      } else if (region.contains(position)) {
        updateOccupancy(region.id(), actorId, false, true);
        activate(
            eventKey,
            tile,
            occurrenceId,
            mapId,
            actorId,
            Trigger.PULSE,
            tick,
            turnId,
            actorProperties);
      }
    }
    return eventResults(eventKey);
  }

  /** Reset repeat/cooldown/pending state. Receipts remain immutable: an old event never rerolls. */
  public synchronized void resetTileState(String tileId, String actorId) {
    var tile = tiles.get(tileId);
    if (tile == null) throw new IllegalArgumentException("Unknown tile: " + tileId);
    if (actorId == null) policyStates.keySet().removeIf(k -> k.startsWith(key(tileId)));
    else policyStates.remove(policyKey(tile, actorId));
  }

  private void activate(
      String eventKey,
      TileDefinition tile,
      String occurrenceId,
      String mapId,
      String actorId,
      Trigger trigger,
      long tick,
      String turnId,
      Map<String, String> properties) {
    String activationKey = key(runId, occurrenceId, tile.id(), actorId);
    eventReceipts.get(eventKey).add(activationKey);
    if (receipts.containsKey(activationKey)) return;
    var context =
        new Context(
            runId,
            occurrenceId,
            mapId,
            tile.id(),
            actorId,
            trigger,
            tick,
            turnId,
            properties,
            activationKey);
    if (!tile.enabled()) {
      record(context, Status.SKIPPED, null, List.of(), "Tile is disabled");
      return;
    }
    var errors = validationErrors(tile.id());
    if (tile.policy().repeat() == Repeat.ONCE_PER_TURN && TileDefinition.blank(turnId)) {
      errors = new ArrayList<>(errors);
      errors.add("Once per turn requires a turn ID");
    }
    if (!errors.isEmpty()) {
      record(context, Status.FAILED, null, List.of(), String.join("; ", errors));
      return;
    }
    String policyKey = policyKey(tile, actorId);
    var previous = policyStates.getOrDefault(policyKey, new PolicyState(false, -1, null, 0, null));
    long entry =
        regionStates.getOrDefault(key(tile.regionId(), actorId), new RegionState(0, false)).entry();
    String denied = suppression(tile, previous, entry, tick, turnId);
    if (denied != null) {
      record(context, Status.SKIPPED, null, List.of(), denied);
      return;
    }
    policyStates.put(
        policyKey,
        new PolicyState(
            previous.consumed(),
            previous.lastEntry(),
            previous.lastTurn(),
            previous.eligibleTick(),
            activationKey));
    record(context, Status.RUNNING, null, List.of(), null);
    CheckResult result;
    try {
      result =
          tile.check() == null
              ? CheckResult.outcome("ACTIVATED")
              : rollAdapters
                  .get(tile.check().adapterId())
                  .roll(new CheckRequest(context, tile.check()));
      if (result == null || TileDefinition.blank(result.outcome()))
        throw new IllegalArgumentException("Check adapter returned no outcome");
      if (result.total() != null && !Double.isFinite(result.total()))
        throw new IllegalArgumentException("Check total must be finite");
      if (result.dice().stream().anyMatch(value -> !Double.isFinite(value)))
        throw new IllegalArgumentException("Rolled components must be finite");
    } catch (Exception e) {
      record(context, Status.INTERRUPTED, null, List.of(), message(e));
      return;
    }
    var actions = new ArrayList<>(tile.outcomes().getOrDefault(result.outcome(), List.of()));
    actions.addAll(tile.alwaysActions());
    var steps = new ArrayList<StepReceipt>();
    for (int index = 0; index < actions.size(); index++) {
      var action = actions.get(index);
      String stepKey = key(activationKey, Integer.toString(index));
      steps.add(new StepReceipt(stepKey, action.handlerId(), Status.RUNNING, null));
      record(context, Status.RUNNING, result, steps, null);
      try {
        actionHandlers
            .get(action.handlerId())
            .execute(new ActionRequest(stepKey, context, action, result));
        steps.set(index, new StepReceipt(stepKey, action.handlerId(), Status.COMPLETED, null));
      } catch (Exception e) {
        steps.set(
            index, new StepReceipt(stepKey, action.handlerId(), Status.INTERRUPTED, message(e)));
        record(context, Status.INTERRUPTED, result, steps, message(e));
        return;
      }
    }
    long eligibleTick = tick;
    if (tile.policy().repeat() == Repeat.COOLDOWN) {
      eligibleTick =
          tile.policy().cooldownTicks() > Long.MAX_VALUE - tick
              ? Long.MAX_VALUE
              : tick + tile.policy().cooldownTicks();
    }
    policyStates.put(policyKey, new PolicyState(true, entry, turnId, eligibleTick, null));
    record(context, Status.COMPLETED, result, steps, null);
  }

  private String suppression(
      TileDefinition tile, PolicyState state, long entry, long tick, String turnId) {
    if (state.pendingKey() != null)
      return "Pending or interrupted activation requires GM resolution: " + state.pendingKey();
    return switch (tile.policy().repeat()) {
      case ALWAYS -> null;
      case ONCE -> state.consumed() ? "Once policy already consumed" : null;
      case ONCE_PER_ENTRY ->
          state.consumed() && state.lastEntry() == entry ? "Already activated in this entry" : null;
      case ONCE_PER_TURN ->
          state.consumed() && Objects.equals(turnId, state.lastTurn())
              ? "Already activated in this turn"
              : null;
      case COOLDOWN ->
          state.consumed() && tick < state.eligibleTick()
              ? "Cooldown until tick " + state.eligibleTick()
              : null;
    };
  }

  private void updateOccupancy(String regionId, String actorId, boolean entered, boolean inside) {
    String key = key(regionId, actorId);
    var old = regionStates.getOrDefault(key, new RegionState(0, false));
    long generation = old.entry();
    if (entered || (inside && !old.inside())) generation = Math.addExact(generation, 1);
    regionStates.put(key, new RegionState(generation, inside));
  }

  private void record(
      Context context, Status status, CheckResult result, List<StepReceipt> steps, String reason) {
    receipts.put(
        context.activationKey(),
        new ActivationReceipt(context.activationKey(), context, status, result, steps, reason));
  }

  private List<ActivationReceipt> eventResults(String eventKey) {
    return eventReceipts.get(eventKey).stream()
        .map(receipts::get)
        .filter(Objects::nonNull)
        .toList();
  }

  private String policyKey(TileDefinition tile, String actorId) {
    return key(
        tile.id(),
        tile.policy().scope().name(),
        tile.policy().scope() == Scope.TILE ? "" : actorId);
  }

  /** Length-prefixed components prevent delimiter collisions in external IDs. */
  private static String key(String... components) {
    var result = new StringBuilder();
    for (String component : components) {
      String value = component == null ? "" : component;
      result.append(value.length()).append(':').append(value);
    }
    return result.toString();
  }

  private static void validateEvent(String occurrenceId, String mapId, String actorId, long tick) {
    requireId(occurrenceId, "occurrence ID");
    requireId(mapId, "map ID");
    requireId(actorId, "actor ID");
    if (tick < 0) throw new IllegalArgumentException("Tick cannot be negative");
  }

  private static void requireId(String id, String label) {
    if (TileDefinition.blank(id)) throw new IllegalArgumentException(label + " is required");
  }

  private static void validatePoint(Point point) {
    if (point == null || !Double.isFinite(point.x()) || !Double.isFinite(point.y())) {
      throw new IllegalArgumentException("A finite point is required");
    }
  }

  private static Map<String, String> copyProperties(Map<String, String> properties) {
    try {
      return properties == null ? Map.of() : Map.copyOf(properties);
    } catch (NullPointerException e) {
      throw new IllegalArgumentException("Actor properties cannot contain null keys or values", e);
    }
  }

  private static String message(Exception e) {
    return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
  }

  public synchronized Snapshot snapshot() {
    var events = new LinkedHashMap<String, List<String>>();
    eventReceipts.forEach((key, value) -> events.put(key, List.copyOf(value)));
    return new Snapshot(
        SNAPSHOT_VERSION,
        runId,
        regions(),
        definitions(),
        Map.copyOf(receipts),
        Map.copyOf(policyStates),
        Map.copyOf(regionStates),
        Collections.unmodifiableMap(events));
  }

  public synchronized String snapshotJson() {
    return new Gson().toJson(snapshot());
  }

  /** Runtime handlers are intentionally not serialized and must be registered after restoration. */
  public static TileEngine restore(Snapshot snapshot) {
    if (snapshot.version() != SNAPSHOT_VERSION)
      throw new IllegalArgumentException(
          "Unsupported tile snapshot version: " + snapshot.version());
    var engine = new TileEngine(snapshot.runId());
    snapshot.regions().forEach(engine::putRegion);
    snapshot.tiles().forEach(engine::putTile);
    engine.receipts.putAll(snapshot.receipts());
    snapshot
        .receipts()
        .forEach(
            (key, receipt) -> {
              if (receipt.status() == Status.RUNNING) {
                var steps =
                    receipt.steps().stream()
                        .map(
                            step ->
                                step.status() == Status.RUNNING
                                    ? new StepReceipt(
                                        step.key(),
                                        step.handlerId(),
                                        Status.INTERRUPTED,
                                        "Interrupted while snapshot was taken")
                                    : step)
                        .toList();
                engine.receipts.put(
                    key,
                    new ActivationReceipt(
                        key,
                        receipt.context(),
                        Status.INTERRUPTED,
                        receipt.result(),
                        steps,
                        "Interrupted while snapshot was taken; GM resolution required"));
              }
            });
    engine.policyStates.putAll(snapshot.policyStates());
    engine.regionStates.putAll(snapshot.regionStates());
    snapshot
        .eventReceipts()
        .forEach((key, value) -> engine.eventReceipts.put(key, new ArrayList<>(value)));
    return engine;
  }

  public static TileEngine fromJson(String json) {
    return restore(new Gson().fromJson(json, Snapshot.class));
  }
}
