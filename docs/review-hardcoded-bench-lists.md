# Review: Hardcoded Lists in the Bench/Tab System

## Summary

Five hardcoded lists were audited. Three should be externalized to config, one is already adequately data-driven, and one is already config-backed. A single config file (`bench-tab-groups.json`) can absorb all three without schema sprawl.

---

## 1. Classification

| # | Constant | Location | Verdict | Rationale |
|---|----------|----------|---------|-----------|
| 1 | `CRAFTING_BENCH_IDS` | `BenchRegistry:75` | **Externalize** | Contradicts dynamic discovery. New crafting benches silently miscategorized as processing benches. |
| 2 | `FURNITURE_BENCH_ID` | `BenchRegistry:68` | **Externalize** | Hardcoded per-bench behavior override. Should be a config property on any bench. |
| 3 | `DEFAULT_SKIP_PREFIXES` | `RecipeFilterRegistry:67` | **Externalize** | New recipe categories (e.g., `"Tutorial_"`) require a code change to exclude. |
| 4 | `AUTO_MERGE_SUFFIXES` | `BenchTabGrouper:41` | **Already handled** | Serves as default when `suffixes` is absent from config. This is correct fallback-default behavior; no change needed. |
| 5 | `denyList` | `BenchRegistry:82` | **Already data-driven** | Loaded from `deny-list.json`. No change needed. |
| 6 | `CRAFTING_BENCH_IDS` (duplicate) | `NaturalResourceRegistry:236` | **Remove (redundant)** | Stale duplicate of #1 with only 2 entries vs 4. Must be unified with a single source of truth. |

---

## 2. Proposed Config Schema Change

Extend `bench-tab-groups.json` with two new top-level fields. No new config files needed.

### Current schema

```json
{
  "autoMergeEnabled": true,
  "suffixes": ["bench", "misc", "table"],
  "groups": [
    { "displayName": "Crafting", "benchIds": ["fieldcraft", "workbench"] }
  ]
}
```

### Proposed schema (additions in bold comments)

```jsonc
{
  "autoMergeEnabled": true,
  "suffixes": ["bench", "misc", "table"],

  // NEW — replaces BenchRegistry.CRAFTING_BENCH_IDS
  // Bench IDs that count as "true crafting" (not processing/refinement).
  // Used for recipe priority and natural-resource classification.
  // Case-insensitive matching.
  "craftingBenchIds": ["Builders", "Furniture_Bench", "Workbench", "Fieldcraft"],

  // NEW — replaces RecipeFilterRegistry.DEFAULT_SKIP_PREFIXES
  // Recipe IDs starting with any of these prefixes are excluded from the UI.
  "recipeSkipPrefixes": ["Stencil_", "Salvage"],

  // NEW — replaces BenchRegistry.FURNITURE_BENCH_ID hardcode
  // Per-bench overrides. Each entry can set preferNatural on a specific bench.
  "benchOverrides": {
    "Furniture_Bench": { "preferNatural": true }
  },

  "groups": [
    { "displayName": "Crafting", "benchIds": ["fieldcraft", "workbench"] }
  ]
}
```

### Design decisions

| Decision | Reasoning |
|----------|-----------|
| Single config file | All three lists serve the bench/tab pipeline. One file = one place to misconfigure = one place to validate. |
| `craftingBenchIds` as explicit list (not "all non-denied") | The semantic distinction between crafting and processing benches is domain knowledge, not discoverable from recipe structure. An explicit list is safer than inference. |
| `benchOverrides` map | Generalizes the `FURNITURE_BENCH_ID` pattern. Any bench can opt into `preferNatural` without code changes. Future per-bench flags (e.g., `hidden`, `sortOrder`) slot in naturally. |
| `recipeSkipPrefixes` in bench config (not a separate file) | Skip prefixes are consumed during the same init pipeline. Co-location prevents config drift. |

### Fallback defaults

Every new field MUST have a hardcoded fallback in `loadConfig()` so the system still works if the field is absent:

| Field | Default if missing |
|-------|-------------------|
| `craftingBenchIds` | All bench IDs from `BenchRegistry.allBenchIds()` (treat everything as crafting — conservative) |
| `recipeSkipPrefixes` | `["Stencil_", "Salvage"]` (current behavior) |
| `benchOverrides` | Empty map (no overrides — all benches get `preferNatural = false`) |

---

## 3. Impact Map

### 3a. `craftingBenchIds` externalization

| File | Method | Change |
|------|--------|--------|
| [`BenchTabGrouper.java`](src/main/java/com/CodeCreature/registry/BenchTabGrouper.java) | `loadConfig()` | Parse new `craftingBenchIds` array from JSON. Add field to `TabGroupConfig` record. |
| [`BenchRegistry.java`](src/main/java/com/CodeCreature/registry/BenchRegistry.java#L75) | Field `CRAFTING_BENCH_IDS` | Remove constant. Replace with mutable `Set<String>` populated from config during `init()`. |
| [`BenchRegistry.java`](src/main/java/com/CodeCreature/registry/BenchRegistry.java#L240) | `isCraftingBenchId()` | Read from config-loaded set instead of compile-time constant. |
| [`BenchRegistry.java`](src/main/java/com/CodeCreature/registry/BenchRegistry.java#L130) | `init()` | Receive crafting bench IDs from loaded config; store them. |
| [`NaturalResourceRegistry.java`](src/main/java/com/CodeCreature/scaling/NaturalResourceRegistry.java#L236) | `CRAFTING_BENCH_IDS` + `isCraftingBench()` | **Delete entirely.** Delegate to `BenchRegistry.isCraftingBenchId()`. This is a stale duplicate. |
| [`BenchRecipeRegistries.java`](src/main/java/com/CodeCreature/registry/BenchRecipeRegistries.java#L82) | `getRecipeForBlock()` | No change — already calls `BenchRegistry.isCraftingBenchId()`. |

### 3b. `benchOverrides` / `preferNatural` externalization

| File | Method | Change |
|------|--------|--------|
| [`BenchTabGrouper.java`](src/main/java/com/CodeCreature/registry/BenchTabGrouper.java) | `loadConfig()` | Parse `benchOverrides` map from JSON. |
| [`BenchRegistry.java`](src/main/java/com/CodeCreature/registry/BenchRegistry.java#L68) | Field `FURNITURE_BENCH_ID` | Remove. |
| [`BenchRegistry.java`](src/main/java/com/CodeCreature/registry/BenchRegistry.java#L159) | `init()` loop | Replace `FURNITURE_BENCH_ID.equals(req.id)` with lookup into config-provided `benchOverrides` map. |

### 3c. `recipeSkipPrefixes` externalization

| File | Method | Change |
|------|--------|--------|
| [`BenchTabGrouper.java`](src/main/java/com/CodeCreature/registry/BenchTabGrouper.java) | `loadConfig()` | Parse `recipeSkipPrefixes` array from JSON. |
| [`BenchRegistry.java`](src/main/java/com/CodeCreature/registry/BenchRegistry.java#L130) | `init()` | Pass loaded skip prefixes downstream (or expose via getter). |
| [`RecipeFilterRegistry.java`](src/main/java/com/CodeCreature/registry/RecipeFilterRegistry.java#L67) | `DEFAULT_SKIP_PREFIXES` | Retain as compile-time fallback, but callers should prefer config value. |
| [`DropScaler.java`](src/main/java/com/CodeCreature/scaling/DropScaler.java#L71) | `apply()` | Change `RecipeFilterRegistry.init(RecipeFilterRegistry.DEFAULT_SKIP_PREFIXES)` → pass config-loaded prefixes. |
| Test files (6+) | Various `@BeforeEach` | Continue using `DEFAULT_SKIP_PREFIXES` — tests should not depend on runtime config. No change. |

### 3d. `NaturalResourceRegistry.CRAFTING_BENCH_IDS` removal (stale duplicate)

| File | Method | Change |
|------|--------|--------|
| [`NaturalResourceRegistry.java`](src/main/java/com/CodeCreature/scaling/NaturalResourceRegistry.java#L236) | `CRAFTING_BENCH_IDS` | Delete field. |
| [`NaturalResourceRegistry.java`](src/main/java/com/CodeCreature/scaling/NaturalResourceRegistry.java#L238) | `isCraftingBench()` | Replace body with `BenchRegistry.isCraftingBenchId(req.id)` delegation. |

---

## 4. Risk Analysis

### 4a. `craftingBenchIds` misconfiguration

| Risk | Scenario | Impact | Mitigation |
|------|----------|--------|------------|
| **Empty list** | Admin sets `"craftingBenchIds": []` | All benches treated as processing → recipe priority breaks; blocks wrongly classified as natural | Validate at load time: if empty AND `BenchRegistry.allBenchIds()` is non-empty, log `WARNING` and fall back to all bench IDs |
| **Stale list** | New bench added to game but admin forgets to update config | New bench treated as processing bench → same impact as current hardcoded bug | Log `INFO` at init listing bench IDs discovered but not in `craftingBenchIds` — makes omissions visible |
| **Typo in ID** | `"Buidlers"` instead of `"Builders"` | That bench silently treated as processing | Cross-validate against `BenchRegistry.allBenchIds()` at init; warn on IDs in config that don't match any discovered bench |

### 4b. `recipeSkipPrefixes` misconfiguration

| Risk | Scenario | Impact | Mitigation |
|------|----------|--------|------------|
| **Too broad** | `"recipeSkipPrefixes": [""]` | Empty string prefix matches everything → all recipes filtered out → empty bench UI | Reject empty-string prefixes at load time |
| **Missing key prefixes** | Admin removes `"Salvage"` | Salvage recipes appear in UI | Acceptable — admin's intent may be to show them. Log the active prefix list at `INFO`. |

### 4c. `benchOverrides` misconfiguration

| Risk | Scenario | Impact | Mitigation |
|------|----------|--------|------------|
| **Unknown bench ID** | Override for `"FurnitureBench"` (wrong case/spelling) | Override silently ignored; bench gets default `preferNatural = false` | Cross-validate override keys against discovered bench IDs; warn on mismatches |
| **Conflicting semantics** | `preferNatural = true` on every bench | All resource-type inputs resolve to natural variants | Technically valid — admin's choice. No guard needed. |

### 4d. Init-order dependency

`BenchTabGrouper.loadConfig()` currently runs inside `BenchRegistry.init()`. The new fields (`craftingBenchIds`, `recipeSkipPrefixes`, `benchOverrides`) are consumed by `BenchRegistry.init()` itself and by `RecipeFilterRegistry.init()` (which runs later in `DropScaler.apply()`).

**Risk**: If config loading is split across multiple init methods, field availability depends on call order.

**Mitigation**: Load the entire config once in `BenchRegistry.init()`, store the parsed values, and expose them via getters (`getCraftingBenchIds()`, `getRecipeSkipPrefixes()`, `getBenchOverrides()`). Downstream consumers read from `BenchRegistry` — single parse, no ordering issues.

---

## 5. Recommendation

The user's stated goal — *"tabs should be generated, grouped, then filtered down based on the exclusion list"* — aligns with making the pipeline fully data-driven. The three externalizations above achieve this with minimal schema expansion (three new fields in one existing file) and zero new config files.

**Priority order for implementation:**

1. **`NaturalResourceRegistry.CRAFTING_BENCH_IDS` removal** — prerequisite cleanup; eliminates stale duplicate regardless of config work.
2. **`craftingBenchIds`** — highest impact; fixes the core contradiction between dynamic discovery and static whitelist.
3. **`benchOverrides`** — generalizes the `FURNITURE_BENCH_ID` pattern; enables future per-bench tuning.
4. **`recipeSkipPrefixes`** — lowest urgency (current values are stable), but trivial to add alongside the others.

`AUTO_MERGE_SUFFIXES` needs no changes — the current fallback-default pattern is already correct.
