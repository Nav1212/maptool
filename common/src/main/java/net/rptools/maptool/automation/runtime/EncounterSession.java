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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.rptools.maptool.automation.npc.NpcEngine;
import net.rptools.maptool.automation.npc.NpcWorld;
import net.rptools.maptool.automation.tile.TileEngine;
import net.rptools.maptool.automation.tile.TileRegion.Point;

/**
 * Composition boundary for one map, its shared clock, tile consumers, and NPC decisions.
 *
 * <p>The world adapter owns accepted movement, permissions, media, and persistence of actual map
 * entities. It must report committed movement segments to {@link #acceptedMovement}; observing
 * clients render results rather than running this session. No GUI or global current-map state is
 * consulted here.
 */
public final class EncounterSession {
  public interface World extends NpcWorld {
    Map<String, String> properties(String mapId, String actorId);

    String saveMapState(String mapId);

    /** Must validate before changing state and restore atomically, or leave the world unchanged. */
    void restoreMapState(String mapId, String state);
  }

  public record Snapshot(
      int version,
      String mapId,
      String runId,
      String authorityId,
      String runtimeJson,
      String tileJson,
      NpcEngine.Snapshot npcs,
      String worldJson,
      List<MapRuntime.Event> acceptedMovementOutbox) {
    public Snapshot {
      acceptedMovementOutbox =
          acceptedMovementOutbox == null ? List.of() : List.copyOf(acceptedMovementOutbox);
    }
  }

  private static final Gson GSON = new Gson();
  private final String mapId;
  private final String runId;
  private final String authorityId;
  private final World world;
  private final MapRuntime runtime;
  private final TileEngine tiles;
  private final NpcEngine npcs;
  private final Map<String, MapRuntime.Event> movementDuringNpcDecision = new LinkedHashMap<>();
  private boolean decidingNpcs;

  public EncounterSession(String mapId, String runId, String authorityId, World world) {
    this(
        mapId,
        runId,
        authorityId,
        world,
        new MapRuntime(runId, mapId, authorityId),
        new TileEngine(runId),
        new NpcEngine());
  }

  private EncounterSession(
      String mapId,
      String runId,
      String authorityId,
      World world,
      MapRuntime runtime,
      TileEngine tiles,
      NpcEngine npcs) {
    this.mapId = Objects.requireNonNull(mapId);
    this.runId = Objects.requireNonNull(runId);
    this.authorityId = Objects.requireNonNull(authorityId);
    this.world = Objects.requireNonNull(world);
    this.runtime = Objects.requireNonNull(runtime);
    this.tiles = Objects.requireNonNull(tiles);
    this.npcs = Objects.requireNonNull(npcs);
    runtime.guardExecutionWith(authorityId, () -> world.hasAuthority(runId, mapId));
    runtime.register(authorityId, "movement", this::applyMovement);
    runtime.register(authorityId, "npc-decision", this::decideNpcs);
    runtime.register(authorityId, "tile-pulse", this::pulseTiles);
  }

  /**
   * Host-internal authoring API; do not expose these mutable engines through a player transport.
   */
  public TileEngine tiles() {
    return tiles;
  }

  public NpcEngine npcs() {
    return npcs;
  }

  public MapRuntime.Snapshot clock() {
    return runtime.snapshot();
  }

  public synchronized void pause(String caller) {
    runtime.pause(caller);
  }

  public synchronized void resume(String caller) {
    authorize(caller);
    if (!world.hasAuthority(runId, mapId))
      throw new SecurityException("World authority is unavailable");
    flushAcceptedMovements();
    runtime.resume(caller);
  }

  public synchronized void persistWith(String caller, java.util.function.Consumer<Snapshot> save) {
    authorize(caller);
    runtime.persistWith(caller, ignored -> save.accept(snapshot()));
  }

