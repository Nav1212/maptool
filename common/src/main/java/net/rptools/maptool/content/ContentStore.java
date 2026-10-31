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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Campaign content with immutable values and explicit, audience-filtered transport snapshots. */
public final class ContentStore {
  public static final int SCHEMA_VERSION = 1;
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private final Map<RecordRef, CompendiumRecord> compendium = new LinkedHashMap<>();
  private final Map<String, CampaignRecord> copies = new LinkedHashMap<>();
  private final Map<String, JournalEntry> journals = new LinkedHashMap<>();
  private final Map<String, MapPin> pins = new LinkedHashMap<>();

  public record Viewer(String playerId, boolean gm) {
    public Viewer {
      playerId = required(playerId, "player ID").toLowerCase(Locale.ROOT);
    }
  }

  public record RecordRef(String namespace, String id) {
    public RecordRef {
      namespace = identifier(namespace, "namespace");
      id = identifier(id, "record ID");
    }

    @Override
    public String toString() {
      return namespace + ":" + id;
    }
  }

  /** Records registered by a package are read-only. JSON system data is defensively copied. */
  public record CompendiumRecord(
      RecordRef reference,
      String kind,
      String name,
      Set<String> tags,
      String description,
      JsonObject systemData,
      String source,
      String license,
      List<String> assetIds) {
    public CompendiumRecord {
      Objects.requireNonNull(reference, "reference");
      kind = identifier(kind, "record kind");
      name = required(name, "record name");
      tags = strings(tags);
      description = Objects.requireNonNullElse(description, "");
      systemData = systemData == null ? new JsonObject() : systemData.deepCopy();
      source = Objects.requireNonNullElse(source, "");
      license = Objects.requireNonNullElse(license, "");
      assetIds = List.copyOf(strings(assetIds));
    }

    @Override
    public JsonObject systemData() {
      return systemData.deepCopy();
    }
  }

  public record CampaignRecord(String id, RecordRef sourceReference, CompendiumRecord value) {
    public CampaignRecord {
      id = identifier(id, "campaign record ID");
      Objects.requireNonNull(sourceReference, "sourceReference");
      Objects.requireNonNull(value, "value");
      if (!value.reference().equals(new RecordRef("campaign", id))) {
        throw new IllegalArgumentException("Campaign record value must have its campaign identity");
      }
    }
  }

  public record JournalEntry(
      String id,
      String title,
      String folder,
      Set<String> tags,
      String markdown,
      Set<String> assetIds,
      Set<String> links,
      boolean sharedWithAll,
      Set<String> sharedWith) {
    public JournalEntry {
      id = identifier(id, "journal ID");
      title = required(title, "journal title");
      folder = Objects.requireNonNullElse(folder, "").trim();
      tags = strings(tags);
      markdown = Objects.requireNonNullElse(markdown, "");
      assetIds = strings(assetIds);
      links = strings(links);
      links.forEach(link -> identifier(link, "linked journal ID"));
      sharedWith = strings(strings(sharedWith).stream().map(ContentStore::lower).toList());
    }

    public boolean visibleTo(Viewer viewer) {
      return viewer.gm() || sharedWithAll || sharedWith.contains(viewer.playerId());
    }
  }

  public enum PinVisibility {
    /** Only the GM may see the pin, even if its journal is shared. */
    GM_ONLY,
    /** Deliberate handout: visible to a reader of the journal regardless of fog. */
    SHARED,
    /** Visible to a journal reader only when the host's map visibility predicate allows it. */
    VISIBLE_AREA
  }

  public record MapPin(
      String id,
      String mapId,
      String journalId,
      String label,
      double x,
      double y,
      PinVisibility visibility) {
    public MapPin {
      id = identifier(id, "pin ID");
      mapId = required(mapId, "map ID");
      journalId = identifier(journalId, "journal ID");
      label = required(label, "pin label");
      Objects.requireNonNull(visibility, "visibility");
      if (!Double.isFinite(x) || !Double.isFinite(y)) {
        throw new IllegalArgumentException("Pin coordinates must be finite");
      }
    }
  }

