---
topic: "Debug: Shadow Recipe Registration Chain for Stencil Crafting"
category: "Crafting / Recipe Registration"
updated: 2026-04-23
sources: ["CraftingPlugin.java (decompiled)", "BenchRecipeRegistry.java (decompiled)", "StructuralCraftingWindow.java (decompiled)", "AssetStore.java (decompiled)", "CraftingManager.java (decompiled)", "CraftingRecipe.java (decompiled)", "BenchRequirement.java (decompiled)", "StencilBookRecipeMutator.java", "LoadedAssetsEvent.java (decompiled)", "LoadAssetEvent.java (decompiled)"]
---

# Debug: Shadow Recipe Registration Chain for Stencil Crafting

## Executive Summary

The full chain from `loadAssets()` → `LoadedAssetsEvent` → `onRecipeLoad()` → `BenchRecipeRegistry` → `getBenchRecipes()` → `getMatchingRecipes()` has been traced end-to-end. **Every link checks out in isolation.** The code should work as written. The investigation identifies **three failure points** that require runtime verification, ranked by likelihood.

---

## 1. The Complete Chain — Annotated

### Step 1: `StencilBookRecipeMutator.mutate()`

Called from `onAssetsLoaded(LoadAssetEvent)` in `UnobstructedThirdPersonPlugin`, **after** all assets are loaded:

```
UnobstructedThirdPersonPlugin.onAssetsLoaded(LoadAssetEvent)
  └── DropScaler.apply()
  └── StencilBookRecipeMutator.mutate()
```

Creates shadow recipes via:
```java
CraftingRecipe shadow = new CraftingRecipe(recipe);     // copy constructor
idField.set(shadow, "Stencil_" + originalId);          // reflection
inputField.set(shadow, new MaterialQuantity[]{           // reflection
    new MaterialQuantity(null, "PlaceBlock", null, 1, null)
});
benchReqField.set(shadow, new BenchRequirement[]{        // reflection
    new BenchRequirement(BenchType.StructuralCrafting, "Stencil", sourceReq.categories, 0)
});
```

Then calls:
```java
CraftingRecipe.getAssetStore().loadAssets("Hytale:Hytale", shadowRecipes);
```

### Step 2: `AssetStore.loadAssets()` (line 444)

```java
public AssetLoadResult<K, T> loadAssets(String packKey, List<T> assets, AssetUpdateQuery query, boolean forceLoadAll) {
    Map<K, T> loadedAssets = Collections.synchronizedMap(new Object2ObjectLinkedOpenHashMap<>());
    Set<Path> documents = new HashSet<>();
    this.loadAllChildren(loadedAssets, assets, documents);
    // loadAllChildren puts each shadow recipe into loadedAssets keyed by recipe.id
    // documents remains empty (no file paths for programmatic assets)

    List<RawAsset<K>> rawAssets = new ArrayList<>(documents.size()); // empty
    // ...
    this.loadAssets0(packKey, loadedAssets, rawAssets, ...);
}
```

**Key**: `loadAllChildren` uses `this.keyFunction.apply(asset)` which is `recipe -> recipe.id`. The `id` field was set via reflection to `"Stencil_XXX"`. So `loadedAssets.put("Stencil_XXX", shadow)`.

### Step 3: `AssetStore.loadAssets0()` (line 774)

```java
protected void loadAssets0(...) {
    // 1. decodeAssets — only processes rawAssets (empty), so no-op
    this.decodeAssets(packKey, preLoaded, loadedAssets, ...);
    // loadedAssets still contains shadow recipes untouched

    AssetRegistry.ASSET_LOCK.writeLock().lock();
    try {
        // 2. GenerateAssetsEvent — unlikely to affect shadow recipes
        // 3. failedToLoadKeys/failedToLoadPaths — both empty (no file decoding happened)
        // 4. Put shadow recipes into asset map
        this.assetMap.putAll(packKey, this.codec, loadedAssets, loadedKeyToPathMap, loadedAssetChildren);

        // 5. Fire LoadedAssetsEvent — SYNCHRONOUS
        if (!loadedAssets.isEmpty()) {
            IEventDispatcher dispatcher = this.getEventBus().dispatchFor(LoadedAssetsEvent.class, this.tClass);
            if (dispatcher.hasListener()) {
                dispatcher.dispatch(new LoadedAssetsEvent<>(this.tClass, this.assetMap, loadedAssets, false, query));
            }
        }
    } finally {
        AssetRegistry.ASSET_LOCK.writeLock().unlock();
    }
}
```

