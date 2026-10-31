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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.rptools.maptool.automation.npc.NpcProfile.PatrolMode;
import net.rptools.maptool.automation.npc.NpcProfile.Waypoint;
import net.rptools.maptool.automation.npc.NpcResult.Outcome;
import net.rptools.maptool.automation.npc.NpcWorld.ActionOutcome;
import net.rptools.maptool.automation.npc.NpcWorld.ActionRequest;
import net.rptools.maptool.automation.npc.NpcWorld.MoveOutcome;
import net.rptools.maptool.automation.npc.NpcWorld.MovePlan;
import net.rptools.maptool.automation.npc.NpcWorld.Observation;
import net.rptools.maptool.automation.npc.NpcWorld.PlanStatus;
import net.rptools.maptool.automation.npc.NpcWorld.Point;
import net.rptools.maptool.automation.npc.NpcWorld.Token;
import org.junit.jupiter.api.Test;

class NpcEngineScenarioTest {
  private static final String MAP = "encounter";
  private static final Point ORIGIN = new Point(0, 0);

  @Test
  void independentObserversPursueOnlyTheirOwnPerceivedTargetsWithinEachBudget() {
    var engine = enabledEngine();
    var world = new ScenarioWorld();
    world.add("north-guard", ORIGIN, true);
    world.add("south-guard", new Point(100, 0), true);
    world.add("north-target", new Point(10, 0), false);
    world.add("south-target", new Point(90, 0), false);
    world.add("unperceived-nearby-target", new Point(0.5, 0), false);
    world.sees("north-guard", "north-target");
    world.sees("south-guard", "south-target");
    engine.assign(MAP, "north-guard", pursuitProfile());
    engine.assign(MAP, "south-guard", pursuitProfile());

    var results = engine.tick("host", "turn-1", MAP, 1, world);

    assertEquals(2, results.size());
    var north = forNpc(results, "north-guard");
    var south = forNpc(results, "south-guard");
    assertEquals(Outcome.MOVED, north.outcome());
    assertEquals(Outcome.MOVED, south.outcome());
    assertEquals("north-target", north.targetId());
    assertEquals("south-target", south.targetId());
    assertEquals(new Point(2, 0), world.position("north-guard"));
    assertEquals(new Point(98, 0), world.position("south-guard"));
    assertEquals(2, north.movementSpent());
    assertEquals(2, south.movementSpent());
    assertEquals(2, world.movesApplied);
  }

  @Test
  void duplicateEventReturnsReceiptAndNewEventReceivesANewBudget() {
    var engine = enabledEngine();
    var world = patrolWorld();
    engine.assign(MAP, "guard", patrolProfile());

    var first = only(engine.tick("host", "turn-1", MAP, 1, world));
    var duplicate = only(engine.tick("host", "turn-1", MAP, 1, world));

    assertFalse(first.duplicate());
    assertEquals(first.asDuplicate(), duplicate);
    assertEquals(1, world.commitCalls);
    assertEquals(1, world.movesApplied);
    assertEquals(new Point(2, 0), world.position("guard"));

    var next = only(engine.tick("host", "turn-2", MAP, 2, world));
    assertFalse(next.duplicate());
    assertEquals(Outcome.MOVED, next.outcome());
    assertEquals(2, next.movementSpent());
    assertEquals(new Point(4, 0), world.position("guard"));
    assertEquals(2, world.commitCalls);
  }

  @Test
  void blockedPathProducesAReceiptWithoutDispatchingMovement() {
    var engine = enabledEngine();
    var world = patrolWorld();
    world.planStatus = PlanStatus.NO_PATH;
    engine.assign(MAP, "guard", patrolProfile());

    var result = only(engine.tick("host", "blocked-turn", MAP, 1, world));

    assertEquals(Outcome.NO_PATH, result.outcome());
    assertEquals(0, result.movementSpent());
    assertEquals(0, world.commitCalls);
    assertEquals(ORIGIN, world.position("guard"));
  }

