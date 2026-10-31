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
import net.rptools.maptool.automation.npc.NpcWorld.Observation;
import net.rptools.maptool.automation.npc.NpcWorld.Point;
import net.rptools.maptool.automation.npc.NpcWorld.Token;

/** Registered rules are pure deterministic selectors, never privileged macro executors. */
@FunctionalInterface
public interface NpcRule {
  Decision decide(Context context);

  record Context(
      String mapId,
      String npcId,
      long logicalTick,
      NpcProfile profile,
      Token actor,
      List<Observation> observations,
      NpcBehavior behavior,
      Point home,
      Point lastSeen,
      String lastTargetId) {
    public Context {
      observations = List.copyOf(observations);
    }
  }

  enum Kind {
    WAIT,
    MOVE,
    ACTION
  }

  record Decision(
      Kind kind,
      Point destination,
      String actionId,
      String targetId,
      int actionCost,
      Map<String, String> parameters,
      String reason) {
    public Decision {
      Objects.requireNonNull(kind);
      actionId = Objects.requireNonNullElse(actionId, "");
      targetId = Objects.requireNonNullElse(targetId, "");
      parameters = Map.copyOf(parameters);
      reason = Objects.requireNonNullElse(reason, "");
      if (kind == Kind.MOVE) {
        Objects.requireNonNull(destination);
      }
      if (kind == Kind.ACTION && (actionId.isBlank() || actionCost < 1)) {
        throw new IllegalArgumentException("An action requires an ID and positive cost");
      }
    }

    public static Decision waitFor(String reason) {
      return new Decision(Kind.WAIT, null, "", "", 0, Map.of(), reason);
    }

    public static Decision move(Point destination, String reason) {
      return new Decision(Kind.MOVE, destination, "", "", 0, Map.of(), reason);
    }

    public static Decision action(String actionId, String targetId, int cost, String reason) {
      return new Decision(Kind.ACTION, null, actionId, targetId, cost, Map.of(), reason);
    }
  }
}
