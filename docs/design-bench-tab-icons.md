# Design: Bench Tab Icons

## Overview

Add per-tab icons to the Stencil Crafting UI via a `tabIcons` map in `bench-tab-groups.json`. Each tab resolves its icon through a three-step fallback: group key → constituent bench IDs → default `RecipesIcon.png`.

## 1. Config Schema Change

Add a top-level `tabIcons` field to `bench-tab-groups.json`:

```json
{
  "autoMergeEnabled": false,
  "suffixes": ["bench", "misc", "table"],
  "groups": [ ... ],
  "tabIcons": {
    "Builders": "Blocks.png",
    "Furniture": "Furniture.png",
    "Crafting": "Bench.png",
    "Farmingbench": "Natural-Vegetal.png",
    "Furnace": "Metal.png",
    "fieldcraft": "Items-Ingredients.png"
  },
  "benchOverrides": { ... },
  "skipPrefixes": ["Stencil_", "Salvage"],
  "excludedTabs": [...]
}
```

- Keys are **case-insensitive** (matching existing convention).
- Values are filenames relative to `Common/GroupIcons/` (e.g. `"Bench.png"`).
- Missing field → empty map → all tabs use the default icon (backward-compatible).

## 2. Resolution Algorithm

```
resolveTabIcon(groupKey):
  1. Look up groupKey in tabIcons map → if found, return "Common/GroupIcons/" + value
  2. Get TabGroup for groupKey → iterate benchIds in order:
     - Look up each benchId in tabIcons map → if found, return "Common/GroupIcons/" + value
  3. Return DEFAULT_TAB_ICON ("../../Common/RecipesIcon.png")
```

The "All" tab is skipped — it keeps its default icon (set in the .ui file).

## 3. Java Changes

### 3.1 `BenchTabGrouper.java`

**`TabGroupConfig` record** — add `tabIcons` parameter:

```java
record TabGroupConfig(
        @Nonnull List<ManualGroup> groups,
        boolean autoMergeEnabled,
        @Nonnull List<String> suffixes,
        @Nonnull Map<String, BenchOverride> benchOverrides,
        @Nonnull List<String> skipPrefixes,
        @Nonnull Set<String> excludedTabs,
        @Nonnull Map<String, String> tabIcons          // ← NEW
) {}
```

**Constructor & field** — add `tabIcons` field:

```java
/** Tab group key or bench ID → icon filename (case-insensitive). */
private final Map<String, String> tabIcons;
```

Pass through from `create()` alongside existing state.

**`loadConfig()`** — parse the new field:

```java
// After excludedTabs parsing, before return:
Map<String, String> tabIcons = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
if (doc.containsKey("tabIcons") && doc.isDocument("tabIcons")) {
    BsonDocument iconsDoc = doc.getDocument("tabIcons");
    for (String key : iconsDoc.keySet()) {
        BsonValue val = iconsDoc.get(key);
        if (val.isString() && !val.asString().getValue().isEmpty()) {
            tabIcons.put(key, val.asString().getValue());
        }
    }
}
```

**New public method** — `resolveTabIcon(String groupKey)`:

```java
private static final String ICON_PATH_PREFIX = "Common/GroupIcons/";
private static final String DEFAULT_TAB_ICON = "../../Common/RecipesIcon.png";

/**
 * Resolves the icon path for a tab group.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>Direct match on {@code groupKey} in {@code tabIcons}</li>
 *   <li>First matching bench ID (from the group's {@code benchIds}) in {@code tabIcons}</li>
 *   <li>{@link #DEFAULT_TAB_ICON}</li>
 * </ol>
 *
 * @param groupKey the tab group key
 * @return icon path relative to the UI file directory; never null
 */
@Nonnull
public String resolveTabIcon(@Nonnull String groupKey) {
    // TODO: implement three-step resolution algorithm
}
```

### 3.2 `StencilSelectionPage.java`

**`buildBenchTabs()`** — add one `cmd.set` call per bench tab for the icon:

```java
// Inside the for-loop over benchIds, after setting Visible:
for (String benchId : benchIds) {
    if (tabIndex >= MAX_BENCH_TABS) break;
    cmd.set("#BenchTabs[" + tabIndex + "].Id", benchId);
    cmd.set("#BenchTabs[" + tabIndex + "].TooltipText",
            BenchRegistry.getTabGrouper().getDisplayName(benchId));
    cmd.set("#BenchTabs[" + tabIndex + "].Visible", true);
    // ── NEW: set tab icon ──
    String iconPath = BenchRegistry.getTabGrouper().resolveTabIcon(benchId);
    cmd.set("#BenchTabs[" + tabIndex + "].Icon", iconPath);
    tabIndex++;
}
```

No change to the "All" tab (index 0) — it retains its .ui-file default.

## 4. Files Changed

| File | Change |
|------|--------|
| `bench-tab-groups.json` | Add `tabIcons` map |
| `BenchTabGrouper.java` | Add `tabIcons` field, parse in `loadConfig()`, add `resolveTabIcon()` |
| `StencilSelectionPage.java` | Call `resolveTabIcon()` in `buildBenchTabs()` loop |

## 5. Task Decomposition

### Wave 1 (no dependencies)

#### Unit: BenchTabGrouper — config parsing
- **Methods**: Update `TabGroupConfig` record, update `loadConfig()`, update constructor, update `create()`
- **Contract**: Parse `tabIcons` from JSON config into a case-insensitive map; empty map when field is absent
- **Done when**: `loadConfig()` round-trips `tabIcons` correctly; existing tests still pass

#### Unit: bench-tab-groups.json
- **Files**: Plugin data directory config file
- **Contract**: Add `tabIcons` map with initial mappings for known bench groups
- **Done when**: File parses without errors

### Wave 2 (depends on Wave 1)

#### Unit: BenchTabGrouper.resolveTabIcon()
- **Methods**: `resolveTabIcon(String groupKey)`
- **Contract**: Three-step fallback: group key → bench IDs → default icon path
- **Dependencies**: Wave 1 (tabIcons field must be populated)
- **Done when**: Returns correct icon path for direct match, bench-ID match, and fallback cases

### Wave 3 (depends on Wave 2)

#### Unit: StencilSelectionPage integration
- **Files**: `StencilSelectionPage.java`
- **Contract**: Call `resolveTabIcon()` in `buildBenchTabs()` and set icon on each tab
- **Dependencies**: Wave 2
- **Done when**: Tabs display correct icons at runtime; "All" tab unchanged

---

→ @Engineer implement docs/design-bench-tab-icons.md
