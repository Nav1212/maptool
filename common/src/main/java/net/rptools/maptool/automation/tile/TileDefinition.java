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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Serializable authoring data. Dice mechanics and game rules belong to injected adapters. */
public record TileDefinition(
    String id,
    String label,
    String mapId,
    String regionId,
    boolean enabled,
    Set<Trigger> triggers,
    Check check,
    Policy policy,
    Map<String, List<Action>> outcomes,
    List<Action> alwaysActions) {

  public TileDefinition {
    triggers = triggers == null ? Set.of() : Set.copyOf(triggers);
    policy = policy == null ? Policy.always() : policy;
    var branches = new LinkedHashMap<String, List<Action>>();
    if (outcomes != null) {
      outcomes.forEach((key, value) -> branches.put(key, List.copyOf(value)));
    }
    outcomes = Collections.unmodifiableMap(branches);
    alwaysActions = alwaysActions == null ? List.of() : List.copyOf(alwaysActions);
  }

  public enum Trigger {
    ENTER,
    EXIT,
    CROSS,
    INTERACT,
    PULSE
  }

  public enum Repeat {
    ALWAYS,
    ONCE,
    ONCE_PER_ENTRY,
    ONCE_PER_TURN,
    COOLDOWN
  }

  public enum Scope {
    ACTOR,
    TILE
  }

  /** Visibility is a presentation contract: transport adapters must filter the actual payload. */
  public enum Visibility {
    PUBLIC,
    OWNER,
    GM
  }

  public record Check(
      String adapterId, String checkId, Map<String, String> parameters, Visibility visibility) {
    public Check {
      parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
      visibility = visibility == null ? Visibility.PUBLIC : visibility;
    }

    public Check(String adapterId, String checkId, Map<String, String> parameters) {
      this(adapterId, checkId, parameters, Visibility.PUBLIC);
    }
  }

  public record Action(String handlerId, Map<String, String> parameters) {
    public Action {
      parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
  }

  /** Policies are consumed after all actions complete, including a completed FAILURE outcome. */
  public record Policy(Repeat repeat, Scope scope, long cooldownTicks) {
    public static Policy always() {
      return new Policy(Repeat.ALWAYS, Scope.ACTOR, 0);
    }
  }

  public List<String> validationErrors() {
    var errors = new ArrayList<String>();
    if (blank(id)) errors.add("Tile ID is required");
    if (blank(mapId)) errors.add("Map ID is required");
    if (blank(label)) errors.add("Tile label is required");
    if (triggers.isEmpty()) errors.add("At least one trigger is required");
    if (triggers.stream().anyMatch(t -> t != Trigger.INTERACT) && blank(regionId)) {
      errors.add("Movement and pulse triggers require a region");
    }
    if (policy.repeat() == null || policy.scope() == null)
      errors.add("Policy type/scope is required");
    if (policy.cooldownTicks() < 0) errors.add("Cooldown cannot be negative");
    if (policy.repeat() == Repeat.ONCE_PER_ENTRY && policy.scope() != Scope.ACTOR) {
      errors.add("Once per entry requires actor scope");
    }
    if (policy.repeat() == Repeat.ONCE_PER_ENTRY && blank(regionId)) {
      errors.add("Once per entry requires a region");
    }
    if (check != null && (blank(check.adapterId()) || blank(check.checkId()))) {
      errors.add("Check adapter and check ID are required");
    }
    for (var branch : outcomes.entrySet()) {
      if (blank(branch.getKey())) errors.add("Outcome name cannot be blank");
      for (var action : branch.getValue()) {
        if (blank(action.handlerId())) errors.add("Action handler is required");
      }
    }
    for (var action : alwaysActions) {
      if (blank(action.handlerId())) errors.add("Action handler is required");
    }
    return List.copyOf(errors);
  }

  static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
