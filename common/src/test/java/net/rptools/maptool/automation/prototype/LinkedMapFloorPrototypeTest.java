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
package net.rptools.maptool.automation.prototype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.rptools.maptool.automation.npc.NpcEngine;
import net.rptools.maptool.automation.npc.NpcProfile;
import net.rptools.maptool.automation.npc.NpcProfile.PatrolMode;
import net.rptools.maptool.automation.npc.NpcProfile.Waypoint;
import net.rptools.maptool.automation.npc.NpcResult.Outcome;
import net.rptools.maptool.automation.npc.NpcWorld;
import net.rptools.maptool.automation.npc.NpcWorld.MoveOutcome;
import net.rptools.maptool.automation.npc.NpcWorld.MovePlan;
import net.rptools.maptool.automation.npc.NpcWorld.Observation;
import net.rptools.maptool.automation.npc.NpcWorld.Point;
import net.rptools.maptool.automation.npc.NpcWorld.Token;
import org.junit.jupiter.api.Test;

/**
 * Bounded, test-only prototype of a proposed contract for floors held in separate maps. The fixture
 * uses NPC token/position records but does not implement a production floor API or integrate
 * Campaign, Zone, GUI, real fog polygons, topology, lighting, network synchronization, or portals.
 * Marker preservation documents isolation expectations, not validation of MapTool rendering. The
 * NPC assertion exercises only the existing explicit-map adapter contract after a fixture
 * transition.
 */
class LinkedMapFloorPrototypeTest {
  private static final String GROUND = "ground-map";
  private static final String UPPER = "upper-map";
  private static final String TOKEN = "scout-token";
  private static final String LINK = "stairs-up";

  @Test
  void directedOwnerTransferPreservesTokenDataAndKeepsFloorMarkersSeparate() {
    var world = twoFloors(false);
    var before = world.snapshot();
    var sourceToken = world.placed(GROUND, TOKEN);
    var occurrence = new Occurrence("alice", "session-a:stairs-1", TOKEN, LINK);

    var receipt = world.transfer(occurrence, GROUND);

    assertEquals(TransferStatus.APPLIED, receipt.status());
    assertFalse(receipt.duplicate());
    assertTrue(world.token(GROUND, TOKEN).isEmpty());
    var destinationToken = world.placed(UPPER, TOKEN);
    assertEquals(sourceToken.token().id(), destinationToken.token().id());
    assertEquals(sourceToken.owners(), destinationToken.owners());
    assertEquals(sourceToken.properties(), destinationToken.properties());
    assertEquals(new Point(4, 6), destinationToken.token().position());
    assertEquals(12, world.floors.get(UPPER).baseElevation);
    assertEquals(3, destinationToken.relativeElevation());
    assertEquals(15, receipt.absoluteElevation());
    assertEquals(15, world.absoluteElevation(UPPER, TOKEN));
    assertEquals(1, world.tokenCount());
    assertMarkersUnchanged(before, world.snapshot());

    var afterTransfer = world.snapshot();
    var reverseAttempt =
        world.transfer(new Occurrence("alice", "session-a:reverse-1", TOKEN, LINK), UPPER);
    assertEquals(TransferStatus.DENIED, reverseAttempt.status());
    assertEquals(afterTransfer, world.snapshot());
  }

  @Test
  void nonOwnerTransferRejectsBeforeEitherMapOrItsTokenDataChanges() {
    var world = twoFloors(false);
    var before = world.snapshot();

    var denied =
        world.transfer(new Occurrence("mallory", "session-a:stairs-1", TOKEN, LINK), GROUND);

    assertEquals(TransferStatus.DENIED, denied.status());
    assertEquals("", denied.destinationMapId());
    assertEquals(before, world.snapshot());
    assertTrue(world.token(GROUND, TOKEN).isPresent());
    assertTrue(world.token(UPPER, TOKEN).isEmpty());
    assertEquals(1, world.tokenCount());
  }

  @Test
  void hiddenDestinationRejectsAtomicallyWithoutCopyingOrExposingTheToken() {
    var world = twoFloors(true);
    var before = world.snapshot();

    var denied = world.transfer(new Occurrence("alice", "session-a:stairs-1", TOKEN, LINK), GROUND);

    assertEquals(TransferStatus.DENIED, denied.status());
    assertEquals("", denied.destinationMapId());
    assertEquals(before, world.snapshot());
    assertEquals(Set.of("alice"), world.placed(GROUND, TOKEN).owners());
    assertTrue(world.token(UPPER, TOKEN).isEmpty());
    assertEquals(1, world.tokenCount());
  }