  /** Only this filtered value, never toJson(), should be sent to a player. */
  public record ViewSnapshot(
      int schemaVersion,
      List<CompendiumRecord> compendium,
      List<CampaignRecord> campaignRecords,
      List<JournalEntry> journals,
      List<MapPin> pins) {
    public ViewSnapshot {
      compendium = List.copyOf(compendium);
      campaignRecords = List.copyOf(campaignRecords);
      journals = List.copyOf(journals);
      pins = List.copyOf(pins);
    }

    public String toJson() {
      return GSON.toJson(this);
    }
  }

  public record ReferenceIssue(String kind, String ownerId, String referenceId) {}

  private record SavedState(
      int schemaVersion,
      List<CompendiumRecord> compendium,
      List<CampaignRecord> campaignRecords,
      List<JournalEntry> journals,
      List<MapPin> pins) {}

  private record CompendiumDocument(
      int schemaVersion, String namespace, List<CompendiumRecord> records) {}

  /** Optional compendium.json package document. Installation is atomic and namespace-bound. */
  public synchronized void registerCompendiumJson(String namespace, String json) {
    try {
      var document = JsonParser.parseString(json).getAsJsonObject();
      if (!document.has("schemaVersion")
          || !document.get("schemaVersion").isJsonPrimitive()
          || !document.get("schemaVersion").getAsJsonPrimitive().isNumber()
          || document.get("schemaVersion").getAsBigDecimal().intValueExact() != SCHEMA_VERSION) {
        throw new IllegalArgumentException("Unsupported compendium schema version");
      }
      var parsed = GSON.fromJson(document, CompendiumDocument.class);
      if (!namespace.equals(parsed.namespace()) || parsed.records() == null) {
        throw new IllegalArgumentException(
            "Compendium must declare its package namespace and records");
      }
      registerCompendium(namespace, parsed.records());
    } catch (RuntimeException failure) {
      throw new IllegalArgumentException("Invalid compendium: " + failure.getMessage(), failure);
    }
  }

  /** Atomically replace one package's records, rejecting duplicate identities before mutation. */
  public synchronized void registerCompendium(String namespace, List<CompendiumRecord> records) {
    identifier(namespace, "namespace");
    if (namespace.equals("campaign"))
      throw new IllegalArgumentException("Reserved namespace: campaign");
    var replacement = new LinkedHashMap<RecordRef, CompendiumRecord>();
    for (var record : List.copyOf(records)) {
      if (!record.reference().namespace().equals(namespace)) {
        throw new IllegalArgumentException("Record belongs to a different package namespace");
      }
      if (replacement.putIfAbsent(record.reference(), record) != null) {
        throw new IllegalArgumentException("Duplicate record identity: " + record.reference());
      }
    }
    compendium.keySet().removeIf(ref -> ref.namespace().equals(namespace));
    compendium.putAll(replacement);
  }

  public synchronized void removeCompendium(String namespace) {
    compendium.keySet().removeIf(ref -> ref.namespace().equals(namespace));
  }