  @Test
  void stalePlannerResponseNeverDispatchesMovement() {
    var engine = enabledEngine();
    var world = patrolWorld();
    world.planRevisionDelta = 1;
    engine.assign(MAP, "guard", patrolProfile());

    var result = only(engine.tick("host", "stale-turn", MAP, 1, world));

    assertEquals(Outcome.STALE, result.outcome());
    assertEquals(0, world.commitCalls);
    assertEquals(ORIGIN, world.position("guard"));
  }

  @Test
  void overBudgetPlannerResponseNeverDispatchesMovement() {
    var engine = enabledEngine();
    var world = patrolWorld();
    world.forcedPlanCost = 3.;
    engine.assign(MAP, "guard", patrolProfile());

    var result = only(engine.tick("host", "over-budget-turn", MAP, 1, world));

    assertEquals(Outcome.REJECTED, result.outcome());
    assertEquals(0, result.movementSpent());
    assertEquals(0, world.commitCalls);
    assertEquals(ORIGIN, world.position("guard"));
  }

  @Test
  void mapAndNpcPauseAndManualOverridePreventPlanningUntilReleased() {
    var engine = enabledEngine();
    var world = patrolWorld();
    engine.assign(MAP, "guard", patrolProfile());

    engine.setMapPaused(MAP, true);
    assertEquals(Outcome.PAUSED, only(engine.tick("host", "map-pause", MAP, 1, world)).outcome());
    engine.setMapPaused(MAP, false);
    engine.setNpcPaused(MAP, "guard", true);
    assertEquals(Outcome.PAUSED, only(engine.tick("host", "npc-pause", MAP, 2, world)).outcome());
    engine.setNpcPaused(MAP, "guard", false);
    engine.setManualOverride(MAP, "guard", true);
    assertEquals(Outcome.MANUAL, only(engine.tick("host", "manual", MAP, 3, world)).outcome());
    assertEquals(0, world.planCalls);
    assertEquals(0, world.commitCalls);

    engine.setManualOverride(MAP, "guard", false);
    assertEquals(Outcome.MOVED, only(engine.tick("host", "resumed", MAP, 4, world)).outcome());
    assertEquals(new Point(2, 0), world.position("guard"));
  }

  @Test
  void pausingWhileAPathIsBeingPlannedPreventsItsCommit() {
    var engine = enabledEngine();
    var world = patrolWorld();
    engine.assign(MAP, "guard", patrolProfile());
    world.onPlan = () -> engine.setMapPaused(MAP, true);

    var result = only(engine.tick("host", "pause-during-plan", MAP, 1, world));

    assertEquals(Outcome.PAUSED, result.outcome());
    assertEquals(1, world.planCalls);
    assertEquals(0, world.commitCalls);
    assertEquals(ORIGIN, world.position("guard"));
  }

  @Test
  void gsonSnapshotRestoresReceiptsAndPauseStateWithoutReapplyingEffects() {
    var engine = enabledEngine();
    var world = patrolWorld();
    engine.assign(MAP, "guard", patrolProfile());
    var first = only(engine.tick("host", "turn-1", MAP, 1, world));
    engine.setNpcPaused(MAP, "guard", true);
    var gson = new Gson();
    var serialized = gson.toJson(engine.snapshot());
    var restored = NpcEngine.fromSnapshot(gson.fromJson(serialized, NpcEngine.Snapshot.class));

    var replay = only(restored.tick("host", "turn-1", MAP, 1, world));

    assertEquals(first.asDuplicate(), replay);
    assertEquals(1, world.commitCalls);
    assertEquals(1, world.movesApplied);
    assertEquals(Outcome.PAUSED, only(restored.tick("host", "turn-2", MAP, 2, world)).outcome());
    assertEquals(new Point(2, 0), world.position("guard"));
  }

