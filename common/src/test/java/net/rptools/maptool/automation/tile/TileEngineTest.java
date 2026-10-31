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

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.rptools.maptool.automation.tile.TileDefinition.Action;
import net.rptools.maptool.automation.tile.TileDefinition.Check;
import net.rptools.maptool.automation.tile.TileDefinition.Policy;
import net.rptools.maptool.automation.tile.TileDefinition.Repeat;
import net.rptools.maptool.automation.tile.TileDefinition.Scope;
import net.rptools.maptool.automation.tile.TileDefinition.Trigger;
import net.rptools.maptool.automation.tile.TileEngine.Status;
import net.rptools.maptool.automation.tile.TileRegion.Point;
import org.junit.jupiter.api.Test;

class TileEngineTest {
  private static final Point OUTSIDE = new Point(-5, 5);
  private static final Point INSIDE = new Point(5, 5);
  private static final Map<String, String> PROPS = Map.of("skill", "3");

  private TileEngine engine(AtomicInteger rolls) {
    var engine = new TileEngine("run-1");
    engine.putRegion(TileRegion.rectangle("region", "Plate", "map", 0, 0, 10, 10));
    engine.registerRollAdapter(
        "rules",
        request -> {
          rolls.incrementAndGet();
          return new TileEngine.CheckResult(
              "SUCCESS",
              "check",
              9.0,
              List.of(2.0, 4.0),
              Map.of("skill", request.context().actorProperties().get("skill")));
        });
    return engine;
  }

  private TileDefinition tile(String id, Set<Trigger> triggers, Policy policy) {
    return new TileDefinition(
        id,
        "Trap",
        "map",
        "region",
        true,
        triggers,
        new Check("rules", "agility", Map.of("dice", "2d6")),
        policy,
        Map.of(),
        List.of());
  }

  private TileEngine.ActivationReceipt move(
      TileEngine engine, String event, String actor, Point from, Point to, long tick, String turn) {
    return engine.onMove(event, "map", actor, from, to, tick, turn, PROPS).getFirst();
  }

  private TileEngine.ActivationReceipt pulse(
      TileEngine engine, String event, String actor, long tick, String turn) {
    return engine.pulse(event, "map", actor, INSIDE, tick, turn, PROPS).getFirst();
  }

  @Test
  void passThroughRollsOnceEvenWhenSeveralTriggersMatchAndDuplicateIsRedelivered() {
    var rolls = new AtomicInteger();
    var engine = engine(rolls);
    engine.putTile(
        tile("trap", Set.of(Trigger.ENTER, Trigger.EXIT, Trigger.CROSS), Policy.always()));
    var result = move(engine, "move-1", "actor", OUTSIDE, new Point(15, 5), 4, "turn");
    var duplicate = move(engine, "move-1", "actor", OUTSIDE, new Point(15, 5), 99, "other-turn");
    assertAll(
        () -> assertEquals(Status.COMPLETED, result.status()),
        () -> assertEquals(result, duplicate),
        () -> assertEquals(1, rolls.get()),
        () -> assertEquals(Trigger.ENTER, result.context().trigger()),
        () -> assertEquals(4, result.context().tick()));
  }

  @Test
  void boundaryAndUnrelatedMapDoNotActivateAndExitCanActivate() {
    var rolls = new AtomicInteger();
    var engine = engine(rolls);
    engine.putTile(tile("exit", Set.of(Trigger.EXIT), Policy.always()));
    assertTrue(
        engine
            .onMove("tangent", "map", "actor", new Point(-1, 0), new Point(11, 0), 1, "t", PROPS)
            .isEmpty());
    assertTrue(
        engine.onMove("other-map", "elsewhere", "actor", INSIDE, OUTSIDE, 1, "t", PROPS).isEmpty());
    assertEquals(
        Trigger.EXIT, move(engine, "exit", "actor", INSIDE, OUTSIDE, 2, "t").context().trigger());
    assertEquals(1, rolls.get());
  }

