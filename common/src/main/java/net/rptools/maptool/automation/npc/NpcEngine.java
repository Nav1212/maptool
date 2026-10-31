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
package net.rptools.maptool.automation.npc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.rptools.maptool.automation.npc.NpcResult.Outcome;
import net.rptools.maptool.automation.npc.NpcRule.Context;
import net.rptools.maptool.automation.npc.NpcRule.Decision;
import net.rptools.maptool.automation.npc.NpcWorld.ActionRequest;
import net.rptools.maptool.automation.npc.NpcWorld.Observation;
import net.rptools.maptool.automation.npc.NpcWorld.PlanStatus;
import net.rptools.maptool.automation.npc.NpcWorld.Point;
import net.rptools.maptool.automation.npc.NpcWorld.Token;

/**
 * Pure caller-driven NPC coordinator. It owns no threads, clock, GUI state, dice RNG or transport.
 * Each unique event schedules at most one move or action per assigned NPC, in token-ID order.
 */
public final class NpcEngine {
  private static final double EPSILON = 0.0000001;
  private static final int MAX_OBSERVATIONS = 1024;
  private static final int MAX_PATH_POINTS = 8192;
  private final int maxNpcsPerMap;
  private final int receiptLimit;
  private final Map<String, MapState> maps = new TreeMap<>();
  private final Map<String, NpcRule> rules = new HashMap<>();
  private final LinkedHashMap<ReceiptKey, NpcResult> receipts = new LinkedHashMap<>();
  private final Set<ActorKey> inFlight = new HashSet<>();

  public NpcEngine() {
    this(256, 2048);
  }

  /**
   * Limits bound work and retained receipts; expired event ticks are rejected, never reexecuted.
   */
  public NpcEngine(int maxNpcsPerMap, int receiptLimit) {
    if (maxNpcsPerMap < 1 || receiptLimit < 1) {
      throw new IllegalArgumentException("Limits must be positive");
    }
    this.maxNpcsPerMap = maxNpcsPerMap;
    this.receiptLimit = receiptLimit;
  }

  public synchronized void assign(String mapId, String npcId, NpcProfile profile) {
    requireId(npcId);
    Objects.requireNonNull(profile);
    var map = map(mapId);
    if (!map.actors.containsKey(npcId) && map.actors.size() >= maxNpcsPerMap) {
      throw new IllegalArgumentException("NPC limit for map exceeded");
    }
    var old = map.actors.get(npcId);
    var state = new ActorState(profile);
    if (old != null) {
      state.lastTick = old.lastTick;
      state.retiredThroughTick = old.retiredThroughTick;
      state.paused = old.paused;
      state.manual = old.manual;
    }
    var removedFloor = map.removedActorFloors.remove(npcId);
    if (removedFloor != null) {
      state.lastTick = Math.max(state.lastTick, removedFloor);
      state.retiredThroughTick = Math.max(state.retiredThroughTick, removedFloor);
    }
    map.actors.put(npcId, state);
  }

  public synchronized void removeNpc(String mapId, String npcId) {
    var map = maps.get(mapId);
    if (map != null) {
      var removed = map.actors.remove(npcId);
      if (removed != null) {
        map.removedActorFloors.merge(npcId, removed.lastTick, Math::max);
      }
    }
  }

  public synchronized void setMapEnabled(String mapId, boolean enabled) {
    map(mapId).enabled = enabled;
  }

  public synchronized void setMapPaused(String mapId, boolean paused) {
    map(mapId).paused = paused;
  }

  public synchronized void setNpcPaused(String mapId, String npcId, boolean paused) {
    actor(mapId, npcId).paused = paused;
  }

  public synchronized void setManualOverride(String mapId, String npcId, boolean manual) {
    actor(mapId, npcId).manual = manual;
  }

  /** An override still needs an observer-visible target; it does not confer GM omniscience. */
  public synchronized void setTargetOverride(String mapId, String npcId, String targetId) {
    actor(mapId, npcId).targetOverride = Objects.requireNonNullElse(targetId, "");
  }