  @Test
  void authorityRejectionPreventsAnyMovementOrActionDispatch() {
    var engine = enabledEngine();
    var world = patrolWorld();
    world.authority = false;
    engine.assign(MAP, "guard", patrolProfile());

    var result = only(engine.tick("untrusted-client", "turn-1", MAP, 1, world));

    assertEquals(Outcome.REJECTED, result.outcome());
    assertEquals(0, world.planCalls);
    assertEquals(0, world.commitCalls);
    assertEquals(0, world.actionCalls);
    assertEquals(ORIGIN, world.position("guard"));
  }

  @Test
  void approvedActionRecordsItsOutcomeAndDuplicateDeliveryDoesNotRollAgain() {
    var engine = enabledEngine();
    var world = actionWorld();
    engine.registerRule(
        "attack", context -> NpcRule.Decision.action("strike", "target", 1, "in range"));
    engine.assign(MAP, "guard", actionProfile());

    var first = only(engine.tick("host", "attack-turn", MAP, 1, world));
    var duplicate = only(engine.tick("host", "attack-turn", MAP, 1, world));

    assertEquals(Outcome.ACTED, first.outcome());
    assertEquals(1, first.actionsSpent());
    assertEquals(List.of("1d20=12"), first.recordedRolls());
    assertEquals(Map.of("hit", "true"), first.recordedOutcomes());
    assertEquals(first.asDuplicate(), duplicate);
    assertEquals(1, world.actionCalls);
    assertEquals(1, world.actionsApplied);
    assertEquals(0, world.commitCalls);
  }

  @Test
  void ruleCannotDispatchAnActionAgainstAnUnperceivedTarget() {
    var engine = enabledEngine();
    var world = actionWorld();
    world.observations.clear();
    engine.registerRule("attack", context -> NpcRule.Decision.action("strike", "target", 1, "try"));
    engine.assign(MAP, "guard", actionProfile());

    var result = only(engine.tick("host", "hidden-target", MAP, 1, world));

    assertEquals(Outcome.REJECTED, result.outcome());
    assertEquals(0, world.actionCalls);
    assertEquals(0, world.actionsApplied);
  }

  @Test
  void ruleCannotDispatchAnActionThatExceedsItsActionBudget() {
    var engine = enabledEngine();
    var world = actionWorld();
    engine.registerRule("attack", context -> NpcRule.Decision.action("strike", "target", 2, "try"));
    engine.assign(MAP, "guard", actionProfile());

    var result = only(engine.tick("host", "expensive-action", MAP, 1, world));

    assertEquals(Outcome.WAITING, result.outcome());
    assertEquals(0, world.actionCalls);
    assertEquals(0, result.actionsSpent());
  }

  @Test
  void loopingPatrolHonorsLogicalWaypointWaitsAndReturnsToItsFirstWaypoint() {
    var engine = enabledEngine();
    var world = patrolWorld();
    engine.assign(
        MAP,
        "guard",
        NpcProfile.patrol(
            List.of(new Waypoint(new Point(2, 0), 2), new Waypoint(new Point(4, 0), 0)),
            PatrolMode.LOOP,
            2));

    assertEquals(Outcome.MOVED, only(engine.tick("host", "arrival", MAP, 1, world)).outcome());
    assertEquals(new Point(2, 0), world.position("guard"));
    assertEquals(Outcome.WAITING, only(engine.tick("host", "waiting", MAP, 2, world)).outcome());
    assertEquals(1, world.commitCalls);
    assertEquals(Outcome.MOVED, only(engine.tick("host", "departure", MAP, 3, world)).outcome());
    assertEquals(new Point(4, 0), world.position("guard"));
    assertEquals(Outcome.MOVED, only(engine.tick("host", "loop", MAP, 4, world)).outcome());
    assertEquals(new Point(2, 0), world.position("guard"));
  }