  @Test
  void evaluatesSelectedOutcomeThenAlwaysActionsInOrderWithStableStepKeys() {
    var engine = engine(new AtomicInteger());
    engine.registerRollAdapter("rules", request -> TileEngine.CheckResult.outcome("FAILURE"));
    var calls = new ArrayList<String>();
    var stepKeys = new ArrayList<String>();
    engine.registerActionHandler(
        "apply",
        request -> {
          calls.add(request.action().parameters().get("name"));
          stepKeys.add(request.stepKey());
          assertEquals("actor", request.context().actorId());
          assertEquals("FAILURE", request.result().outcome());
        });
    engine.putTile(
        new TileDefinition(
            "trap",
            "Trap",
            "map",
            "region",
            true,
            Set.of(Trigger.ENTER),
            new Check("rules", "skill", Map.of()),
            Policy.always(),
            Map.of(
                "SUCCESS",
                List.of(new Action("apply", Map.of("name", "success"))),
                "FAILURE",
                List.of(new Action("apply", Map.of("name", "failure")))),
            List.of(new Action("apply", Map.of("name", "always")))));
    var receipt = move(engine, "e", "actor", OUTSIDE, INSIDE, 1, "t");
    assertEquals(List.of("failure", "always"), calls);
    assertEquals(2, Set.copyOf(stepKeys).size());
    assertTrue(receipt.steps().stream().allMatch(step -> step.status() == Status.COMPLETED));
  }

  @Test
  void oncePolicyIsPerActorOrGlobalAndCompletedFailureConsumesIt() {
    var rolls = new AtomicInteger();
    var engine = engine(rolls);
    engine.registerRollAdapter(
        "rules",
        request -> {
          rolls.incrementAndGet();
          return TileEngine.CheckResult.outcome("FAILURE");
        });
    engine.putTile(tile("trap", Set.of(Trigger.PULSE), new Policy(Repeat.ONCE, Scope.ACTOR, 0)));
    assertEquals(Status.COMPLETED, pulse(engine, "a", "one", 1, "t").status());
    assertEquals(Status.SKIPPED, pulse(engine, "b", "one", 2, "t").status());
    assertEquals(Status.COMPLETED, pulse(engine, "c", "two", 2, "t").status());
    assertEquals(2, rolls.get());
    var global = engine(new AtomicInteger());
    global.putTile(tile("trap", Set.of(Trigger.PULSE), new Policy(Repeat.ONCE, Scope.TILE, 0)));
    pulse(global, "a", "one", 1, "t");
    assertEquals(Status.SKIPPED, pulse(global, "b", "two", 2, "t").status());
  }

  @Test
  void oncePerEntryDoesNotResetForDuplicateMovesOrRepeatedPulses() {
    var rolls = new AtomicInteger();
    var engine = engine(rolls);
    engine.putTile(
        tile(
            "trap",
            Set.of(Trigger.ENTER, Trigger.PULSE),
            new Policy(Repeat.ONCE_PER_ENTRY, Scope.ACTOR, 0)));
    move(engine, "enter", "actor", OUTSIDE, INSIDE, 1, "t");
    assertEquals(Status.SKIPPED, pulse(engine, "pulse", "actor", 2, "t").status());
    move(engine, "enter", "actor", OUTSIDE, INSIDE, 3, "t");
    assertEquals(Status.SKIPPED, pulse(engine, "pulse-2", "actor", 3, "t").status());
    engine.onMove("leave", "map", "actor", INSIDE, OUTSIDE, 4, "t", PROPS);
    assertEquals(
        Status.COMPLETED, move(engine, "reenter", "actor", OUTSIDE, INSIDE, 5, "t").status());
    assertEquals(2, rolls.get());
  }

  @Test
  void turnIdentityAndCooldownUseOnlyCallerClockAndCanBeReset() {
    var rolls = new AtomicInteger();
    var engine = engine(rolls);
    engine.putTile(
        tile("trap", Set.of(Trigger.PULSE), new Policy(Repeat.ONCE_PER_TURN, Scope.ACTOR, 0)));
    assertEquals(Status.FAILED, pulse(engine, "no-turn", "actor", 0, null).status());
    assertEquals(Status.COMPLETED, pulse(engine, "first", "actor", 0, "turn-1").status());
    assertEquals(Status.SKIPPED, pulse(engine, "same-turn", "actor", 99, "turn-1").status());
    assertEquals(Status.COMPLETED, pulse(engine, "next-turn", "actor", 99, "turn-2").status());
    engine.putTile(
        tile("cool", Set.of(Trigger.INTERACT), new Policy(Repeat.COOLDOWN, Scope.ACTOR, 10)));
    var first = engine.interact("click1", "map", "cool", "actor", 20, "turn", PROPS).getFirst();
    assertEquals(Status.COMPLETED, first.status());
    assertEquals(
        Status.SKIPPED,
        engine.interact("click2", "map", "cool", "actor", 29, "turn", PROPS).getFirst().status());
    assertEquals(
        Status.COMPLETED,
        engine.interact("click3", "map", "cool", "actor", 30, "turn", PROPS).getFirst().status());
    engine.resetTileState("cool", "actor");
    assertEquals(
        Status.COMPLETED,
        engine.interact("click4", "map", "cool", "actor", 31, "turn", PROPS).getFirst().status());
    assertEquals(
        first, engine.interact("click1", "map", "cool", "actor", 500, "turn", PROPS).getFirst());
  }

