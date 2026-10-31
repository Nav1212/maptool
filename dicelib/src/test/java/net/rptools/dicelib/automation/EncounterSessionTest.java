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
package net.rptools.dicelib.automation;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.rptools.maptool.automation.npc.NpcProfile;
import net.rptools.maptool.automation.npc.NpcWorld;
import net.rptools.maptool.automation.runtime.EncounterSession;
import net.rptools.maptool.automation.runtime.MapRuntime;
import net.rptools.maptool.automation.tile.TileDefinition;
import net.rptools.maptool.automation.tile.TileEngine;
import net.rptools.maptool.automation.tile.TileRegion;
import org.junit.jupiter.api.Test;

class EncounterSessionTest {
  private static final Gson GSON = new Gson();

  private EncounterSession session(Fixture world) {
    var session = new EncounterSession("map", "run", "gm", world);
    bind(session, world);
    session.tiles().putRegion(TileRegion.rectangle("trap-area", "Trap", "map", 1, 1, 2, 2));
    session
        .tiles()
        .putTile(
            new TileDefinition(
                "trap",
                "Trap",
                "map",
                "trap-area",
                true,
                Set.of(TileDefinition.Trigger.ENTER),
                new TileDefinition.Check(
                    "dice",
                    "awareness",
                    Map.of("expression", "2d1", "property", "Awareness", "target", "5")),
                TileDefinition.Policy.always(),
                Map.of(
                    "FAILURE", List.of(new TileDefinition.Action("damage", Map.of("amount", "2")))),
                List.of()));
    session
        .npcs()
        .assign(
            "map",
            "guard",
            NpcProfile.patrol(
                List.of(
                    new NpcProfile.Waypoint(new NpcWorld.Point(4, 0), 0),
                    new NpcProfile.Waypoint(new NpcWorld.Point(0, 0), 0)),
                NpcProfile.PatrolMode.LOOP,
                1));
    session.npcs().setMapEnabled("map", true);
    return session;
  }

  private void bind(EncounterSession session, Fixture world) {
    var dice = new DiceCheckAdapter();
    session
        .tiles()
        .registerRollAdapter(
            "dice",
            request -> {
              world.rolls++;
              return dice.roll(request);
            });
    session
        .tiles()
        .registerActionHandler(
            "damage",
            request -> {
              if (world.applied.add(request.stepKey()))
                world.hp -= Integer.parseInt(request.action().parameters().get("amount"));
            });
  }

  @Test
  void tileRollAndNpcPatrolConsumeTheSameClockWithoutDuplicateEffects() {
    var world = new Fixture();
    var session = session(world);
    assertTrue(
        session.acceptedMovement(
            "gm",
            "move-player",
            "player",
            new TileRegion.Point(0, 2),
            new TileRegion.Point(2, 2),
            "turn1"));
    assertFalse(
        session.acceptedMovement(
            "gm",
            "move-player",
            "player",
            new TileRegion.Point(0, 2),
            new TileRegion.Point(2, 2),
            "turn1"));
    session.scheduleNpcDecision("gm", "guard-turn1", 1);
    session.resume("gm");
    var results = session.advanceTo("gm", 1);
    assertEquals(2, results.size());
    assertTrue(results.stream().allMatch(r -> r.state() == MapRuntime.State.COMPLETED));
    assertEquals(1, world.rolls);
    assertEquals(8, world.hp);
    assertEquals(1, world.tokens.get("guard").position().x());
    assertFalse(session.scheduleNpcDecision("gm", "guard-turn1", 1));
    session.scheduleNpcDecision("gm", "guard-turn2", 2);
    session.advanceTo("gm", 2);
    assertEquals(2, world.tokens.get("guard").position().x());
    assertEquals(1, world.rolls);
  }

  @Test
  void checkpointRestoresWorldAndQueuedWorkPausedWithoutRerollingCompletedChecks() {
    var world = new Fixture();
    var session = session(world);
    session.acceptedMovement(
        "gm", "move1", "player", new TileRegion.Point(0, 2), new TileRegion.Point(2, 2), "turn1");
    session.step("gm");
    session.scheduleNpcDecision("gm", "guard-turn", 3);
    String json = session.toJson();
    world.hp = 1;
    var restored = EncounterSession.fromJson(json, "map", "gm", world);
    bind(restored, world);
    assertEquals(8, world.hp);
    assertTrue(restored.clock().paused());
    assertFalse(
        restored.acceptedMovement(
            "gm",
            "move1",
            "player",
            new TileRegion.Point(0, 2),
            new TileRegion.Point(2, 2),
            "turn1"));
    restored.step("gm");
    assertEquals(1, world.rolls);
    assertEquals(1, world.tokens.get("guard").position().x());
  }