**Key**: `LoadedAssetsEvent` is dispatched **synchronously** within the write lock. The `loadedAssets` map passed to the event contains the shadow recipes. The asset map (`this.assetMap`) already has them inserted via `putAll` before the event fires.

### Step 4: `CraftingPlugin.onRecipeLoad()` (line 147)

Registered in `CraftingPlugin.setup()`:
```java
this.getEventRegistry().register(LoadedAssetsEvent.class, CraftingRecipe.class, CraftingPlugin::onRecipeLoad);
```

Handler:
```java
private static void onRecipeLoad(LoadedAssetsEvent<String, CraftingRecipe, ...> event) {
    for (CraftingRecipe recipe : event.getLoadedAssets().values()) {
        // Remove from all existing registries (no-op for new shadow recipes)
        for (BenchRecipeRegistry registry : registries.values()) {
            registry.removeRecipe(recipe.getId());
        }
        // Add to registry by BenchRequirement.id
        if (recipe.getBenchRequirement() != null) {
            for (BenchRequirement benchRequirement : recipe.getBenchRequirement()) {
                BenchRecipeRegistry benchRecipeRegistry =
                    registries.computeIfAbsent(benchRequirement.id, BenchRecipeRegistry::new);
                benchRecipeRegistry.addRecipe(benchRequirement, recipe);
            }
        }
    }
    computeBenchRecipeRegistries();
}
```

**What happens for a shadow recipe**:
- `recipe.getBenchRequirement()` → `[BenchRequirement(StructuralCrafting, "Stencil", ["Bricks"], 0)]`
- `registries.computeIfAbsent("Stencil", BenchRecipeRegistry::new)` → creates or gets "Stencil" registry
- `benchRecipeRegistry.addRecipe(req, recipe)` → adds recipe ID to `categoryMap["Bricks"]`
- `computeBenchRecipeRegistries()` → calls `recompute()` on all registries

**Key**: The `registries` map is `static` on `CraftingPlugin`. The `"Stencil"` entry is created lazily here via `computeIfAbsent`. The `onRecipeLoad` method uses the recipe object from the event payload, NOT from the asset map. But `addRecipe` stores only the recipe **ID** (String), not the object.

### Step 5: `BenchRecipeRegistry.addRecipe()` (line 44)

```java
public void addRecipe(BenchRequirement benchRequirement, CraftingRecipe recipe) {
    if (benchRequirement.categories != null && benchRequirement.categories.length != 0) {
        for (String category : benchRequirement.categories) {
            this.categoryMap.computeIfAbsent(category, k -> new ObjectOpenHashSet<>()).add(recipe.getId());
        }
    } else {
        this.uncategorizedRecipes.add(recipe.getId());
    }
}
```

Shadow recipe's `sourceReq.categories` is non-null (copied from original recipe, e.g., `["Bricks"]`). So the recipe ID is added to `categoryMap["Bricks"]`.

### Step 6: `BenchRecipeRegistry.recompute()` (via `computeBenchRecipeRegistries()`)

```java
public void recompute() {
    this.allMaterialIds.clear();
    this.allMaterialResourceType.clear();
    this.itemToIncomingRecipe.clear();
    for (Set<String> recipes : this.categoryMap.values()) {
        this.extractMaterialFromRecipes(recipes);
    }
    this.extractMaterialFromRecipes(this.uncategorizedRecipes);
}
```

`extractMaterialFromRecipes` iterates recipe IDs and looks them up from the asset map:
```java
CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);
```

**⚠️ FAILURE POINT #1**: If the shadow recipe ID is NOT in the asset map at this point, the recipe is silently skipped. `allMaterialResourceType` would not contain `"PlaceBlock"`. The `isValidCraftingMaterial()` method (used for inventory highlighting) would not recognize placeholder items.

However, `putAll` ran BEFORE the event dispatch, so the asset map SHOULD have the recipe. This failure would only occur if `putAll` silently rejected the programmatic asset (e.g., due to codec validation failure).

### Step 7: Player opens Stencil bench → `StructuralCraftingWindow` created