  @Test
  void restoredReceiptPreventsDuplicateTransferAndNpcEnrollmentRemainsPerMap() {
    var world = twoFloors(false);
    var engine = new NpcEngine();
    engine.setMapEnabled(GROUND, true);
    engine.setMapEnabled(UPPER, true);
    engine.assign(
        GROUND,
        TOKEN,
        NpcProfile.patrol(List.of(new Waypoint(new Point(2, 0), 0)), PatrolMode.ONCE, 2));
    var occurrence = new Occurrence("alice", "session-a:stairs-1", TOKEN, LINK);
    var first = world.transfer(occurrence, GROUND);
    var gson = new Gson();
    var restored =
        FixtureWorld.fromSnapshot(
            gson.fromJson(gson.toJson(world.snapshot()), FloorSnapshot.class));
    var beforeReplay = restored.snapshot();

    var repeated = restored.transfer(occurrence, GROUND);

    assertEquals(first.asDuplicate(), repeated);
    assertEquals(beforeReplay, restored.snapshot());
    assertEquals(1, restored.tokenCount());
    assertTrue(restored.token(GROUND, TOKEN).isEmpty());
    assertTrue(restored.token(UPPER, TOKEN).isPresent());
    var sourceResults = engine.tick("host", "after-stairs", GROUND, 1, restored);
    assertEquals(1, sourceResults.size());
    assertEquals(Outcome.MISSING_TOKEN, sourceResults.getFirst().outcome());
    assertTrue(engine.tick("host", "after-stairs", UPPER, 1, restored).isEmpty());
    assertTrue(engine.inspect(UPPER, TOKEN).isEmpty());
    assertEquals(1, restored.tokenCount());
  }

  private static FixtureWorld twoFloors(boolean destinationHidden) {
    var ground =
        new Floor(
            GROUND,
            0,
            false,
            Set.of("ground-revealed-area"),
            Set.of("ground-wall"),
            Set.of("ground-torch"));
    ground.tokens.put(
        TOKEN,
        new PlacedToken(
            new Token(TOKEN, new Point(1, 1), 1, true),
            Set.of("alice"),
            Map.of("name", "Scout", "hp", "12", "custom-rule", "unchanged"),
            2));
    var upper =
        new Floor(
            UPPER,
            12,
            destinationHidden,
            Set.of("upper-unexplored-area"),
            Set.of("upper-balcony-wall"),
            Set.of("upper-lantern"));
    var world = new FixtureWorld();
    world.floors.put(GROUND, ground);
    world.floors.put(UPPER, upper);
    world.links.put(LINK, new LandingLink(LINK, GROUND, UPPER, new Point(4, 6), 3));
    return world;
  }

  private static void assertMarkersUnchanged(FloorSnapshot before, FloorSnapshot after) {
    for (var mapId : List.of(GROUND, UPPER)) {
      var original =
          before.floors().stream()
              .filter(floor -> floor.id().equals(mapId))
              .findFirst()
              .orElseThrow();
      var current =
          after.floors().stream()
              .filter(floor -> floor.id().equals(mapId))
              .findFirst()
              .orElseThrow();
      assertEquals(original.fogMarkers(), current.fogMarkers());
      assertEquals(original.topologyMarkers(), current.topologyMarkers());
      assertEquals(original.lightMarkers(), current.lightMarkers());
    }
  }

  private record PlacedToken(
      Token token, Set<String> owners, Map<String, String> properties, double relativeElevation) {
    private PlacedToken {
      owners = Set.copyOf(owners);
      properties = Map.copyOf(properties);
      if (!Double.isFinite(relativeElevation)) {
        throw new IllegalArgumentException("Relative elevation must be finite");
      }
    }
  }

  private record LandingLink(
      String id,
      String sourceMapId,
      String destinationMapId,
      Point landing,
      double relativeElevation) {}

  private record Occurrence(String ownerId, String occurrenceId, String tokenId, String linkId) {}

  private enum TransferStatus {
    APPLIED,
    DENIED
  }

  private record TransferReceipt(
      Occurrence occurrence,
      TransferStatus status,
      String destinationMapId,
      double absoluteElevation,
      boolean duplicate) {
    private TransferReceipt asDuplicate() {
      return new TransferReceipt(occurrence, status, destinationMapId, absoluteElevation, true);
    }
  }

  private record MapSnapshot(
      String id,
      double baseElevation,
      boolean hidden,
      Map<String, PlacedToken> tokens,
      Set<String> fogMarkers,
      Set<String> topologyMarkers,
      Set<String> lightMarkers) {
    private MapSnapshot {
      tokens = Map.copyOf(tokens);
      fogMarkers = Set.copyOf(fogMarkers);
      topologyMarkers = Set.copyOf(topologyMarkers);
      lightMarkers = Set.copyOf(lightMarkers);
    }
  }

  private record FloorSnapshot(
      List<MapSnapshot> floors, List<LandingLink> links, List<TransferReceipt> receipts) {
    private FloorSnapshot {
      floors = List.copyOf(floors);
      links = List.copyOf(links);
      receipts = List.copyOf(receipts);
    }
  }

  private static final class Floor {
    private final String id;
    private final double baseElevation;
    private final boolean hidden;
    private final Map<String, PlacedToken> tokens = new LinkedHashMap<>();
    private final Set<String> fogMarkers;
    private final Set<String> topologyMarkers;
    private final Set<String> lightMarkers;

