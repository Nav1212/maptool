# Add-on compatibility and dependency preflight

Existing `.mtlib` bundles remain loadable without compatibility metadata. Their add-on details show **MapTool compatibility: unknown (legacy bundle)**. No application version or package compatibility is inferred from an undeclared field.

New bundles may declare requirements in `library.json`:

```json
{
  "name": "Example framework",
  "namespace": "example.framework",
  "version": "1.2.0",
  "supportedMapToolVersions": ">=1.18.0 <2.0.0",
  "dependencies": [
    {"namespace": "example.core", "versionRange": "^1.0.0"},
    {"namespace": "example.extras", "versionRange": "~2.0.0", "optional": true}
  ]
}
```

Ranges support complete semantic versions, partial/wildcard versions (`1`, `1.2`, `1.2.x`, `*`), `^1.2.3`, `~1.2.3`, comparisons (`>=1.2.3 <2.0.0`), and `||` alternatives. Caret ranges respect zero-major compatibility: `^0.2.3` stops before `0.3.0`, and `^0.0.3` stops before `0.0.4`. Prereleases are accepted only by an alternative containing an explicit prerelease bound with the same major/minor/patch. Build metadata does not affect ordering. Hyphen intervals and other range syntaxes are rejected with an actionable error rather than guessed.

Dependencies identify add-on namespaces case-insensitively. An absent optional dependency is reported but does not prevent activation. If an optional dependency is present, its version must satisfy the declared range. A missing version range defaults to `*`. A package that declares requirements must use a semantic package version; legacy package version strings are left unchanged, but cannot satisfy a versioned dependency if they are not parseable.

The planner checks the proposed final set before replacing an existing bundle. It reports missing/incompatible dependencies, duplicate identities, invalid declarations, incompatible MapTool releases, and dependency cycles. Installed dependents are included, so updating a dependency cannot silently invalidate another declared package. A rejected preflight leaves the old bundle and its data registered. Candidate sets are activated in dependency order; archives and network batches are collected before preflight so file/asset arrival order does not determine validity.

Compatibility declarations are retained in protobuf and campaign add-on details. The original bundle archive remains the authoritative asset. Existing notes, data namespaces, and legacy add-on behavior remain supported.

Development builds such as `DEVELOPMENT` and `SNAPSHOT-…` do not establish a semantic application version. Bundles requiring a MapTool range are rejected if it cannot be verified. For an intentional local development compatibility target, start the application with `-Dmaptool.addonCompatibilityVersion=1.18.0` (substitute the version being developed). This explicit override is not a claim that the development build has passed that release's compatibility checks.

Preflight is not a transaction around arbitrary initialization macros. If an initialization handler changes campaign data before failing, package code registration can be restored but arbitrary handler effects cannot be promised to roll back. Full update/data-snapshot rollback belongs to the separate package-update workflow.