  @Test
  void actorPropertiesAreCapturedAtTheAcceptedOccurrence() {
    var world = new Fixture();
    var session = session(world);
    session.acceptedMovement(
        "gm", "move1", "player", new TileRegion.Point(0, 2), new TileRegion.Point(2, 2), "turn1");
    world.awareness = "99";
    session.step("gm");
    var receipt = session.tiles().receipts().values().iterator().next();
    assertEquals("FAILURE", receipt.result().outcome());
    assertEquals(2.0, receipt.result().total());
  }

  @Test
  void checkErrorsPauseTheSharedRuntimeInsteadOfApplyingAFailureBranch() {
    var world = new Fixture();
    world.awareness = null;
    var session = session(world);
    session.acceptedMovement(
        "gm", "move1", "player", new TileRegion.Point(0, 2), new TileRegion.Point(2, 2), "turn1");
    session.scheduleNpcDecision("gm", "guard-turn", 1);
    session.resume("gm");
    session.advanceTo("gm", 1);
    assertTrue(session.clock().paused());
    assertEquals(10, world.hp);
    assertEquals(0, world.tokens.get("guard").position().x());
    assertEquals(1, session.clock().pending().size());
  }

  @Test
  void unavailableAuthorityOrInvalidSnapshotCannotRestoreOrExecuteWorldEffects() {
    var world = new Fixture();
    var session = session(world);
    String saved = session.toJson();
    assertThrows(
        SecurityException.class, () -> EncounterSession.fromJson(saved, "map", "player", world));
    assertThrows(
        IllegalArgumentException.class,
        () -> EncounterSession.fromJson(saved, "another-map", "gm", world));
    assertEquals(0, world.restores);
    world.authority = false;
    assertThrows(SecurityException.class, () -> session.resume("gm"));
    assertThrows(
        SecurityException.class, () -> EncounterSession.fromJson(saved, "map", "gm", world));
    assertEquals(0, world.restores);
  }

  @Test
  void synchronousNpcMovementReportsPersistOnlyAfterNpcStateIsConsistent() {
    var world = new Fixture();
    var session = session(world);
    var saved = new ArrayList<EncounterSession.Snapshot>();
    session.persistWith("gm", saved::add);
    world.onMove =
        (actor, from, to) ->
            session.acceptedMovement(
                "gm",
                "guard-segment",
                actor,
                new TileRegion.Point(from.x(), from.y()),
                new TileRegion.Point(to.x(), to.y()),
                "turn1");
    session.scheduleNpcDecision("gm", "guard-turn", 1);
    session.resume("gm");
    var results = session.advanceTo("gm", 1);
    assertEquals(2, results.size());
    assertTrue(results.stream().allMatch(r -> r.state() == MapRuntime.State.COMPLETED));
    assertEquals(1, world.tokens.get("guard").position().x());
    assertFalse(saved.isEmpty());
    var restored = EncounterSession.fromJson(session.toJson(), "map", "gm", world);
    assertTrue(restored.clock().pending().isEmpty());
    assertFalse(
        restored.acceptedMovement(
            "gm",
            "guard-segment",
            "guard",
            new TileRegion.Point(0, 0),
            new TileRegion.Point(1, 0),
            "turn1"));
  }