  public synchronized void registerRule(String id, NpcRule rule) {
    requireId(id);
    rules.put(id, Objects.requireNonNull(rule));
  }

  /** Explicitly begin a new run. Run identifiers cannot be reused for the same map. */
  public synchronized void resetRun(String mapId, String runId) {
    requireId(runId);
    var map = map(mapId);
    if (runId.equals(map.runId) || map.retiredRuns.contains(runId)) {
      throw new IllegalArgumentException("Run identifier has already been used");
    }
    if (!map.runId.isEmpty()) {
      map.retiredRuns.add(map.runId);
    }
    map.runId = runId;
    map.removedActorFloors.clear();
    for (var entry : map.actors.entrySet()) {
      var old = entry.getValue();
      var state = new ActorState(old.profile);
      state.paused = old.paused;
      state.manual = old.manual;
      entry.setValue(state);
    }
    receipts.keySet().removeIf(key -> key.mapId.equals(mapId));
  }

  /**
   * Caller supplies the shared clock and one stable event ID per turn/tick occurrence. Repeated IDs
   * return receipts. Adapter command keys also need durable deduplication if effects must survive a
   * process crash between mutation and snapshot persistence.
   */
  public synchronized List<NpcResult> tick(
      String runId, String eventId, String mapId, long logicalTick, NpcWorld world) {
    requireId(runId);
    requireId(eventId);
    requireId(mapId);
    requireTick(logicalTick);
    Objects.requireNonNull(world);
    var map = maps.get(mapId);
    if (map == null) {
      return List.of();
    }
    if (!inFlight.isEmpty()) {
      return map.actors.entrySet().stream()
          .map(
              entry -> {
                var key = new ReceiptKey(runId, eventId, mapId, entry.getKey());
                var previous = receipts.get(key);
                return previous != null
                    ? previous.asDuplicate()
                    : result(
                        key,
                        logicalTick,
                        entry.getValue(),
                        Outcome.WAITING,
                        null,
                        "Coordinator is executing; submit deferred event after it completes");
              })
          .toList();
    }
    if (map.runId.isEmpty() && !map.retiredRuns.contains(runId)) {
      map.runId = runId;
    }
    var results = new ArrayList<NpcResult>();
    // Snapshot membership, so callbacks cannot invalidate this iteration.
    for (var npcId : List.copyOf(map.actors.keySet())) {
      var state = map.actors.get(npcId);
      if (state == null) {
        continue;
      }
      var key = new ReceiptKey(runId, eventId, mapId, npcId);
      var previous = receipts.get(key);
      if (previous != null) {
        results.add(previous.asDuplicate());
        continue;
      }
      if (inFlight.contains(new ActorKey(mapId, npcId))) {
        results.add(
            result(
                key, logicalTick, state, Outcome.WAITING, null, "NPC event is already executing"));
        continue;
      }
      if (!map.runId.equals(runId)) {
        results.add(
            result(key, logicalTick, state, Outcome.REJECTED, null, "Run is not active for map"));
        continue;
      }
      if (logicalTick < state.lastTick || logicalTick <= state.retiredThroughTick) {
        results.add(result(key, logicalTick, state, Outcome.STALE, null, "Event tick has expired"));
        continue;
      }
      NpcResult receipt;
      var actorKey = new ActorKey(mapId, npcId);
      inFlight.add(actorKey);
      try {
        receipt = process(key, logicalTick, map, state, world);
      } catch (RuntimeException failure) {
        // Do not retry an adapter failure: it may have occurred after a world effect.
        receipt =
            result(
                key,
                logicalTick,
                state,
                Outcome.ERROR,
                null,
                "Rule/adapter failed; inspect effects before a new event: "
                    + failure.getClass().getSimpleName());
      } finally {
        inFlight.remove(actorKey);
      }
      if (receipt.outcome() == Outcome.ERROR) {
        state.paused = true;
      }
      state.lastTick = Math.max(state.lastTick, logicalTick);
      var currentState = map.actors.get(npcId);
      if (currentState != null && map.runId.equals(runId)) {
        currentState.lastTick = Math.max(currentState.lastTick, logicalTick);
      }
      remember(key, receipt);
      results.add(receipt);
    }
    return List.copyOf(results);
  }

