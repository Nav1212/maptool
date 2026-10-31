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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Set;
import net.rptools.maptool.content.ContentStore.CompendiumRecord;
import net.rptools.maptool.content.ContentStore.JournalEntry;
import net.rptools.maptool.content.ContentStore.MapPin;
import net.rptools.maptool.content.ContentStore.PinVisibility;
import net.rptools.maptool.content.ContentStore.RecordRef;
import net.rptools.maptool.content.ContentStore.Viewer;
import org.junit.jupiter.api.Test;

class ContentStoreTest {
  private static final Viewer GM = new Viewer("gamemaster", true);
  private static final Viewer ALICE = new Viewer("alice", false);
  private static final Viewer BOB = new Viewer("bob", false);

  private static CompendiumRecord record(String id, String name, String kind, String... tags) {
    var data = new JsonObject();
    data.addProperty("health", 12);
    return new CompendiumRecord(
        new RecordRef("example", id),
        kind,
        name,
        Set.of(tags),
        "A game-neutral **" + name + "**",
        data,
        "Example system",
        "CC0",
        List.of("portrait"));
  }

  private static JournalEntry journal(
      String id, boolean all, Set<String> audience, String... links) {
    return new JournalEntry(
        id,
        "Title " + id,
        "Adventure/Places",
        Set.of("location"),
        "Private body for " + id,
        Set.of("image-" + id),
        Set.of(links),
        all,
        audience);
  }

  @Test
  void searchFiltersKindsTagsTextAndReturnsDeterministicOrder() {
    var store = new ContentStore();
    store.registerCompendium(
        "example",
        List.of(
            record("guard2", "Guard", "actor", "Town"),
            record("sword", "Sword", "item", "Town"),
            record("guard1", "Guard", "actor", "Town", "Elite")));
    assertEquals(
        List.of("guard1", "guard2"),
        store.searchCompendium("GUARD", "ACTOR", Set.of("town")).stream()
            .map(r -> r.reference().id())
            .toList());
    assertEquals(
        List.of("guard1"),
        store.searchCompendium("", "actor", Set.of("elite", "town")).stream()
            .map(r -> r.reference().id())
            .toList());
    assertTrue(store.searchCompendium("missing", "", Set.of()).isEmpty());
  }

