---
topic: "R9 Investigation — Placeholder Item in StructuralCrafting Input Slot"
category: "Crafting / Windows"
updated: 2026-04-22
sources: ["StructuralCraftingWindow.java (decompiled)", "CraftingManager.java (decompiled)", "CraftingWindow.java (decompiled)", "CraftingPlugin.java (decompiled)", "BenchRecipeRegistry.java (decompiled, engine)", "HytaleServer.java (decompiled)", "crafting-window-architecture.md", "resourcetypeid-resolution.md", "custom-bench-creation.md", "BlueprintBookRecipeMutator.java", "Bench_Blueprint.json", "Block_Placeholder_Blue.json", "Block_Placeholder_Green.json", "Block_Placeholder_Red.json"]
---

# R9 Investigation — Placeholder Item in StructuralCrafting Input Slot

## Verdict

| Question | Answer |
|----------|--------|
| Is R9 a Phase 1 blocker? | **No.** Phase 1 only needs "bench opens successfully." The StructuralCraftingWindow opens fine with an empty input slot. |
| Is R9 a Phase 2 blocker? | **Yes.** Phase 2 requires the placeholder to enter the input slot, trigger recipe display, and fire `CraftRecipeEvent.Pre`. Without a match, none of this works. |
| Can we make Block_Placeholder match all recipes? | **Yes — via ResourceTypes on the placeholder item asset + runtime recipe mutation.** See recommended approach below. |

---

## 1. How StructuralCraftingWindow Determines Which Recipes to Show

### The Flow

```
Player places item in input slot (slot 0)
        │
        ▼
isValidInput() filter runs
        │ Calls getMatchingRecipes(stack)
        │ If no matches → item REJECTED from slot (never enters)
        │ If matches exist → item accepted
        ▼
inputContainer.changeEvent fires
        │
        ▼
updateRecipes() runs
        │ Calls getMatchingRecipes(inputStack)
        │ Populates optionsContainer (slots 1–64) with output items
        │ Sends optionSlotRecipes array in windowData JSON
        ▼
Client renders option slots with recipe outputs
```

### Key Code: `getMatchingRecipes()`

```java
private ObjectList<CraftingRecipe> getMatchingRecipes(ItemStack inputStack) {
    if (inputStack == null) return null;

    List<CraftingRecipe> recipes = CraftingPlugin.getBenchRecipes(bench.getType(), bench.getId());
    ObjectList<CraftingRecipe> matching = new ObjectArrayList<>();

    for (CraftingRecipe recipe : recipes) {
        List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(recipe);
        // CRITICAL: Only matches recipes with EXACTLY 1 input material
        if (inputs.size() == 1 && CraftingManager.matches(inputs.getFirst(), inputStack)) {
            matching.add(recipe);
        }
    }

    return matching.isEmpty() ? null : matching;
}
```

### Key Code: `isValidInput()` — The Gate

```java
private boolean isValidInput(FilterActionType type, ItemContainer container, short slot, ItemStack stack) {
    if (type != FilterActionType.ADD) return true;
    ObjectList<CraftingRecipe> matching = this.getMatchingRecipes(stack);
    return matching != null && !matching.isEmpty();
}
```

**This is a hard gate.** If the item matches zero recipes, it is **physically rejected** from the input slot. The player cannot place it there at all. There is no "empty recipe grid" state — the item simply bounces back to the cursor.

### Key Code: `CraftingManager.matches()`

The engine uses exact string equality for matching:

```
Recipe MaterialQuantity
    │
    ├── Has ItemId? ──→ inputStack.getItem().getId().equals(itemId)
    │
    ├── Has ResourceTypeId? ──→ For each rt in inputStack.getItem().getResourceTypes():
    │                               if resourceTypeId.equals(rt.getId()) → MATCH
    │
    └── Has TagIndex? ──→ inputStack has matching tag → MATCH
```

No wildcards. No prefix matching. No BlockGroup consultation. Pure string equality.

---

## 2. What Happens When a Non-Recipe Item Is Placed in the Input Slot

**It is rejected.** The `isValidInput` slot filter prevents the item from entering slot 0. The item stays on the player's cursor. No crash. No error. No empty grid displayed.

The StructuralCraftingWindow only shows two states:
1. **Input slot empty** → Option slots empty, grid blank
2. **Input slot has valid item** → Option slots populated with matching recipe outputs

There is no "invalid item in slot" state — the filter prevents it entirely.

---

## 3. Current Block_Placeholder Definition

All three placeholder variants (`Block_Placeholder_Blue`, `_Green`, `_Red`) have the same structure:

```json
{
  "TranslationProperties": { "Name": "...", "Description": "..." },
  "Icon": "Icons/ItemsGenerated/Bench_Architects.png",
  "Categories": ["Tool.PlaceBlock"],
  "SubCategory": "BuildingTools",
  "MaxStack": 1,
  "Quality": "Tool",
  "PlayerAnimationsId": "Item",
  "Interactions": { "Use": "PlaceBlock_Menu" }
}
```