  /** Pure decision preview on copied state. It never plans/commits movement or executes actions. */
  public synchronized Decision preview(
      String mapId, String npcId, long logicalTick, NpcWorld world) {
    requireTick(logicalTick);
    var state = new ActorState(actor(mapId, npcId).snapshot(npcId));
    var token = checkedToken(world, mapId, npcId);
    if (token.isEmpty() || !token.get().npc()) {
      return Decision.waitFor("NPC token is unavailable");
    }
    if (state.home == null) {
      state.home = token.get().position();
    }
    var observations = observations(world, mapId, npcId);
    return decide(mapId, npcId, logicalTick, state, token.get(), observations);
  }

  public synchronized Optional<ActorSnapshot> inspect(String mapId, String npcId) {
    var map = maps.get(mapId);
    var state = map == null ? null : map.actors.get(npcId);
    return state == null ? Optional.empty() : Optional.of(state.snapshot(npcId));
  }

  private NpcResult process(
      ReceiptKey key, long tick, MapState map, ActorState state, NpcWorld world) {
    var stopped = stopped(key, tick, map, state, world, null);
    if (stopped != null) {
      return stopped;
    }
    var optionalToken = checkedToken(world, key.mapId, key.npcId);
    if (optionalToken.isEmpty() || !optionalToken.get().npc()) {
      return result(key, tick, state, Outcome.MISSING_TOKEN, null, "NPC token is unavailable");
    }
    var token = optionalToken.get();
    if (state.home == null) {
      state.home = token.position();
    }
    var observations = observations(world, key.mapId, key.npcId);
    var decision = decide(key.mapId, key.npcId, tick, state, token, observations);
    if (decision.kind() == NpcRule.Kind.WAIT) {
      return result(key, tick, state, Outcome.WAITING, token.position(), decision.reason());
    }
    if (decision.kind() == NpcRule.Kind.MOVE) {
      return move(key, tick, map, state, token, decision, world);
    }
    return act(key, tick, map, state, token, observations, decision, world);
  }

  private NpcResult move(
      ReceiptKey key,
      long tick,
      MapState map,
      ActorState state,
      Token token,
      Decision decision,
      NpcWorld world) {
    double budget = state.profile.movementBudget();
    if (budget == 0 || near(token.position(), decision.destination())) {
      return result(
          key, tick, state, Outcome.WAITING, token.position(), "No movement needed/budget");
    }
    var plan =
        Objects.requireNonNull(
            world.planMove(key.mapId, key.npcId, decision.destination(), budget));
    if (plan.status() != PlanStatus.READY) {
      return result(
          key,
          tick,
          state,
          plan.status() == PlanStatus.STALE ? Outcome.STALE : Outcome.NO_PATH,
          token.position(),
          plan.detail());
    }
    if (plan.tokenRevision() != token.revision()) {
      return result(key, tick, state, Outcome.STALE, token.position(), "Actor revision is stale");
    }
    if (!Double.isFinite(plan.cost())
        || plan.cost() < 0
        || plan.cost() > budget
        || plan.path().isEmpty()
        || plan.path().size() > MAX_PATH_POINTS
        || !near(token.position(), plan.origin())) {
      return result(key, tick, state, Outcome.REJECTED, token.position(), "Invalid movement plan");
    }
    var stopped = stopped(key, tick, map, state, world, token.position());
    if (stopped != null) {
      return stopped;
    }
    if (!unchanged(world, key.mapId, token)) {
      return result(
          key, tick, state, Outcome.STALE, token.position(), "Actor changed after planning");
    }
    var outcome =
        Objects.requireNonNull(
            world.commitMove(key.mapId, key.npcId, plan, key.commandKey("move")));
    if (!outcome.committed()) {
      return result(
          key,
          tick,
          state,
          outcome.stale() ? Outcome.STALE : Outcome.REJECTED,
          token.position(),
          outcome.detail());
    }
    if (!near(outcome.position(), plan.path().getLast())) {
      return result(
          key,
          tick,
          state,
          Outcome.ERROR,
          outcome.position(),
          "Adapter committed unexpected position");
    }
    if (state.behavior == NpcBehavior.PATROL
        && !state.profile.waypoints().isEmpty()
        && near(
            outcome.position(), state.profile.waypoints().get(state.waypointIndex).position())) {
      arrive(state, tick);
    }
    return new NpcResult(
        key.runId,
        key.eventId,
        key.mapId,
        key.npcId,
        tick,
        state.behavior,
        Outcome.MOVED,
        outcome.position(),
        plan.cost(),
        0,
        state.targetId,
        decision.reason(),
        List.of(),
        Map.of(),
        false);
  }

