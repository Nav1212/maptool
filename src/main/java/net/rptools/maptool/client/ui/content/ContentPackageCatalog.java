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
package net.rptools.maptool.client.ui.content;

import java.util.Set;
import net.rptools.maptool.content.ContentPackageArchive;
import net.rptools.maptool.content.ContentStore;
import net.rptools.maptool.model.AssetManager;
import net.rptools.maptool.model.library.LibraryManager;
import net.rptools.maptool.model.library.LibraryType;
import net.rptools.maptool.model.library.addon.AddOnLibrary;

/** Explicit reload keeps package import independent of campaign content update transactions. */
final class ContentPackageCatalog {
  private ContentPackageCatalog() {}

  static void reload(ContentStore target) {
    var candidate = ContentStore.fromJson(target.toJson());
    candidate.searchCompendium("", "", Set.of()).stream()
        .map(record -> record.reference().namespace())
        .distinct()
        .toList()
        .forEach(candidate::removeCompendium);
    try {
      var libraries = new LibraryManager();
      for (var info : libraries.getLibraries(LibraryType.ADD_ON)) {
        var library = libraries.getLibrary(info.namespace()).orElseThrow();
        if (!(library instanceof AddOnLibrary addOn)) continue;
        var asset = AssetManager.getAsset(addOn.getAssetKey());
        if (asset == null)
          throw new IllegalStateException("Missing archive for " + info.namespace());
        ContentPackageArchive.registerCompendium(candidate, info.namespace(), asset.getData());
      }
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Compendium reload was interrupted", failure);
    } catch (Exception failure) {
      throw new IllegalArgumentException(
          "Could not reload add-on compendiums: " + failure.getMessage(), failure);
    }
    target.replaceFromJson(candidate.toJson());
  }
}
