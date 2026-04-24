---
topic: "Runtime CraftingRecipe & MaterialQuantity Construction"
category: "Crafting"
updated: 2026-04-23
sources: ["decompiled CraftingRecipe.java (server)", "decompiled MaterialQuantity.java (server)", "decompiled CraftingPlugin.java", "decompiled Item.java processConfig()", "decompiled AssetStore.java"]
---

# Runtime CraftingRecipe & MaterialQuantity Construction

## Summary

CraftingRecipe and MaterialQuantity can both be constructed directly without reflection. The engine already does this internally (see `Item.processConfig()` generating recipes from item definitions). New recipes are registered via `AssetStore.loadAssets(packKey, List<T>)` and automatically appear in `CraftingPlugin.getBenchRecipes()` through the `LoadedAssetsEvent` handler.

---

## 1. CraftingRecipe (server-side)

**Package**: `com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe`

**Implements**: `JsonAssetWithMap<String, DefaultAssetMap<String, CraftingRecipe>>`
- → extends `JsonAsset<String>` (single method: `getId()`)
- **No abstract parent class** — it directly implements the interface.

### Constructors

| Constructor | Visibility | Notes |
|---|---|---|
| `CraftingRecipe()` | `protected` | No-arg. Used by the CODEC for deserialization. Fields default to sensible values. |
| `CraftingRecipe(CraftingRecipe other)` | `public` | **Copy constructor** — shallow copies all fields from another recipe. |
| `CraftingRecipe(MaterialQuantity[] input, MaterialQuantity primaryOutput, MaterialQuantity[] outputs, int outputQuantity, BenchRequirement[] benchRequirement, float timeSeconds, boolean knowledgeRequired, int requiredMemoriesLevel)` | `public` | **Full constructor** — sets all fields directly. |

### All Fields

| Field | Type | Default | Access | Notes |
|---|---|---|---|---|
| `id` | `String` | `null` | `protected` | The recipe key. Set manually or by CODEC via `recipe.id = blockTypeKey`. Has `getId()` getter. |
| `input` | `MaterialQuantity[]` | `null` | `protected` | Recipe inputs. Has `getInput()` getter. |
| `outputs` | `MaterialQuantity[]` | `EMPTY_OUTPUT` (empty array) | `protected` | All outputs. Has `getOutputs()` getter. |
| `primaryOutput` | `MaterialQuantity` | `null` | `protected` | The primary output shown in UI. Has `getPrimaryOutput()` getter. |
| `primaryOutputQuantity` | `int` | `1` | `protected` | Multiplier for the primary output. No public getter — used internally and in the full constructor as `outputQuantity`. |
| `benchRequirement` | `BenchRequirement[]` | `null` | `protected` | Which bench(es) can craft this. Has `getBenchRequirement()` getter. |
| `timeSeconds` | `float` | `0.0f` | `protected` | Processing time. Has `getTimeSeconds()` getter. |
| `knowledgeRequired` | `boolean` | `false` | `protected` | Whether player must learn the recipe first. Has `isKnowledgeRequired()` getter. |
| `requiredMemoriesLevel` | `int` | `1` | `protected` | Minimum memories level (1 = always available). Has `getRequiredMemoriesLevel()` getter. |
| `data` | `AssetExtraInfo.Data` | `null` | `private` | Internal codec metadata. Safe to leave null for programmatic recipes. |

### How `id` is Set

The `id` field lives directly on `CraftingRecipe` (not a parent class). In the CODEC, it's set via the lambda `(recipe, blockTypeKey) -> recipe.id = blockTypeKey`. For programmatic construction, you set it directly:

```java
recipe.id = "MyPlugin_Shadow_OriginalRecipeId";
```

Since `id` is `protected`, from outside the package you must use reflection or extend the class. From subclasses or same-package code, direct field access works.

### Copy Pattern (no `clone()` or builder)

There is **no** `copy()`, `clone()`, or builder method. But the **copy constructor** exists:

```java
CraftingRecipe shadow = new CraftingRecipe(originalRecipe);
shadow.id = "shadow_" + originalRecipe.getId();  // must set new id
shadow.input = newInputs;  // replace inputs
```

This is exactly how `Item.processConfig()` creates generated recipes:
```java
CraftingRecipe newRecipe = new CraftingRecipe(recipe);  // copy constructor
newRecipe.primaryOutput = primaryOutput;
newRecipe.id = CraftingRecipe.generateIdFromItemRecipe(this, 0);
```

### Can We Use Reflection for No-Arg?

Yes, since the no-arg constructor is `protected`:
```java
CraftingRecipe.class.getDeclaredConstructor().newInstance()
```
But the **full constructor** and **copy constructor** are both `public`, so reflection is unnecessary.

---

## 2. MaterialQuantity (server-side)