  /** Captures the actor's properties at the accepted occurrence, before subsequent mutations. */
  public synchronized boolean acceptedMovement(
      String caller, String occurrenceId, String actorId, Point from, Point to, String turnId) {
    authorize(caller);
    var arguments = new LinkedHashMap<String, String>();
    arguments.put("fromX", Double.toString(from.x()));
    arguments.put("fromY", Double.toString(from.y()));
    arguments.put("toX", Double.toString(to.x()));
    arguments.put("toY", Double.toString(to.y()));
    arguments.put("turnId", Objects.requireNonNullElse(turnId, ""));
    world
        .properties(mapId, actorId)
        .forEach((key, value) -> arguments.put("property." + key, value));
    var event =
        new MapRuntime.Event(
            runId, occurrenceId, mapId, actorId, "movement", runtime.tick(), arguments);
    if (decidingNpcs) {
      if (runtime.hasEvent(occurrenceId) || movementDuringNpcDecision.containsKey(occurrenceId))
        return false;
      if (movementDuringNpcDecision.size() >= runtime.snapshot().queueLimit()) {
        throw new IllegalStateException("Too many accepted segments in one NPC decision batch");
      }
      movementDuringNpcDecision.put(occurrenceId, event);
      return true;
    }
    return runtime.enqueue(caller, event);
  }

  public synchronized boolean scheduleNpcDecision(String caller, String eventId, long tick) {
    authorize(caller);
    return runtime.enqueue(
        caller, new MapRuntime.Event(runId, eventId, mapId, "", "npc-decision", tick, Map.of()));
  }

  public synchronized boolean schedulePulse(
      String caller, String eventId, String actorId, long tick, String turnId) {
    authorize(caller);
    return runtime.enqueue(
        caller,
        new MapRuntime.Event(
            runId,
            eventId,
            mapId,
            actorId,
            "tile-pulse",
            tick,
            Map.of("turnId", Objects.requireNonNullElse(turnId, ""))));
  }

  public synchronized MapRuntime.Receipt step(String caller) {
    authorize(caller);
    if (decidingNpcs)
      throw new IllegalStateException("An NPC action cannot recursively step the map");
    flushAcceptedMovements();
    return runtime.step(caller);
  }

  public synchronized boolean cancelQueued(String caller, String eventId) {
    authorize(caller);
    return runtime.cancel(caller, eventId);
  }

  /** Explicitly retry notification delivery after a GM frees queue capacity or repairs storage. */
  public synchronized void retryDelivery(String caller) {
    authorize(caller);
    flushAcceptedMovements();
  }

  public synchronized List<MapRuntime.Receipt> advanceTo(String caller, long tick) {
    authorize(caller);
    if (decidingNpcs)
      throw new IllegalStateException("An NPC action cannot recursively advance the map");
    flushAcceptedMovements();
    return runtime.advanceTo(caller, tick);
  }

  public synchronized Snapshot snapshot() {
    return new Snapshot(
        1,
        mapId,
        runId,
        authorityId,
        runtime.toJson(),
        tiles.snapshotJson(),
        npcs.snapshot(),
        world.saveMapState(mapId),
        List.copyOf(movementDuringNpcDecision.values()));
  }

  /** Full GM checkpoint. Player transports must use their own explicitly filtered view. */
  public synchronized String toJson() {
    return GSON.toJson(snapshot());
  }

  /** All models are validated before the supplied world is restored. Restore starts paused. */
  public static EncounterSession fromJson(
      String json, String expectedMapId, String authorityId, World world) {
    Snapshot saved = GSON.fromJson(Objects.requireNonNull(json), Snapshot.class);
    if (saved == null || saved.version() != 1)
      throw new IllegalArgumentException("Unsupported encounter version");
    if (!Objects.equals(saved.mapId(), expectedMapId))
      throw new IllegalArgumentException("Encounter map mismatch");
    if (!Objects.equals(saved.authorityId(), authorityId))
      throw new SecurityException("Encounter authority mismatch");
    var runtime = MapRuntime.fromJson(saved.runtimeJson(), expectedMapId, authorityId);
    var tiles = TileEngine.fromJson(saved.tileJson());
    if (!saved.runId().equals(runtime.snapshot().runId())
        || !saved.runId().equals(tiles.snapshot().runId())) {
      throw new IllegalArgumentException("Encounter components belong to different runs");
    }
    for (var region : tiles.regions()) {
      if (!expectedMapId.equals(region.mapId()))
        throw new IllegalArgumentException("Region map mismatch");
    }
    for (var tile : tiles.definitions()) {
      if (!expectedMapId.equals(tile.mapId()))
        throw new IllegalArgumentException("Tile map mismatch");
    }
    var npcs = NpcEngine.fromSnapshot(saved.npcs());
    for (var map : npcs.snapshot().maps()) {
      if (!expectedMapId.equals(map.mapId())
          || (!map.runId().isEmpty() && !saved.runId().equals(map.runId()))) {
        throw new IllegalArgumentException("NPC state belongs to another map or encounter run");
      }
    }
    if (!world.hasAuthority(saved.runId(), expectedMapId))
      throw new SecurityException("World authority is unavailable");
    var session =
        new EncounterSession(
            expectedMapId, saved.runId(), authorityId, world, runtime, tiles, npcs);
    for (var movement : saved.acceptedMovementOutbox()) {
      if (!saved.runId().equals(movement.runId())
          || !expectedMapId.equals(movement.mapId())
          || !"movement".equals(movement.type())
          || session.movementDuringNpcDecision.putIfAbsent(movement.id(), movement) != null) {
        throw new IllegalArgumentException("Invalid accepted movement outbox");
      }
    }
    if (session.movementDuringNpcDecision.size() > runtime.snapshot().queueLimit()) {
      throw new IllegalArgumentException("Oversized accepted movement outbox");
    }
    world.restoreMapState(expectedMapId, saved.worldJson());
    return session;
  }