  @Test
  void fullQueueRetainsEveryCommittedNpcSegmentInTheDurableOutbox() {
    var world = new Fixture();
    var session = session(world);
    world.tokens.put(
        "other-guard", new NpcWorld.Token("other-guard", new NpcWorld.Point(0, 0), 1, true));
    session
        .npcs()
        .assign(
            "map",
            "other-guard",
            NpcProfile.patrol(
                List.of(new NpcProfile.Waypoint(new NpcWorld.Point(4, 0), 0)),
                NpcProfile.PatrolMode.LOOP,
                1));
    world.onMove =
        (actor, from, to) ->
            session.acceptedMovement(
                "gm",
                "segment-" + actor,
                actor,
                new TileRegion.Point(from.x(), from.y()),
                new TileRegion.Point(to.x(), to.y()),
                "turn1");
    for (int i = 0; i < 999; i++) session.scheduleNpcDecision("gm", "future-" + i, 1000);
    session.scheduleNpcDecision("gm", "two-moves", 1);
    var durable = new ArrayList<EncounterSession.Snapshot>();
    session.persistWith("gm", durable::add);
    session.resume("gm");
    session.advanceTo("gm", 1);
    assertTrue(session.clock().paused());
    assertEquals(999, session.clock().pending().size());
    assertEquals(2, session.snapshot().acceptedMovementOutbox().size());
    assertEquals(1, world.tokens.get("guard").position().x());
    assertEquals(1, world.tokens.get("other-guard").position().x());
    var restored = EncounterSession.fromJson(GSON.toJson(durable.getLast()), "map", "gm", world);
    assertEquals(2, restored.snapshot().acceptedMovementOutbox().size());
    assertTrue(restored.cancelQueued("gm", "future-0"));
    restored.retryDelivery("gm");
    assertTrue(restored.snapshot().acceptedMovementOutbox().isEmpty());
    assertEquals(1000, restored.clock().pending().size());
    assertTrue(
        restored.clock().pending().stream().anyMatch(p -> p.event().id().equals("segment-guard")));
    assertTrue(
        restored.clock().pending().stream()
            .anyMatch(p -> p.event().id().equals("segment-other-guard")));
  }

  @Test
  void checkpointDuringBatchEnqueuePreservesAllAcceptedSegmentsWithoutReapplyingMoves() {
    var world = new Fixture();
    var session = session(world);
    world.tokens.put(
        "other-guard", new NpcWorld.Token("other-guard", new NpcWorld.Point(0, 0), 1, true));
    session
        .npcs()
        .assign(
            "map",
            "other-guard",
            NpcProfile.patrol(
                List.of(new NpcProfile.Waypoint(new NpcWorld.Point(4, 0), 0)),
                NpcProfile.PatrolMode.LOOP,
                1));
    world.onMove =
        (actor, from, to) ->
            session.acceptedMovement(
                "gm",
                "segment-" + actor,
                actor,
                new TileRegion.Point(from.x(), from.y()),
                new TileRegion.Point(to.x(), to.y()),
                "turn1");
    var durable = new ArrayList<EncounterSession.Snapshot>();
    session.persistWith("gm", durable::add);
    session.scheduleNpcDecision("gm", "two-moves", 1);
    session.resume("gm");
    session.advanceTo("gm", 1);
    var checkpoint =
        durable.stream()
            .filter(s -> s.acceptedMovementOutbox().size() == 2)
            .findFirst()
            .orElseThrow();
    var restored = EncounterSession.fromJson(GSON.toJson(checkpoint), "map", "gm", world);
    restored.retryDelivery("gm");
    assertEquals(2, restored.clock().pending().size());
    assertTrue(restored.snapshot().acceptedMovementOutbox().isEmpty());
    assertEquals(1, world.tokens.get("guard").position().x());
    assertEquals(1, world.tokens.get("other-guard").position().x());
  }

