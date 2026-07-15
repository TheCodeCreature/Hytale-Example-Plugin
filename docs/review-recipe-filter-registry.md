# Review: RecipeFilterRegistry Refactor

**Date:** 2026-04-27  
**Scope:** Extraction of duplicated recipe scanning into shared `RecipeFilterRegistry`  
**Design doc:** [design-recipe-filter-registry.md](Plans/design-recipe-filter-registry.md)  
**Mode:** Verification (post-implementation)

---

## Architecture Quality
**NEEDS WORK** — 1 finding

The refactor successfully achieves its primary goals: single source of truth for recipe scanning, correct multi-BenchRequirement matching, and shared `Item.set` extraction. The abstraction boundary between the registry (data provider) and consumers (UI filtering, base-block classification) is clean.

| # | Category | Severity | Location | Detail |
|---|----------|----------|----------|--------|
| 1 | Consistency | 🟡 Should Fix | [DropScaler.java](../src/main/java/com/UnobstructedThirdPerson/resourcecollection/DropScaler.java#L64) | `DropScaler.apply()` passes `Set.of("Stencil_", "Salvage")` inline instead of using `RecipeFilterRegistry.DEFAULT_SKIP_PREFIXES`. Tests all use the constant. If the prefixes ever change, the two sites could diverge. |

---

## Correctness
**NEEDS WORK** — 2 findings

| # | Category | Severity | Location | Detail |
|---|----------|----------|----------|--------|
| 2 | Correctness | 🟡 Should Fix | [BenchRecipeRegistries.java](../src/main/java/com/UnobstructedThirdPerson/resourcecollection/BenchRecipeRegistries.java#L34-L36) | `init()` iterates `BenchCategory.allBenchIds()` which returns `Collectors.toUnmodifiableSet()` — an **unordered** set. The old code explicitly ordered `("Builders", "Furniture_Bench")`, giving Builders priority in `getRecipeForBlock()` (first-match-wins via `LinkedHashMap`). The new code has **non-deterministic** iteration order, meaning the priority of which bench "wins" for dual-bench block types could vary between JVM versions. Fix: `allBenchIds()` should return a `LinkedHashSet` or `List`, or `BenchRecipeRegistries.init()` should enforce ordering explicitly. |
| 3 | Correctness | 🟡 Should Fix | [RecipeFilterRegistry.java](../src/main/java/com/UnobstructedThirdPerson/resourcecollection/RecipeFilterRegistry.java#L107-L108) | Javadoc declares `@throws IllegalStateException if called more than once` but the implementation has **no guard**. In production this is benign (called once from `DropScaler.apply()`), but the doc/implementation mismatch is misleading. Either add the guard (with a `reset()` for tests), or remove the Javadoc claim. |

---

## Consistency
**NEEDS WORK** — 2 findings

| # | Category | Severity | Location | Detail |
|---|----------|----------|----------|--------|
| 4 | Consistency | 🟠 QA | [AssetTestHelper.java](../src/test/java/com/UnobstructedThirdPerson/resourcecollection/AssetTestHelper.java#L364-L375) | `cleanup()` resets `BenchRecipeRegistries.registries` but does **not** reset `RecipeFilterRegistry` state (`entries`, `byRecipeId`, `byBlockType`, `byBenchId`, `initialized`). Tests call `RecipeFilterRegistry.init()` in `@BeforeEach` which silently overwrites stale state. If a double-init guard is added (finding #3), all tests will break. Add `RecipeFilterRegistry` reset to `cleanup()`. |
| 5 | Consistency | 🔵 Review | [BenchRecipeRegistries.java](../src/main/java/com/UnobstructedThirdPerson/resourcecollection/BenchRecipeRegistries.java#L18) | Javadoc says "Initialized once by `DropScaler#apply()` with the configured bench IDs" — but `init()` no longer takes bench ID parameters. Stale doc from pre-refactor signature. |

---

## Dead Code
**2 items found**

| # | Category | Severity | Location | Detail |
|---|----------|----------|----------|--------|
| 6 | Dead code | 🔵 Review | [FilteredRecipeEntry.java](../src/main/java/com/UnobstructedThirdPerson/resourcecollection/FilteredRecipeEntry.java#L56-L58) | `primaryBenchId()` method is never called. `StencilSelectionPage.loadRecipes()` does its own `benchIds().stream().min(String.CASE_INSENSITIVE_ORDER)` instead. Either the consumer should use `primaryBenchId()`, or the method should be removed. |
| 7 | Dead code | 🔵 Review | [RecipeFilterRegistry.java](../src/main/java/com/UnobstructedThirdPerson/resourcecollection/RecipeFilterRegistry.java#L263-L265) | `isInitialized()` is defined but never called by any consumer or test. Standard registry pattern — acceptable to keep as a debugging aid, but note it's unused. |

---

## Thread Safety
**PASS** — 0 critical findings

The static mutable fields in `RecipeFilterRegistry` are written once during `init()` and then wrapped in `Collections.unmodifiable*()` — effectively immutable after initialization. The `initialized` flag is not `volatile`, but the single-threaded init-then-read lifecycle (init on server thread, read on same or later) makes this safe in practice given the Hytale plugin lifecycle where `onAssetsLoaded` completes before any player interactions trigger UI queries.

The `BenchRecipeRegistries` and `BenchRecipeRegistry` follow the same pattern — write once, read many — and are already established in the codebase.

No new shared mutable state was introduced by this refactor.

---

## Summary

| Metric | Result |
|--------|--------|
| Quality | **NEEDS WORK** |
| Total findings | 7 |
| Critical findings | 0 |
| 🟡 Should Fix | 3 (#1, #2, #3) |
| 🟠 QA | 1 (#4) |
| 🔵 Review | 3 (#5, #6, #7) |

### Highest-impact fix

**Finding #2** — the non-deterministic bench ordering in `BenchRecipeRegistries.init()`. This is a subtle regression from the old explicit ordering that could cause different block-type → recipe mappings depending on JVM hash seed. A one-line fix in `BenchCategory.allBenchIds()` to return a `LinkedHashSet` (preserving enum declaration order) resolves it.

### What went well

- Clean separation: registry owns scanning/indexing, consumers own their domain logic
- `FilteredRecipeEntry` record eliminates repeated reflection for `Item.set`
- Bug fix for single-BenchRequirement scanning is correctly implemented in `extractMatchingBenchIds()`
- Predicate helpers are package-private for testability
- All collections are defensively wrapped as unmodifiable

---

```
→ @Engineer implement fixes from docs/review-recipe-filter-registry.md (findings #1-#4)
→ @Architect if bench ordering semantics need a design decision (finding #2)
```
