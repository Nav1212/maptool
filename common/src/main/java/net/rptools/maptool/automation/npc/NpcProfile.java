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
import java.util.Objects;
import net.rptools.maptool.automation.npc.NpcWorld.Point;

/** Budgets apply to one unique caller-scheduled decision event, not animation frames. */
public record NpcProfile(
    List<Waypoint> waypoints,
    PatrolMode patrolMode,
    double movementBudget,
    int actionBudget,
    boolean pursueVisibleTargets,
    long memoryTicks,
    double maxPursuitDistance,
    String actionId,
    double actionRange,
    String ruleId) {
  public NpcProfile {
    waypoints = List.copyOf(waypoints);
    Objects.requireNonNull(patrolMode);
    if (!Double.isFinite(movementBudget) || movementBudget < 0 || actionBudget < 0) {
      throw new IllegalArgumentException("Budgets must be finite and nonnegative");
    }
    if (memoryTicks < 0
        || !Double.isFinite(maxPursuitDistance)
        || maxPursuitDistance < 0
        || !Double.isFinite(actionRange)
        || actionRange < 0) {
      throw new IllegalArgumentException("Memory, range and pursuit limit must be nonnegative");
    }
    actionId = Objects.requireNonNullElse(actionId, "");
    ruleId = Objects.requireNonNullElse(ruleId, "");
  }

  public static NpcProfile patrol(List<Waypoint> waypoints, PatrolMode mode, double budget) {
    return new NpcProfile(waypoints, mode, budget, 1, false, 0, 0, "", 0, "");
  }

  public enum PatrolMode {
    ONCE,
    LOOP,
    PING_PONG
  }

  public record Waypoint(Point position, long waitTicks) {
    public Waypoint {
      Objects.requireNonNull(position);
      if (waitTicks < 0) {
        throw new IllegalArgumentException("Waypoint wait must be nonnegative");
      }
    }
  }
}
