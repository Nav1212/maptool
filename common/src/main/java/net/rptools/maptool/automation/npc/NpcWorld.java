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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Explicit map/observer adapter. Reads and planning must not mutate the world or roll dice. */
public interface NpcWorld {
  boolean hasAuthority(String runId, String mapId);

  Optional<Token> token(String mapId, String tokenId);

  /** Return only targets this NPC is allowed to perceive, never the unrestricted GM token list. */
  List<Observation> perceive(String mapId, String npcId);

  /** May return a partial path, but its total cost must fit the supplied budget. */
  MovePlan planMove(String mapId, String npcId, Point destination, double budget);

  /**
   * Revalidate actor/world revisions and legal movement before mutation. Repeated command keys must
   * not apply an effect twice. Rejected moves leave the world unchanged.
   */
  MoveOutcome commitMove(String mapId, String npcId, MovePlan plan, String commandKey);

  /** Revalidate approval, actor, target, costs and macro trust before executing an action. */
  default ActionOutcome executeApprovedAction(
      String mapId, String npcId, ActionRequest request, String commandKey) {
    return new ActionOutcome(false, 0, List.of(), Map.of(), "No approved action adapter");
  }

  record Point(double x, double y) {
    public Point {
      if (!Double.isFinite(x) || !Double.isFinite(y)) {
        throw new IllegalArgumentException("Coordinates must be finite");
      }
    }

    public double distance(Point other) {
      return Math.hypot(x - other.x, y - other.y);
    }
  }

  record Token(String id, Point position, long revision, boolean npc) {
    public Token {
      requireId(id);
      Objects.requireNonNull(position);
    }
  }

  record Observation(String tokenId, Point position, long revision, boolean targetable) {
    public Observation {
      requireId(tokenId);
      Objects.requireNonNull(position);
    }
  }

  enum PlanStatus {
    READY,
    NO_PATH,
    STALE,
    UNSUPPORTED
  }

  record MovePlan(
      PlanStatus status,
      long tokenRevision,
      Point origin,
      List<Point> path,
      double cost,
      String worldRevision,
      String detail) {
    public MovePlan {
      Objects.requireNonNull(status);
      path = List.copyOf(path);
      worldRevision = Objects.requireNonNullElse(worldRevision, "");
      detail = Objects.requireNonNullElse(detail, "");
    }

    public static MovePlan ready(Token actor, List<Point> path, double cost, String revision) {
      return new MovePlan(
          PlanStatus.READY, actor.revision(), actor.position(), path, cost, revision, "");
    }

    public static MovePlan blocked(PlanStatus status, String detail) {
      if (status == PlanStatus.READY) {
        throw new IllegalArgumentException("A blocked plan cannot be READY");
      }
      return new MovePlan(status, 0, null, List.of(), 0, "", detail);
    }
  }

  record MoveOutcome(boolean committed, boolean stale, Point position, String detail) {
    public MoveOutcome {
      detail = Objects.requireNonNullElse(detail, "");
      if (committed) {
        Objects.requireNonNull(position);
        if (stale) {
          throw new IllegalArgumentException("A committed move cannot be stale");
        }
      }
    }
  }

  record ActionRequest(
      String actionId,
      String targetId,
      long actorRevision,
      long targetRevision,
      int actionCost,
      Map<String, String> parameters) {
    public ActionRequest {
      requireId(actionId);
      targetId = Objects.requireNonNullElse(targetId, "");
      parameters = Map.copyOf(parameters);
      if (actionCost < 1) {
        throw new IllegalArgumentException("Action cost must be positive");
      }
    }
  }

  /**
   * Recorded rolls are opaque outcomes supplied once by the authority, not instructions to roll.
   */
  record ActionOutcome(
      boolean committed,
      int actionsSpent,
      List<String> recordedRolls,
      Map<String, String> recordedOutcomes,
      String detail) {
    public ActionOutcome {
      recordedRolls = List.copyOf(recordedRolls);
      recordedOutcomes = Map.copyOf(recordedOutcomes);
      detail = Objects.requireNonNullElse(detail, "");
    }
  }

  private static void requireId(String id) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("An identifier must not be blank");
    }
  }
}