  private NpcResult act(
      ReceiptKey key,
      long tick,
      MapState map,
      ActorState state,
      Token token,
      List<Observation> observations,
      Decision decision,
      NpcWorld world) {
    if (decision.actionCost() > state.profile.actionBudget()) {
      return result(key, tick, state, Outcome.WAITING, token.position(), "Action budget exhausted");
    }
    var target =
        observations.stream().filter(o -> o.tokenId().equals(decision.targetId())).findFirst();
    if (!decision.targetId().isEmpty() && target.isEmpty()) {
      return result(
          key, tick, state, Outcome.REJECTED, token.position(), "Target is not perceived");
    }
    var stopped = stopped(key, tick, map, state, world, token.position());
    if (stopped != null) {
      return stopped;
    }
    if (!unchanged(world, key.mapId, token)) {
      return result(
          key, tick, state, Outcome.STALE, token.position(), "Actor changed before action");
    }
    if (target.isPresent()) {
      var seen = target.get();
      var current = checkedToken(world, key.mapId, seen.tokenId());
      boolean stillSeen = observations(world, key.mapId, key.npcId).stream().anyMatch(seen::equals);
      if (current.isEmpty()
          || current.get().revision() != seen.revision()
          || !near(current.get().position(), seen.position())
          || !stillSeen) {
        return result(
            key, tick, state, Outcome.STALE, token.position(), "Target/perception changed");
      }
    }
    var request =
        new ActionRequest(
            decision.actionId(),
            decision.targetId(),
            token.revision(),
            target.map(Observation::revision).orElse(0L),
            decision.actionCost(),
            decision.parameters());
    var outcome =
        Objects.requireNonNull(
            world.executeApprovedAction(key.mapId, key.npcId, request, key.commandKey("action")));
    if (!outcome.committed()) {
      return result(key, tick, state, Outcome.REJECTED, token.position(), outcome.detail());
    }
    if (outcome.actionsSpent() != decision.actionCost()) {
      return result(
          key, tick, state, Outcome.ERROR, token.position(), "Adapter returned invalid cost");
    }
    return new NpcResult(
        key.runId,
        key.eventId,
        key.mapId,
        key.npcId,
        tick,
        state.behavior,
        Outcome.ACTED,
        token.position(),
        0,
        outcome.actionsSpent(),
        decision.targetId(),
        outcome.detail(),
        outcome.recordedRolls(),
        outcome.recordedOutcomes(),
        false);
  }

  private Decision decide(
      String mapId,
      String npcId,
      long tick,
      ActorState state,
      Token token,
      List<Observation> observations) {
    var defaultDecision = builtin(tick, state, token, observations);
    if (state.profile.ruleId().isEmpty()) {
      return defaultDecision;
    }
    var rule = rules.get(state.profile.ruleId());
    if (rule == null) {
      return Decision.waitFor("Configured rule is not registered");
    }
    return Objects.requireNonNull(
        rule.decide(
            new Context(
                mapId,
                npcId,
                tick,
                state.profile,
                token,
                observations,
                state.behavior,
                state.home,
                state.lastSeen,
                state.targetId)));
  }

