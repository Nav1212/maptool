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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Preflights the final package set before an add-on is activated or an old bundle is removed. */
public final class InstallationPlanner {
  public enum Severity {
    ERROR,
    WARNING
  }

  public enum Code {
    INVALID_IDENTITY,
    INVALID_VERSION,
    INVALID_RANGE,
    DUPLICATE_IDENTITY,
    MISSING_DEPENDENCY,
    OPTIONAL_DEPENDENCY_MISSING,
    INCOMPATIBLE_DEPENDENCY,
    MAPTOOL_VERSION_INCOMPATIBLE,
    UNKNOWN_HOST_VERSION,
    UNKNOWN_COMPATIBILITY,
    DEPENDENCY_CYCLE
  }

  public record Diagnostic(Severity severity, Code code, String namespace, String message) {}

  public record Plan(List<AddOnManifest> installationOrder, List<Diagnostic> diagnostics) {
    public Plan {
      installationOrder = List.copyOf(installationOrder);
      diagnostics = List.copyOf(diagnostics);
    }

    public boolean canInstall() {
      return diagnostics.stream().noneMatch(d -> d.severity() == Severity.ERROR);
    }

    public String summary() {
      return diagnostics.stream()
          .map(Diagnostic::message)
          .reduce((a, b) -> a + "\n" + b)
          .orElse("All declared add-on requirements are satisfied.");
    }
  }

  private InstallationPlanner() {}

  /** Checks declaration syntax without requiring an installed environment. */
  public static List<Diagnostic> validateManifest(AddOnManifest manifest) {
    var diagnostics = new ArrayList<Diagnostic>();
    if (manifest.namespace().isBlank()) {
      error(diagnostics, Code.INVALID_IDENTITY, manifest, "An add-on namespace is required.");
    }
    if (manifest.declaresRequirements()) {
      try {
        SemanticVersion.parse(manifest.version());
      } catch (IllegalArgumentException e) {
        error(
            diagnostics,
            Code.INVALID_VERSION,
            manifest,
            manifest.namespace()
                + ": packages declaring requirements must use a semantic package version. "
                + e.getMessage());
      }
    }
    if (!manifest.supportedMapToolVersions().isBlank()) {
      validateRange(diagnostics, manifest, manifest.supportedMapToolVersions(), "MapTool");
    }
    var seen = new HashSet<String>();
    for (var dependency : manifest.dependencies()) {
      if (dependency.namespace().isBlank()) {
        error(
            diagnostics,
            Code.INVALID_IDENTITY,
            manifest,
            manifest.namespace() + ": dependency namespace is required.");
      } else if (!seen.add(dependency.namespace())) {
        error(
            diagnostics,
            Code.DUPLICATE_IDENTITY,
            manifest,
            manifest.namespace()
                + ": dependency "
                + dependency.namespace()
                + " is declared more than once. Combine its requirements.");
      }
      validateRange(diagnostics, manifest, dependency.versionRange(), dependency.namespace());
    }
    return List.copyOf(diagnostics);
  }

  private static void validateRange(
      List<Diagnostic> diagnostics, AddOnManifest manifest, String expression, String target) {
    try {
      VersionRange.parse(expression);
    } catch (IllegalArgumentException e) {
      error(
          diagnostics,
          Code.INVALID_RANGE,
          manifest,
          manifest.namespace()
              + ": invalid range for "
              + target
              + ": "
              + expression
              + ". "
              + e.getMessage());
    }
  }

