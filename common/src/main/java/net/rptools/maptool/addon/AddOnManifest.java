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

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Compatibility metadata independent of GUI, archive storage, and generated protobuf classes. */
public record AddOnManifest(
    String namespace,
    String version,
    String supportedMapToolVersions,
    List<Dependency> dependencies) {
  public record Dependency(String namespace, String versionRange, boolean optional) {
    public Dependency {
      namespace = normalize(namespace);
      versionRange = Objects.requireNonNullElse(versionRange, "").trim();
      if (versionRange.isEmpty()) versionRange = "*";
    }
  }

  public AddOnManifest {
    namespace = normalize(namespace);
    version = Objects.requireNonNullElse(version, "").trim();
    supportedMapToolVersions = Objects.requireNonNullElse(supportedMapToolVersions, "").trim();
    dependencies = List.copyOf(Objects.requireNonNullElse(dependencies, List.of()));
  }

  private static String normalize(String namespace) {
    return Objects.requireNonNullElse(namespace, "").trim().toLowerCase(Locale.ROOT);
  }

  public boolean declaresRequirements() {
    return !supportedMapToolVersions.isEmpty() || !dependencies.isEmpty();
  }
}
