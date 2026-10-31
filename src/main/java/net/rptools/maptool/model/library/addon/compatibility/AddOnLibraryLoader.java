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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.rptools.lib.MD5Key;
import net.rptools.maptool.model.AssetManager;
import net.rptools.maptool.model.library.addon.AddOnLibrary;
import net.rptools.maptool.model.library.addon.AddOnLibraryImporter;

/** Collects an announced dependency set before activation, regardless of asset arrival order. */
public final class AddOnLibraryLoader {
  private AddOnLibraryLoader() {}

  public static CompletableFuture<List<AddOnLibrary>> load(List<MD5Key> keys) {
    var imports = new ArrayList<CompletableFuture<AddOnLibrary>>();
    for (var key : keys) {
      var imported = new CompletableFuture<AddOnLibrary>();
      imports.add(imported);
      AssetManager.getAssetAsynchronously(
          key,
          assetKey -> {
            try {
              var asset = AssetManager.getAsset(assetKey);
              imported.complete(new AddOnLibraryImporter().importFromAsset(asset));
            } catch (Exception e) {
              imported.completeExceptionally(e);
            }
          });
    }
    return CompletableFuture.allOf(imports.toArray(CompletableFuture[]::new))
        .thenApply(ignored -> imports.stream().map(CompletableFuture::join).toList());
  }
}