**Package**: `com.hypixel.hytale.server.core.inventory.MaterialQuantity`

**Implements**: `NetworkSerializable<com.hypixel.hytale.protocol.MaterialQuantity>`

### Constructors

| Constructor | Visibility | Notes |
|---|---|---|
| `MaterialQuantity()` | `protected` | No-arg. Used by CODEC. |
| `MaterialQuantity(String itemId, String resourceTypeId, String tag, int quantity, BsonDocument metadata)` | `public` | **Full constructor.** Validates that at least one of `itemId`/`resourceTypeId`/`tag` is non-null, and `quantity > 0`. |

### All Fields

| Field | Type | Default | Access | Notes |
|---|---|---|---|---|
| `itemId` | `String` | `null` | `protected` | Specific item ID (e.g., `"Block_Oak_Log"`). Nullable. |
| `resourceTypeId` | `String` | `null` | `protected` | Resource type group (e.g., `"Wood_Hardwood"`). Nullable. |
| `tag` | `String` | `null` | `protected` | Item tag for matching. Nullable. |
| `tagIndex` | `int` | `Integer.MIN_VALUE` | `protected` | Resolved tag index (set by CODEC afterDecode). Not in constructor. |
| `quantity` | `int` | `1` | `protected` | Amount required/produced. Must be > 0. |
| `metadata` | `BsonDocument` | `null` | `protected` | Additional BSON metadata. Nullable. |

### Construction Examples

**By specific item:**
```java
new MaterialQuantity("Block_Oak_Log", null, null, 2, null)
```

**By resource type:**
```java
new MaterialQuantity(null, "Wood_Hardwood", null, 1, null)
```

**By tag:**
```java
new MaterialQuantity(null, null, "SomeTag", 1, null)
```

### `clone(int quantity)` Method
Returns a new MaterialQuantity with same itemId/resourceTypeId/tag/metadata but different quantity:
```java
MaterialQuantity doubled = original.clone(original.getQuantity() * 2);
```

### Important: `tagIndex` Resolution
When creating MaterialQuantity with a `tag`, the `tagIndex` field is NOT set by the constructor. The CODEC's `afterDecode` does:
```java
if (materialQuantity.tag != null) {
    materialQuantity.tagIndex = AssetRegistry.getOrCreateTagIndex(materialQuantity.tag);
}
```
If you construct a MaterialQuantity with a tag programmatically, you may need to set `tagIndex` via reflection or call `AssetRegistry.getOrCreateTagIndex()` yourself if tag-based matching is needed.

---

## 3. BenchRequirement (protocol-side, also used server-side)

**Package**: `com.hypixel.hytale.protocol.BenchRequirement`

### Constructors

| Constructor | Visibility | Notes |
|---|---|---|
| `BenchRequirement()` | `public` | No-arg. `type` defaults to `BenchType.Crafting`. |
| `BenchRequirement(BenchType type, String id, String[] categories, int requiredTierLevel)` | `public` | Full constructor. |
| `BenchRequirement(BenchRequirement other)` | `public` | Copy constructor. |

### All Fields (all `public`)

| Field | Type | Default |
|---|---|---|
| `type` | `BenchType` | `BenchType.Crafting` |
| `id` | `String` | `null` |
| `categories` | `String[]` | `null` |
| `requiredTierLevel` | `int` | `0` |

### Construction Example
```java
BenchRequirement req = new BenchRequirement();
req.type = BenchType.Processing;
req.id = "Anvil";
req.categories = new String[]{"Smelting"};
req.requiredTierLevel = 1;
```

Or via full constructor:
```java
new BenchRequirement(BenchType.Processing, "Anvil", new String[]{"Smelting"}, 1)
```

---

## 4. Asset Registration

### `AssetStore.loadAssets(String packKey, List<T> assets)`

**This works for brand-new recipes**, not just re-loading. Evidence: `CraftingPlugin.onItemAssetLoad()` constructs `CraftingRecipe` objects in memory and calls:

```java
CraftingRecipe.getAssetStore().loadAssets("Hytale:Hytale", recipesToLoad);
```

These are entirely new recipe objects that never existed as JSON files. The method:
1. Takes the list of asset objects
2. Extracts their keys via `keyFunction` (i.e., `recipe.id`)
3. Inserts them into the `DefaultAssetMap` internal `assetMap`
4. Fires `LoadedAssetsEvent`
5. The `CraftingPlugin.onRecipeLoad()` handler picks them up and adds them to `BenchRecipeRegistry`

### Registration Flow

```
loadAssets(packKey, List<CraftingRecipe>)
  → AssetStore.loadAssets0()
    → DefaultAssetMap.putAll(packKey, codec, loadedAssets, pathMap, childrenMap)
      → assetMap.put(key, asset)   // stores in the map
    → fires LoadedAssetsEvent
      → CraftingPlugin.onRecipeLoad()
        → BenchRecipeRegistry.addRecipe(requirement, recipe)
        → computeBenchRecipeRegistries()
```