  @Test
  void referenceAdapterSupportsPlainRollsAlternateComparisonsAndPreRollValidation()
      throws Exception {
    var adapter = new DiceCheckAdapter();
    var context =
        new TileEngine.Context(
            "run",
            "event",
            "map",
            "tile",
            "player",
            TileDefinition.Trigger.ENTER,
            0,
            "turn",
            Map.of("Awareness", "2"),
            "key");
    var result =
        adapter.roll(
            new TileEngine.CheckRequest(
                context, new TileDefinition.Check("dice", "plain", Map.of("expression", "3d1"))));
    assertEquals("ROLLED", result.outcome());
    assertEquals(List.of(1.0, 1.0, 1.0), result.dice());
    var check =
        adapter.roll(
            new TileEngine.CheckRequest(
                context,
                new TileDefinition.Check(
                    "dice",
                    "small",
                    Map.of(
                        "expression",
                        "1d1",
                        "property",
                        "Awareness",
                        "target",
                        "3",
                        "comparison",
                        "eq"))));
    assertEquals("SUCCESS", check.outcome());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            adapter.roll(
                new TileEngine.CheckRequest(
                    context,
                    new TileDefinition.Check(
                        "dice", "too-many", Map.of("expression", "1000000000d6")))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            adapter.roll(
                new TileEngine.CheckRequest(
                    context,
                    new TileDefinition.Check(
                        "dice", "unknown", Map.of("expression", "1d6", "property", "Missing")))));
    for (String field : List.of("modifier", "target")) {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              adapter.roll(
                  new TileEngine.CheckRequest(
                      context,
                      new TileDefinition.Check(
                          "dice",
                          "overscaled",
                          Map.of("expression", "1d6", field, "1e1000000000")))));
    }
    var badPropertyContext =
        new TileEngine.Context(
            "run",
            "event",
            "map",
            "tile",
            "player",
            TileDefinition.Trigger.ENTER,
            0,
            "turn",
            Map.of("Awareness", "1e-1000000000"),
            "key");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            adapter.roll(
                new TileEngine.CheckRequest(
                    badPropertyContext,
                    new TileDefinition.Check(
                        "dice",
                        "overscaled",
                        Map.of("expression", "1d6", "property", "Awareness")))));
  }

  private static final class Fixture implements EncounterSession.World {
    final Map<String, Token> tokens = new LinkedHashMap<>();
    final Set<String> applied = new java.util.HashSet<>();
    int hp = 10;
    int rolls;
    int restores;
    String awareness = "0";
    boolean authority = true;

    @FunctionalInterface
    interface MoveNotice {
      void accept(String actor, Point from, Point to);
    }

    MoveNotice onMove = (actor, from, to) -> {};

    Fixture() {
      tokens.put("player", new Token("player", new Point(2, 2), 1, false));
      tokens.put("guard", new Token("guard", new Point(0, 0), 1, true));
    }

    @Override
    public boolean hasAuthority(String runId, String mapId) {
      return authority && runId.equals("run") && mapId.equals("map");
    }

    @Override
    public Optional<Token> token(String mapId, String id) {
      return mapId.equals("map") ? Optional.ofNullable(tokens.get(id)) : Optional.empty();
    }

    @Override
    public List<Observation> perceive(String mapId, String npcId) {
      return List.of();
    }

    @Override
    public Map<String, String> properties(String mapId, String actorId) {
      return awareness == null ? Map.of() : Map.of("Awareness", awareness);
    }

    @Override
    public MovePlan planMove(String mapId, String npcId, Point destination, double budget) {
      var actor = tokens.get(npcId);
      double distance = actor.position().distance(destination);
      double step = Math.min(distance, budget);
      Point endpoint =
          distance == 0
              ? destination
              : new Point(
                  actor.position().x() + (destination.x() - actor.position().x()) * step / distance,
                  actor.position().y()
                      + (destination.y() - actor.position().y()) * step / distance);
      return MovePlan.ready(actor, List.of(actor.position(), endpoint), step, "world1");
    }

    @Override
    public MoveOutcome commitMove(String mapId, String npcId, MovePlan plan, String key) {
      var actor = tokens.get(npcId);
      if (actor.revision() != plan.tokenRevision())
        return new MoveOutcome(false, true, null, "stale");
      if (!applied.add(key)) return new MoveOutcome(false, false, actor.position(), "duplicate");
      var end = plan.path().getLast();
      tokens.put(npcId, new Token(npcId, end, actor.revision() + 1, true));
      onMove.accept(npcId, actor.position(), end);
      return new MoveOutcome(true, false, end, "moved");
    }

    record Saved(int hp, List<Token> tokens, Set<String> applied) {}

    @Override
    public String saveMapState(String mapId) {
      return GSON.toJson(new Saved(hp, List.copyOf(tokens.values()), Set.copyOf(applied)));
    }

    @Override
    public void restoreMapState(String mapId, String json) {
      var saved = GSON.fromJson(json, Saved.class);
      if (saved == null || saved.tokens() == null)
        throw new IllegalArgumentException("Invalid world state");
      restores++;
      hp = saved.hp();
      tokens.clear();
      saved.tokens().forEach(t -> tokens.put(t.id(), t));
      applied.clear();
      applied.addAll(saved.applied());
    }
  }
}