  private MapRuntime.Result applyMovement(MapRuntime.Event event) {
    checkWorldAuthority();
    var values = event.arguments();
    var properties = new LinkedHashMap<String, String>();
    values.forEach(
        (key, value) -> {
          if (key.startsWith("property."))
            properties.put(key.substring("property.".length()), value);
        });
    var results =
        tiles.onMove(
            event.id(),
            mapId,
            event.actorId(),
            new Point(
                Double.parseDouble(values.get("fromX")), Double.parseDouble(values.get("fromY"))),
            new Point(Double.parseDouble(values.get("toX")), Double.parseDouble(values.get("toY"))),
            runtime.tick(),
            values.get("turnId"),
            properties);
    return summarizeTiles(results);
  }

  private MapRuntime.Result decideNpcs(MapRuntime.Event event) {
    checkWorldAuthority();
    decidingNpcs = true;
    try {
      var results = npcs.tick(runId, event.id(), mapId, runtime.tick(), world);
      return new MapRuntime.Result(
          "NPC decisions: " + results.size(), Map.of("npcResults", GSON.toJson(results)));
    } finally {
      decidingNpcs = false;
      // NPC snapshots reject in-flight execution. Delay persistence-triggering enqueue calls until
      // its batch has completed, while retaining accepted movement order and captured actor inputs.
      flushAcceptedMovements();
    }
  }

  private MapRuntime.Result pulseTiles(MapRuntime.Event event) {
    checkWorldAuthority();
    var actor =
        world
            .token(mapId, event.actorId())
            .orElseThrow(() -> new IllegalArgumentException("Actor no longer exists"));
    var point = new Point(actor.position().x(), actor.position().y());
    return summarizeTiles(
        tiles.pulse(
            event.id(),
            mapId,
            event.actorId(),
            point,
            runtime.tick(),
            event.arguments().get("turnId"),
            world.properties(mapId, event.actorId())));
  }

  private MapRuntime.Result summarizeTiles(List<TileEngine.ActivationReceipt> results) {
    var errors =
        results.stream()
            .filter(
                r ->
                    r.status() == TileEngine.Status.FAILED
                        || r.status() == TileEngine.Status.INTERRUPTED)
            .toList();
    if (!errors.isEmpty()) runtime.pause(authorityId);
    return new MapRuntime.Result(
        "Tile activations: " + results.size() + "; blocked: " + errors.size(),
        Map.of("tileResults", GSON.toJson(results)));
  }

  private void flushAcceptedMovements() {
    if (movementDuringNpcDecision.isEmpty()) return;
    var accepted = List.copyOf(movementDuringNpcDecision.values());
    try {
      // The durability callback sees the complete outbox while the batch is being reserved. A
      // restored checkpoint may contain both representations; receipt deduplication makes that
      // safe.
      runtime.enqueueAll(authorityId, accepted);
      movementDuringNpcDecision.clear();
    } catch (RuntimeException failure) {
      // Already committed movement must not disappear just because its trigger delivery is blocked.
      // Keep the outbox in the snapshot and require explicit GM recovery before more work runs.
      runtime.pause(authorityId);
      throw failure;
    }
  }

  private void authorize(String caller) {
    if (!authorityId.equals(caller))
      throw new SecurityException("Only the encounter authority may change state");
  }

  private void checkWorldAuthority() {
    if (!world.hasAuthority(runId, mapId))
      throw new SecurityException("World authority is unavailable");
  }
}