```java
public StructuralCraftingWindow(BenchState benchState) {
    super(WindowType.StructuralCrafting, benchState);
    this.inputContainer = new SimpleItemContainer((short)1);
    this.inputContainer.registerChangeEvent(e -> this.updateRecipes());
    this.inputContainer.setSlotFilter(FilterActionType.ADD, (short)0, this::isValidInput);
    this.optionsContainer = new SimpleItemContainer((short)64);
    this.optionsContainer.setGlobalFilter(FilterType.DENY_ALL);
    // ...
}
```

`onOpen0()` calls `CraftingPlugin.getBenchRecipes(this.bench)` for inventory hints.

### Step 8: Player tries to place placeholder → `isValidInput()` (line 63)

```java
private boolean isValidInput(FilterActionType type, ItemContainer container, short slot, ItemStack itemStack) {
    if (type != FilterActionType.ADD) return true;
    ObjectList<CraftingRecipe> matchingRecipes = this.getMatchingRecipes(itemStack);
    return matchingRecipes != null && !matchingRecipes.isEmpty();
}
```

Delegates to `getMatchingRecipes()`.

### Step 9: `getMatchingRecipes()` (line 313)

```java
private ObjectList<CraftingRecipe> getMatchingRecipes(ItemStack inputStack) {
    if (inputStack == null) return null;

    List<CraftingRecipe> recipes = CraftingPlugin.getBenchRecipes(this.bench.getType(), this.bench.getId());
    if (recipes.isEmpty()) return null;  // ← ⚠️ FAILURE POINT #2

    ObjectList<CraftingRecipe> matchingRecipes = new ObjectArrayList<>();
    for (CraftingRecipe recipe : recipes) {
        List<MaterialQuantity> inputMaterials = CraftingManager.getInputMaterials(recipe);
        if (inputMaterials.size() == 1 && CraftingManager.matches(inputMaterials.getFirst(), inputStack)) {
            matchingRecipes.add(recipe);
        }
    }
    return matchingRecipes.isEmpty() ? null : matchingRecipes;
}
```

### Step 10: `CraftingPlugin.getBenchRecipes()` (line 221)

```java
public static List<CraftingRecipe> getBenchRecipes(BenchType benchType, String benchId, String category) {
    BenchRecipeRegistry registry = registries.get(benchId);  // "Stencil"
    if (registry == null) return List.of();  // ← ⚠️ FAILURE POINT #3

    List<CraftingRecipe> list = new ObjectArrayList<>();
    for (CraftingRecipe recipe : registry.getAllRecipes()) {
        BenchRequirement[] benchRequirement = recipe.getBenchRequirement();
        if (benchRequirement != null) {
            for (BenchRequirement requirement : benchRequirement) {
                if (requirement.type == benchType && requirement.id.equals(benchId)
                    && (category == null || hasCategory(recipe, category))) {
                    list.add(recipe);
                    break;
                }
            }
        }
    }
    return list;
}
```

Called from `getMatchingRecipes()` with `category = null` (2-arg overload), so no category filtering.

### Step 11: `BenchRecipeRegistry.getAllRecipes()` (line 56)

```java
public CraftingRecipe[] getAllRecipes() {
    Set<String> allRecipeIds = new ObjectOpenHashSet<>(this.uncategorizedRecipes);
    for (Set<String> recipes : this.categoryMap.values()) {
        allRecipeIds.addAll(recipes);
    }
    List<CraftingRecipe> allRecipes = new ObjectArrayList<>(allRecipeIds.size());
    for (String recipeId : allRecipeIds) {
        CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);  // ← ⚠️ FAILURE POINT #1 again
        if (recipe != null) {
            allRecipes.add(recipe);
        }
    }
    return allRecipes.toArray(CraftingRecipe[]::new);
}
```

**⚠️ FAILURE POINT #1 (repeated)**: The recipe ID was stored in `categoryMap` by `addRecipe()`, but the actual CraftingRecipe object is looked up from the asset map here. If `getAsset(recipeId)` returns null, the recipe is silently skipped and the returned array is empty.

### Step 12: `CraftingManager.matches()` (line 571)

```java
public static boolean matches(MaterialQuantity craftingMaterial, ItemStack itemStack) {
    String itemId = craftingMaterial.getItemId();       // null for shadow recipes
    if (itemId != null) {
        return itemId.equals(itemStack.getItemId());
    } else {
        String resourceTypeId = craftingMaterial.getResourceTypeId();  // "PlaceBlock"
        if (resourceTypeId != null && itemStack.getItem().getResourceTypes() != null) {
            for (ItemResourceType itemResourceType : itemStack.getItem().getResourceTypes()) {
                if (resourceTypeId.equals(itemResourceType.id)) {  // "PlaceBlock" == "PlaceBlock"
                    return true;
                }
            }
        }
        return false;
    }
}
```