  private Decision builtin(
      long tick, ActorState state, Token token, List<Observation> observations) {
    var profile = state.profile;
    Optional<Observation> target = Optional.empty();
    if (profile.pursueVisibleTargets() || !state.targetOverride.isEmpty()) {
      target =
          observations.stream()
              .filter(
                  o -> state.targetOverride.isEmpty() || state.targetOverride.equals(o.tokenId()))
              .filter(
                  o ->
                      profile.maxPursuitDistance() == 0
                          || state.home.distance(o.position()) <= profile.maxPursuitDistance())
              .min(
                  Comparator.comparingDouble(
                          (Observation o) -> token.position().distance(o.position()))
                      .thenComparing(Observation::tokenId));
    }
    if (!state.targetOverride.isEmpty() && target.isEmpty()) {
      return Decision.waitFor("Override target is not perceived/in territory");
    }
    if (target.isPresent()) {
      var seen = target.get();
      state.targetId = seen.tokenId();
      state.lastSeen = seen.position();
      state.lastSeenTick = tick;
      state.behavior = NpcBehavior.PURSUE;
      if (!profile.actionId().isEmpty()
          && token.position().distance(seen.position()) <= profile.actionRange()) {
        return Decision.action(
            profile.actionId(), seen.tokenId(), 1, "Visible target in action range");
      }
      return Decision.move(seen.position(), "Pursuing perceived target");
    }
    if (state.lastSeen != null
        && profile.memoryTicks() > 0
        && tick >= state.lastSeenTick
        && tick - state.lastSeenTick <= profile.memoryTicks()) {
      state.behavior = NpcBehavior.INVESTIGATE;
      return near(token.position(), state.lastSeen)
          ? Decision.waitFor("Investigating last-seen position")
          : Decision.move(state.lastSeen, "Moving to last-seen position");
    }
    if (state.behavior == NpcBehavior.PURSUE || state.behavior == NpcBehavior.INVESTIGATE) {
      state.behavior = NpcBehavior.RETURN;
    }
    if (state.behavior == NpcBehavior.RETURN) {
      if (!near(token.position(), state.home)) {
        return Decision.move(state.home, "Returning home after target loss");
      }
      state.lastSeen = null;
      state.targetId = "";
    }
    if (profile.waypoints().isEmpty() || state.finished) {
      state.behavior = NpcBehavior.IDLE;
      return Decision.waitFor("No remaining patrol route");
    }
    state.behavior = NpcBehavior.PATROL;
    if (state.arrived) {
      if (tick < state.wakeAt) {
        return Decision.waitFor("Waiting at patrol waypoint");
      }
      advance(state);
      if (state.finished) {
        state.behavior = NpcBehavior.IDLE;
        return Decision.waitFor("Patrol completed");
      }
    }
    var waypoint = profile.waypoints().get(state.waypointIndex);
    if (near(token.position(), waypoint.position())) {
      arrive(state, tick);
      return Decision.waitFor("Reached patrol waypoint");
    }
    return Decision.move(waypoint.position(), "Patrol waypoint " + state.waypointIndex);
  }

  private static void arrive(ActorState state, long tick) {
    state.arrived = true;
    long wait = state.profile.waypoints().get(state.waypointIndex).waitTicks();
    state.wakeAt = tick > Long.MAX_VALUE - wait ? Long.MAX_VALUE : tick + wait;
  }

  private static void advance(ActorState state) {
    state.arrived = false;
    int size = state.profile.waypoints().size();
    switch (state.profile.patrolMode()) {
      case ONCE -> {
        if (state.waypointIndex + 1 >= size) {
          state.finished = true;
        } else {
          state.waypointIndex++;
        }
      }
      case LOOP -> state.waypointIndex = (state.waypointIndex + 1) % size;
      case PING_PONG -> {
        if (size > 1) {
          int next = state.waypointIndex + state.direction;
          if (next < 0 || next >= size) {
            state.direction = -state.direction;
          }
          state.waypointIndex += state.direction;
        }
      }
    }
  }