  @Test
  void packageReplacementAndDuplicateRejectionAreAtomic() {
    var store = new ContentStore();
    var first = record("one", "Original", "actor");
    store.registerCompendium("example", List.of(first));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            store.registerCompendium(
                "example", List.of(record("two", "Other", "actor"), first, first)));
    assertEquals(List.of(first), store.searchCompendium("", "", Set.of()));
    assertThrows(
        IllegalArgumentException.class, () -> store.registerCompendium("wrong", List.of(first)));
    store.registerCompendium("example", List.of(record("two", "Replacement", "item")));
    assertEquals("two", store.searchCompendium("", "", Set.of()).getFirst().reference().id());
  }

  @Test
  void campaignCopiesStayIndependentOfSourceReplacementAndMutableJson() {
    var store = new ContentStore();
    var source = record("guard", "Guard", "actor");
    store.registerCompendium("example", List.of(source));
    var copy = store.copyToCampaign(GM, source.reference(), "copy-1");
    var exported = copy.value().systemData();
    exported.addProperty("health", 999);
    source.systemData().addProperty("health", 1);
    assertEquals(12, copy.value().systemData().get("health").getAsInt());
    store.registerCompendium("example", List.of(record("guard", "Updated", "actor")));
    assertEquals("Guard", store.campaignRecords(GM).getFirst().value().name());
    assertEquals(source.reference(), copy.sourceReference());
    assertEquals(new RecordRef("campaign", "copy-1"), copy.value().reference());
    assertThrows(
        IllegalArgumentException.class,
        () -> store.copyToCampaign(GM, source.reference(), "copy-1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> store.copyToCampaign(ALICE, source.reference(), "copy-2"));
  }

  @Test
  void journalsSupportFoldersTagsSharingRevocationAndPermissionCheckedMutation() {
    var store = new ContentStore();
    store.putJournal(GM, journal("private", false, Set.of()));
    store.putJournal(GM, journal("selected", false, Set.of("alice")));
    store.putJournal(GM, journal("public", true, Set.of()));
    assertEquals(3, store.searchJournals(GM, "location").size());
    assertEquals(2, store.searchJournals(ALICE, "adventure").size());
    assertEquals(1, store.searchJournals(BOB, "Private body").size());
    assertNotNull(store.journal(new Viewer("ALICE", false), "selected"));
    assertNull(store.journal(BOB, "selected"));
    assertThrows(
        IllegalArgumentException.class,
        () -> store.putJournal(ALICE, journal("bad", true, Set.of())));
    assertThrows(IllegalArgumentException.class, () -> store.deleteJournal(BOB, "public"));
    store.putJournal(GM, journal("selected", false, Set.of()));
    assertNull(store.journal(ALICE, "selected"));
    assertFalse(store.snapshot(ALICE, p -> true).toJson().contains("Private body for selected"));
  }

  @Test
  void playerSnapshotRedactsPrivateBodiesAssetIdsAudienceAndHiddenLinkMetadata() {
    var store = new ContentStore();
    store.putJournal(GM, journal("secret", false, Set.of()));
    store.putJournal(GM, journal("shared", false, Set.of("alice", "bob"), "secret", "other"));
    store.putJournal(GM, journal("other", true, Set.of()));
    var payload = store.snapshot(ALICE, p -> true);
    var text = payload.toJson();
    assertFalse(text.contains("Private body for secret"));
    assertFalse(text.contains("image-secret"));
    assertFalse(text.contains("\"bob\""));
    var shared =
        payload.journals().stream().filter(j -> j.id().equals("shared")).findFirst().orElseThrow();
    assertEquals(Set.of("other"), shared.links());
    assertEquals(Set.of("alice"), shared.sharedWith());
    assertTrue(payload.campaignRecords().isEmpty());
    var client = ContentStore.fromJson(payload.toJson());
    assertEquals(2, client.searchJournals(ALICE, "").size());
  }

  @Test
  void pinsRequireBothJournalAccessAndDeclaredMapVisibilityPolicy() {
    var store = new ContentStore();
    store.putJournal(GM, journal("public", true, Set.of()));
    store.putJournal(GM, journal("private", false, Set.of()));
    store.putPin(
        GM, new MapPin("gm", "map1", "public", "Secret GM pin", 0, 0, PinVisibility.GM_ONLY));
    store.putPin(
        GM, new MapPin("fogged", "map1", "public", "Fogged pin", 1, 1, PinVisibility.VISIBLE_AREA));
    store.putPin(
        GM,
        new MapPin("visible", "map1", "public", "Visible pin", 2, 2, PinVisibility.VISIBLE_AREA));
    store.putPin(
        GM, new MapPin("handout", "map1", "public", "Shared handout", 3, 3, PinVisibility.SHARED));
    store.putPin(
        GM, new MapPin("private", "map1", "private", "Private label", 4, 4, PinVisibility.SHARED));
    assertEquals(5, store.pins(GM, "map1", p -> false).size());
    assertEquals(
        List.of("visible", "handout"),
        store.pins(ALICE, "map1", p -> p.x() == 2).stream().map(MapPin::id).toList());
    assertFalse(store.snapshot(ALICE, p -> false).toJson().contains("Private label"));
    assertTrue(store.pins(ALICE, "another-map", p -> true).isEmpty());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            store.putPin(
                ALICE, new MapPin("bad", "map1", "public", "Bad", 0, 0, PinVisibility.SHARED)));
  }

  @Test
  void deletedJournalLeavesRepairableGmPinButDisappearsFromPlayerPayload() {
    var store = new ContentStore();
    store.putJournal(GM, journal("note", true, Set.of()));
    store.putPin(GM, new MapPin("pin", "map", "note", "Place", 1, 2, PinVisibility.SHARED));
    store.deleteJournal(GM, "note");
    assertEquals(1, store.pins(GM, null, p -> false).size());
    assertTrue(store.pins(ALICE, null, p -> true).isEmpty());
    var restored = ContentStore.fromJson(store.toJson());
    assertEquals(1, restored.pins(GM, null, p -> false).size());
    restored.putJournal(GM, journal("repair", true, Set.of()));
    restored.putPin(GM, new MapPin("pin", "map", "repair", "Repaired", 1, 2, PinVisibility.SHARED));
    assertEquals("Repaired", restored.pins(ALICE, null, p -> false).getFirst().label());
  }

  @Test
  void jsonRoundTripPreservesContentAndLateJoinerPermissions() {
    var store = new ContentStore();
    var source = record("guard", "Guard", "actor", "Town");
    store.registerCompendium("example", List.of(source));
    store.copyToCampaign(GM, source.reference(), "copy");
    store.putJournal(GM, journal("note", false, Set.of("alice")));
    store.putPin(GM, new MapPin("pin", "map", "note", "Place", -5, 7, PinVisibility.SHARED));
    var restored = ContentStore.fromJson(store.toJson());
    assertEquals(store.snapshot(GM, p -> true), restored.snapshot(GM, p -> true));
    assertEquals(1, restored.snapshot(ALICE, p -> true).journals().size());
    assertTrue(restored.snapshot(BOB, p -> true).journals().isEmpty());
    assertTrue(restored.snapshot(BOB, p -> true).pins().isEmpty());
  }

  @Test
  void legacyBlankOrVersionZeroDataLoadsButFutureMalformedAndDuplicateDataFail() {
    assertTrue(ContentStore.fromJson(null).searchJournals(GM, "").isEmpty());
    assertTrue(ContentStore.fromJson("").searchJournals(GM, "").isEmpty());
    assertTrue(ContentStore.fromJson("{}").searchJournals(GM, "").isEmpty());
    assertTrue(ContentStore.fromJson("{\"schemaVersion\":0}").searchJournals(GM, "").isEmpty());
    assertThrows(
        IllegalArgumentException.class, () -> ContentStore.fromJson("{\"schemaVersion\":2}"));
    assertThrows(
        IllegalArgumentException.class, () -> ContentStore.fromJson("{\"schemaVersion\":1.5}"));
    assertThrows(
        IllegalArgumentException.class, () -> ContentStore.fromJson("{\"schemaVersion\":\"1\"}"));
    assertThrows(IllegalArgumentException.class, () -> ContentStore.fromJson("[]"));
    assertThrows(IllegalArgumentException.class, () -> ContentStore.fromJson("not json"));
    var store = new ContentStore();
    store.putJournal(GM, journal("note", true, Set.of()));
    var object = JsonParser.parseString(store.toJson()).getAsJsonObject();
    object.getAsJsonArray("journals").add(object.getAsJsonArray("journals").get(0));
    assertThrows(IllegalArgumentException.class, () -> ContentStore.fromJson(object.toString()));
  }

  @Test
  void invalidIdentityAndCoordinateInputsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> new RecordRef("", "id"));
    assertThrows(
        IllegalArgumentException.class, () -> new RecordRef("namespace", "path/traversal"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new MapPin("pin", "map", "entry", "Place", Double.NaN, 0, PinVisibility.SHARED));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MapPin(
                "pin", "map", "entry", "Place", 0, Double.POSITIVE_INFINITY, PinVisibility.SHARED));
    assertThrows(
        IllegalArgumentException.class,
        () -> new JournalEntry("id", "", "", Set.of(), "body", Set.of(), Set.of(), true, Set.of()));
  }

  @Test
  void missingAssetsAndLinksAreDiagnosedWithoutDeletingOrLeakingReferences() {
    var store = new ContentStore();
    store.putJournal(GM, journal("private", false, Set.of(), "missing-private"));
    store.putJournal(GM, journal("shared", true, Set.of(), "missing-public"));
    var issues = store.referenceIssues(GM, asset -> false);
    assertEquals(4, issues.size());
    var playerIssues = store.referenceIssues(ALICE, asset -> false);
    assertEquals(1, playerIssues.size());
    assertEquals("image-shared", playerIssues.getFirst().referenceId());
    assertEquals(Set.of("missing-public"), store.journal(GM, "shared").links());
    assertEquals(Set.of("image-shared"), store.journal(ALICE, "shared").assetIds());
    assertEquals(Set.of("image-private", "image-shared"), store.referencedAssetIds());
  }

  @Test
  void versionedCompendiumDocumentsAreNamespaceBoundAndReplaceAtomically() {
    var store = new ContentStore();
    var gson = new com.google.gson.Gson();
    var data = new com.google.gson.JsonObject();
    data.addProperty("schemaVersion", 1);
    data.addProperty("namespace", "example");
    data.add("records", gson.toJsonTree(List.of(record("actor", "Actor", "actor"))));
    store.registerCompendiumJson("example", data.toString());
    assertEquals(1, store.searchCompendium("", "", Set.of()).size());
    assertThrows(
        IllegalArgumentException.class,
        () -> store.registerCompendiumJson("other", data.toString()));
    data.getAsJsonArray("records").add(data.getAsJsonArray("records").get(0));
    assertThrows(
        IllegalArgumentException.class,
        () -> store.registerCompendiumJson("example", data.toString()));
    assertEquals(1, store.searchCompendium("", "", Set.of()).size());
    data.addProperty("schemaVersion", 2);
    assertThrows(
        IllegalArgumentException.class,
        () -> store.registerCompendiumJson("example", data.toString()));
  }

  @Test
  void replacingClientViewIsAtomicAndRevocationRemovesPreviouslyVisibleData() {
    var store = new ContentStore();
    store.putJournal(GM, journal("shared", true, Set.of()));
    var client = ContentStore.fromJson(store.snapshot(ALICE, p -> false).toJson());
    assertNotNull(client.journal(ALICE, "shared"));
    assertThrows(IllegalArgumentException.class, () -> client.replaceFromJson("invalid"));
    assertNotNull(client.journal(ALICE, "shared"));
    store.putJournal(GM, journal("shared", false, Set.of()));
    client.replaceFromJson(store.snapshot(ALICE, p -> false).toJson());
    assertNull(client.journal(ALICE, "shared"));
  }
}