**Confirmed**: `ItemResourceType.id` is populated from JSON `"Id"` via codec:
```java
new KeyedCodec<>("Id", Codec.STRING), (irt, s) -> irt.id = s, irt -> irt.id
```

So `"PlaceBlock".equals("PlaceBlock")` → `true`. The match succeeds **if** the recipe reaches this point.

### Step 13: `updateRecipes()` (line 263)

Called via `inputContainer.changeEvent` after item is accepted:

```java
private void updateRecipes() {
    this.optionsContainer.clear();
    this.optionSlotToRecipeMap.clear();
    ItemStack inputStack = this.inputContainer.getItemStack((short)0);
    ObjectList<CraftingRecipe> matchingRecipes = this.getMatchingRecipes(inputStack);
    if (matchingRecipes != null) {
        // Sort recipes...
        for (CraftingRecipe match : matchingRecipes) {
            for (BenchRequirement requirement : match.getBenchRequirement()) {
                if (requirement.type == this.bench.getType() && requirement.id.equals(this.bench.getId())) {
                    List<ItemStack> output = CraftingManager.getOutputItemStacks(match);
                    this.optionsContainer.setItemStackForSlot(index, output.getFirst(), false);
                    this.optionSlotToRecipeMap.put(index, match.getId());
                    index++;
                }
            }
        }
    }
}
```

---

## 2. Identified Failure Points

### ⚠️ FAILURE POINT #1: `getAllRecipes()` asset map lookup returns null

**Location**: `BenchRecipeRegistry.getAllRecipes()` line 62 and `extractMaterialFromRecipes()` line 87

**Mechanism**: `addRecipe()` stores the recipe **ID** (String) in the `categoryMap`. Later, `getAllRecipes()` looks up each ID from `CraftingRecipe.getAssetMap().getAsset(recipeId)`. If the asset map doesn't contain the shadow recipe, it returns null and the recipe is silently dropped.

**Why it could happen**: The `assetMap.putAll()` call in `loadAssets0()` runs before the event dispatch, but `putAll` could silently reject assets that fail codec validation or lack required metadata. Programmatic assets don't go through `decodeAssets`/`afterDecode`, so they might be handled differently by the asset map internals.

**How to verify**:
```java
// Add after loadAssets() in StencilBookRecipeMutator.mutate()
for (CraftingRecipe shadow : shadowRecipes) {
    CraftingRecipe found = CraftingRecipe.getAssetMap().getAsset(shadow.getId());
    log("Asset map lookup for '" + shadow.getId() + "': " + (found != null ? "FOUND" : "NOT FOUND"));
}
```

**Likelihood**: **HIGH** — This is the most likely failure point. The divergence between "recipe ID registered in BenchRecipeRegistry" and "recipe object actually in asset map" is exactly the pattern that produces "accepted but no recipes" behavior.

### ⚠️ FAILURE POINT #2: `getBenchRecipes()` returns empty list

**Location**: `CraftingPlugin.getBenchRecipes()` line 222

**Mechanism**: `registries.get("Stencil")` returns null because `onRecipeLoad()` was never called with the shadow recipes.

**Why it could happen**:
- `LoadedAssetsEvent` dispatch was skipped (e.g., `loadedAssets` was empty by the time `loadAssets0` checked)
- The event handler registration `register(LoadedAssetsEvent.class, CraftingRecipe.class, ...)` doesn't match the dispatched event's type parameter
- `CraftingPlugin.setup()` hasn't run yet when `mutate()` is called (unlikely — `LoadAssetEvent` fires after `pluginManager.setup()`)

**How to verify**:
```java
// Add after loadAssets() in StencilBookRecipeMutator.mutate()
// Use reflection to access CraftingPlugin.registries
Field regField = CraftingPlugin.class.getDeclaredField("registries");
regField.setAccessible(true);
Map<String, ?> regs = (Map<String, ?>) regField.get(null);
log("'Stencil' registry exists: " + regs.containsKey("Stencil"));
if (regs.containsKey("Stencil")) {
    log("Stencil registry: " + regs.get("Stencil"));
}
```