  @Test
  void targetLossInvestigatesOnlyTheLastSeenPositionThenReturnsHomeWhenMemoryExpires() {
    var engine = enabledEngine();
    var world = patrolWorld();
    world.add("target", new Point(10, 0), false);
    world.sees("guard", "target");
    engine.assign(
        MAP, "guard", new NpcProfile(List.of(), PatrolMode.ONCE, 2, 1, true, 1, 200, "", 0, ""));

    var pursuing = only(engine.tick("host", "seen", MAP, 1, world));
    assertEquals(NpcBehavior.PURSUE, pursuing.behavior());
    assertEquals(new Point(2, 0), world.position("guard"));
    world.observations.clear();
    world.tokens.put("target", new Token("target", new Point(100, 0), 2, false));

    var investigating = only(engine.tick("host", "recently-lost", MAP, 2, world));
    assertEquals(NpcBehavior.INVESTIGATE, investigating.behavior());
    assertEquals(new Point(4, 0), world.position("guard"));
    var returning = only(engine.tick("host", "memory-expired", MAP, 3, world));
    assertEquals(NpcBehavior.RETURN, returning.behavior());
    assertEquals(new Point(2, 0), world.position("guard"));
    engine.tick("host", "homeward", MAP, 4, world);
    assertEquals(ORIGIN, world.position("guard"));
    var idle = only(engine.tick("host", "home", MAP, 5, world));
    assertEquals(NpcBehavior.IDLE, idle.behavior());
    assertEquals(Outcome.WAITING, idle.outcome());
    assertEquals("", idle.targetId());
  }

  @Test
  void evictedReceiptsRejectTheirExpiredTicksEvenAfterSnapshotRestoration() {
    var engine = new NpcEngine(8, 1);
    engine.setMapEnabled(MAP, true);
    var world = patrolWorld();
    engine.assign(MAP, "guard", patrolProfile());
    engine.tick("host", "evicted-turn", MAP, 1, world);
    engine.tick("host", "retained-turn", MAP, 2, world);
    var gson = new Gson();
    var restored =
        NpcEngine.fromSnapshot(
            gson.fromJson(gson.toJson(engine.snapshot()), NpcEngine.Snapshot.class));

    var expired = only(restored.tick("host", "evicted-turn", MAP, 1, world));

    assertEquals(Outcome.STALE, expired.outcome());
    assertEquals(2, world.commitCalls);
    assertEquals(2, world.movesApplied);
    assertEquals(new Point(4, 0), world.position("guard"));
  }

  @Test
  void removedNpcCannotReplayItsOldEventAfterRestoreAndReassignment() {
    var engine = enabledEngine();
    var world = patrolWorld();
    engine.assign(MAP, "guard", patrolProfile());
    assertEquals(Outcome.MOVED, only(engine.tick("host", "old-turn", MAP, 1, world)).outcome());
    engine.removeNpc(MAP, "guard");
    var snapshot = engine.snapshot();
    assertTrue(snapshot.receipts().isEmpty());
    var gson = new Gson();
    var restored =
        NpcEngine.fromSnapshot(gson.fromJson(gson.toJson(snapshot), NpcEngine.Snapshot.class));
    restored.assign(MAP, "guard", patrolProfile());

    var oldEvent = only(restored.tick("host", "old-turn", MAP, 1, world));

    assertEquals(Outcome.STALE, oldEvent.outcome());
    assertEquals(1, world.commitCalls);
    assertEquals(1, world.movesApplied);
    assertEquals(new Point(2, 0), world.position("guard"));
    var nextEvent = only(restored.tick("host", "new-turn", MAP, 2, world));
    assertEquals(Outcome.MOVED, nextEvent.outcome());
    assertEquals(2, world.commitCalls);
    assertEquals(new Point(4, 0), world.position("guard"));
  }