After this, `CraftingPlugin.getBenchRecipes(bench)` will include the new recipe.

### Removing Recipes

```java
CraftingRecipe.getAssetStore().removeAssets(List.of("recipe_id_1", "recipe_id_2"));
```
This fires `RemovedAssetsEvent`, and `CraftingPlugin.onRecipeRemove()` removes them from registries.

### Direct Map Access

`CraftingRecipe.getAssetMap()` returns `DefaultAssetMap<String, CraftingRecipe>`. The `getAssetMap().getAssetMap()` returns an **unmodifiable** view. You should NOT try to put directly — use `loadAssets()` instead, which handles events and locking correctly.

---

## 5. Complete Recipe Construction Pattern

```java
// 1. Create inputs
MaterialQuantity[] inputs = new MaterialQuantity[]{
    new MaterialQuantity(null, "Wood_Hardwood", null, 2, null),  // 2x any hardwood
    new MaterialQuantity("Block_Stone", null, null, 1, null)     // 1x stone
};

// 2. Create output
MaterialQuantity primaryOutput = new MaterialQuantity("Tool_Axe_Stone", null, null, 1, null);

// 3. Create bench requirement
BenchRequirement benchReq = new BenchRequirement(
    BenchType.Crafting, "Workbench", new String[]{"Tools"}, 1
);

// 4. Construct recipe (via full constructor)
CraftingRecipe recipe = new CraftingRecipe(
    inputs,                           // input
    primaryOutput,                    // primaryOutput
    new MaterialQuantity[]{primaryOutput}, // outputs
    1,                                // outputQuantity
    new BenchRequirement[]{benchReq}, // benchRequirement
    0.0f,                             // timeSeconds
    false,                            // knowledgeRequired
    1                                 // requiredMemoriesLevel
);

// 5. Set the ID (protected field — use reflection if outside the package)
Field idField = CraftingRecipe.class.getDeclaredField("id");
idField.setAccessible(true);
idField.set(recipe, "MyPlugin_Custom_StoneAxe");

// 6. Register
CraftingRecipe.getAssetStore().loadAssets("MyPlugin:MyPack", List.of(recipe));
```

### Alternative: Copy Constructor Pattern (for shadow recipes)

```java
CraftingRecipe original = CraftingRecipe.getAssetMap().getAsset("Original_Recipe_Id");

// Copy everything
CraftingRecipe shadow = new CraftingRecipe(original);

// Override inputs only
shadow.input = new MaterialQuantity[]{
    new MaterialQuantity(null, "Wood_Softwood", null, 3, null)
};

// Set new ID (required — must be unique)
Field idField = CraftingRecipe.class.getDeclaredField("id");
idField.setAccessible(true);
idField.set(shadow, "Shadow_Original_Recipe_Id");

// Register
CraftingRecipe.getAssetStore().loadAssets("MyPlugin:MyPack", List.of(shadow));
```

---

## 6. Gotchas

1. **`id` is protected** — From outside the `com.hypixel.hytale.server.core.asset.type.item.config` package, you need reflection to set `recipe.id`. Same for `input`, `outputs`, `primaryOutput`, `benchRequirement`, etc.

2. **`input` is also protected** — ALL fields on CraftingRecipe are `protected`. The copy constructor and full constructor are public, but mutating individual fields after construction requires reflection or same-package access.

3. **Unique IDs required** — If you `loadAssets` with an ID that already exists, it will **replace** the existing recipe. This is by design (asset overriding).

4. **`processConfig()` is called by CODEC afterDecode** — When constructing manually, the recipe does NOT go through `processConfig()`. That method just ensures `outputs` is populated from `primaryOutput` if empty. If you set both `primaryOutput` and `outputs`, this doesn't matter.

5. **Protocol vs Server classes** — There are TWO CraftingRecipe classes and TWO MaterialQuantity classes. The `protocol` versions are for network serialization. The `server.core` versions are what you construct and register. The server versions have `toPacket()` methods.

6. **`loadAssets` fires events synchronously** — The `LoadedAssetsEvent` handler in `CraftingPlugin` runs immediately, adding recipes to bench registries. No deferred work needed.

7. **Tag-based MaterialQuantity needs tagIndex** — If using `tag` field in MaterialQuantity, manually resolve: `tagIndex = AssetRegistry.getOrCreateTagIndex(tag)` (protected field, needs reflection).

8. **`outputs` vs `primaryOutput`** — If `outputs` is null/empty and `primaryOutput` is set, `processConfig()` creates `outputs = [primaryOutput]`. Since `processConfig()` is NOT called for manually constructed recipes, explicitly set BOTH.