**Likelihood**: **MEDIUM** — If `loadAssets()` completes without exception, the event should fire. But this is worth confirming.

### ⚠️ FAILURE POINT #3: Contradictory observation — acceptance vs. empty grid

**The user claims**: "The placeholder IS accepted into the bench input slot. But no recipes appear in the grid."

**The code says**: Both `isValidInput()` and `updateRecipes()` call `getMatchingRecipes()`. If the first returns non-empty (allowing acceptance), the second should too (populating the grid). They query the same registry, same asset map, same data. There is no caching, no state mutation between the two calls.

**If the placeholder truly IS accepted but the grid is empty, there are only three explanations**:

1. **The item was placed via a mechanism that bypasses `isValidInput()`** — e.g., a command, direct inventory manipulation, or admin override. In that case, the slot filter was never checked, and `getMatchingRecipes()` was only called once (in `updateRecipes()`), where it returned empty.

2. **An exception in `updateRecipes()` silently kills execution** — If `CraftingManager.getOutputItemStacks(match)` returns empty and `output.getFirst()` throws `NoSuchElementException`, the entire `updateRecipes()` method would abort. The input slot would show the item (it was accepted before the crash), but the options grid would remain empty. Check server logs for exceptions.

3. **The observation is incorrect** — The item might be bouncing back to the cursor too fast for the user to notice, or the user is testing with a different placeholder variant that doesn't have `ResourceTypes`.

**How to verify**: Check if the item remains in the input slot after the interaction, or if it bounces back to the cursor. Check server logs for `NoSuchElementException` or similar stack traces during bench interaction.

---

## 3. Timing Analysis

### Event Timeline

```
1. pluginManager.setup()
   └── CraftingPlugin.setup()
       └── Registers: LoadedAssetsEvent<CraftingRecipe> → onRecipeLoad()
       └── Registers: LoadedAssetsEvent<Item> → onItemAssetLoad()

2. Asset stores load all JSON assets
   └── LoadedAssetsEvent<CraftingRecipe> fires (initial=true)
       └── onRecipeLoad() builds registries: "Builders", "Carpenter", "Fieldcraft", etc.
       └── NO "Stencil" registry (no recipe has Stencil requirement yet)

3. LoadedAssetsEvent<Item> fires
   └── onItemAssetLoad() generates inline recipes → more LoadedAssetsEvent<CraftingRecipe>

4. ★ LoadAssetEvent fires (all assets loaded)
   └── onAssetsLoaded()
       └── DropScaler.apply()
       └── StencilBookRecipeMutator.mutate()
           ├── Creates shadow CraftingRecipe objects via copy constructor + reflection
           ├── Calls CraftingRecipe.getAssetStore().loadAssets("Hytale:Hytale", shadowRecipes)
           │   ├── loadAllChildren() puts shadows into loadedAssets map
           │   ├── loadAssets0()
           │   │   ├── decodeAssets() — no-op (no raw assets)
           │   │   ├── assetMap.putAll() — adds shadows to CraftingRecipe asset map
           │   │   └── dispatch LoadedAssetsEvent<CraftingRecipe> (synchronous)
           │   │       └── CraftingPlugin.onRecipeLoad()
           │   │           ├── registries.computeIfAbsent("Stencil", ...) → creates registry
           │   │           ├── addRecipe() for each shadow → adds to categoryMap
           │   │           └── computeBenchRecipeRegistries() → recompute()
           │   │               └── extractMaterialFromRecipes()
           │   │                   └── CraftingRecipe.getAssetMap().getAsset(shadowId) ← ⚠️ FP #1
           │   │                   └── adds "PlaceBlock" to allMaterialResourceType
           │   └── Returns AssetLoadResult
           └── Logs "Registered N shadow Stencil recipes."

5. Server ready, players can join

6. Player opens Stencil bench
   └── StructuralCraftingWindow created
       └── onOpen0() → getBenchRecipes("Stencil") → generates inventory hints
       └── Player places placeholder → isValidInput() → getMatchingRecipes()
           └── getBenchRecipes(StructuralCrafting, "Stencil") ← ⚠️ FP #2
               └── registries.get("Stencil") → BenchRecipeRegistry
                   └── getAllRecipes()
                       └── CraftingRecipe.getAssetMap().getAsset(shadowId) ← ⚠️ FP #1 again
```

