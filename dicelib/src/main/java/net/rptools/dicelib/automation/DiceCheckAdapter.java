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

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Pattern;
import net.rptools.dicelib.expression.ExpressionParser;
import net.rptools.maptool.automation.tile.TileEngine;

/** Reference map-local check adapter. Game-specific saves and resources remain external. */
public final class DiceCheckAdapter implements TileEngine.RollAdapter {
  private static final Pattern DICE = Pattern.compile("(?i)([0-9]*)d([0-9]+)");
  private final ExpressionParser parser =
      new ExpressionParser(
          (resolver, name) -> {
            throw new IllegalArgumentException("Unknown dice variable: " + name);
          },
          (resolver, name) -> {
            throw new IllegalArgumentException("Property expressions are unsupported");
          },
          name -> {
            throw new IllegalArgumentException("Interactive dice prompts are unsupported");
          });

  @Override
  public synchronized TileEngine.CheckResult roll(TileEngine.CheckRequest request)
      throws Exception {
    var parameters = request.check().parameters();
    String expression = parameters.getOrDefault("expression", "");
    validateExpression(expression);
    String property = parameters.getOrDefault("property", "");
    BigDecimal modifier = parseNumber(parameters.getOrDefault("modifier", "0"), "modifier");
    if (!property.isBlank()) {
      String value = request.context().actorProperties().get(property);
      if (value == null) throw new IllegalArgumentException("Missing actor property: " + property);
      modifier = modifier.add(parseNumber(value, "actor property " + property));
    }
    BigDecimal target =
        parameters.containsKey("target") ? parseNumber(parameters.get("target"), "target") : null;
    String comparison = parameters.getOrDefault("comparison", "gte").toLowerCase(Locale.ROOT);
    if (!java.util.Set.of("gte", "gt", "lte", "lt", "eq").contains(comparison)) {
      throw new IllegalArgumentException("Unsupported comparison: " + comparison);
    }
    // Validate every deterministic input before the first random outcome is consumed.
    var rolled = parser.evaluate(expression);
    if (!(rolled.getValue() instanceof Number))
      throw new IllegalArgumentException("Dice expression is not numeric");
    BigDecimal total = new BigDecimal(rolled.getValue().toString()).add(modifier);
    if (!Double.isFinite(total.doubleValue()))
      throw new IllegalArgumentException("Check result is outside supported range");
    String outcome = "ROLLED";
    if (target != null) {
      int order = total.compareTo(target);
      boolean success =
          switch (comparison) {
            case "gte" -> order >= 0;
            case "gt" -> order > 0;
            case "lte" -> order <= 0;
            case "lt" -> order < 0;
            case "eq" -> order == 0;
            default -> throw new IllegalStateException("Validated comparator changed");
          };
      outcome = success ? "SUCCESS" : "FAILURE";
    }
    var details = new LinkedHashMap<String, String>();
    details.put("expression", expression);
    details.put("detail", rolled.getDetailExpression());
    details.put("modifier", modifier.toPlainString());
    details.put("comparison", comparison);
    if (!property.isBlank()) details.put("property", property);
    if (target != null) details.put("target", target.toPlainString());
    return new TileEngine.CheckResult(
        outcome,
        expression + " + " + modifier + " = " + total,
        total.doubleValue(),
        rolled.getRolled().stream().map(Integer::doubleValue).toList(),
        details);
  }

  private static void validateExpression(String expression) {
    if (expression.isBlank()
        || expression.length() > 512
        || !expression.matches("[0-9dD\\s()+*/.\\-]+")) {
      throw new IllegalArgumentException("Use a bounded numeric expression with standard NdS dice");
    }
    var matcher = DICE.matcher(expression);
    int totalDice = 0;
    while (matcher.find()) {
      int count = matcher.group(1).isEmpty() ? 1 : Integer.parseInt(matcher.group(1));
      int sides = Integer.parseInt(matcher.group(2));
      if (count < 1 || count > 1000 || sides < 1 || sides > 1_000_000) {
        throw new IllegalArgumentException("Dice count/sides exceed the reference adapter limits");
      }
      totalDice = Math.addExact(totalDice, count);
      if (totalDice > 2000) throw new IllegalArgumentException("Too many dice in one check");
    }
  }

  private static BigDecimal parseNumber(String text, String field) {
    if (text == null || text.isBlank() || text.length() > 128) {
      throw new IllegalArgumentException("Invalid numeric " + field);
    }
    BigDecimal value = new BigDecimal(text.trim());
    if (value.precision() > 64
        || Math.abs((long) value.scale()) > 32
        || !Double.isFinite(value.doubleValue())) {
      throw new IllegalArgumentException("Numeric " + field + " exceeds supported precision/scale");
    }
    return value;
  }
}