  /** Includes installed dependents so a replacement cannot invalidate a working dependency set. */
  public static Plan plan(
      String mapToolVersion,
      Collection<AddOnManifest> installed,
      Collection<AddOnManifest> candidates,
      boolean allowReplacement) {
    var diagnostics = new ArrayList<Diagnostic>();
    var finalSet = new TreeMap<String, AddOnManifest>();
    var requested = new TreeMap<String, AddOnManifest>();
    for (var current : installed) {
      if (finalSet.putIfAbsent(current.namespace(), current) != null) {
        error(
            diagnostics,
            Code.DUPLICATE_IDENTITY,
            current,
            "Multiple installed add-ons use namespace "
                + current.namespace()
                + ". Keep one identity.");
      }
    }
    for (var candidate : candidates) {
      if (requested.putIfAbsent(candidate.namespace(), candidate) != null) {
        error(
            diagnostics,
            Code.DUPLICATE_IDENTITY,
            candidate,
            "The installation contains multiple bundles for "
                + candidate.namespace()
                + ". Select one version.");
        continue;
      }
      if (!allowReplacement && finalSet.containsKey(candidate.namespace())) {
        error(
            diagnostics,
            Code.DUPLICATE_IDENTITY,
            candidate,
            candidate.namespace()
                + " is already installed. Use the explicit replacement workflow.");
      }
      finalSet.put(candidate.namespace(), candidate);
    }
    var graph = new TreeMap<String, Set<String>>();
    for (var manifest : finalSet.values()) {
      var syntax = validateManifest(manifest);
      diagnostics.addAll(syntax);
      graph.put(manifest.namespace(), new java.util.TreeSet<>());
      if (manifest.supportedMapToolVersions().isBlank()) {
        diagnostics.add(
            new Diagnostic(
                Severity.WARNING,
                Code.UNKNOWN_COMPATIBILITY,
                manifest.namespace(),
                manifest.namespace()
                    + ": MapTool compatibility is unknown (no supportedMapToolVersions"
                    + " declaration)."));
      } else if (syntax.stream().noneMatch(d -> d.code() == Code.INVALID_RANGE)) {
        try {
          if (!VersionRange.parse(manifest.supportedMapToolVersions()).contains(mapToolVersion)) {
            error(
                diagnostics,
                Code.MAPTOOL_VERSION_INCOMPATIBLE,
                manifest,
                manifest.namespace()
                    + " requires MapTool "
                    + manifest.supportedMapToolVersions()
                    + "; current version is "
                    + mapToolVersion
                    + ". Choose a compatible bundle or MapTool release.");
          }
        } catch (IllegalArgumentException e) {
          error(
              diagnostics,
              Code.UNKNOWN_HOST_VERSION,
              manifest,
              "Cannot verify "
                  + manifest.namespace()
                  + " against MapTool version "
                  + mapToolVersion
                  + ". Use a released semantic version or an explicit development compatibility"
                  + " version.");
        }
      }
      for (var dependency : manifest.dependencies()) {
        var target = finalSet.get(dependency.namespace());
        if (target == null) {
          diagnostics.add(
              new Diagnostic(
                  dependency.optional() ? Severity.WARNING : Severity.ERROR,
                  dependency.optional()
                      ? Code.OPTIONAL_DEPENDENCY_MISSING
                      : Code.MISSING_DEPENDENCY,
                  manifest.namespace(),
                  manifest.namespace()
                      + ": "
                      + (dependency.optional() ? "optional add-on " : "install add-on ")
                      + dependency.namespace()
                      + " "
                      + dependency.versionRange()
                      + " is missing."));
          continue;
        }
        graph.get(manifest.namespace()).add(dependency.namespace());
        try {
          var range = VersionRange.parse(dependency.versionRange());
          if (!range.contains(target.version())) {
            error(
                diagnostics,
                Code.INCOMPATIBLE_DEPENDENCY,
                manifest,
                manifest.namespace()
                    + " requires "
                    + dependency.namespace()
                    + " "
                    + dependency.versionRange()
                    + "; selected version is "
                    + target.version()
                    + ". Select a compatible dependency version.");
          }
        } catch (IllegalArgumentException e) {
          // Invalid range syntax is already reported by validateManifest; report bad target
          // versions separately.
          try {
            SemanticVersion.parse(target.version());
          } catch (IllegalArgumentException invalid) {
            error(
                diagnostics,
                Code.INVALID_VERSION,
                manifest,
                "Cannot verify dependency "
                    + target.namespace()
                    + " with non-semantic version "
                    + target.version()
                    + ".");
          }
        }
      }
    }
    var order = new ArrayList<String>();
    var visiting = new LinkedHashSet<String>();
    var complete = new HashSet<String>();
    for (String namespace : graph.keySet())
      visit(namespace, graph, visiting, complete, order, diagnostics);
    boolean valid = diagnostics.stream().noneMatch(d -> d.severity() == Severity.ERROR);
    return new Plan(
        valid
            ? order.stream().filter(requested::containsKey).map(requested::get).toList()
            : List.of(),
        diagnostics);
  }

  private static void visit(
      String namespace,
      Map<String, Set<String>> graph,
      LinkedHashSet<String> visiting,
      Set<String> complete,
      List<String> order,
      List<Diagnostic> diagnostics) {
    if (complete.contains(namespace)) return;
    if (!visiting.add(namespace)) {
      var path = new ArrayList<>(visiting);
      path = new ArrayList<>(path.subList(path.indexOf(namespace), path.size()));
      path.add(namespace);
      diagnostics.add(
          new Diagnostic(
              Severity.ERROR,
              Code.DEPENDENCY_CYCLE,
              namespace,
              "Dependency cycle: "
                  + String.join(" -> ", path)
                  + ". Remove a dependency edge before activation."));
      return;
    }
    for (String dependency : graph.get(namespace))
      visit(dependency, graph, visiting, complete, order, diagnostics);
    visiting.remove(namespace);
    complete.add(namespace);
    order.add(namespace);
  }

  private static void error(
      List<Diagnostic> diagnostics, Code code, AddOnManifest manifest, String message) {
    diagnostics.add(new Diagnostic(Severity.ERROR, code, manifest.namespace(), message));
  }
}
