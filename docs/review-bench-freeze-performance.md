# Performance Review: Stencil Crafting UI — Server Freeze Analysis

## Executive Summary

The Stencil Crafting UI has two compounding performance problems that explain both symptoms. **On every menu open**, `build()` runs the full filter pipeline **3 times** (once inside `loadRecipes()`, once in `computeMaxLayout()`, once for initial display), each time copying ~500+ recipes through ~10 intermediate `ArrayList` allocations and running ~500 affordability checks (each doing asset map lookups + inventory scans). This explains the **~1-second freeze on open**. **On every interaction**, `handleDataEvent()` runs the pipeline again, writes to a BSON file on disk (`savePrefs()`), and — in the `pruneInvalidMaterialGroups()` path — can run the pipeline **twice** per event. Since there is **no debouncing**, rapid tab switching or search typing queues up N heavyweight operations back-to-back on the server thread, explaining the **complete lock-up after repeated use**.

---

## Findings

| # | Category | Severity | Location | Detail |
|---|----------|----------|----------|--------|
| F1 | Redundant computation | **CRITICAL** | [StencilSelectionPage.java](src/main/java/com/CodeCreature/ui/bench/StencilSelectionPage.java#L200-L206) → [L95-L130](src/main/java/com/CodeCreature/ui/bench/StencilSelectionPage.java#L95-L130) | **Pipeline runs 3× on every menu open.** `loadRecipes()` (L206) calls `applyFilter()` which runs `pipeline.execute()`. Then `build()` calls `computeMaxLayout()` (L290) which runs `pipeline.execute()` again with ALL recipes (no filters) to compute the max layout. Then `build()` calls the full update chain (L371–375) which calls `gridController.updateUI()` using the results from the first `applyFilter()`. The `computeMaxLayout()` run is the most expensive — it processes all 500+ recipes with no tab/search filter, creating `InputRecipe` objects a second time (L97-103). |
| F2 | Redundant computation | **CRITICAL** | [StencilSelectionPage.java](src/main/java/com/CodeCreature/ui/bench/StencilSelectionPage.java#L247-L258) | **`applyFilter()` rebuilds the `InputRecipe` list every call.** Lines 247-253 create a `new ArrayList<>()` and loop through all ~500 `RecipeEntry` objects, constructing a new `InputRecipe` record for each one. This conversion is pure overhead — the data never changes between calls. Happens on every open, every tab switch, every filter toggle, every search keystroke. |
| F3 | Redundant computation | **HIGH** | [StencilSelectionPage.java](src/main/java/com/CodeCreature/ui/bench/StencilSelectionPage.java#L815-L818) | **`pruneInvalidMaterialGroups()` can trigger a second `applyFilter()` within the same event.** If any material groups are pruned (L818), `applyFilter()` runs again. Multiple `handleDataEvent` paths call `applyFilter()` then `pruneInvalidMaterialGroups()` — e.g., set filter toggle (L501-505), search (L551-556), ingredient toggle (L596-602). This means a single interaction can run the pipeline **twice**. |
| F4 | Missing caching | **CRITICAL** | [StencilSelectionPage.java](src/main/java/com/CodeCreature/ui/bench/StencilSelectionPage.java#L895-L920) | **`isAffordable()` does full asset lookups + inventory scans per recipe, ×500, uncached.** Each call does: `CraftingRecipe.getAssetMap().getAsset()`, `PlaceBlockCostUtil.getPerUnitCost()`, `container.canRemoveMaterials()`, then `Item.getAssetMap().getAsset()`, `BlockGroup.findItemGroup()`, and a loop over all group members calling `canRemoveMaterials()` for each. With 500+ recipes, that's 500+ asset map lookups + 500+ inventory scans, repeated on every pipeline execution. |
| F5 | Memory pressure | **HIGH** | [RecipeFilterPipeline.java](src/main/java/com/CodeCreature/ui/bench/RecipeFilterPipeline.java#L190-L235) | **Pipeline creates ~10 intermediate `ArrayList` copies per execution.** Counting `new ArrayList` in `execute()`: `filterByTab` (1 copy), `filterBySearch` (1 copy), `tagAffordability` (1 copy of TaggedRecipe objects), `filterByAffordability` (1 copy), `filterBySets` called twice (2 copies), `filterByMaterialGroups` called twice (2 copies), `extractSets` (1 copy), `sort` (1 copy). That's ~10 list allocations per execution × 3 executions per open = **~30 intermediate lists** of up to 500 records each. |
| F6 | Excessive logging | **HIGH** | [StencilSelectionPage.java](src/main/java/com/CodeCreature/ui/bench/StencilSelectionPage.java#L143-L193) | **10 `INFO`-level log statements fire on every menu open** in `loadRecipes()` and `buildCategoryInfoMap()`. Several perform string concatenation with collections (L181: `tabKeyCounts`, L183: `benchIds`, L193: `recipeCatIds`, L850: `map.keySet()`). The per-recipe log inside the `for` loop (L158-161) fires for every recipe where a bench ID resolves differently — potentially hundreds of times. These all run on the server thread, forcing string building + I/O. |
| F7 | Excessive logging | **MEDIUM** | [StencilSelectionPage.java](src/main/java/com/CodeCreature/ui/bench/StencilSelectionPage.java#L826-L850) | **`buildCategoryInfoMap()` logs every top-level and child category at INFO.** Lines 830-845 log per-category including children. With ~12 top-level + ~40 child categories, that's ~50+ INFO log calls on every open, each doing string concatenation. |
| F8 | Excessive cmd.set() volume | **HIGH** | [GridLayoutController.java](src/main/java/com/CodeCreature/ui/bench/GridLayoutController.java#L65-L120) + [StencilSelectionPage.java](src/main/java/com/CodeCreature/ui/bench/StencilSelectionPage.java#L371-L375) | **Grid update writes ~2000+ `cmd.set()` commands per update.** For 500 recipes across ~15 sets: each visible cell = 5 `cmd.set()` calls (Visible, ItemId, Dim.Visible, Btn.Style, + group header). Each hidden cell = 2 calls. Hidden groups = 1 call each. Total estimate for 500 cells: ~2500 `cmd.set()` calls for the grid alone. Add set filters (~100), material groups (~100), bench tabs (~50), detail panel (~40), ingredient tree (~200+). A single `sendUpdate` could carry **3000+ commands**. |
| F9 | Disk I/O on server thread | **HIGH** | [StencilBookPrefsStore.java](src/main/java/com/CodeCreature/ui/bench/StencilBookPrefsStore.java#L44-L53) | **`savePrefs()` writes BSON to disk synchronously on the server thread.** Called on every tab switch, every filter toggle, every search input, every recipe select — 8 call sites in `handleDataEvent()`. Each call does BSON encode + file write. Under rapid interaction, this is N synchronous disk writes blocking the server thread. |
| F10 | Debouncing gaps | **CRITICAL** | [StencilSelectionPage.java](src/main/java/com/CodeCreature/ui/bench/StencilSelectionPage.java#L551-L560) | **Search input has no debounce.** Every keystroke fires `handleDataEvent()` with `data.searchQuery`, which runs `applyFilter()` (500+ recipe pipeline), `pruneInvalidMaterialGroups()` (possible second pipeline run), full grid UI update (~2500 cmd.set), `savePrefs()` (disk write), and `sendUpdate()` (network packet). Typing "wood" = 4 full pipeline runs + 4 disk writes + 4 network packets, all synchronous on the server thread. |
| F11 | Redundant computation | **MEDIUM** | [StencilSelectionPage.java](src/main/java/com/CodeCreature/ui/bench/StencilSelectionPage.java#L280-L284) | **Prefs loaded twice during `build()`.** `StencilBookPrefsStore.load()` is called at L280 and again at L365 (to restore ingredient selections). Each call reads from disk and decodes BSON. |
| F12 | Quadratic scan | **MEDIUM** | [StencilSelectionPage.java](src/main/java/com/CodeCreature/ui/bench/StencilSelectionPage.java#L800-L811) + [DetailPanelController.java](src/main/java/com/CodeCreature/ui/bench/DetailPanelController.java#L125-L129) | **`findEntry()` is an O(N) linear scan** over ~500 recipes. Called from `DetailPanelController.updateUI()` on every interaction that triggers `updateDetail()`. Also called in `giveSelectedStencil()`. With 500 recipes, each scan touches up to 500 records. Not quadratic alone, but it's called inside flows that already do O(N) work. |
| F13 | Missing caching | **HIGH** | [StencilSelectionPage.java](src/main/java/com/CodeCreature/ui/bench/StencilSelectionPage.java#L136-L206) | **`loadRecipes()` rebuilds everything from scratch on every open.** Recipe data, bench IDs, category info map, ingredient tree — all recomputed from the registry on every `build()` call. The recipe registry is static at runtime. This is the dominant cost of the initial open. |
| F14 | Object churn | **MEDIUM** | [StencilSelectionPage.java](src/main/java/com/CodeCreature/ui/bench/StencilSelectionPage.java#L95-L130) | **`computeMaxLayout()` creates a full `InputRecipe` list then walks the entire pipeline output.** The `MaxLayoutInfo` never changes between opens (it depends only on the static recipe registry). It could be computed once and cached. |
| F15 | Excessive cmd.set() | **MEDIUM** | [IngredientTreeGridController.java](src/main/java/com/CodeCreature/ui/ingredienttree/IngredientTreeGridController.java#L93-L133) | **`updateUI()` writes every element every time**, regardless of whether state changed. With ~20 groups × ~5 children = ~100 nodes, each with 3-4 `cmd.set()` calls = ~350 commands per update. Called on ingredient toggle, affordability toggle, and every filter change. |

---

## Contribution Analysis

### (a) ~1-second freeze on menu open

**Primary contributors (ordered by impact):**

1. **F1 — Triple pipeline execution**: 3 full passes over 500 recipes, including 500 affordability checks each (F4). Estimated: ~60-70% of the freeze.
2. **F13 — Full rebuild from registry**: Recipe loading, ingredient tree building, category map construction — all from scratch. Estimated: ~15-20% of the freeze.
3. **F5 — 30 intermediate list copies**: GC pressure from ~30 ArrayList allocations of 500 records. Estimated: ~5%.
4. **F6/F7 — 60+ INFO logs with string concat**: Synchronous I/O on server thread. Estimated: ~5%.
5. **F8 — 3000+ cmd.set() accumulation**: Large command buffer on initial build. Estimated: ~5%.

### (b) Complete freeze after repeated use

**Primary contributors (ordered by impact):**

1. **F10 — No search debounce**: Each keystroke = full pipeline + disk write + network packet. A 10-char search = 10 sequential heavy operations. **This is the #1 cause.**
2. **F9 — Synchronous disk I/O per interaction**: Every event writes to disk. Under rapid use, disk I/O stacks up.
3. **F3 — Double pipeline in prune path**: Some events run the pipeline twice, doubling the already-heavy computation.
4. **F5 — Memory pressure accumulation**: ~10 ArrayList copies per event × rapid events = sustained GC pressure, potentially triggering full GC pauses.
5. **F4 — Uncached affordability checks**: 500 asset lookups + inventory scans per pipeline run, repeated on every interaction.

---

## Priority Fix Recommendations

| Priority | Fix | Findings Addressed | Expected Impact |
|----------|-----|-------------------|-----------------|
| **P0** | Add debouncing for search input (server-side throttle — only process the latest query after N ms of inactivity) | F10 | **Eliminates the repeated-use freeze** for the most common trigger |
| **P1** | Cache `InputRecipe` list — build once in `loadRecipes()`, reuse in `applyFilter()` and `computeMaxLayout()` | F2, F1 (partial) | Eliminates 500-object list rebuild on every event |
| **P2** | Cache `MaxLayoutInfo` — compute once per `loadRecipes()`, not per `build()` | F1 (partial), F14 | Eliminates 1 of 3 pipeline executions on open |
| **P3** | Merge `loadRecipes()`→`applyFilter()` into `build()`'s `applyFilter()` — avoid the early pipeline run before layout is even needed | F1 | Eliminates another pipeline execution on open, down to 1 |
| **P4** | Make `savePrefs()` async or batched — defer disk write until dismiss, or write on a background thread | F9 | Eliminates synchronous disk I/O from every interaction |
| **P5** | Eliminate double `applyFilter()` in `pruneInvalidMaterialGroups()` — fold the prune check into the pipeline or return a "needs re-run" flag | F3 | Eliminates the second pipeline run on prune events |
| **P6** | Downgrade all per-open INFO logs to FINE/DEBUG, use lazy message suppliers | F6, F7 | Eliminates ~60 string concat + I/O operations per open |
| **P7** | Cache the `allRecipes`→`InputRecipe` conversion as a static/registry-level list | F13 (partial) | Avoids rebuilding 500 records from registry every open |
| **P8** | Cache recipe registry data across opens (recipe list, ingredient tree, category map are static) | F13 | Major reduction in open cost — skips all registry iteration |
| **P9** | Add dirty tracking to `IngredientTreeGridController.updateUI()` — only write changed elements | F15 | Reduces ~350 cmd.set() calls to only changed nodes |
| **P10** | Replace `findEntry()` linear scan with a `Map<String, RecipeEntry>` | F12 | O(1) lookup instead of O(N) scan |