  @Test
  void disabledAndInvalidDefinitionsCannotInvokeHandlers() {
    var rolls = new AtomicInteger();
    var engine = engine(rolls);
    engine.putTile(
        new TileDefinition(
            "off",
            "Disabled",
            "map",
            "region",
            false,
            Set.of(Trigger.PULSE),
            new Check("missing", "check", Map.of()),
            Policy.always(),
            Map.of(),
            List.of()));
    assertEquals(Status.SKIPPED, pulse(engine, "off", "actor", 0, "t").status());
    engine.putTile(
        tile(
            "invalid", Set.of(Trigger.INTERACT), new Policy(Repeat.ONCE_PER_ENTRY, Scope.TILE, 0)));
    assertFalse(engine.validationErrors("invalid").isEmpty());
    assertEquals(
        Status.FAILED,
        engine.interact("bad", "map", "invalid", "actor", 1, "t", PROPS).getFirst().status());
    assertEquals(0, rolls.get());
  }

  @Test
  void missingRegionAndHandlerAreActionableErrors() {
    var engine = engine(new AtomicInteger());
    engine.putTile(tile("trap", Set.of(Trigger.ENTER), Policy.always()));
    engine.removeRegion("region");
    var failure = move(engine, "bad-region", "actor", OUTSIDE, INSIDE, 1, "t");
    assertEquals(Status.FAILED, failure.status());
    assertTrue(failure.reason().contains("Missing region"));
    engine.putTile(
        new TileDefinition(
            "action",
            "Action",
            "map",
            null,
            true,
            Set.of(Trigger.INTERACT),
            null,
            Policy.always(),
            Map.of(),
            List.of(new Action("missing", Map.of()))));
    var missing = engine.interact("bad-action", "map", "action", "actor", 1, "t", PROPS).getFirst();
    assertEquals(Status.FAILED, missing.status());
    assertTrue(missing.reason().contains("Unavailable action handler"));
  }

  @Test
  void interruptedActionsKeepPriorReceiptsAndDoNotAutomaticallyRepeat() {
    var rolls = new AtomicInteger();
    var engine = engine(rolls);
    var effects = new AtomicInteger();
    engine.registerActionHandler("first", request -> effects.incrementAndGet());
    engine.registerActionHandler(
        "uncertain",
        request -> {
          throw new IllegalStateException("connection interrupted after effect");
        });
    engine.putTile(
        new TileDefinition(
            "trap",
            "Trap",
            "map",
            "region",
            true,
            Set.of(Trigger.PULSE),
            new Check("rules", "check", Map.of()),
            Policy.always(),
            Map.of(),
            List.of(new Action("first", Map.of()), new Action("uncertain", Map.of()))));
    var interrupted = pulse(engine, "one", "actor", 1, "t");
    assertEquals(Status.INTERRUPTED, interrupted.status());
    assertEquals(Status.COMPLETED, interrupted.steps().get(0).status());
    assertEquals(Status.INTERRUPTED, interrupted.steps().get(1).status());
    assertEquals(interrupted, pulse(engine, "one", "actor", 2, "t"));
    assertEquals(Status.SKIPPED, pulse(engine, "new-event", "actor", 3, "t").status());
    assertEquals(1, effects.get());
    assertEquals(1, rolls.get());
    var restored = TileEngine.fromJson(engine.snapshotJson());
    restored.registerRollAdapter(
        "rules",
        request -> {
          fail("Interrupted activation must not reroll");
          return null;
        });
    restored.registerActionHandler("first", request -> fail("Must not repeat effect"));
    restored.registerActionHandler("uncertain", request -> fail("Must not repeat effect"));
    assertEquals(interrupted, pulse(restored, "one", "actor", 5, "t"));
    assertEquals(Status.SKIPPED, pulse(restored, "another", "actor", 6, "t").status());
  }