**Missing fields that matter:**
- **No `ResourceTypes`** — cannot match any recipe's `ResourceTypeId` input
- **No `BlockType`** — not a block item, so it won't match any recipe's `ItemId` for block items
- **No `Tags`** — cannot match any recipe's `TagIndex` input

**Result: `Block_Placeholder` will be REJECTED from the input slot** by `isValidInput()`. Zero recipes match → filter returns false → item bounces back.

---

## 4. StructuralCraftingWindow UI — No Category Tabs

Unlike `PocketCrafting` (FieldCraftingWindow) and `BasicCrafting` (SimpleCraftingWindow), the **StructuralCraftingWindow does NOT display category tabs**. Per decompiled code and documentation:

> "StructuralCraftingWindow does NOT write `categories` to JSON — it gets recipes dynamically based on the input item slot."

> "For StructuralCraftingWindow specifically, categories are NOT displayed as tabs — instead, recipes are resolved dynamically based on the input item. The `Categories` array on a `StructuralCrafting` bench controls which recipe categories are *eligible*, and the `HeaderCategories` array controls visual grouping of the options."

The UI shows:
- **1 input slot** (left side) — player drops a block here
- **64 option slots** (right side grid) — populated from `updateRecipes()` based on the input
- **Block group cycling arrows** (if `AllowBlockGroupCycling: true`) — cycles input between variants
- **No category tabs** — unlike PocketCrafting/BasicCrafting

This means Phase 1 test criterion "shows recipe categories" is **not applicable** to StructuralCrafting. The bench will open showing an empty input slot and an empty option grid. This is normal and correct behavior.

---

## 5. Phase 1 vs Phase 2 Impact

### Phase 1: NOT Blocked

Phase 1's goal is: "Create the `Bench_PlaceBlock_Builders` bench block asset, place it in the world, and verify the engine opens a `StructuralCraftingWindow` when right-clicked."

| Phase 1 Test | R9 Impact | Status |
|---|---|---|
| Place bench in world → bench block appears | No impact | ✅ Works |
| Right-click bench → StructuralCraftingWindow opens | No impact — window opens regardless of input | ✅ Works |
| ~~Category tabs match configured categories~~ | N/A — StructuralCrafting has no category tabs | ⚠️ Revise test |
| Place `Block_Placeholder` in input slot → observe recipes | **BLOCKED — placeholder will be rejected from slot** | ❌ Fails |
| Place a normal block item in input slot → recipes appear | No impact — normal blocks match normally | ✅ Works |

**Phase 1 is not blocked** as long as test #4 is reclassified as a Phase 2 validation test. Phase 1 can verify the bench opens and that normal items work in the input slot. The placeholder-specific behavior is a Phase 2 concern.

**Recommendation:** Move Phase 1 test #4 ("Place `Block_Placeholder` in input slot → observe whether recipes appear") to Phase 2 prerequisites. Phase 1 should only verify the bench opens and normal crafting works at it.

### Phase 2: BLOCKED Until Resolved

Phase 2 requires the full flow: placeholder enters input slot → recipes shown → player selects recipe → `CraftRecipeEvent.Pre` fires → interceptor arms placeholder.

If the placeholder can't enter the input slot, the entire Phase 2 flow is blocked at step 1. **R9 must be resolved before Phase 2 begins.**

---

## 6. Approaches for Making Block_Placeholder Match Recipes

### Approach A: Add ResourceTypes to Block_Placeholder (Recommended)

Give the placeholder a comprehensive `ResourceTypes` array that includes every `ResourceTypeId` used by single-input recipes at the bench:

```json
{
  "ResourceTypes": [
    { "Id": "Wood_Hardwood" },
    { "Id": "Wood_Softwood" },
    { "Id": "Wood_All" },
    { "Id": "Rock" },
    { "Id": "Stone" },
    { "Id": "Clay" }
  ]
}
```

**How it works:**
- `CraftingManager.matches()` checks if the input item's `ResourceTypes` includes the recipe's `ResourceTypeId`
- If `Block_Placeholder` declares `"Wood_Hardwood"`, it matches all recipes with `ResourceTypeId: "Wood_Hardwood"`
- By declaring ALL ResourceTypeIds used by bench recipes, it matches ALL recipes

**Pros:**
- Pure asset change — no Java code needed for matching
- Engine handles everything natively: `isValidInput` accepts it, `updateRecipes` populates options
- All matching recipes appear in the option grid for selection

