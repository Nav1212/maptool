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
package net.rptools.maptool.model.library.addon;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.addon.AddOnManifest;
import net.rptools.maptool.model.library.LibraryInfo;
import net.rptools.maptool.model.library.addon.compatibility.AddOnCompatibility;
import net.rptools.maptool.model.library.addon.compatibility.AddOnCompatibilityException;
import net.rptools.maptool.model.library.proto.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AddOnLibraryCompatibilityTest {
  @TempDir Path temp;

  private AddOnLibrary library(String namespace, String version, AddOnManifest.Dependency... deps) {
    var manifest = new AddOnManifest(namespace, version, "", List.of(deps));
    var library = mock(AddOnLibrary.class);
    when(library.getCompatibilityManifest()).thenReturn(manifest);
    when(library.getNamespace()).thenReturn(CompletableFuture.completedFuture(namespace));
    when(library.getLibraryInfo())
        .thenReturn(
            CompletableFuture.completedFuture(
                new LibraryInfo(
                    namespace,
                    namespace,
                    version,
                    "",
                    "",
                    new String[0],
                    "",
                    "",
                    "",
                    false,
                    null,
                    null)));
    when(library.toDetailsDto())
        .thenReturn(
            AddOnLibraryDto.newBuilder().setNamespace(namespace).setVersion(version).build());
    when(library.getAssetKey()).thenReturn(new MD5Key(new byte[] {1}));
    return library;
  }

  @Test
  void invalidReplacementDoesNotRemoveOrReinitializeTheWorkingBundle() {
    var manager = new AddOnLibraryManager(() -> "1.18.0");
    var old = library("example.core", "1.0.0");
    var app =
        library(
            "example.app", "1.0.0", new AddOnManifest.Dependency("example.core", "^1.0.0", false));
    manager.registerLibraries(List.of(app, old), false);
    var replacement = library("example.core", "2.0.0");
    var error =
        assertThrows(
            AddOnCompatibilityException.class,
            () -> manager.registerLibraries(List.of(replacement), true));
    assertTrue(error.getMessage().contains("example.core ^1.0.0"));
    assertSame(old, manager.getLibrary("example.core"));
    verify(old, never()).cleanup();
    verify(old, times(1)).initialize();
    verify(replacement, never()).initialize();
  }

  @Test
  void stagesACompleteBatchAndInvokesInitializationInDependencyOrder() {
    var manager = new AddOnLibraryManager(() -> "1.18.0");
    var core = library("example.core", "1.0.0");
    var app =
        library(
            "example.app", "1.0.0", new AddOnManifest.Dependency("example.core", "^1.0.0", false));
    var order = new ArrayList<String>();
    doAnswer(
            call -> {
              assertSame(core, manager.getLibrary("example.core"));
              order.add("core");
              return null;
            })
        .when(core)
        .initialize();
    doAnswer(
            call -> {
              assertSame(core, manager.getLibrary("example.core"));
              order.add("app");
              return null;
            })
        .when(app)
        .initialize();
    manager.registerLibraries(List.of(app, core), false);
    assertEquals(List.of("core", "app"), order);
  }

  @Test
  void retainsCompatibilityMetadataWhenProducingCampaignAndWireDetails() throws Exception {
    var definition =
        AddOnLibraryDto.newBuilder()
            .setNamespace("example.app")
            .setName("Example")
            .setVersion("1.0.0")
            .setSupportedMapToolVersions("^1.18.0")
            .setAllowsUriAccess(true)
            .addDependencies(
                AddOnDependencyDto.newBuilder()
                    .setNamespace("example.optional")
                    .setVersionRange("^1.0.0")
                    .setOptional(true))
            .build();
    var addon =
        AddOnLibrary.fromDto(
            new MD5Key(new byte[] {1}),
            definition,
            MTScriptPropertiesDto.getDefaultInstance(),
            AddOnLibraryEventsDto.getDefaultInstance(),
            AddOnStatSheetsDto.getDefaultInstance(),
            AddonSlashCommandsDto.getDefaultInstance(),
            Map.of());
    assertEquals(definition, addon.toDetailsDto());
    assertTrue(
        AddOnCompatibility.describe(addon.getCompatibilityManifest())
            .contains("Requires MapTool ^1.18.0"));
    var manager = new AddOnLibraryManager(() -> "1.18.0");
    var stub = library("example.app", "1.0.0");
    when(stub.getCompatibilityManifest()).thenReturn(addon.getCompatibilityManifest());
    when(stub.toDetailsDto()).thenReturn(addon.toDetailsDto());
    manager.registerLibraries(List.of(stub), false);
    assertEquals(definition, manager.toDto().get().getLibraries(0).getDetails());
  }

  @Test
  void importerRejectsMalformedMetadataBeforeReadingOrRegisteringAssets() throws Exception {
    var archive = temp.resolve("invalid.mtlib");
    try (var out = new ZipOutputStream(Files.newOutputStream(archive))) {
      out.putNextEntry(new ZipEntry("library.json"));
      out.write(
          "{\"namespace\":\"example.invalid\",\"version\":\"1.0.0\",\"supportedMapToolVersions\":\"not-a-range\"}"
              .getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
    var error =
        assertThrows(
            java.io.IOException.class,
            () -> new AddOnLibraryImporter().importFromFile(archive.toFile()));
    assertTrue(error.getMessage().contains("invalid range"));
  }
}