  private NpcResult stopped(
      ReceiptKey key, long tick, MapState map, ActorState state, NpcWorld world, Point position) {
    boolean authority = world.hasAuthority(key.runId, key.mapId);
    if (!map.enabled || map.paused || state.paused) {
      return result(key, tick, state, Outcome.PAUSED, position, "Map/NPC automation is paused");
    }
    if (state.manual) {
      return result(key, tick, state, Outcome.MANUAL, position, "NPC is under manual control");
    }
    if (map.actors.get(key.npcId) != state || !map.runId.equals(key.runId) || !authority) {
      return result(
          key, tick, state, Outcome.REJECTED, position, "Map authority/configuration changed");
    }
    return null;
  }

  private static Optional<Token> checkedToken(NpcWorld world, String mapId, String tokenId) {
    var token = Objects.requireNonNull(world.token(mapId, tokenId));
    if (token.isPresent() && !token.get().id().equals(tokenId)) {
      throw new IllegalArgumentException("World returned a different token");
    }
    return token;
  }

  private static boolean unchanged(NpcWorld world, String mapId, Token expected) {
    return checkedToken(world, mapId, expected.id()).filter(expected::equals).isPresent();
  }

  private static List<Observation> observations(NpcWorld world, String mapId, String npcId) {
    var supplied = Objects.requireNonNull(world.perceive(mapId, npcId));
    if (supplied.size() > MAX_OBSERVATIONS) {
      throw new IllegalArgumentException("Perception observation limit exceeded");
    }
    var ids = new HashSet<String>();
    var result = new ArrayList<Observation>();
    for (var observation : supplied) {
      Objects.requireNonNull(observation);
      if (!ids.add(observation.tokenId())) {
        throw new IllegalArgumentException("Duplicate observation ID");
      }
      if (observation.targetable() && !observation.tokenId().equals(npcId)) {
        result.add(observation);
      }
    }
    result.sort(Comparator.comparing(Observation::tokenId));
    return List.copyOf(result);
  }

  private void remember(ReceiptKey key, NpcResult result) {
    receipts.put(key, result);
    while (receipts.size() > receiptLimit) {
      var first = receipts.entrySet().iterator();
      var expired = first.next();
      var map = maps.get(expired.getKey().mapId);
      var state = map == null ? null : map.actors.get(expired.getKey().npcId);
      if (state != null && map.runId.equals(expired.getKey().runId)) {
        state.retiredThroughTick =
            Math.max(state.retiredThroughTick, expired.getValue().logicalTick());
        state.lastTick = Math.max(state.lastTick, state.retiredThroughTick);
      } else if (map != null && map.runId.equals(expired.getKey().runId)) {
        map.removedActorFloors.merge(
            expired.getKey().npcId, expired.getValue().logicalTick(), Math::max);
      }
      first.remove();
    }
  }

  private static NpcResult result(
      ReceiptKey key, long tick, ActorState state, Outcome outcome, Point position, String detail) {
    return new NpcResult(
        key.runId,
        key.eventId,
        key.mapId,
        key.npcId,
        tick,
        state.behavior,
        outcome,
        position,
        0,
        0,
        state.targetId,
        detail,
        List.of(),
        Map.of(),
        false);
  }

  public synchronized Snapshot snapshot() {
    if (!inFlight.isEmpty()) {
      throw new IllegalStateException("Snapshot must be taken after NPC events complete");
    }
    var snapshots = new ArrayList<MapSnapshot>();
    for (var entry : maps.entrySet()) {
      var map = entry.getValue();
      var actors =
          map.actors.entrySet().stream().map(e -> e.getValue().snapshot(e.getKey())).toList();
      snapshots.add(
          new MapSnapshot(
              entry.getKey(),
              map.enabled,
              map.paused,
              map.runId,
              List.copyOf(map.retiredRuns),
              map.removedActorFloors,
              actors));
    }
    var retained =
        receipts.values().stream()
            .filter(
                r -> {
                  var map = maps.get(r.mapId());
                  return map != null
                      && map.actors.containsKey(r.npcId())
                      && map.runId.equals(r.runId());
                })
            .toList();
    return new Snapshot(1, maxNpcsPerMap, receiptLimit, snapshots, retained);
  }