  @Test
  void replacingProfileDuringPlanningSurvivesReceiptEvictionAndSnapshotRestoration() {
    var engine = new NpcEngine(8, 1);
    engine.setMapEnabled(MAP, true);
    var world = patrolWorld();
    engine.assign(MAP, "guard", patrolProfile());
    var replacement =
        NpcProfile.patrol(List.of(new Waypoint(new Point(8, 0), 0)), PatrolMode.ONCE, 1);
    world.onPlan = () -> engine.assign(MAP, "guard", replacement);

    var replacedEvent = only(engine.tick("host", "replaced-turn", MAP, 1, world));

    assertEquals(Outcome.REJECTED, replacedEvent.outcome());
    assertEquals(0, world.commitCalls);
    assertEquals(ORIGIN, world.position("guard"));
    world.onPlan = () -> {};
    var nextEvent = only(engine.tick("host", "replacement-turn", MAP, 2, world));
    assertEquals(Outcome.MOVED, nextEvent.outcome());
    assertEquals(1, nextEvent.movementSpent());
    assertEquals(new Point(1, 0), world.position("guard"));
    var snapshot = engine.snapshot();
    assertEquals(1, snapshot.receipts().size());
    assertEquals("replacement-turn", snapshot.receipts().getFirst().eventId());
    var gson = new Gson();
    var restored =
        NpcEngine.fromSnapshot(gson.fromJson(gson.toJson(snapshot), NpcEngine.Snapshot.class));

    var replay = only(restored.tick("host", "replaced-turn", MAP, 1, world));

    assertEquals(Outcome.STALE, replay.outcome());
    assertEquals(1, world.commitCalls);
    assertEquals(1, world.movesApplied);
    assertEquals(new Point(1, 0), world.position("guard"));
  }

  @Test
  void anAdapterExceptionAfterApplyingAnEffectIsRecordedAndNeverAutomaticallyRetried() {
    var engine = enabledEngine();
    var world = patrolWorld();
    world.failAfterMove = true;
    engine.assign(MAP, "guard", patrolProfile());

    var uncertain = only(engine.tick("host", "uncertain-turn", MAP, 1, world));
    var duplicate = only(engine.tick("host", "uncertain-turn", MAP, 1, world));

    assertEquals(Outcome.ERROR, uncertain.outcome());
    assertEquals(uncertain.asDuplicate(), duplicate);
    assertEquals(1, world.commitCalls);
    assertEquals(1, world.movesApplied);
    assertEquals(new Point(2, 0), world.position("guard"));

    var nextEvent = only(engine.tick("host", "next-clock-event", MAP, 2, world));
    assertEquals(Outcome.PAUSED, nextEvent.outcome());
    assertEquals(1, world.commitCalls);
    assertEquals(1, world.movesApplied);
    var gson = new Gson();
    var restored =
        NpcEngine.fromSnapshot(
            gson.fromJson(gson.toJson(engine.snapshot()), NpcEngine.Snapshot.class));
    var afterRestore = only(restored.tick("host", "restored-clock-event", MAP, 3, world));
    assertEquals(Outcome.PAUSED, afterRestore.outcome());
    assertEquals(1, world.commitCalls);
    assertEquals(1, world.movesApplied);
    assertEquals(new Point(2, 0), world.position("guard"));
  }

  @Test
  void reentrantCommitCallbackCannotRedispatchOrReplaceTheCanonicalReceipt() {
    var engine = enabledEngine();
    var world = patrolWorld();
    engine.assign(MAP, "guard", patrolProfile());
    var callbackResults = new AtomicReference<List<NpcResult>>();
    world.onCommit =
        () -> callbackResults.set(engine.tick("host", "reentrant-turn", MAP, 1, world));

    var first = only(engine.tick("host", "reentrant-turn", MAP, 1, world));

    assertEquals(Outcome.MOVED, first.outcome());
    assertEquals(Outcome.WAITING, only(callbackResults.get()).outcome());
    assertEquals(1, world.commitCalls);
    assertEquals(1, world.movesApplied);
    assertEquals(new Point(2, 0), world.position("guard"));
    assertEquals(first.asDuplicate(), only(engine.tick("host", "reentrant-turn", MAP, 1, world)));
  }

  @Test
  void authorityLostWhilePlanningPreventsThePendingMovementFromBeingDispatched() {
    var engine = enabledEngine();
    var world = patrolWorld();
    engine.assign(MAP, "guard", patrolProfile());
    world.onPlan = () -> world.authority = false;

    var result = only(engine.tick("host", "authority-lost", MAP, 1, world));

    assertEquals(Outcome.REJECTED, result.outcome());
    assertEquals(0, world.commitCalls);
    assertEquals(ORIGIN, world.position("guard"));
  }

