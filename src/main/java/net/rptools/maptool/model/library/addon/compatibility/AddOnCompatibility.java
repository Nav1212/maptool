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
package net.rptools.maptool.model.library.addon.compatibility;

import java.util.stream.Collectors;
import net.rptools.maptool.addon.AddOnManifest;
import net.rptools.maptool.addon.InstallationPlanner;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.model.library.proto.AddOnLibraryDto;

/** Maps archive/wire compatibility metadata to the environment-independent installation planner. */
public final class AddOnCompatibility {
  private AddOnCompatibility() {}

  public static AddOnManifest fromDto(AddOnLibraryDto dto) {
    return new AddOnManifest(
        dto.getNamespace(),
        dto.getVersion(),
        dto.getSupportedMapToolVersions(),
        dto.getDependenciesList().stream()
            .map(
                dependency ->
                    new AddOnManifest.Dependency(
                        dependency.getNamespace(),
                        dependency.getVersionRange(),
                        dependency.getOptional()))
            .toList());
  }

  public static void validateMetadata(AddOnLibraryDto dto) {
    var errors =
        InstallationPlanner.validateManifest(fromDto(dto)).stream()
            .filter(diagnostic -> diagnostic.severity() == InstallationPlanner.Severity.ERROR)
            .toList();
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(
          errors.stream()
              .map(InstallationPlanner.Diagnostic::message)
              .collect(Collectors.joining("\n")));
    }
  }

  /** Development builds must opt into a semantic compatibility version rather than invent one. */
  public static String currentVersion() {
    return System.getProperty("maptool.addonCompatibilityVersion", MapTool.getVersion());
  }

  public static String describe(AddOnManifest manifest) {
    String status =
        manifest.supportedMapToolVersions().isEmpty()
            ? "MapTool compatibility: unknown (legacy bundle)"
            : "Requires MapTool " + manifest.supportedMapToolVersions();
    if (!manifest.dependencies().isEmpty()) {
      status +=
          "\n"
              + manifest.dependencies().stream()
                  .map(
                      dependency ->
                          (dependency.optional() ? "Optional: " : "Required: ")
                              + dependency.namespace()
                              + " "
                              + dependency.versionRange())
                  .collect(Collectors.joining("\n"));
    }
    return status;
  }
}