  public static NpcEngine fromSnapshot(Snapshot snapshot) {
    Objects.requireNonNull(snapshot);
    if (snapshot.version != 1) {
      throw new IllegalArgumentException("Unsupported NPC snapshot version");
    }
    var engine = new NpcEngine(snapshot.maxNpcsPerMap, snapshot.receiptLimit);
    for (var saved : snapshot.maps) {
      requireId(saved.mapId);
      if (engine.maps.containsKey(saved.mapId) || saved.actors.size() > engine.maxNpcsPerMap) {
        throw new IllegalArgumentException("Duplicate map or NPC limit exceeded");
      }
      var map = engine.map(saved.mapId);
      map.enabled = saved.enabled;
      map.paused = saved.paused;
      map.runId = Objects.requireNonNull(saved.runId);
      for (var retired : saved.retiredRuns) {
        requireId(retired);
        if (!map.retiredRuns.add(retired)) {
          throw new IllegalArgumentException("Duplicate retired run");
        }
      }
      for (var removed : saved.removedActorFloors.entrySet()) {
        requireId(removed.getKey());
        if (removed.getValue() < -1) {
          throw new IllegalArgumentException("Invalid removed NPC event floor");
        }
        map.removedActorFloors.put(removed.getKey(), removed.getValue());
      }
      if (map.retiredRuns.contains(map.runId)) {
        throw new IllegalArgumentException("Active run was retired");
      }
      for (var actor : saved.actors) {
        requireId(actor.npcId);
        if (map.removedActorFloors.containsKey(actor.npcId)) {
          throw new IllegalArgumentException("NPC is both assigned and removed");
        }
        if (map.actors.putIfAbsent(actor.npcId, new ActorState(actor)) != null) {
          throw new IllegalArgumentException("Duplicate NPC in snapshot");
        }
      }
    }
    for (var receipt : snapshot.receipts) {
      requireId(receipt.runId());
      requireId(receipt.eventId());
      requireId(receipt.mapId());
      requireId(receipt.npcId());
      requireTick(receipt.logicalTick());
      if (!Double.isFinite(receipt.movementSpent())
          || receipt.movementSpent() < 0
          || receipt.actionsSpent() < 0
          || receipt.behavior() == null
          || receipt.outcome() == null) {
        throw new IllegalArgumentException("Invalid NPC receipt");
      }
      var map = engine.maps.get(receipt.mapId());
      if (map == null
          || !map.runId.equals(receipt.runId())
          || !map.actors.containsKey(receipt.npcId())) {
        throw new IllegalArgumentException("Receipt references an unavailable NPC/run");
      }
      if (receipt.logicalTick() > map.actors.get(receipt.npcId()).lastTick) {
        throw new IllegalArgumentException("Receipt is newer than NPC state");
      }
      var key =
          new ReceiptKey(receipt.runId(), receipt.eventId(), receipt.mapId(), receipt.npcId());
      if (engine.receipts.containsKey(key)) {
        throw new IllegalArgumentException("Duplicate receipt");
      }
      engine.remember(key, receipt);
    }
    return engine;
  }

  private MapState map(String mapId) {
    requireId(mapId);
    return maps.computeIfAbsent(mapId, ignored -> new MapState());
  }

  private ActorState actor(String mapId, String npcId) {
    var state = map(mapId).actors.get(npcId);
    if (state == null) {
      throw new IllegalArgumentException("NPC is not assigned");
    }
    return state;
  }

  private static boolean near(Point a, Point b) {
    return a != null && b != null && a.distance(b) <= EPSILON;
  }