  @Test
  void actorRevisionChangedWhilePlanningPreventsThePendingMovementFromBeingDispatched() {
    var engine = enabledEngine();
    var world = patrolWorld();
    engine.assign(MAP, "guard", patrolProfile());
    world.onPlan = () -> world.tokens.put("guard", new Token("guard", ORIGIN, 2, true));

    var result = only(engine.tick("host", "actor-changed", MAP, 1, world));

    assertEquals(Outcome.STALE, result.outcome());
    assertEquals(0, world.commitCalls);
    assertEquals(ORIGIN, world.position("guard"));
  }

  @Test
  void previewUsesObserverContextWithoutChangingPersistentStateOrDispatchingEffects() {
    var engine = enabledEngine();
    var world = actionWorld();
    var observedTargets = new ArrayList<String>();
    engine.registerRule(
        "attack",
        context -> {
          assertEquals("guard", context.npcId());
          assertEquals(MAP, context.mapId());
          context.observations().forEach(observation -> observedTargets.add(observation.tokenId()));
          return NpcRule.Decision.waitFor("inspect without acting");
        });
    engine.assign(MAP, "guard", actionProfile());
    var gson = new Gson();
    var before = gson.toJson(engine.snapshot());

    var decision = engine.preview(MAP, "guard", 1, world);

    assertEquals(NpcRule.Kind.WAIT, decision.kind());
    assertEquals(List.of("target"), observedTargets);
    assertEquals(before, gson.toJson(engine.snapshot()));
    assertEquals(0, world.commitCalls);
    assertEquals(0, world.actionCalls);
  }

  private static NpcEngine enabledEngine() {
    var engine = new NpcEngine();
    engine.setMapEnabled(MAP, true);
    return engine;
  }

  private static NpcProfile patrolProfile() {
    return NpcProfile.patrol(List.of(new Waypoint(new Point(10, 0), 0)), PatrolMode.ONCE, 2);
  }

  private static NpcProfile pursuitProfile() {
    return new NpcProfile(List.of(), PatrolMode.ONCE, 2, 1, true, 3, 200, "", 0, "");
  }

  private static NpcProfile actionProfile() {
    return new NpcProfile(List.of(), PatrolMode.ONCE, 2, 1, true, 3, 200, "strike", 2, "attack");
  }

  private static ScenarioWorld patrolWorld() {
    var world = new ScenarioWorld();
    world.add("guard", ORIGIN, true);
    return world;
  }

  private static ScenarioWorld actionWorld() {
    var world = patrolWorld();
    world.add("target", new Point(1, 0), false);
    world.sees("guard", "target");
    return world;
  }

  private static NpcResult only(List<NpcResult> results) {
    assertEquals(1, results.size());
    return results.getFirst();
  }

  private static NpcResult forNpc(List<NpcResult> results, String npcId) {
    return results.stream()
        .filter(result -> result.npcId().equals(npcId))
        .findFirst()
        .orElseThrow();
  }

  /** An independent adapter models revision checks and partial movement, without GUI state. */
  private static final class ScenarioWorld implements NpcWorld {
    private final Map<String, Token> tokens = new HashMap<>();
    private final Map<String, List<Observation>> observations = new HashMap<>();
    private final Map<String, MoveOutcome> moveReceipts = new HashMap<>();
    private final Map<String, ActionOutcome> actionReceipts = new HashMap<>();
    private boolean authority = true;
    private PlanStatus planStatus = PlanStatus.READY;
    private long planRevisionDelta;
    private Double forcedPlanCost;
    private boolean failAfterMove;
    private Runnable onPlan = () -> {};
    private Runnable onCommit = () -> {};
    private int planCalls;
    private int commitCalls;
    private int movesApplied;
    private int actionCalls;
    private int actionsApplied;

