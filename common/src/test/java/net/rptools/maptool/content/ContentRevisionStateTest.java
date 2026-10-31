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

import com.google.gson.Gson;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContentRevisionStateTest {
  @Test
  void restoreKeepsRevisionAndRejectsStaleWritesWithoutMutatingState() {
    var store = new ContentStore();
    store.putJournal(
        new ContentStore.Viewer("gm", true),
        new ContentStore.JournalEntry(
            "note", "Title", "", Set.of(), "Body", Set.of(), Set.of(), true, Set.of()));
    var state = new ContentRevisionState("", 5).update(5, store.toJson());
    var gson = new Gson();
    var restored = gson.fromJson(gson.toJson(state), ContentRevisionState.class);
    assertEquals(6, restored.revision());
    assertNotNull(
        ContentStore.fromJson(restored.json())
            .journal(new ContentStore.Viewer("alice", false), "note"));
    assertThrows(IllegalArgumentException.class, () -> restored.update(5, "invalid"));
    assertEquals(state, restored);
    assertThrows(IllegalArgumentException.class, () -> restored.update(6, "invalid"));
    assertEquals(state, restored);
  }

  @Test
  void invalidVersionsAndOverflowFailWithoutWrappingTheRevision() {
    assertThrows(IllegalArgumentException.class, () -> new ContentRevisionState("", -1));
    var exhausted = new ContentRevisionState("", Long.MAX_VALUE);
    assertThrows(IllegalArgumentException.class, () -> exhausted.update(Long.MAX_VALUE, ""));
    assertEquals(Long.MAX_VALUE, exhausted.revision());
  }

  @Test
  void replacementEpochInvalidatesBothInflightAndLoadedRevisionWrites() {
    long epoch = ContentRevisionState.replacementRevision(12, 5);
    assertEquals(13, epoch);
    var replacement = new ContentRevisionState("", epoch);
    assertThrows(IllegalArgumentException.class, () -> replacement.update(12, ""));
    assertThrows(IllegalArgumentException.class, () -> replacement.update(5, ""));
    assertEquals(14, replacement.update(epoch, "").revision());
    assertEquals(21, ContentRevisionState.replacementRevision(12, 20));
    assertThrows(
        IllegalArgumentException.class, () -> ContentRevisionState.replacementRevision(-1, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> ContentRevisionState.replacementRevision(0, Long.MAX_VALUE));
  }
}
