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

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstallationPlannerTest {
  private static AddOnManifest legacy(String namespace, String version) {
    return new AddOnManifest(namespace, version, "", List.of());
  }

  private static AddOnManifest declared(
      String namespace, String range, AddOnManifest.Dependency... dependencies) {
    return new AddOnManifest(namespace, "1.0.0", range, List.of(dependencies));
  }

  private static AddOnManifest.Dependency needs(String namespace, String range) {
    return new AddOnManifest.Dependency(namespace, range, false);
  }

  private static boolean has(InstallationPlanner.Plan plan, InstallationPlanner.Code code) {
    return plan.diagnostics().stream().anyMatch(d -> d.code() == code);
  }

  @Test
  void keepsLegacyBundlesLoadableWithoutInventingCompatibility() {
    var plan =
        InstallationPlanner.plan(
            "DEVELOPMENT", List.of(), List.of(legacy("example.legacy", "old-beta")), false);
    assertTrue(plan.canInstall());
    assertTrue(has(plan, InstallationPlanner.Code.UNKNOWN_COMPATIBILITY));
    assertEquals("example.legacy", plan.installationOrder().getFirst().namespace());
  }

  @Test
  void installsDependenciesFirstIndependentlyOfInputOrder() {
    var core = declared("example.core", ">=1.18.0 <2.0.0");
    var ui = declared("example.ui", "^1.18.0", needs(core.namespace(), "^1.0.0"));
    var top = declared("example.top", "1.18.x", needs(ui.namespace(), "~1.0.0"));
    var plan = InstallationPlanner.plan("1.18.3", List.of(), List.of(top, ui, core), false);
    assertTrue(plan.canInstall(), plan.summary());
    assertEquals(List.of(core, ui, top), plan.installationOrder());
  }

  @Test
  void reportsMissingRequiredButAllowsMissingOptionalDependencies() {
    var required = declared("example.required", "*", needs("missing.required", ">=2.0.0"));
    var optional =
        declared(
            "example.optional",
            "*",
            new AddOnManifest.Dependency("missing.optional", "^1.0.0", true));
    var bad = InstallationPlanner.plan("1.18.0", List.of(), List.of(required), false);
    assertFalse(bad.canInstall());
    assertTrue(has(bad, InstallationPlanner.Code.MISSING_DEPENDENCY));
    assertTrue(bad.summary().contains("missing.required >=2.0.0"));
    var good = InstallationPlanner.plan("1.18.0", List.of(), List.of(optional), false);
    assertTrue(good.canInstall());
    assertTrue(has(good, InstallationPlanner.Code.OPTIONAL_DEPENDENCY_MISSING));
  }

  @Test
  void optionalDependencyMustBeCompatibleIfPresent() {
    var addon =
        declared("example.app", "*", new AddOnManifest.Dependency("optional", "^2.0.0", true));
    var plan =
        InstallationPlanner.plan(
            "1.18.0", List.of(legacy("optional", "1.0.0")), List.of(addon), true);
    assertFalse(plan.canInstall());
    assertTrue(has(plan, InstallationPlanner.Code.INCOMPATIBLE_DEPENDENCY));
  }

  @Test
  void rejectsCyclesAndDuplicateNamespacesCaseInsensitively() {
    var a = declared("a", "*", needs("b", "*"));
    var b = declared("b", "*", needs("a", "*"));
    var cycle = InstallationPlanner.plan("1.18.0", List.of(), List.of(b, a), false);
    assertFalse(cycle.canInstall());
    assertTrue(has(cycle, InstallationPlanner.Code.DEPENDENCY_CYCLE));
    assertTrue(cycle.summary().contains("a -> b -> a"));
    assertTrue(cycle.installationOrder().isEmpty());
    var duplicate =
        InstallationPlanner.plan(
            "1.18.0",
            List.of(),
            List.of(legacy("Example.A", "1.0.0"), legacy("example.a", "2.0.0")),
            true);
    assertFalse(duplicate.canInstall());
    assertTrue(has(duplicate, InstallationPlanner.Code.DUPLICATE_IDENTITY));
  }

  @Test
  void validatesInstalledDependentsBeforeReplacingTheirDependency() {
    var core = legacy("core", "1.0.0");
    var app = declared("app", "*", needs("core", "^1.0.0"));
    var installed = new ArrayList<>(List.of(core, app));
    var plan =
        InstallationPlanner.plan("1.18.0", installed, List.of(legacy("core", "2.0.0")), true);
    assertFalse(plan.canInstall());
    assertTrue(has(plan, InstallationPlanner.Code.INCOMPATIBLE_DEPENDENCY));
    assertEquals(List.of(core, app), installed, "Preflight must never mutate the installed set");
    var replacement =
        InstallationPlanner.plan("1.18.0", installed, List.of(legacy("core", "1.3.0")), true);
    assertTrue(replacement.canInstall(), replacement.summary());
    assertFalse(InstallationPlanner.plan("1.18.0", installed, List.of(core), false).canInstall());
  }

  @Test
  void rejectsInvalidDeclarationsAndUnverifiableHostInsteadOfAssumingCompatibility() {
    var invalid =
        InstallationPlanner.plan(
            "1.18.0", List.of(), List.of(declared("bad", "1.2.3 - 2.0.0")), false);
    assertTrue(has(invalid, InstallationPlanner.Code.INVALID_RANGE));
    assertFalse(invalid.canInstall());
    var unknown =
        InstallationPlanner.plan(
            "SNAPSHOT-abcd", List.of(), List.of(declared("new", "^1.18.0")), false);
    assertTrue(has(unknown, InstallationPlanner.Code.UNKNOWN_HOST_VERSION));
    assertFalse(unknown.canInstall());
    var incompatible =
        InstallationPlanner.plan("1.17.0", List.of(), List.of(declared("new", "^1.18.0")), false);
    assertTrue(has(incompatible, InstallationPlanner.Code.MAPTOOL_VERSION_INCOMPATIBLE));
    assertFalse(incompatible.canInstall());
  }

  @Test
  void requirementVersionChecksReportLegacyDependenciesWithUnparseableVersions() {
    var plan =
        InstallationPlanner.plan(
            "1.18.0",
            List.of(legacy("core", "old-beta")),
            List.of(declared("app", "*", needs("core", "*"))),
            false);
    assertFalse(plan.canInstall());
    assertTrue(has(plan, InstallationPlanner.Code.INVALID_VERSION));
  }
}