  public synchronized List<CompendiumRecord> searchCompendium(
      String text, String kind, Set<String> requiredTags) {
    var needle = lower(text);
    var filterKind = lower(kind);
    var tagFilter = strings(requiredTags).stream().map(ContentStore::lower).toList();
    return compendium.values().stream()
        .filter(r -> filterKind.isEmpty() || lower(r.kind()).equals(filterKind))
        .filter(r -> r.tags().stream().map(ContentStore::lower).toList().containsAll(tagFilter))
        .filter(
            r ->
                lower(r.name() + " " + r.description() + " " + String.join(" ", r.tags()))
                    .contains(needle))
        .sorted(
            Comparator.comparing(CompendiumRecord::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(r -> r.reference().toString()))
        .toList();
  }

  public synchronized CampaignRecord copyToCampaign(
      Viewer viewer, RecordRef reference, String copyId) {
    requireGm(viewer);
    if (copies.containsKey(copyId))
      throw new IllegalArgumentException("Campaign record already exists");
    var source = compendium.get(reference);
    if (source == null) throw new IllegalArgumentException("Missing source record: " + reference);
    var value =
        new CompendiumRecord(
            new RecordRef("campaign", copyId),
            source.kind(),
            source.name(),
            source.tags(),
            source.description(),
            source.systemData(),
            source.source(),
            source.license(),
            source.assetIds());
    var copy = new CampaignRecord(copyId, reference, value);
    copies.put(copy.id(), copy);
    return copy;
  }

  public synchronized void putCampaignRecord(Viewer viewer, CampaignRecord record) {
    requireGm(viewer);
    copies.put(record.id(), record);
  }

  public synchronized List<CampaignRecord> campaignRecords(Viewer viewer) {
    requireGm(viewer);
    return List.copyOf(copies.values());
  }

  public synchronized void putJournal(Viewer viewer, JournalEntry journal) {
    requireGm(viewer);
    journals.put(journal.id(), journal);
  }

  /** Pins keep their stable dangling reference so a GM can repair it after deleting an entry. */
  public synchronized void deleteJournal(Viewer viewer, String id) {
    requireGm(viewer);
    journals.remove(id);
  }

  public synchronized List<JournalEntry> searchJournals(Viewer viewer, String text) {
    var needle = lower(text);
    return journals.values().stream()
        .filter(j -> j.visibleTo(viewer))
        .filter(
            j ->
                lower(
                        j.title()
                            + " "
                            + j.folder()
                            + " "
                            + j.markdown()
                            + " "
                            + String.join(" ", j.tags()))
                    .contains(needle))
        .map(j -> redact(j, viewer))
        .sorted(
            Comparator.comparing(JournalEntry::title, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(JournalEntry::id))
        .toList();
  }

  public synchronized JournalEntry journal(Viewer viewer, String id) {
    var entry = journals.get(id);
    return entry == null || !entry.visibleTo(viewer) ? null : redact(entry, viewer);
  }

  public synchronized void putPin(Viewer viewer, MapPin pin) {
    requireGm(viewer);
    if (!journals.containsKey(pin.journalId())) {
      throw new IllegalArgumentException("Select an existing journal entry before saving the pin");
    }
    pins.put(pin.id(), pin);
  }

  public synchronized void deletePin(Viewer viewer, String id) {
    requireGm(viewer);
    pins.remove(id);
  }

  public synchronized List<MapPin> pins(
      Viewer viewer, String mapId, Predicate<MapPin> visibleArea) {
    Objects.requireNonNull(visibleArea, "visibleArea");
    return pins.values().stream()
        .filter(p -> mapId == null || p.mapId().equals(mapId))
        .filter(
            p ->
                viewer.gm()
                    || (journal(viewer, p.journalId()) != null
                        && p.visibility() != PinVisibility.GM_ONLY
                        && (p.visibility() == PinVisibility.SHARED || visibleArea.test(p))))
        .toList();
  }

  public synchronized ViewSnapshot snapshot(Viewer viewer, Predicate<MapPin> visibleArea) {
    return new ViewSnapshot(
        SCHEMA_VERSION,
        List.copyOf(compendium.values()),
        viewer.gm() ? List.copyOf(copies.values()) : List.of(),
        searchJournals(viewer, ""),
        pins(viewer, null, visibleArea));
  }

  /** Report missing references without dropping stored content or disclosing hidden entries. */
  public synchronized List<ReferenceIssue> referenceIssues(
      Viewer viewer, Predicate<String> assetExists) {
    Objects.requireNonNull(assetExists, "assetExists");
    var issues = new java.util.ArrayList<ReferenceIssue>();
    for (var journal : searchJournals(viewer, "")) {
      for (var asset : journal.assetIds()) {
        if (!assetExists.test(asset)) issues.add(new ReferenceIssue("asset", journal.id(), asset));
      }
      for (var link : journal.links()) {
        if (!journals.containsKey(link))
          issues.add(new ReferenceIssue("journal", journal.id(), link));
      }
    }
    if (viewer.gm()) {
      for (var pin : pins.values()) {
        if (!journals.containsKey(pin.journalId())) {
          issues.add(new ReferenceIssue("journal", pin.id(), pin.journalId()));
        }
      }
    }
    return List.copyOf(issues);
  }

  /** Full persistence asset set, not a player-facing list. */
  public synchronized Set<String> referencedAssetIds() {
    var assets = new LinkedHashSet<String>();
    compendium.values().forEach(record -> assets.addAll(record.assetIds()));
    copies.values().forEach(copy -> assets.addAll(copy.value().assetIds()));
    journals.values().forEach(journal -> assets.addAll(journal.assetIds()));
    return java.util.Collections.unmodifiableSet(assets);
  }

  /** Full GM persistence, not a network payload. Missing/blank data represents an old campaign. */
  public synchronized String toJson() {
    return GSON.toJson(
        new SavedState(
            SCHEMA_VERSION,
            List.copyOf(compendium.values()),
            List.copyOf(copies.values()),
            List.copyOf(journals.values()),
            List.copyOf(pins.values())));
  }

  /** Replace an existing view only after the entire replacement has validated. */
  public void replaceFromJson(String json) {
    var replacement = fromJson(json);
    synchronized (this) {
      compendium.clear();
      compendium.putAll(replacement.compendium);
      copies.clear();
      copies.putAll(replacement.copies);
      journals.clear();
      journals.putAll(replacement.journals);
      pins.clear();
      pins.putAll(replacement.pins);
    }
  }

  public static ContentStore fromJson(String json) {
    if (json == null || json.isBlank()) return new ContentStore();
    try {
      var object = JsonParser.parseString(json).getAsJsonObject();
      if (object.has("schemaVersion")
          && (!object.get("schemaVersion").isJsonPrimitive()
              || !object.get("schemaVersion").getAsJsonPrimitive().isNumber())) {
        throw new IllegalArgumentException("Content schema version must be a number");
      }
      int version =
          object.has("schemaVersion")
              ? object.get("schemaVersion").getAsBigDecimal().intValueExact()
              : 0;
      if (version < 0 || version > SCHEMA_VERSION) {
        throw new IllegalArgumentException("Unsupported content schema version: " + version);
      }
      var saved = GSON.fromJson(object, SavedState.class);
      var store = new ContentStore();
      for (var record : list(saved.compendium())) {
        if (record.reference().namespace().equals("campaign")) {
          throw new IllegalArgumentException("Reserved compendium namespace");
        }
        putUnique(store.compendium, record.reference(), record);
      }
      for (var copy : list(saved.campaignRecords())) putUnique(store.copies, copy.id(), copy);
      for (var journal : list(saved.journals())) putUnique(store.journals, journal.id(), journal);
      for (var pin : list(saved.pins())) putUnique(store.pins, pin.id(), pin);
      return store;
    } catch (RuntimeException failure) {
      throw new IllegalArgumentException(
          "Invalid campaign content: " + failure.getMessage(), failure);
    }
  }

  private JournalEntry redact(JournalEntry journal, Viewer viewer) {
    if (viewer.gm()) return journal;
    var visibleLinks =
        journal.links().stream()
            .filter(id -> journals.containsKey(id) && journals.get(id).visibleTo(viewer))
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    return new JournalEntry(
        journal.id(),
        journal.title(),
        journal.folder(),
        journal.tags(),
        journal.markdown(),
        journal.assetIds(),
        visibleLinks,
        false,
        Set.of(viewer.playerId()));
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + field);
    return value.trim();
  }

  private static String identifier(String value, String field) {
    value = required(value, field);
    if (!value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) {
      throw new IllegalArgumentException("Invalid " + field + ": " + value);
    }
    return value;
  }

  private static Set<String> strings(Iterable<String> values) {
    var result = new LinkedHashSet<String>();
    if (values != null) for (var value : values) result.add(required(value, "list value"));
    return java.util.Collections.unmodifiableSet(result);
  }

  private static String lower(String text) {
    return Objects.requireNonNullElse(text, "").toLowerCase(Locale.ROOT);
  }

  private static void requireGm(Viewer viewer) {
    if (viewer == null || !viewer.gm())
      throw new IllegalArgumentException("Only the GM may edit campaign content");
  }

  private static <T> List<T> list(List<T> values) {
    return values == null ? List.of() : values;
  }

  private static <K, V> void putUnique(Map<K, V> map, K key, V value) {
    if (map.putIfAbsent(key, value) != null)
      throw new IllegalArgumentException("Duplicate content ID: " + key);
  }
}
