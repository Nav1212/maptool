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
package net.rptools.maptool.addon;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Explicitly supported ranges: exact, partial/wildcard, ^, ~, comparators, AND, and ||. */
public final class VersionRange {
  private static final BigInteger ONE = BigInteger.ONE;
  private static final BigInteger ZERO = BigInteger.ZERO;
  private static final Pattern PARTIAL =
      Pattern.compile("v?([0-9]+|[xX*])(?:\\.([0-9]+|[xX*]))?(?:\\.([0-9]+|[xX*]))?");
  private final List<List<Bound>> alternatives;

  private record Bound(String operator, SemanticVersion version) {
    boolean accepts(SemanticVersion candidate) {
      int value = candidate.compareTo(version);
      return switch (operator) {
        case "=" -> value == 0;
        case ">" -> value > 0;
        case ">=" -> value >= 0;
        case "<" -> value < 0;
        case "<=" -> value <= 0;
        default -> throw new IllegalStateException(operator);
      };
    }
  }

  private VersionRange(List<List<Bound>> alternatives) {
    this.alternatives = alternatives.stream().map(List::copyOf).toList();
  }

  public static VersionRange parse(String expression) {
    if (expression == null || expression.isBlank()) {
      throw new IllegalArgumentException("A version range is required; use * for any release");
    }
    var clauses = new ArrayList<List<Bound>>();
    for (String alternative : expression.trim().split("\\|\\|", -1)) {
      if (alternative.isBlank()) throw new IllegalArgumentException("Empty range alternative");
      var bounds = new ArrayList<Bound>();
      // Whitespace after an explicit comparator is permitted, including >= 1.2.3.
      String normalized = alternative.trim().replaceAll("(>=|<=|>|<|=|\\^|~)\\s+", "$1");
      for (String token : normalized.split("\\s+")) addToken(token, bounds);
      clauses.add(bounds);
    }
    return new VersionRange(clauses);
  }

  private static void addToken(String token, List<Bound> bounds) {
    if (token.equals("*") || token.equalsIgnoreCase("x")) return;
    if (token.startsWith("^") || token.startsWith("~")) {
      SemanticVersion low = SemanticVersion.parse(token.substring(1));
      bounds.add(new Bound(">=", low));
      SemanticVersion high;
      if (token.startsWith("~")) {
        high = SemanticVersion.release(low.major(), low.minor().add(ONE), ZERO);
      } else if (low.major().signum() > 0) {
        high = SemanticVersion.release(low.major().add(ONE), ZERO, ZERO);
      } else if (low.minor().signum() > 0) {
        high = SemanticVersion.release(ZERO, low.minor().add(ONE), ZERO);
      } else {
        high = SemanticVersion.release(ZERO, ZERO, low.patch().add(ONE));
      }
      bounds.add(new Bound("<", high));
      return;
    }
    for (String operator : List.of(">=", "<=", ">", "<", "=")) {
      if (token.startsWith(operator)) {
        bounds.add(new Bound(operator, SemanticVersion.parse(token.substring(operator.length()))));
        return;
      }
    }
    var match = PARTIAL.matcher(token);
    if (match.matches()) {
      String[] pieces = {match.group(1), match.group(2), match.group(3)};
      int explicit = 0;
      boolean wildcard = false;
      var values = new BigInteger[] {ZERO, ZERO, ZERO};
      for (int i = 0; i < pieces.length; i++) {
        String piece = pieces[i];
        if (piece == null || piece.equals("*") || piece.equalsIgnoreCase("x")) {
          wildcard = true;
        } else {
          if (wildcard || (piece.length() > 1 && piece.startsWith("0"))) {
            throw new IllegalArgumentException("Invalid partial version: " + token);
          }
          explicit++;
          values[i] = new BigInteger(piece);
        }
      }
      if (explicit == 0) return;
      SemanticVersion low = SemanticVersion.release(values[0], values[1], values[2]);
      if (explicit == 3) {
        bounds.add(new Bound("=", low));
      } else {
        bounds.add(new Bound(">=", low));
        bounds.add(
            new Bound(
                "<",
                explicit == 1
                    ? SemanticVersion.release(values[0].add(ONE), ZERO, ZERO)
                    : SemanticVersion.release(values[0], values[1].add(ONE), ZERO)));
      }
      return;
    }
    bounds.add(new Bound("=", SemanticVersion.parse(token)));
  }

  public boolean contains(String version) {
    return contains(SemanticVersion.parse(version));
  }

  public boolean contains(SemanticVersion version) {
    return alternatives.stream()
        .anyMatch(
            bounds ->
                bounds.stream().allMatch(bound -> bound.accepts(version))
                    && (version.prerelease().isEmpty()
                        || bounds.stream()
                            .anyMatch(
                                bound ->
                                    !bound.version().prerelease().isEmpty()
                                        && bound.version().sameCore(version))));
  }
}