**Cons:**
- Must enumerate all ResourceTypeIds used by recipes at the bench (can be done at dev time by scanning recipe assets)
- If new recipes with new ResourceTypeIds are added later, the placeholder must be updated
- The placeholder becomes "every resource type at once" — semantically weird but functionally correct
- `ChangeBlockAction` (block group cycling) may behave unexpectedly since the placeholder isn't a real block
- All matching recipes show at once (potentially >64 — but StructuralCrafting recipes tend to be grouped by input ResourceType, so this could overflow)

**Overflow risk:** If the placeholder matches hundreds of recipes, only the first 64 fit in the option slots. The engine's `updateRecipes()` populates slots sequentially and stops at capacity 64. Recipes beyond slot 64 are silently dropped.

### Approach B: Runtime Recipe Mutation — Add Placeholder ItemId to Recipes

During `LoadAssetEvent`, clone all bench recipes and create versions with `ItemId: "Block_Placeholder_Blue"` as input:

```java
// For each recipe assigned to the bench:
MaterialQuantity placeholderInput = new MaterialQuantity();
placeholderInput.setItemId("Block_Placeholder_Blue");
placeholderInput.setQuantity(1);
// Create a clone recipe with this input
```

**Pros:**
- No changes to the placeholder item asset
- Explicit control over which recipes match

**Cons:**
- Creates hundreds of duplicate recipes in the asset registry
- Requires reflection to construct `MaterialQuantity` and `CraftingRecipe` instances
- Must handle all three placeholder variants (Blue, Green, Red) or consolidate to one
- `getMatchingRecipes()` requires `inputs.size() == 1` — each cloned recipe must have exactly 1 input
- Maintenance burden: new recipes must also be cloned

### Approach C: Custom StructuralCraftingWindow Subclass

Replace the engine's `StructuralCraftingWindow` with a custom subclass that overrides `isValidInput` and `getMatchingRecipes`.

**Pros:**
- Full control over matching logic
- Could implement a true wildcard match

**Cons:**
- `StructuralCraftingWindow` is an engine class, not designed for plugin extension
- Would need to intercept `OpenBenchPageInteraction` to substitute the window
- Requires reimplementing `updateRecipes()`, `handleAction()`, `onClose0()`, and all container setup
- Fragile across engine updates
- **Rejected in the design doc** — "No custom window needed; the engine opens a real StructuralCraftingWindow"

### Approach D: Intercept UseBlockEvent.Pre and Pre-populate the Window

Instead of modifying the placeholder to match recipes, intercept the bench interaction and inject a different item into the input slot after the window opens, or replace the window entirely.

**Pros:**
- No changes to item assets or recipe assets

**Cons:**
- Timing-sensitive — the window is opened by the engine's interaction chain
- Replacing the window still requires reimplementation (same as Approach C)
- Injecting items post-open might trigger `updateRecipes()` with the wrong item

---

## 7. Recommended Approach

**Use Approach A (ResourceTypes on Placeholder) + runtime completion at `LoadAssetEvent`.**

### Step 1: Enumerate ResourceTypeIds at LoadAssetEvent

```java
// During LoadAssetEvent, scan all recipes for the bench
Set<String> allResourceTypeIds = new HashSet<>();
for (CraftingRecipe recipe : CraftingRecipe.getAssetMap().getAssetMap().values()) {
    for (BenchRequirement req : recipe.getBenchRequirement()) {
        if ("PlaceBlock_Builders".equals(req.getId()) && "StructuralCrafting".equals(req.getType())) {
            MaterialQuantity[] inputs = recipe.getInput();
            if (inputs.length == 1 && inputs[0].getResourceTypeId() != null) {
                allResourceTypeIds.add(inputs[0].getResourceTypeId());
            }
        }
    }
}
// allResourceTypeIds now contains every ResourceTypeId that bench recipes accept
```

### Step 2: Add ResourceTypes to Placeholder Item via Reflection

```java
// Use reflection to set ResourceTypes on the Block_Placeholder item assets
Item placeholderItem = Item.getAssetMap().getAsset("Block_Placeholder_Blue");
// Build ResourceType array from allResourceTypeIds
// Set via reflection on the Item's resourceTypes field
```

### Step 3: Handle the >64 Recipe Overflow

If the bench has more than 64 matching recipes (likely when the placeholder matches ALL recipes), the option grid can only show 64. Options:

1. **Accept the limit** — first 64 recipes appear. Player can use block group cycling to switch to a different resource type's recipes.
2. **Filter by HeaderCategory** — only show recipes from the currently selected header category group. Requires custom `updateRecipes()` logic (Approach C territory).
3. **Show ALL recipes but paginate** — no engine support for pagination in StructuralCrafting.

**Recommendation:** Accept the 64-slot limit for now. Block group cycling may naturally segment recipes. If overflow is confirmed in testing, escalate to a design decision.

### Step 4: Disable Block Group Cycling for Placeholder