    private Floor(
        String id,
        double baseElevation,
        boolean hidden,
        Set<String> fogMarkers,
        Set<String> topologyMarkers,
        Set<String> lightMarkers) {
      this.id = id;
      this.baseElevation = baseElevation;
      this.hidden = hidden;
      this.fogMarkers = Set.copyOf(fogMarkers);
      this.topologyMarkers = Set.copyOf(topologyMarkers);
      this.lightMarkers = Set.copyOf(lightMarkers);
    }

    private MapSnapshot snapshot() {
      return new MapSnapshot(
          id, baseElevation, hidden, tokens, fogMarkers, topologyMarkers, lightMarkers);
    }
  }

  /** Two maps, one directed link, and at most sixteen successful occurrence receipts. */
  private static final class FixtureWorld implements NpcWorld {
    private final Map<String, Floor> floors = new LinkedHashMap<>();
    private final Map<String, LandingLink> links = new LinkedHashMap<>();
    private final Map<Occurrence, TransferReceipt> receipts = new LinkedHashMap<>();

    private TransferReceipt transfer(Occurrence occurrence, String sourceMapId) {
      var previous = receipts.get(occurrence);
      if (previous != null) {
        return previous.asDuplicate();
      }
      var link = links.get(occurrence.linkId());
      var source = floors.get(sourceMapId);
      var destination = link == null ? null : floors.get(link.destinationMapId());
      var actor = source == null ? null : source.tokens.get(occurrence.tokenId());
      if (link == null
          || source == null
          || destination == null
          || !link.sourceMapId().equals(sourceMapId)
          || !actorOwnedBy(actor, occurrence.ownerId())
          || destination.hidden
          || destination.tokens.containsKey(occurrence.tokenId())
          || receipts.size() >= 16) {
        // Deliberately disclose no hidden destination information in a denial.
        return new TransferReceipt(occurrence, TransferStatus.DENIED, "", 0, false);
      }
      var movedToken =
          new Token(
              actor.token().id(),
              link.landing(),
              Math.addExact(actor.token().revision(), 1),
              actor.token().npc());
      var moved =
          new PlacedToken(movedToken, actor.owners(), actor.properties(), link.relativeElevation());
      var receipt =
          new TransferReceipt(
              occurrence,
              TransferStatus.APPLIED,
              destination.id,
              destination.baseElevation + moved.relativeElevation(),
              false);
      // All validation and replacement construction precede these fixture mutations.
      source.tokens.remove(occurrence.tokenId());
      destination.tokens.put(occurrence.tokenId(), moved);
      receipts.put(occurrence, receipt);
      return receipt;
    }

    private static boolean actorOwnedBy(PlacedToken token, String playerId) {
      return token != null && token.owners().contains(playerId);
    }

    private PlacedToken placed(String mapId, String tokenId) {
      return floors.get(mapId).tokens.get(tokenId);
    }

    private double absoluteElevation(String mapId, String tokenId) {
      return floors.get(mapId).baseElevation + placed(mapId, tokenId).relativeElevation();
    }

    private int tokenCount() {
      return floors.values().stream().mapToInt(floor -> floor.tokens.size()).sum();
    }

    private FloorSnapshot snapshot() {
      return new FloorSnapshot(
          floors.values().stream().map(Floor::snapshot).toList(),
          List.copyOf(links.values()),
          List.copyOf(receipts.values()));
    }

    private static FixtureWorld fromSnapshot(FloorSnapshot snapshot) {
      if (snapshot.floors().size() != 2
          || snapshot.links().size() != 1
          || snapshot.receipts().size() > 16) {
        throw new IllegalArgumentException("Snapshot exceeds two-floor prototype bounds");
      }
      var restored = new FixtureWorld();
      for (var saved : snapshot.floors()) {
        var floor =
            new Floor(
                saved.id(),
                saved.baseElevation(),
                saved.hidden(),
                saved.fogMarkers(),
                saved.topologyMarkers(),
                saved.lightMarkers());
        floor.tokens.putAll(saved.tokens());
        restored.floors.put(floor.id, floor);
      }
      snapshot.links().forEach(link -> restored.links.put(link.id(), link));
      snapshot.receipts().forEach(receipt -> restored.receipts.put(receipt.occurrence(), receipt));
      return restored;
    }

    @Override
    public boolean hasAuthority(String runId, String mapId) {
      return runId.equals("host") && floors.containsKey(mapId);
    }

    @Override
    public Optional<Token> token(String mapId, String tokenId) {
      var floor = floors.get(mapId);
      var token = floor == null ? null : floor.tokens.get(tokenId);
      return token == null ? Optional.empty() : Optional.of(token.token());
    }

    @Override
    public List<Observation> perceive(String mapId, String npcId) {
      return List.of();
    }

    @Override
    public MovePlan planMove(String mapId, String npcId, Point destination, double budget) {
      throw new AssertionError("This floor contract must not schedule NPC movement");
    }

    @Override
    public MoveOutcome commitMove(String mapId, String npcId, MovePlan plan, String commandKey) {
      throw new AssertionError("This floor contract must not dispatch NPC movement");
    }
  }
}
