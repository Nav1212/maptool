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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;

/** Reads the optional public rules-record document from an already-installed .mtlib archive. */
public final class ContentPackageArchive {
  public static final String COMPENDIUM_FILE = "compendium.json";
  private static final int MAX_DOCUMENT_BYTES = 8 * 1024 * 1024;

  private ContentPackageArchive() {}

  public static boolean registerCompendium(ContentStore target, String namespace, byte[] archive)
      throws IOException {
    if (archive == null || archive.length < 4 || archive[0] != 'P' || archive[1] != 'K') {
      throw new IOException("Add-on archive is unavailable or invalid");
    }
    String document = null;
    try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
      for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        if (!entry.getName().equals(COMPENDIUM_FILE)) continue;
        if (document != null || entry.isDirectory())
          throw new IOException("Duplicate or invalid compendium document");
        byte[] bytes = zip.readNBytes(MAX_DOCUMENT_BYTES + 1);
        if (bytes.length > MAX_DOCUMENT_BYTES)
          throw new IOException("Compendium exceeds the 8 MiB document limit");
        document =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
      }
    }
    if (document == null) return false;
    target.registerCompendiumJson(namespace, document);
    return true;
  }
}