### Is `loadAssets()` synchronous?

**Yes.** `AssetStore.loadAssets()` is a blocking call. It acquires the write lock, processes assets, dispatches events, and returns. When `StencilBookRecipeMutator.mutate()` returns, all shadow recipes should be in the asset map and the "Stencil" registry should exist.

### Could `onRecipeLoad()` have already run?

The handler is registered for `LoadedAssetsEvent<CraftingRecipe>`. Each call to `loadAssets()` fires a new event. The handler runs for EVERY `LoadedAssetsEvent<CraftingRecipe>`, including the one fired by the mutator's `loadAssets()` call. The initial recipe load event has already completed before `LoadAssetEvent` fires, so there's no conflict.

---

## 4. The `CraftingRecipe` Copy Constructor Gap

```java
public CraftingRecipe(CraftingRecipe other) {
    this.input = other.input;           // ← overridden by reflection
    this.primaryOutput = other.primaryOutput;
    this.outputs = other.outputs;
    this.primaryOutputQuantity = other.primaryOutputQuantity;
    this.benchRequirement = other.benchRequirement; // ← overridden by reflection
    this.timeSeconds = other.timeSeconds;
    this.knowledgeRequired = other.knowledgeRequired;
    this.requiredMemoriesLevel = other.requiredMemoriesLevel;
}
```

**NOT copied**: `id` (set via reflection), `data` (AssetExtraInfo.Data — remains null).

The `data` field being null is notable. `AssetExtraInfo.Data` contains metadata used by the asset system (pack key, file path, parent key, etc.). For programmatically created assets, this is null. Whether `assetMap.putAll()` handles null `data` correctly is **not confirmed from the decompiled code** — this is a potential cause of FAILURE POINT #1.

---

## 5. Diagnostic Steps — Priority Order

### Step A: Verify shadow recipes are in the asset map (FAILURE POINT #1)

Add to `StencilBookRecipeMutator.mutate()`, after `loadAssets()`:

```java
int found = 0, missing = 0;
for (CraftingRecipe shadow : shadowRecipes) {
    CraftingRecipe lookup = CraftingRecipe.getAssetMap().getAsset(shadow.getId());
    if (lookup != null) {
        found++;
    } else {
        missing++;
        log("MISSING from asset map: " + shadow.getId());
    }
}
log("Asset map verification: " + found + " found, " + missing + " missing");
```

**If missing > 0**: The `assetMap.putAll()` is silently rejecting shadow recipes. Root cause is likely the null `data` field or a codec validation issue. Fix: investigate what `putAll` requires, or try loading via `loadAssetsWithReferences()`.

### Step B: Verify "Stencil" registry exists (FAILURE POINT #2)

```java
try {
    Field regField = CraftingPlugin.class.getDeclaredField("registries");
    regField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, BenchRecipeRegistry> regs = (Map<String, BenchRecipeRegistry>) regField.get(null);
    BenchRecipeRegistry stencilReg = regs.get("Stencil");
    if (stencilReg != null) {
        log("Stencil registry exists: " + stencilReg);
    } else {
        log("ERROR: No 'Stencil' registry in CraftingPlugin.registries!");
        log("Available registries: " + regs.keySet());
    }
} catch (Exception e) {
    log("Could not inspect registries: " + e.getMessage());
}
```

### Step C: Verify `getBenchRecipes()` returns results

Add temporary logging at bench open time (e.g., in `PlaceBlockBenchInterceptor` or via event hook):

```java
List<CraftingRecipe> recipes = CraftingPlugin.getBenchRecipes(BenchType.StructuralCrafting, "Stencil");
log("getBenchRecipes('Stencil'): " + recipes.size() + " recipes");
for (CraftingRecipe r : recipes) {
    log("  Recipe: " + r.getId() + " | inputs: " + Arrays.toString(r.getInput()));
}
```

### Step D: Check for exceptions during `updateRecipes()`

Search server logs for `NoSuchElementException`, `NullPointerException`, or any exception stack trace involving `StructuralCraftingWindow`, `updateRecipes`, or `getOutputItemStacks`.

---

## 6. The `isValidInput` vs Grid Contradiction

The decompiled code is unambiguous:

