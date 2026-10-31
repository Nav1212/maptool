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
package net.rptools.maptool.content;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class ContentPackageArchiveTest {
  @Test
  void optionalDocumentRegistersVersionedRecordsAndMissingDocumentLeavesStoreUntouched()
      throws Exception {
    var store = new ContentStore();
    String json =
        """
        {"schemaVersion":1,"namespace":"example","records":[
          {"reference":{"namespace":"example","id":"guard"},"kind":"actor",
           "name":"Guard","description":"Town guard","systemData":{"properties":{"health":12}}}
        ]}
        """;
    assertTrue(
        ContentPackageArchive.registerCompendium(
            store, "example", archive("compendium.json", json.getBytes(StandardCharsets.UTF_8))));
    assertEquals("Guard", store.searchCompendium("", "actor", Set.of()).getFirst().name());
    assertFalse(
        ContentPackageArchive.registerCompendium(
            store, "example", archive("library.json", "{}".getBytes(StandardCharsets.UTF_8))));
    assertEquals(1, store.searchCompendium("", "", Set.of()).size());
  }

  @Test
  void invalidArchivesEncodingDocumentsAndWrongNamespaceFail() throws Exception {
    var store = new ContentStore();
    assertThrows(
        IOException.class,
        () -> ContentPackageArchive.registerCompendium(store, "example", new byte[0]));
    assertThrows(
        IOException.class,
        () ->
            ContentPackageArchive.registerCompendium(
                store,
                "example",
                archive("compendium.json", new byte[] {(byte) 0xc3, (byte) 0x28})));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ContentPackageArchive.registerCompendium(
                store,
                "example",
                archive("compendium.json", "invalid".getBytes(StandardCharsets.UTF_8))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ContentPackageArchive.registerCompendium(
                store,
                "other",
                archive(
                    "compendium.json",
                    "{\"schemaVersion\":1,\"namespace\":\"example\",\"records\":[]}"
                        .getBytes(StandardCharsets.UTF_8))));
    assertTrue(store.searchCompendium("", "", Set.of()).isEmpty());
  }

  private static byte[] archive(String name, byte[] content) throws IOException {
    var out = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry(name));
      zip.write(content);
      zip.closeEntry();
    }
    return out.toByteArray();
  }
}
