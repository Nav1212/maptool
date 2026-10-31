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
import net.rptools.maptool.automation.npc.NpcWorld.Point;

/** Receipt and inspection data. Returning a receipt never asks the caller to reapply its effect. */
public record NpcResult(
    String runId,
    String eventId,
    String mapId,
    String npcId,
    long logicalTick,
    NpcBehavior behavior,
    Outcome outcome,
    Point position,
    double movementSpent,
    int actionsSpent,
    String targetId,
    String detail,
    List<String> recordedRolls,
    Map<String, String> recordedOutcomes,
    boolean duplicate) {
  public NpcResult {
    recordedRolls = List.copyOf(recordedRolls);
    recordedOutcomes = Map.copyOf(recordedOutcomes);
  }

  public enum Outcome {
    MOVED,
    ACTED,
    WAITING,
    PAUSED,
    MANUAL,
    NO_PATH,
    STALE,
    REJECTED,
    MISSING_TOKEN,
    ERROR
  }

  public NpcResult asDuplicate() {
    return new NpcResult(
        runId,
        eventId,
        mapId,
        npcId,
        logicalTick,
        behavior,
        outcome,
        position,
        movementSpent,
        actionsSpent,
        targetId,
        detail,
        recordedRolls,
        recordedOutcomes,
        true);
  }
}