  private static void requireId(String id) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("Identifier must not be blank");
    }
  }

  private static void requireTick(long tick) {
    if (tick < 0) {
      throw new IllegalArgumentException("Logical tick must be nonnegative");
    }
  }

  private record ReceiptKey(String runId, String eventId, String mapId, String npcId) {
    String commandKey(String kind) {
      return encode(runId) + encode(eventId) + encode(mapId) + encode(npcId) + encode(kind);
    }

    private static String encode(String value) {
      return value.length() + ":" + value;
    }
  }

  private record ActorKey(String mapId, String npcId) {}

  private static final class MapState {
    boolean enabled;
    boolean paused;
    String runId = "";
    final Set<String> retiredRuns = new java.util.TreeSet<>();
    final Map<String, ActorState> actors = new TreeMap<>();
    final Map<String, Long> removedActorFloors = new TreeMap<>();
  }

  private static final class ActorState {
    final NpcProfile profile;
    boolean paused;
    boolean manual;
    String targetOverride = "";
    NpcBehavior behavior = NpcBehavior.IDLE;
    Point home;
    int waypointIndex;
    int direction = 1;
    long wakeAt;
    boolean arrived;
    boolean finished;
    String targetId = "";
    Point lastSeen;
    long lastSeenTick = -1;
    long lastTick = -1;
    long retiredThroughTick = -1;

    ActorState(NpcProfile profile) {
      this.profile = Objects.requireNonNull(profile);
    }

    ActorState(ActorSnapshot saved) {
      this(saved.profile);
      if (saved.waypointIndex < 0
          || (!profile.waypoints().isEmpty() && saved.waypointIndex >= profile.waypoints().size())
          || (saved.direction != 1 && saved.direction != -1)
          || saved.wakeAt < 0
          || saved.lastTick < -1
          || saved.retiredThroughTick < -1
          || saved.retiredThroughTick > saved.lastTick
          || saved.lastSeenTick < -1) {
        throw new IllegalArgumentException("Invalid NPC snapshot state");
      }
      paused = saved.paused;
      manual = saved.manual;
      targetOverride = Objects.requireNonNull(saved.targetOverride);
      behavior = Objects.requireNonNull(saved.behavior);
      home = saved.home;
      waypointIndex = saved.waypointIndex;
      direction = saved.direction;
      wakeAt = saved.wakeAt;
      arrived = saved.arrived;
      finished = saved.finished;
      targetId = Objects.requireNonNull(saved.targetId);
      lastSeen = saved.lastSeen;
      lastSeenTick = saved.lastSeenTick;
      lastTick = saved.lastTick;
      retiredThroughTick = saved.retiredThroughTick;
    }

    ActorSnapshot snapshot(String id) {
      return new ActorSnapshot(
          id,
          profile,
          paused,
          manual,
          targetOverride,
          behavior,
          home,
          waypointIndex,
          direction,
          wakeAt,
          arrived,
          finished,
          targetId,
          lastSeen,
          lastSeenTick,
          lastTick,
          retiredThroughTick);
    }
  }

  public record Snapshot(
      int version,
      int maxNpcsPerMap,
      int receiptLimit,
      List<MapSnapshot> maps,
      List<NpcResult> receipts) {
    public Snapshot {
      maps = List.copyOf(maps);
      receipts = List.copyOf(receipts);
    }
  }

  public record MapSnapshot(
      String mapId,
      boolean enabled,
      boolean paused,
      String runId,
      List<String> retiredRuns,
      Map<String, Long> removedActorFloors,
      List<ActorSnapshot> actors) {
    public MapSnapshot {
      retiredRuns = List.copyOf(retiredRuns);
      removedActorFloors = Map.copyOf(Objects.requireNonNullElse(removedActorFloors, Map.of()));
      actors = List.copyOf(actors);
    }
  }

  public record ActorSnapshot(
      String npcId,
      NpcProfile profile,
      boolean paused,
      boolean manual,
      String targetOverride,
      NpcBehavior behavior,
      Point home,
      int waypointIndex,
      int direction,
      long wakeAt,
      boolean arrived,
      boolean finished,
      String targetId,
      Point lastSeen,
      long lastSeenTick,
      long lastTick,
      long retiredThroughTick) {}
}