When the placeholder is in the input slot and the player hits the cycling button, `changeBlockType()` tries to cycle through items in the same ResourceType group. Since the placeholder isn't a real block, this could behave unpredictably.

**Mitigation:** Set `AllowBlockGroupCycling: false` on the `Bench_PlaceBlock_Builders` bench asset. This removes the cycling arrows entirely. Alternatively, intercept `ChangeBlockAction` via `CraftRecipeEvent.Pre` (but ChangeBlockAction is NOT a CraftRecipeEvent — it's a separate WindowAction).

---

## 8. The 64-Slot Problem — Deeper Analysis

The Builders Bench avoids the 64-slot limit because each input item (e.g., `Wood_Hardwood_Planks`) only matches recipes with `ResourceTypeId: "Wood_Hardwood"` — typically 10–20 structural variants (planks, stairs, slabs, fences, etc.).

If `Block_Placeholder` matches ALL ResourceTypeIds, the option grid shows ALL structural recipes for ALL materials simultaneously. For a bench with recipes across 10 material types × 15 shapes = 150+ recipes → only first 64 shown.

**This is a significant UX issue** but not a crash or functional blocker. The recipes that DO appear are still selectable and craftable (interceptable).

### Possible mitigations:

1. **Split by HeaderCategory** — The bench `HeaderCategories` field groups materials (e.g., "WoodPlanks", "Bricks"). If the client uses this for visual grouping of option slots, the display might naturally organize. But the 64-slot cap still applies to the total.

2. **Use a PocketCrafting window instead** — `WindowType.PocketCrafting` shows categories as tabs with scrollable recipe lists (no 64-slot limit). But this loses the input-slot-to-output-grid interaction pattern.

3. **Two-step selection** — Phase 1 of the bench interaction selects a material (via category), Phase 2 shows shapes for that material. Requires a custom window or two windows.

4. **Multiple bench blocks** — One bench per material family (Wood bench, Stone bench, etc.). Each bench has fewer recipes. Most complex for the player but simplest technically.

---

## 9. Gotchas

1. **`isValidInput` is the FIRST gate** — Before `updateRecipes()` even runs, the slot filter must accept the item. If ResourceTypes don't match any recipe, the item bounces back immediately.

2. **`inputs.size() == 1` requirement** — `getMatchingRecipes()` only matches recipes with exactly ONE input. Multi-input recipes (common in `Crafting`-type benches like Furniture) are excluded. Since StructuralCrafting recipes are single-input by convention, this should be fine for the Builders bench recipes.

3. **ResourceTypes is on the Item asset, not ItemStack** — You modify it on the `Item` (asset definition), not per-instance. All placeholder stacks share the same Item asset. Modification via reflection at `LoadAssetEvent` affects all instances.

4. **Block group cycling with placeholder** — The `ChangeBlockAction` handler calls `changeBlockType()`, which cycles through items sharing the same ResourceType group. For the placeholder (which declares many ResourceTypes), this could find unexpected "next" items. Set `AllowBlockGroupCycling: false` on the bench to avoid this.

5. **Recipe sorting** — `updateRecipes()` calls `sortRecipes(matchingRecipes, bench)` before populating slots. The sort order determines which recipes appear in the first 64 slots. This is controlled by the engine and may not be predictable when all recipes match.

---

## 10. Summary of Findings

| Finding | Detail |
|---------|--------|
| **How StructuralCrafting filters recipes** | `isValidInput()` gate → `getMatchingRecipes()` → `CraftingManager.matches()` with exact string equality on `ResourceTypeId` / `ItemId` / `TagIndex` |
| **What happens with a non-matching item** | Item is **rejected from the input slot** — cannot be placed. No crash, no empty grid. |
| **StructuralCrafting UI model** | No category tabs. Empty input + empty options grid until valid item placed. Recipes appear dynamically based on input item. |
| **R9 Phase 1 blocker?** | **No.** Phase 1 tests bench opening and normal item crafting. Placeholder-in-slot is a Phase 2 test. |
| **R9 Phase 2 blocker?** | **Yes.** Placeholder must enter input slot to trigger recipe display → `CraftRecipeEvent.Pre` flow. |
| **Recommended approach** | Add comprehensive `ResourceTypes` to Block_Placeholder at `LoadAssetEvent` covering all bench recipe ResourceTypeIds. |
| **64-slot overflow risk** | Real — placeholder matches all recipes, but option grid holds 64. Mitigate by setting `AllowBlockGroupCycling: false` and accepting the cap, or escalate to design. |

---

## 11. Stencil Crafting Investigation — Why Items Are Not Highlighted

### 11.1 Root Cause: CraftingPlugin Registry Not Updated After Mutation

**The mutation is working correctly** — `BlueprintBookRecipeMutator` successfully adds `BenchRequirement` entries with `Id: "Blueprint"` and `Type: StructuralCrafting` to each recipe's `benchRequirement` array via reflection. The in-memory `CraftingRecipe` objects are correctly modified.

**The problem is timing.** The engine's `CraftingPlugin` builds its internal `BenchRecipeRegistry` index **before** the plugin's mutation runs, and the mutation never triggers re-indexing.

#### Event timeline (from decompiled `HytaleServer.boot()` and `CraftingPlugin`):

```
1. pluginManager.setup()
   └── CraftingPlugin.setup() registers handlers:
       - LoadedAssetsEvent<CraftingRecipe> → onRecipeLoad()
       - LoadedAssetsEvent<Item> → onItemAssetLoad()

2. Assets are loaded from JSON files
   └── LoadedAssetsEvent<CraftingRecipe> fires
       └── CraftingPlugin.onRecipeLoad() runs:
           - For each recipe, reads BenchRequirement[] from the recipe object
           - Creates BenchRecipeRegistry entries keyed by BenchRequirement.id
           - Recipes have "Builders" and "Furniture_Bench" requirements
           - Registries created: "Builders" → [...], "Furniture_Bench" → [...]
           - NO "Blueprint" registry created (no recipe has Blueprint requirement yet)
       └── computeBenchRecipeRegistries() recomputes all registries

3. LoadedAssetsEvent<Item> fires
   └── CraftingPlugin.onItemAssetLoad() generates inline recipes
       └── More LoadedAssetsEvent<CraftingRecipe> events
       └── Still no "Blueprint" requirements on any recipe

4. ★ LoadAssetEvent fires (ALL assets loaded)
   └── Plugin's onAssetsLoaded() runs:
       └── DropScaler.apply()
       └── BlueprintBookRecipeMutator.mutate()
           - Iterates all CraftingRecipe objects
           - Adds BenchRequirement{id:"Blueprint", type:StructuralCrafting} to each
           - Mutated count logged to console
           - ★ BUT: CraftingPlugin.registries map is NOT updated
           - ★ No "Blueprint" BenchRecipeRegistry exists in the map
```

#### Evidence chain:

**`CraftingPlugin.getBenchRecipes()` (decompiled):**

```java
public static List<CraftingRecipe> getBenchRecipes(BenchType benchType, String benchId, String category) {
    BenchRecipeRegistry registry = registries.get(benchId);  // ← "Blueprint" → null
    if (registry == null) {
        return List.of();  // ← EMPTY LIST returned
    }
    // ...
}
```

**`StructuralCraftingWindow.onOpen0()` (decompiled):**

```java
// Called when the bench window opens — generates inventory highlights
this.windowData.add("inventoryHints",
    CraftingManager.generateInventoryHints(
        CraftingPlugin.getBenchRecipes(this.bench),  // ← bench.getId() = "Blueprint" → empty list
        0,
        inventory.getCombinedHotbarFirst()
    ));
```

**`CraftingManager.generateInventoryHints()` (decompiled):**

```java
public static JsonArray generateInventoryHints(List<CraftingRecipe> recipes, int inputSlotIndex, ItemContainer container) {
    JsonArray inventoryHints = new JsonArray();
    for (short i = 0; i < container.getCapacity(); i++) {
        ItemStack itemStack = container.getItemStack(i);
        if (itemStack != null && !itemStack.isEmpty() && matchesAnyRecipe(recipes, inputSlotIndex, itemStack)) {
            inventoryHints.add(i);  // ← never reached because recipes is empty
        }
    }
    return inventoryHints;  // ← empty array → no items highlighted
}
```

**`StructuralCraftingWindow.getMatchingRecipes()` (decompiled):**

```java
private ObjectList<CraftingRecipe> getMatchingRecipes(ItemStack inputStack) {
    List<CraftingRecipe> recipes = CraftingPlugin.getBenchRecipes(this.bench.getType(), this.bench.getId());
    // ← returns empty list for "Blueprint"
    if (recipes.isEmpty()) {
        return null;  // ← null → isValidInput returns false → item rejected
    }
    // ...
}
```

**Result:** `getBenchRecipes("Blueprint")` returns empty → `generateInventoryHints` produces empty array → no items highlighted. When the player tries to place an item, `getMatchingRecipes()` returns null → `isValidInput()` returns false → item bounces back. The bench appears completely empty/broken.

### 11.2 Is It a Mutation Issue, Bench Config Issue, or Engine Behavior?

**It is a registry synchronization issue.** The mutation itself is correct — the BenchRequirement objects are correctly constructed and appended. The bench JSON is correct — `Bench.Id: "Blueprint"` with `Type: StructuralCrafting` is valid. The engine behavior is correct — it queries its recipe index as designed.

The gap is that **the mutation modifies recipe objects in memory without notifying the CraftingPlugin's indexing system.** The reflective mutation bypasses the engine's event-driven indexing flow.

### 11.3 Fix: Trigger CraftingPlugin Re-indexing After Mutation

The `CraftingPlugin.onRecipeLoad()` handler is registered on `LoadedAssetsEvent<CraftingRecipe>`. This event fires when recipes are loaded via `CraftingRecipe.getAssetStore().loadAssets()`. If we re-load the mutated recipes through the asset store API after mutation, the engine will re-index them with their new BenchRequirement entries.

**From decompiled `CraftingPlugin.onRecipeLoad()`:**

```java
private static void onRecipeLoad(LoadedAssetsEvent<...> event) {
    for (CraftingRecipe recipe : event.getLoadedAssets().values()) {
        // 1. Remove from all existing registries
        for (BenchRecipeRegistry registry : registries.values()) {
            registry.removeRecipe(recipe.getId());
        }
        // 2. Re-add based on CURRENT BenchRequirements (includes "Blueprint" after mutation)
        if (recipe.getBenchRequirement() != null) {
            for (BenchRequirement benchRequirement : recipe.getBenchRequirement()) {
                BenchRecipeRegistry benchRecipeRegistry =
                    registries.computeIfAbsent(benchRequirement.id, BenchRecipeRegistry::new);
                benchRecipeRegistry.addRecipe(benchRequirement, recipe);
            }
        }
    }
    computeBenchRecipeRegistries();  // Recomputes allMaterialIds, allMaterialResourceType, etc.
}
```

The `computeIfAbsent(benchRequirement.id, ...)` call will lazily create a new `BenchRecipeRegistry` for `"Blueprint"` the first time a recipe with that requirement is processed.

**Recommended fix for `BlueprintBookRecipeMutator.mutate()`:**

```java
public static void mutate() {
    // ... existing mutation logic ...
    List<CraftingRecipe> mutatedRecipes = new ArrayList<>();
    // ... collect each mutated recipe into mutatedRecipes ...

    // After mutation: trigger CraftingPlugin re-indexing
    if (!mutatedRecipes.isEmpty()) {
        CraftingRecipe.getAssetStore().loadAssets("Hytale:Hytale", mutatedRecipes);
        log("Re-indexed " + mutatedRecipes.size() + " recipes via loadAssets().");
    }
}
```

This pattern is safe because:
- `loadAssets()` with existing recipe IDs replaces them in the asset map (same objects, no-op for data)
- It fires `LoadedAssetsEvent<CraftingRecipe>` which `CraftingPlugin.onRecipeLoad()` handles
- The handler removes each recipe from all registries, then re-adds based on CURRENT requirements
- `computeIfAbsent("Blueprint", ...)` creates the "Blueprint" registry on first encounter
- `computeBenchRecipeRegistries()` recomputes material indexes for all registries
- This is the same pattern the engine uses internally in `onItemAssetLoad()` (line 164 of CraftingPlugin.java)

### 11.4 Secondary Issue: Multi-Input Furniture Recipes at StructuralCrafting

Even after fixing the registry issue, **Furniture recipes will not appear in the StructuralCrafting option grid.** The `StructuralCraftingWindow.getMatchingRecipes()` method has a hard filter:

```java
if (inputMaterials.size() == 1 && CraftingManager.matches(inputMaterials.getFirst(), inputStack)) {
    matchingRecipes.add(recipe);
}
```

Furniture recipes typically have multiple inputs (e.g., `Wood_All × 3 + Ingredient_Fibre × 4`). Since `inputs.size() != 1`, they are always excluded from the option grid. This is by design — StructuralCrafting is built for single-input "transform one material into structural variants" workflows.

**Impact on the Stencil Crafting:**
- Builders recipes (single input) → ✅ Will appear in option grid after re-indexing fix
- Furniture recipes (multi input) → ❌ Will NOT appear in option grid
- Furniture recipe inputs WILL contribute to `generateInventoryHints` (highlighting) because `matchesAnyRecipe()` checks `input[inputSlotIndex]` without the `size() == 1` filter — this could cause misleading highlights for items that match Furniture recipes but can't actually be crafted at this bench

**Recommendation:** Either:
1. Remove Furniture recipes from Blueprint mutation (only mutate Builders recipes), OR
2. Accept that Furniture recipes are indexed but invisible, and plan a separate window for Furniture crafting, OR
3. Filter the `SOURCE_BENCH_IDS` set to `Set.of("Builders")` only, removing `"Furniture_Bench"`

### 11.5 Category Mismatch — Not a Blocker But Affects Sorting

The `BlueprintBookRecipeMutator` copies categories from the source BenchRequirement to the new Blueprint requirement. For Builders recipes, these categories (e.g., `"WoodPlanks"`, `"Stairs"`) match the Blueprint bench's `Categories` and `HeaderCategories` arrays. For Furniture recipes, categories like `"Furniture_Beds"`, `"Furniture_Tables"` do NOT match the Blueprint bench's categories.

This does NOT block recipe matching (category filtering is opt-in via the 3-arg `getBenchRecipes(type, id, category)` overload — the StructuralCraftingWindow uses the 2-arg overload with no category filter). However, it affects **sorting order**: `getSortingPriority()` assigns `Integer.MAX_VALUE` to recipes whose categories don't match the bench's `Categories` list, pushing them to the end of the option grid.

### 11.6 BenchRequirement Field Names — Confirmed Correct

The mutator accesses BenchRequirement fields directly and via reflection. Verified against decompiled code:

| Field | Access Pattern | Correct? |
|-------|---------------|----------|
| `req.id` | Direct public field access | ✅ `public String id` (confirmed in `CraftingPlugin.onRecipeLoad()`, `hasHeaderCategory()`, `getSortingPriority()`) |
| `req.type` | Reflection (`BenchRequirement.class.getDeclaredField("type")`) | ✅ Field exists (used as `requirement.type == benchType` in multiple engine methods) |
| `req.categories` | Reflection (`BenchRequirement.class.getDeclaredField("categories")`) | ✅ `String[] categories` (used in `addRecipe()`, `hasHeaderCategory()`, `getSortingPriority()`) |
| `req.requiredTierLevel` | Reflection (`BenchRequirement.class.getDeclaredField("requiredTierLevel")`) | ✅ Used in `CraftingManager.isValidBenchForRecipe()` |

The `CraftingRecipe.benchRequirement` field name is also correct (used in `AssetFieldAccessor` for other reflective access). No field name issues.

### 11.7 Timing — Is There a Cache Issue?

No. The StructuralCraftingWindow does NOT cache recipes at construction time. Both `generateInventoryHints()` and `getMatchingRecipes()` call `CraftingPlugin.getBenchRecipes()` dynamically — they query the live registry on every invocation. The `inventoryRegistration` callback (registered in `onOpen0()`) also calls `generateInventoryHints()` on every inventory change.

The issue is purely that the registry has no "Blueprint" entry — not that the window cached stale data. Once the registry is populated via the `loadAssets()` fix, all dynamic lookups will work correctly.

---

## 12. Updated Plan for Block_Placeholder Recognition (Phase 2)

### 12.1 Approach Still Valid — ResourceTypes on Placeholder

The recommended approach from Section 6 (Approach A) remains valid. Add a comprehensive `ResourceTypes` array to Block_Placeholder at `LoadAssetEvent` time covering all `ResourceTypeId` values used by single-input Blueprint bench recipes.

### 12.2 Collecting Required ResourceTypeIds

After the Section 11 fix (re-indexing via `loadAssets()`), the Stencil CraftingRecipeRegistry will exist. We can collect ResourceTypeIds from it:

```java
// After BlueprintBookRecipeMutator.mutate() + loadAssets() re-indexing
Set<String> allResourceTypeIds = new HashSet<>();
for (CraftingRecipe recipe : CraftingRecipe.getAssetMap().getAssetMap().values()) {
    BenchRequirement[] reqs = recipe.getBenchRequirement();
    if (reqs == null) continue;
    for (BenchRequirement req : reqs) {
        if ("Blueprint".equals(req.id) && req.type == BenchType.StructuralCrafting) {
            MaterialQuantity[] inputs = recipe.getInput();
            // Only collect from single-input recipes (the only ones getMatchingRecipes accepts)
            if (inputs.length == 1 && inputs[0].getResourceTypeId() != null) {
                allResourceTypeIds.add(inputs[0].getResourceTypeId());
            }
            break;
        }
    }
}
```

Typical values: `Wood_Hardwood`, `Wood_Softwood`, `Wood_All`, `Wood_Hardwood_Trunk`, `Rock`, `Rock_Stone`, `Clay`, `Stone`, etc. Expected count: 10–30 unique ResourceTypeIds.

### 12.3 Timing Is Sufficient

Adding ResourceTypes to the Item asset at `LoadAssetEvent` time is sufficient because:

1. `generateInventoryHints()` calls `CraftingManager.matchesAnyRecipe()` which checks `itemStack.getItem().getResourceTypes()` — reads the Item asset live, no caching
2. `CraftingManager.matches()` (used by `getMatchingRecipes()`) also reads `getResourceTypes()` live
3. The Item asset is a shared singleton — modifying it via reflection at `LoadAssetEvent` affects all subsequent lookups
4. The `isValidCraftingMaterial()` method on `BenchRecipeRegistry` checks against `allMaterialResourceType` — but this is populated by `recompute()` during `computeBenchRecipeRegistries()`, which runs during the `loadAssets()` re-indexing step. As long as placeholder ResourceTypes are set BEFORE any bench interaction (which they will be — `LoadAssetEvent` fires at boot, before any player joins), this works.

**Key timing constraint:** The placeholder ResourceTypes must be set at `LoadAssetEvent` time, not later. If they were set lazily (e.g., on first bench interaction), they would not be available for `generateInventoryHints()` which runs on window open.

### 12.4 No Simpler Approach Exists

| Alternative | Feasible? | Why Not |
|-------------|-----------|---------|
| Wildcard ResourceType | No | Engine uses exact string equality — no wildcard support |
| Override `isValidInput()` | No | `StructuralCraftingWindow` is an engine class, not extensible by plugins |
| Override `getMatchingRecipes()` | No | Same — engine class, not pluggable |
| Assign to a `Set` covering all types | No | `Set` field is for UI grouping, not crafting resolution |
| Use `TagIndex` | Possible but complex | Would require adding the tag to all recipes AND the placeholder; tags use integer indexes, harder to manipulate via reflection |

Approach A (ResourceTypes on Placeholder) remains the cleanest path.

### 12.5 64-Slot Overflow — Updated Analysis

If Block_Placeholder matches all ResourceTypeIds for single-input Blueprint recipes:

- Builders recipes alone: ~150–250 recipes across all material types × structural shapes
- All matching recipes are returned by `getMatchingRecipes()` (no category filter)
- `updateRecipes()` populates option slots sequentially, capped at 64
- Recipes beyond slot 64 are silently dropped
- `sortRecipes()` runs first: recipes with matching `HeaderCategories` sort to the top

**Mitigation options (unchanged from Section 8):**
1. Accept the 64-slot limit — first 64 sorted recipes appear
2. Set `AllowBlockGroupCycling: false` to prevent cycling confusion
3. Consider a two-phase approach: category selection → then material recipes

**No pagination or scrolling exists** in the StructuralCraftingWindow. The 64-slot grid is the hard limit.

### 12.6 New Risk: Highlighting vs. Acceptance Mismatch for Furniture

If Furniture recipes remain in the Blueprint mutation set, the placeholder would need `ResourceTypeId` values from Furniture recipes too (e.g., `Wood_All`). Items matching those ResourceTypes would be highlighted in inventory but placing them would show only Builders recipes (multi-input Furniture recipes filtered out). This creates a confusing UX where highlighted items may show fewer recipes than expected.

**Recommendation:** In Phase 2, limit ResourceTypes on the placeholder to values from single-input recipes only. Use the collection logic in 12.2 which already filters to `inputs.length == 1`.

---

## 13. Summary of Findings

| Finding | Detail |
|---------|--------|
| **Root cause: no highlighting** | `CraftingPlugin`'s `BenchRecipeRegistry` has no "Blueprint" entry because recipe indexing (`LoadedAssetsEvent`) completes before the plugin's mutation (`LoadAssetEvent`). `getBenchRecipes("Blueprint")` returns empty list. |
| **Is mutation correct?** | **Yes.** Field names, access patterns, and BenchRequirement construction are all correct. The mutation modifies the right objects correctly. |
| **Is it a bench config issue?** | **No.** `Bench_Blueprint.json` is correct — Type, Id, Categories, BlockEntity are all valid. The bench opens correctly. |
| **Is it engine behavior?** | **Partially.** The engine's event-driven indexing is working as designed — it doesn't monitor for reflective mutations. The plugin must trigger re-indexing explicitly. |
| **Fix for Phase 1** | After `BlueprintBookRecipeMutator.mutate()`, call `CraftingRecipe.getAssetStore().loadAssets("Hytale:Hytale", mutatedRecipes)` to fire `LoadedAssetsEvent<CraftingRecipe>` → `onRecipeLoad()` → creates "Blueprint" registry. |
| **Furniture recipe limitation** | Multi-input Furniture recipes will NOT appear in StructuralCrafting option grid (`getMatchingRecipes` requires `inputs.size() == 1`). Consider removing `Furniture_Bench` from mutation scope. |
| **Placeholder Phase 2 plan** | Add ResourceTypes to Block_Placeholder at LoadAssetEvent time. Collect from single-input Blueprint recipes. Approach is validated as feasible. |
| **64-slot overflow** | Still a risk. Placeholder matching all recipes produces ~150+ matches, but only 64 slots available. Accept limit or design two-phase UX. |
| **New risk: highlight mismatch** | If Furniture recipes stay in scope, inventory hints may highlight items that produce no visible recipes when placed. Limit ResourceTypes to single-input recipes only. |

## See Also

- [crafting-window-architecture.md](./crafting-window-architecture.md) — Full StructuralCraftingWindow decompilation
- [resourcetypeid-resolution.md](./resourcetypeid-resolution.md) — How `CraftingManager.matches()` works
- [custom-bench-creation.md](./custom-bench-creation.md) — Creating new bench blocks
- [design-placeblock-building-tool.md](../../Plans/design-placeblock-building-tool.md) — Parent design doc with R9 in risk register