```java
// isValidInput (slot filter — gate for item placement)
private boolean isValidInput(..., ItemStack itemStack) {
    ObjectList<CraftingRecipe> matchingRecipes = this.getMatchingRecipes(itemStack);
    return matchingRecipes != null && !matchingRecipes.isEmpty();
}

// updateRecipes (change handler — populates grid)
private void updateRecipes() {
    ItemStack inputStack = this.inputContainer.getItemStack((short)0);
    ObjectList<CraftingRecipe> matchingRecipes = this.getMatchingRecipes(inputStack);
    if (matchingRecipes != null) { /* populate options */ }
}
```

Both call `getMatchingRecipes()` which calls `CraftingPlugin.getBenchRecipes()` → `BenchRecipeRegistry.getAllRecipes()` → `CraftingRecipe.getAssetMap().getAsset()`. No caching. No thread-local state. Same data path.

**If the placeholder is truly accepted**, then `getMatchingRecipes()` returned non-empty at filter check time. It must also return non-empty at `updateRecipes()` time (same call stack, same data). The only way to get "accepted + empty grid" is:

1. An exception in `updateRecipes()` AFTER the match check but BEFORE populating options
2. The item was injected into the slot without going through `isValidInput()`
3. The observation is incorrect (item is actually rejected)

---

## 7. `getOutputItemStacks()` — Potential Exception Source

```java
public static List<ItemStack> getOutputItemStacks(CraftingRecipe recipe, int quantity) {
    MaterialQuantity[] output = recipe.getOutputs();
    if (output == null) return List.of();  // ← empty list

    ObjectList<ItemStack> outputItemStacks = new ObjectArrayList<>();
    for (MaterialQuantity outputMaterial : output) {
        ItemStack outputItemStack = getOutputItemStack(outputMaterial, quantity);
        if (outputItemStack != null) {
            outputItemStacks.add(outputItemStack);
        }
    }
    return outputItemStacks;  // ← could be empty if all outputs have null itemId
}
```

In `updateRecipes()`:
```java
List<ItemStack> output = CraftingManager.getOutputItemStacks(match);
this.optionsContainer.setItemStackForSlot(index, output.getFirst(), false);
```

If `output` is empty (e.g., recipe has `outputs = null` or all output items have null `itemId`), then `output.getFirst()` throws **`NoSuchElementException`**. This would crash `updateRecipes()` silently, leaving the options grid empty while the item remains in the input slot.

**The copy constructor copies `outputs` from the original**. If the original recipe's `outputs` was properly set (via `processConfig()` after JSON decoding), the shadow should have valid outputs. But `processConfig()` is NOT called for programmatic assets — it's an `afterDecode` hook. If the original recipe relied on `processConfig()` to set `outputs` from `primaryOutput`, and the shadow recipe's `outputs` was already set in the original, this is fine.

**However**: If any original recipe has `outputs = EMPTY_OUTPUT` (the static empty array, length 0) and `primaryOutput != null`, `processConfig()` would have set `outputs = [primaryOutput]` on the original. The copy constructor copies the reference to the PROCESSED array. So this should be safe.

**Bottom line**: Check server logs for `NoSuchElementException` originating from `updateRecipes()`.

---

## 8. Secondary Issue: Category Mismatch (Not a Blocker)

Shadow recipes inherit categories from `sourceReq.categories`. For example, a stone bricks recipe with `categories: ["Bricks"]` creates a shadow with `BenchRequirement(StructuralCrafting, "Stencil", ["Bricks"], 0)`.

The Stencil bench's `Categories` list includes `"Bricks"`, so the sorting logic in `getSortingPriority()` works correctly. But categories like `"Furniture_Tables"` from non-structural recipes would get `Integer.MAX_VALUE` priority (pushed to end).

**This doesn't affect recipe DISPLAY** — all recipes from `getAllRecipes()` are returned regardless of category. It only affects sort order within the 64-slot grid.

---

## 9. Conclusion

The most likely root cause is **FAILURE POINT #1**: shadow recipe IDs are registered in the `BenchRecipeRegistry.categoryMap` but the actual `CraftingRecipe` objects are not retrievable from `CraftingRecipe.getAssetMap()`. This would produce the exact symptom described — the registry thinks it has recipes, but `getAllRecipes()` returns empty because every `getAsset(id)` returns null.

Run **Diagnostic Step A** first. If shadow recipes are missing from the asset map, the fix involves understanding why `assetMap.putAll()` rejects programmatically constructed `CraftingRecipe` objects (likely related to the null `data` field from the copy constructor).