  @Test
  void completedSnapshotRestoresDefinitionsPolicyAndCanonicalRollWithoutAdapters() {
    var engine = engine(new AtomicInteger());
    engine.putTile(tile("trap", Set.of(Trigger.PULSE), new Policy(Repeat.ONCE, Scope.ACTOR, 0)));
    var first = pulse(engine, "first", "actor", 1, "t");
    var restored = TileEngine.fromJson(engine.snapshotJson());
    assertEquals(first, pulse(restored, "first", "actor", 50, "t"));
    assertEquals(1, restored.definitions().size());
    restored.registerRollAdapter(
        "rules",
        request -> {
          fail("Consumed policy must not reroll");
          return null;
        });
    assertEquals(Status.SKIPPED, pulse(restored, "next", "actor", 50, "t").status());
    assertEquals(first.result().dice(), restored.receipts().get(first.key()).result().dice());
  }

  @Test
  void reentrantDuplicateSeesRunningReceiptWithoutRollingAgain() {
    var rolls = new AtomicInteger();
    var engine = engine(rolls);
    engine.putTile(tile("trap", Set.of(Trigger.PULSE), Policy.always()));
    engine.registerRollAdapter(
        "rules",
        request -> {
          rolls.incrementAndGet();
          assertEquals(Status.RUNNING, pulse(engine, "event", "actor", 1, "t").status());
          assertEquals(Status.SKIPPED, pulse(engine, "nested", "actor", 1, "t").status());
          var restored = TileEngine.fromJson(engine.snapshotJson());
          assertEquals(Status.INTERRUPTED, pulse(restored, "event", "actor", 1, "t").status());
          return TileEngine.CheckResult.outcome("SUCCESS");
        });
    assertEquals(Status.COMPLETED, pulse(engine, "event", "actor", 1, "t").status());
    assertEquals(1, rolls.get());
  }

  @Test
  void concurrentDuplicateCallsShareOneRoll() throws Exception {
    var rolls = new AtomicInteger();
    var engine = engine(rolls);
    engine.putTile(tile("trap", Set.of(Trigger.PULSE), Policy.always()));
    try (var executor = Executors.newFixedThreadPool(2)) {
      var one = executor.submit(() -> pulse(engine, "event", "actor", 1, "t"));
      var two = executor.submit(() -> pulse(engine, "event", "actor", 1, "t"));
      assertEquals(one.get(), two.get());
    }
    assertEquals(1, rolls.get());
  }

  @Test
  void invalidInputIsNotCachedAndIdDelimitersCannotCollide() {
    var rolls = new AtomicInteger();
    var engine = engine(rolls);
    engine.putTile(tile("trap", Set.of(Trigger.PULSE), Policy.always()));
    assertThrows(
        IllegalArgumentException.class,
        () -> engine.pulse("bad", "map", "actor", new Point(Double.NaN, 0), 0, "t", PROPS));
    assertEquals(Status.COMPLETED, pulse(engine, "bad", "actor", 0, "t").status());
    assertThrows(IllegalArgumentException.class, () -> pulse(engine, "negative", "actor", -1, "t"));
    assertThrows(
        IllegalArgumentException.class,
        () -> engine.interact("click", "map", "missing", "actor", 0, "t", PROPS));
    assertThrows(
        IllegalArgumentException.class,
        () -> engine.interact("click", "map", "missing", "actor", 0, "t", PROPS));
    pulse(engine, "a:b", "c", 1, "t");
    pulse(engine, "a", "b:c", 1, "t");
    assertEquals(3, rolls.get());
  }

  @Test
  void throwingRollAdapterNeverFallsThroughToFailureActions() {
    var engine = engine(new AtomicInteger());
    engine.registerRollAdapter(
        "rules",
        request -> {
          throw new IllegalArgumentException("Missing property: agility");
        });
    engine.registerActionHandler("effect", request -> fail("No outcome branch on adapter error"));
    engine.putTile(
        new TileDefinition(
            "trap",
            "Trap",
            "map",
            "region",
            true,
            Set.of(Trigger.ENTER),
            new Check("rules", "save", Map.of()),
            Policy.always(),
            Map.of("FAILURE", List.of(new Action("effect", Map.of()))),
            List.of(new Action("effect", Map.of()))));
    var receipt = move(engine, "event", "actor", OUTSIDE, INSIDE, 0, "t");
    assertEquals(Status.INTERRUPTED, receipt.status());
    assertTrue(receipt.reason().contains("Missing property"));
    assertTrue(receipt.steps().isEmpty());
  }