    void add(String id, Point position, boolean npc) {
      tokens.put(id, new Token(id, position, 1, npc));
    }

    void sees(String npcId, String targetId) {
      var target = tokens.get(targetId);
      observations.put(
          npcId, List.of(new Observation(targetId, target.position(), target.revision(), true)));
    }

    Point position(String npcId) {
      return tokens.get(npcId).position();
    }

    @Override
    public boolean hasAuthority(String runId, String mapId) {
      return authority && MAP.equals(mapId);
    }

    @Override
    public Optional<Token> token(String mapId, String tokenId) {
      return MAP.equals(mapId) ? Optional.ofNullable(tokens.get(tokenId)) : Optional.empty();
    }

    @Override
    public List<Observation> perceive(String mapId, String npcId) {
      assertEquals(MAP, mapId);
      return observations.getOrDefault(npcId, List.of());
    }

    @Override
    public MovePlan planMove(String mapId, String npcId, Point destination, double budget) {
      assertEquals(MAP, mapId);
      planCalls++;
      if (planStatus != PlanStatus.READY) {
        return MovePlan.blocked(planStatus, "scenario obstacle or stale topology");
      }
      var actor = tokens.get(npcId);
      double distance = actor.position().distance(destination);
      double travel = Math.min(distance, budget);
      double fraction = distance == 0 ? 0 : travel / distance;
      var endpoint =
          new Point(
              actor.position().x() + (destination.x() - actor.position().x()) * fraction,
              actor.position().y() + (destination.y() - actor.position().y()) * fraction);
      var plan =
          new MovePlan(
              PlanStatus.READY,
              actor.revision() + planRevisionDelta,
              actor.position(),
              List.of(actor.position(), endpoint),
              forcedPlanCost == null ? travel : forcedPlanCost,
              "world-1",
              "partial path constrained by adapter");
      onPlan.run();
      return plan;
    }

    @Override
    public MoveOutcome commitMove(String mapId, String npcId, MovePlan plan, String commandKey) {
      assertEquals(MAP, mapId);
      assertFalse(commandKey.isBlank());
      commitCalls++;
      if (moveReceipts.containsKey(commandKey)) {
        return moveReceipts.get(commandKey);
      }
      var actor = tokens.get(npcId);
      if (actor.revision() != plan.tokenRevision()
          || !actor.position().equals(plan.origin())
          || !"world-1".equals(plan.worldRevision())) {
        return new MoveOutcome(false, true, actor.position(), "revision changed");
      }
      var endpoint = plan.path().getLast();
      tokens.put(npcId, new Token(npcId, endpoint, actor.revision() + 1, actor.npc()));
      movesApplied++;
      var outcome = new MoveOutcome(true, false, endpoint, "committed");
      moveReceipts.put(commandKey, outcome);
      if (failAfterMove) {
        throw new IllegalStateException("effect applied before adapter completion failed");
      }
      var callback = onCommit;
      onCommit = () -> {};
      callback.run();
      return outcome;
    }

    @Override
    public ActionOutcome executeApprovedAction(
        String mapId, String npcId, ActionRequest request, String commandKey) {
      assertEquals(MAP, mapId);
      assertTrue(!commandKey.isBlank());
      actionCalls++;
      if (actionReceipts.containsKey(commandKey)) {
        return actionReceipts.get(commandKey);
      }
      var actor = tokens.get(npcId);
      var target = tokens.get(request.targetId());
      if (!"strike".equals(request.actionId())
          || actor.revision() != request.actorRevision()
          || target == null
          || target.revision() != request.targetRevision()) {
        return new ActionOutcome(false, 0, List.of(), Map.of(), "action not approved or stale");
      }
      actionsApplied++;
      var outcome =
          new ActionOutcome(
              true,
              request.actionCost(),
              List.of("1d20=12"),
              Map.of("hit", "true"),
              "resolved once");
      actionReceipts.put(commandKey, outcome);
      return outcome;
    }
  }
}
