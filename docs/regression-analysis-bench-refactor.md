# Regression Analysis: Dynamic Bench Discovery Refactor

## Executive Summary

Two reported bugs and **five additional regressions** traced to a single primary root cause: `NaturalResourceRegistry.isCraftingBench()` was changed from a scoped 2-bench set to `BenchRegistry.allBenchIds()` (all benches), violating the documented invariant that processing recipes should not disqualify a block from being natural. This cascades through four downstream systems.

---

## 1. Root Cause Analysis

### Bug 1: "Roofs drop 12× of themselves instead of 12× of their resource"

**Classification**: REGRESSION from refactor  
**Root cause**: Two-factor failure

**Factor A — Recipe priority inversion in `BenchRecipeRegistries.getRecipeForBlock()`:**

`BenchRecipeRegistries.getRecipeForBlock()` iterates registries in insertion order ([BenchRecipeRegistries.java](src/main/java/com/CodeCreature/registry/BenchRecipeRegistries.java#L71-L76)):

```java
for (BenchRecipeRegistry reg : registries.values()) {
    CraftingRecipe recipe = reg.getRecipeForBlock(blockTypeId);
    if (recipe != null) return recipe;
}
```

Registry insertion order is determined by `BenchRegistry.allBenchIds()`, which returns a `LinkedHashMap.keySet()` populated by scanning recipes in asset-map iteration order ([BenchRegistry.java](src/main/java/com/CodeCreature/registry/BenchRegistry.java#L121-L133)). If a processing recipe (e.g., Stonecutter: `2× Roof_Block → 1× Roof_Block`) is encountered before the crafting recipe (e.g., Builders: `4× Wood → 2× Roof_Block`), the **processing recipe is returned instead of the crafting recipe**.

The processing recipe then enters `RecipeTreeResolver.resolveRecipeToRaw()` which:
1. Resolves the self-referential input (block as its own input)
2. `RecipeTierClassifier.isRawInput()` returns `false` (the block's item is now classified as "crafted" due to Factor B)
3. `resolveItemToRaw()` recurses → **cycle detected** → returns `null`
4. Falls back to treating the block as a terminal item → drops = N× of the block itself

**Factor B — `isCraftingBench()` scope expansion:**

[NaturalResourceRegistry.java](src/main/java/com/CodeCreature/scaling/NaturalResourceRegistry.java#L238-L247):
```java
private static boolean isCraftingBench(@Nonnull CraftingRecipe recipe) {
    Set<String> allBenchIds = BenchRegistry.allBenchIds(); // ← ALL benches
    // ...
}
```

The comment on lines 57-60 explicitly states: *"Processing recipes (e.g. 2× Rock_Shale → 1× Rock_Shale at the stonecutter) are refinement recipes and should NOT disqualify a block from being natural"* — **but the implementation does exactly this**.

Blocks with processing-only recipes are now classified as "craftable" (non-natural). This cascades to:
- `RecipeTierClassifier.isRawInput()` → returns false for these items (they're "crafted")  
- Phase 1 (`scaleCraftingCosts`) → does NOT scale their quantities by 12×  
- Phase 3a processes them with the wrong (processing) recipe at wrong (unscaled) quantities

**Execution trace for a block with both crafting and processing recipes:**

| Step | Before Refactor | After Refactor |
|------|----------------|----------------|
| `isCraftingBench()` scope | `{Builders, Furniture_Bench}` | ALL bench IDs |
| Block natural? | Not natural (recipe at Builders) | Not natural (same) |
| `getRecipeForBlock()` | Returns Builders recipe (only bench registered) | May return Stonecutter recipe (non-deterministic order) |
| Recipe used for drops | Crafting recipe → raw materials | Processing recipe → self-referential → **drops self** |

**For blocks with processing-ONLY recipes (no crafting recipe):**

| Step | Before Refactor | After Refactor |
|------|----------------|----------------|
| `isCraftingBench()` | false (Stonecutter not in set) | **true** (Stonecutter in allBenchIds) |
| Block natural? | **Yes** | **No** |
| Pipeline path | Phase 3b → 12× natural drops | Phase 3a → processing recipe → cycle → drops self |

---

### Bug 2: "Blocks that should recognize interchangeable blocks like cobblestone and shale only recognize an exact block like shale"

**Classification**: REGRESSION from refactor  
**Root cause**: Changed natural classification corrupts `ResourceTypeResolver` two-pass resolution

The `ResourceTypeResolver.initialize()` method pre-caches `isNatural` from `NaturalResourceRegistry.isNaturalItem()` ([ResourceTypeResolver.java](src/main/java/com/CodeCreature/scaling/ResourceTypeResolver.java#L69)):

```java
boolean isNatural = NaturalResourceRegistry.isNaturalItem(itemId);
```

The two-pass resolution in `resolveByResourceType()` ([ResourceTypeResolver.java](src/main/java/com/CodeCreature/scaling/ResourceTypeResolver.java#L120-L128)):

```java
// Pass 1: preferred items
for (IndexedItem item : items) {
    if (item.isNatural == preferNatural) return item.itemId;
}
// Pass 2: any item (first set-root)
return items.get(0).itemId;
```

**Execution trace for ResourceTypeId "Rock_All" with `preferNatural=false`:**

| Step | Before Refactor | After Refactor |
|------|----------------|----------------|
| Rock items natural? | All natural (Stonecutter not a "crafting bench") | **Some non-natural** (Stonecutter now a "crafting bench") |
| Pass 1 (non-natural) | No matches → falls through | **Matches newly non-natural Rock_Shale** → returns it |
| Pass 2 (fallback) | Returns first set-root (e.g., Rock_Shale_Cobble = generic cobblestone) | Never reached |
| Result | `Rock_Shale_Cobble` (generic, interchangeable) | `Rock_Shale` (specific variant) |

This means:
1. **Drops change**: blocks that should drop generic cobblestone now drop specific shale
2. **Affordability breaks**: `RecipeAffordabilityResolver` counts `container.countItemStacks(stack -> "Rock_Shale".equals(stack.getItemId()))` — a player with cobblestone can't afford recipes that resolve to shale

---

## 2. Regression Inventory

### Confirmed Regressions (from refactor)

| # | Regression | File | Severity |
|---|-----------|------|----------|
| R1 | `isCraftingBench()` scope expansion | [NaturalResourceRegistry.java](src/main/java/com/CodeCreature/scaling/NaturalResourceRegistry.java#L238) | **CRITICAL** — root cause of both bugs |
| R2 | Recipe priority inversion | [BenchRecipeRegistries.java](src/main/java/com/CodeCreature/registry/BenchRecipeRegistries.java#L71) | **CRITICAL** — causes wrong recipe selection for dual-recipe blocks |
| R3 | Pre-cached isNatural flags corrupted | [ResourceTypeResolver.java](src/main/java/com/CodeCreature/scaling/ResourceTypeResolver.java#L69) | **HIGH** — causes wrong ResourceType resolution |
| R4 | `RecipeTreeResolver.resolveRecipeToRaw()` hardcodes `preferNatural=false` | [RecipeTreeResolver.java](src/main/java/com/CodeCreature/crafting/RecipeTreeResolver.java#L170) | **MEDIUM** — Furniture Bench recipes resolve with wrong preference |
| R5 | `RecipeTierClassifier.isRawInput()` cascade | [RecipeTierClassifier.java](src/main/java/com/CodeCreature/scaling/RecipeTierClassifier.java#L87) | **HIGH** — wrong scaling in Phase 1 for inputs from processing-recipe blocks |
| R6 | No deny-list.json exists | Missing file | **MEDIUM** — safety net not deployed |
| R7 | `ELIGIBLE_BENCH_IDS` not unified with BenchRegistry | [RecipeTreeResolver.java](src/main/java/com/CodeCreature/crafting/RecipeTreeResolver.java#L84) | **LOW** — maintenance risk, not yet causing bugs |

### Pre-existing Issues (not regressions)

| # | Issue | File | Notes |
|---|-------|------|-------|
| P1 | Old processor files not deleted | `BuildersProcessor.java`, `FurnitureProcessor.java`, `OverlapProcessor.java` | Dead code; `OverlapProcessor.category()` references `BenchCategory` enum which may not compile |
| P2 | `GenericBenchProcessor.preferNatural()` unused in processing chain | [AbstractBenchProcessor.java](src/main/java/com/CodeCreature/scaling/AbstractBenchProcessor.java) | Flag is declared but never passed to `RecipeTreeResolver` — only used in a log message |

---

## 3. Proposed Fixes

### Fix 1: Restore `isCraftingBench()` scope (CRITICAL — fixes root cause of both bugs)

**File**: [NaturalResourceRegistry.java](src/main/java/com/CodeCreature/scaling/NaturalResourceRegistry.java#L238-L247)  
**Method**: `isCraftingBench()`  
**Change**: Replace `BenchRegistry.allBenchIds()` with a dedicated crafting-only bench set

```java
// BEFORE (broken):
private static boolean isCraftingBench(@Nonnull CraftingRecipe recipe) {
    Set<String> allBenchIds = BenchRegistry.allBenchIds();
    // ...
}

// AFTER (fixed):
private static final Set<String> CRAFTING_BENCH_IDS = Set.of("Builders", "Furniture_Bench");

private static boolean isCraftingBench(@Nonnull CraftingRecipe recipe) {
    BenchRequirement[] reqs = recipe.getBenchRequirement();
    if (reqs == null) return false;
    for (BenchRequirement req : reqs) {
        if (req != null && req.id != null && CRAFTING_BENCH_IDS.contains(req.id)) {
            return true;
        }
    }
    return false;
}
```

**Risk**: LOW — restores exact pre-refactor behavior for natural classification. No downstream changes needed since all downstream systems read from NaturalResourceRegistry.

### Fix 2: Stabilize recipe priority in `BenchRecipeRegistries` (CRITICAL)

**File**: [BenchRecipeRegistries.java](src/main/java/com/CodeCreature/registry/BenchRecipeRegistries.java#L37-L50)  
**Method**: `init()` and `getRecipeForBlock()`  
**Change**: Ensure crafting benches are iterated before processing benches by adding a `benchType` field to `BenchConfig` and sorting registries accordingly

```java
// In BenchConfig — add bench type:
public record BenchConfig(String benchId, boolean preferNatural, BenchType type) {
    public enum BenchType { CRAFTING, PROCESSING }
}

// In BenchRegistry.init() — classify bench type:
boolean isCrafting = Set.of("Builders", "Furniture_Bench", "Workbench", "Fieldcraft")
    .contains(req.id);
discovered.put(req.id, new BenchConfig(req.id, preferNatural,
    isCrafting ? BenchType.CRAFTING : BenchType.PROCESSING));

// In BenchRecipeRegistries.init() — sort crafting before processing:
List<String> sortedIds = new ArrayList<>(benchIds);
sortedIds.sort(Comparator.comparing(id -> {
    BenchConfig cfg = BenchRegistry.getConfig(id);
    return cfg != null && cfg.type() == BenchType.CRAFTING ? 0 : 1;
}));
```

**Risk**: MEDIUM — changes registry iteration order, which affects all blocks. However, the intent is correct: crafting recipes should take priority over processing recipes for drop generation.

**Alternative (lower risk)**: In `getRecipeForBlock()`, prefer recipes from crafting benches:

```java
public static CraftingRecipe getRecipeForBlock(@Nonnull String blockTypeId) {
    CraftingRecipe fallback = null;
    for (BenchRecipeRegistry reg : registries.values()) {
        CraftingRecipe recipe = reg.getRecipeForBlock(blockTypeId);
        if (recipe != null) {
            BenchConfig cfg = BenchRegistry.getConfig(reg.getBenchId());
            if (cfg != null && cfg.type() == BenchType.CRAFTING) {
                return recipe; // crafting recipe takes priority
            }
            if (fallback == null) fallback = recipe;
        }
    }
    return fallback;
}
```

### Fix 3: Wire `preferNatural` through `RecipeTreeResolver` (MEDIUM)

**Files**: [RecipeTreeResolver.java](src/main/java/com/CodeCreature/crafting/RecipeTreeResolver.java#L170), [AbstractBenchProcessor.java](src/main/java/com/CodeCreature/scaling/AbstractBenchProcessor.java#L57)  
**Change**: Add `preferNatural` parameter to `resolveRecipeToRaw()` and `computeRawCost()`. Wire it from `AbstractBenchProcessor.process()` via `preferNatural()`.

```java
// RecipeTreeResolver:
public static List<RawMaterialRequirement> resolveRecipeToRaw(
        @Nonnull CraftingRecipe recipe, boolean preferNatural) {
    // ...
    String resolvedId = ResourceTypeResolver.resolveInputItemId(mq, preferNatural);
    // ...
}

// AbstractBenchProcessor.process():
List<RawMaterialRequirement> rawCost =
    RecipeTreeResolver.resolveRecipeToRaw(recipe, preferNatural());
```

**Risk**: LOW — adds a parameter, doesn't change behavior for `preferNatural=false` callers (Builders bench). Fixes Furniture Bench resolution.

### Fix 4: Create `deny-list.json` (MEDIUM — safety net)

**File**: New file at plugin data directory  
**Content**:
```json
{
  "deniedBenchIds": []
}
```

An empty deny list is a no-op but ensures the mechanism works. If specific processing benches need exclusion from `allBenchIds()`, they can be added here without code changes.

**Risk**: NONE — file creation with empty deny list changes nothing.

### Fix 5: Unify `ELIGIBLE_BENCH_IDS` with `BenchRegistry` (LOW)

**File**: [RecipeTreeResolver.java](src/main/java/com/CodeCreature/crafting/RecipeTreeResolver.java#L84)  
**Change**: Replace hardcoded set with `BenchRegistry.allBenchIds()`:

```java
// BEFORE:
private static final Set<String> ELIGIBLE_BENCH_IDS = Set.of(
    "Builders", "Furniture_Bench", "Workbench", "Fieldcraft",
    "Stonecutter", "Refinery", "Furnace", "Kiln");

// AFTER:
// In isEligibleBenchRecipe():
private static boolean isEligibleBenchRecipe(@Nonnull CraftingRecipe recipe) {
    Set<String> eligibleIds = BenchRegistry.allBenchIds();
    // ...
}
```

**Risk**: LOW — current hardcoded set already matches BenchRegistry output. Unification prevents future drift.

### Fix 6: Delete dead processor files (LOW)

**Files to delete**:
- `src/main/java/com/CodeCreature/scaling/BuildersProcessor.java`
- `src/main/java/com/CodeCreature/scaling/FurnitureProcessor.java`
- `src/main/java/com/CodeCreature/scaling/OverlapProcessor.java`

**Risk**: NONE — dead code. `OverlapProcessor` references `BenchCategory` enum which may cause compile errors.

---

## 4. Risk Assessment Matrix

| Fix | Blast Radius | Regression Risk | Blocks Fix |
|-----|-------------|-----------------|------------|
| Fix 1 (isCraftingBench scope) | NaturalResourceRegistry only | LOW — restores proven behavior | Bug 1 (partial), Bug 2, R1, R3, R5 |
| Fix 2 (recipe priority) | BenchRecipeRegistries + all Phase 3a blocks | MEDIUM — changes recipe selection for dual-recipe blocks | Bug 1 (complete), R2 |
| Fix 3 (preferNatural wiring) | RecipeTreeResolver + AbstractBenchProcessor | LOW — additive parameter | R4, P2 |
| Fix 4 (deny-list.json) | None (empty file) | NONE | R6 |
| Fix 5 (ELIGIBLE_BENCH_IDS) | RecipeTreeResolver init | LOW | R7 |
| Fix 6 (delete dead files) | None | NONE | P1 |

**Recommended application order**: Fix 1 → Fix 2 → Fix 3 → Fix 4 → Fix 5 → Fix 6

Fix 1 alone resolves Bug 2 completely and mitigates Bug 1 for processing-only blocks. Fix 2 is required to fully resolve Bug 1 for dual-recipe blocks.

---

## 5. Dependency / Cascade Diagram

```
isCraftingBench() scope expansion (R1)
├── NaturalResourceRegistry.isNaturalBlock() → wrong classification
│   ├── Phase 3b filter: isNatural && !hasRecipe → wrong blocks enter/exit
│   └── RecipeTierClassifier.init() → wrong craftedItemIds
│       ├── RecipeTierClassifier.isRawInput() → wrong scaling decisions (R5)
│       │   └── Phase 1: scaleCraftingCosts → wrong quantities
│       └── RecipeTierClassifier.isCraftedItem() → wrong classification
├── NaturalResourceRegistry.isNaturalItem() → wrong classification
│   ├── ResourceTypeResolver.initialize() → wrong isNatural index (R3)
│   │   └── resolveByResourceType() → different concrete items → BUG 2
│   └── RecipeTierClassifier.isRawInput() → (same as above)
└── (No direct link to recipe priority, but compounds with R2)

Recipe priority inversion (R2)
└── BenchRecipeRegistries.getRecipeForBlock() → processing recipe returned
    └── Phase 3a: resolveRecipeToRaw(processing recipe) → self-referential
        └── Cycle → block drops itself → BUG 1

preferNatural not wired (R4)
└── RecipeTreeResolver.resolveRecipeToRaw(recipe) always passes false
    └── Furniture Bench recipes resolve with wrong preference
```

---

## 6. Verification Checklist

After applying fixes, verify:

- [ ] Rock_Shale, Rock_Limestone etc. are classified as natural blocks
- [ ] `isRawInput()` returns true for rock-type ResourceTypeIds
- [ ] Phase 1 scales rock inputs by 12×
- [ ] `resolveByResourceType("Rock_All", false)` returns a generic cobblestone (set-root), not a specific variant
- [ ] Blocks with dual recipes (crafting + processing) use the crafting recipe for drops
- [ ] Blocks with processing-only recipes go through Phase 3b (12× natural drops)
- [ ] Furniture Bench blocks resolve ResourceTypeIds with `preferNatural=true`
- [ ] Old processor files removed, build still compiles
