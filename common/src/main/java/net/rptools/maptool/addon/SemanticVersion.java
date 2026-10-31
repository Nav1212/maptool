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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** A semantic version whose ordering follows SemVer 2.0.0 (build metadata is ignored). */
public record SemanticVersion(
    BigInteger major, BigInteger minor, BigInteger patch, List<String> prerelease)
    implements Comparable<SemanticVersion> {
  private static final Pattern VERSION =
      Pattern.compile(
          "v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"
              + "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?"
              + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?");

  public SemanticVersion {
    Objects.requireNonNull(major);
    Objects.requireNonNull(minor);
    Objects.requireNonNull(patch);
    prerelease = List.copyOf(prerelease);
    if (major.signum() < 0 || minor.signum() < 0 || patch.signum() < 0) {
      throw new IllegalArgumentException("Version numbers must be nonnegative");
    }
    for (String part : prerelease) {
      if (!part.matches("[0-9A-Za-z-]+")
          || (part.matches("[0-9]+") && part.length() > 1 && part.startsWith("0"))) {
        throw new IllegalArgumentException("Invalid prerelease identifier: " + part);
      }
    }
  }

  public static SemanticVersion parse(String text) {
    if (text == null) {
      throw new IllegalArgumentException("A semantic version is required");
    }
    var match = VERSION.matcher(text.trim());
    if (!match.matches()) {
      throw new IllegalArgumentException(
          "Expected a semantic version (major.minor.patch): " + text);
    }
    return new SemanticVersion(
        new BigInteger(match.group(1)),
        new BigInteger(match.group(2)),
        new BigInteger(match.group(3)),
        match.group(4) == null ? List.of() : Arrays.asList(match.group(4).split("\\.")));
  }

  public static SemanticVersion release(BigInteger major, BigInteger minor, BigInteger patch) {
    return new SemanticVersion(major, minor, patch, List.of());
  }

  public boolean sameCore(SemanticVersion other) {
    return major.equals(other.major) && minor.equals(other.minor) && patch.equals(other.patch);
  }

  @Override
  public int compareTo(SemanticVersion other) {
    int result = major.compareTo(other.major);
    if (result == 0) result = minor.compareTo(other.minor);
    if (result == 0) result = patch.compareTo(other.patch);
    if (result != 0) return result;
    if (prerelease.isEmpty()) return other.prerelease.isEmpty() ? 0 : 1;
    if (other.prerelease.isEmpty()) return -1;
    for (int i = 0; i < Math.min(prerelease.size(), other.prerelease.size()); i++) {
      String left = prerelease.get(i);
      String right = other.prerelease.get(i);
      boolean leftNumber = left.matches("[0-9]+");
      boolean rightNumber = right.matches("[0-9]+");
      result =
          leftNumber && rightNumber
              ? new BigInteger(left).compareTo(new BigInteger(right))
              : leftNumber != rightNumber ? (leftNumber ? -1 : 1) : left.compareTo(right);
      if (result != 0) return result;
    }
    return Integer.compare(prerelease.size(), other.prerelease.size());
  }

  @Override
  public String toString() {
    return major
        + "."
        + minor
        + "."
        + patch
        + (prerelease.isEmpty() ? "" : "-" + String.join(".", prerelease));
  }
}
