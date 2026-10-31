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

/** Validated immutable content state; the host authenticates the writer before applying it. */
public record ContentRevisionState(String json, long revision) {
  public ContentRevisionState {
    if (revision < 0) throw new IllegalArgumentException("Invalid content revision");
    json = ContentStore.fromJson(json).toJson();
  }

  public ContentRevisionState update(long expectedRevision, String replacementJson) {
    if (expectedRevision != revision)
      throw new IllegalArgumentException("Content revision conflict");
    if (revision == Long.MAX_VALUE)
      throw new IllegalArgumentException("Content revision exhausted");
    return new ContentRevisionState(replacementJson, revision + 1);
  }

  /** New replacement epoch rejects edits queued against either the old or loaded campaign. */
  public static long replacementRevision(long previousRevision, long loadedRevision) {
    if (previousRevision < 0 || loadedRevision < 0)
      throw new IllegalArgumentException("Invalid content revision");
    long newest = Math.max(previousRevision, loadedRevision);
    if (newest == Long.MAX_VALUE) throw new IllegalArgumentException("Content revision exhausted");
    return newest + 1;
  }
}
