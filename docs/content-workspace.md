# Campaign content workspace

The content foundation adds reusable compendium records, campaign-owned copies, Markdown journals,
and journal-linked map pins. Open **Tools → Campaign content**. The GM can edit journals/sharing and
pins, copy records, import a simple actor, and explicitly reload compendiums from installed add-ons.
Players receive a read-only view of their shared entries. Imported actor records use optional
`systemData.properties` primitive values and retain their source reference on the new token.

## Package format

An installed `.mtlib` may contain a top-level UTF-8 `compendium.json` (up to 8 MiB):

```json
{
  "schemaVersion": 1,
  "namespace": "example.rules",
  "records": [
    {
      "reference": {"namespace": "example.rules", "id": "guard"},
      "kind": "actor",
      "name": "Town guard",
      "tags": ["town", "npc"],
      "description": "A game-neutral **guard** example.",
      "systemData": {"properties": {"health": 12, "awareness": 2}},
      "source": "Example rules",
      "license": "CC0",
      "assetIds": []
    }
  ]
}
```

The namespace must match the installed add-on. Record IDs are unique within that namespace;
registration rejects duplicates atomically. Reload replaces read-only compendiums from currently
installed add-ons. Campaign copies keep independent data and a stable source reference when an
add-on is replaced or removed. Compendium records are public session rules content; private GM
information belongs in a journal with explicit sharing rather than a package compendium.

## Persistence and transport

`ContentStore.toJson()` is the complete GM persistence document. `Campaign` stores it separately
from the generic campaign DTO; only the non-secret revision number is part of that DTO. The host
accepts content writes only from authenticated GM connections, with the expected campaign identity
and content revision. Stale writes return the latest snapshot and an error. Request IDs correlate
acknowledgments so an unrelated broadcast does not complete a pending write.

Campaign replacement is a single authenticated operation: private JSON is carried only on the
GM-to-host replacement request, validated with the candidate campaign, and stripped before public
forwarding. A fresh revision greater than both old and loaded revisions rejects edits left in
flight across a reload. Legacy same-ID replacements without the new field preserve private
content. **Refresh content** reconciles a lost acknowledgment; an uncertain actor import is not
automatically replayed. Actor creation remembers the map selected when the import was requested.

Each player receives `store.snapshot(viewer, visibleArea).toJson()` instead of the full store. This
omits private journal bodies, asset references, campaign-owned copies, and hidden pins. Sharing
metadata is reduced to the recipient's own canonical player name. Replacing the client snapshot
clears entries after access revocation. Late joiners receive a filtered snapshot after their
campaign is installed. The GM can share all or selected player names; names are case-insensitive.

Journal links use `[label](journal:entry-id)`. A link resolves only if that viewer can read its
target. Missing targets remain stored so the GM can repair them. `referenceIssues` reports missing
assets and links without dropping content or revealing private references to other viewers.

## Pin policy and current boundary

- `GM_ONLY`: only the GM receives the pin.
- `SHARED`: a deliberate handout visible to a journal reader regardless of fog.
- `VISIBLE_AREA`: requires both journal access and an authoritative map visibility predicate.

The protocol currently withholds all `VISIBLE_AREA` pins, failing closed until the map's
recipient-specific fog predicate is connected. The workspace lists and edits pins and opens their
journals; it does **not** yet render clickable pin overlays directly on the tabletop. Asset IDs are
shown in content previews; a campaign asset image preview is also pending. These limitations keep
CONTENT-1 and CONTENT-3 partially implemented rather than resolved.

## Validation boundary

The common model/archive tests cover record isolation, replacement/duplicates, search, namespace
validation, versioned JSON, sharing/revocation, missing references, pin policies, and filtered
late-join payloads. The portable Swing dialog has an independent Xvfb smoke check. Protobuf
generation is validated. Campaign persistence and protocol/controller callsites require the full
application build and multiplayer validation before the content tickets can be closed; the cloud
build is currently blocked by external JetBrains redirect hosts.