  @Test
  void noCheckInteractionHasCanonicalActivationOutcomeAndSurvivesSourceRemoval() {
    var engine = new TileEngine("run");
    var effects = new AtomicInteger();
    engine.registerActionHandler(
        "toggle",
        request -> {
          assertEquals("ACTIVATED", request.result().outcome());
          effects.incrementAndGet();
        });
    engine.putTile(
        new TileDefinition(
            "lever",
            "Lever",
            "map",
            null,
            true,
            Set.of(Trigger.INTERACT),
            null,
            Policy.always(),
            Map.of("ACTIVATED", List.of(new Action("toggle", Map.of()))),
            List.of()));
    var first = engine.interact("click", "map", "lever", "actor", 1, "t", PROPS).getFirst();
    engine.removeTile("lever");
    assertEquals(
        first, engine.interact("click", "map", "lever", "actor", 2, "t", PROPS).getFirst());
    assertEquals(1, effects.get());
  }

  @Test
  void snapshotDuringActionRestoresRunningStepAsInterrupted() {
    var engine = engine(new AtomicInteger());
    engine.registerActionHandler(
        "snapshot",
        request -> {
          var restored = TileEngine.fromJson(engine.snapshotJson());
          var receipt = restored.receipts().get(request.context().activationKey());
          assertEquals(Status.INTERRUPTED, receipt.status());
          assertEquals(Status.INTERRUPTED, receipt.steps().getFirst().status());
        });
    engine.putTile(
        new TileDefinition(
            "trap",
            "Trap",
            "map",
            "region",
            true,
            Set.of(Trigger.PULSE),
            null,
            Policy.always(),
            Map.of(),
            List.of(new Action("snapshot", Map.of()))));
    assertEquals(Status.COMPLETED, pulse(engine, "event", "actor", 1, "t").status());
  }

  @Test
  void invalidNumericalAdapterResultCannotBreakSnapshotSerialization() {
    var engine = engine(new AtomicInteger());
    engine.registerRollAdapter(
        "rules",
        request ->
            new TileEngine.CheckResult("SUCCESS", "bad dice", 1.0, List.of(Double.NaN), Map.of()));
    engine.putTile(tile("trap", Set.of(Trigger.PULSE), Policy.always()));
    assertEquals(Status.INTERRUPTED, pulse(engine, "event", "actor", 0, "t").status());
    assertDoesNotThrow(() -> TileEngine.fromJson(engine.snapshotJson()));
  }

  @Test
  void rejectedPropertyMapDoesNotConsumeMovementInteractionOrPulseOccurrence() {
    var engine = engine(new AtomicInteger());
    engine.putTile(
        tile("trap", Set.of(Trigger.ENTER, Trigger.INTERACT, Trigger.PULSE), Policy.always()));
    var invalid = new HashMap<String, String>();
    invalid.put("skill", null);
    assertThrows(
        IllegalArgumentException.class,
        () -> engine.onMove("move", "map", "actor", OUTSIDE, INSIDE, 1, "t", invalid));
    assertThrows(
        IllegalArgumentException.class,
        () -> engine.interact("click", "map", "trap", "actor", 1, "t", invalid));
    assertThrows(
        IllegalArgumentException.class,
        () -> engine.pulse("pulse", "map", "actor", INSIDE, 1, "t", invalid));
    assertEquals(Status.COMPLETED, move(engine, "move", "actor", OUTSIDE, INSIDE, 1, "t").status());
    assertEquals(
        Status.COMPLETED,
        engine.interact("click", "map", "trap", "actor", 1, "t", PROPS).getFirst().status());
    assertEquals(Status.COMPLETED, pulse(engine, "pulse", "actor", 1, "t").status());
  }

  @Test
  void nonfiniteDraftDoesNotReplaceRegionOrPoisonSnapshots() {
    var engine = engine(new AtomicInteger());
    var invalid =
        new TileRegion(
            "region",
            "Invalid",
            "map",
            List.of(new Point(Double.NaN, 0), new Point(0, 1), new Point(1, 0)));
    assertFalse(invalid.validationErrors().isEmpty());
    assertThrows(IllegalArgumentException.class, () -> engine.putRegion(invalid));
    assertEquals("Plate", engine.regions().getFirst().name());
    assertDoesNotThrow(() -> TileEngine.fromJson(engine.snapshotJson()));
  }
}
