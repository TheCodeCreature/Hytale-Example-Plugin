---
topic: "ItemCategory Lookup at Runtime"
category: "Items / Categories"
updated: 2026-05-02
sources:
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/ItemCategory.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/Item.java"
  - ".tmp_hytale_src/com/hypixel/hytale/protocol/ItemCategory.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/ItemCategoryPacketGenerator.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/blocktype/config/bench/CraftingBench.java"
---

# ItemCategory Lookup at Runtime

## Summary

`ItemCategory` is a full asset type in the Hytale engine with its own `AssetStore` and `AssetMap`. Items reference categories via a `String[] categories` field on the `Item` class. The `Item.categories` field has a **public getter** (`getCategories()`), so no reflection is needed for categories (unlike `Item.set` which has no getter). The `ItemCategory` asset store can be queried directly.

## 1. ItemCategory Asset Class

**Package**: `com.hypixel.hytale.server.core.asset.type.item.config.ItemCategory`

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | `String` | Category ID (e.g. `"Wood"`, `"Rock"`, `"Stairs"`) |
| `name` | `String` | Localization key (e.g. `"server.itemCategories.wood"`) |
| `icon` | `String` | Icon path (e.g. `"Icons/ItemCategories/Wood.png"`) |
| `order` | `int` | Sort order for UI display |
| `infoDisplayMode` | `ItemGridInfoDisplayMode` | How items are shown in grids (default: `Tooltip`) |
| `children` | `ItemCategory[]` | Sub-categories (nullable, sorted by `order` after decode) |

### Key API Methods

```java
// Static — get the asset store (lazily initialized)
public static AssetStore<String, ItemCategory, DefaultAssetMap<String, ItemCategory>> getAssetStore()

// Static — get the asset map (String key → ItemCategory)
public static DefaultAssetMap<String, ItemCategory> getAssetMap()

// Instance getters
public String getId()
public String getName()
public String getIcon()
public int getOrder()
public ItemGridInfoDisplayMode getInfoDisplayMode()
public ItemCategory[] getChildren()
```

### JSON Format

```json
{
  "Id": "Wood",
  "Name": "server.itemCategories.wood",
  "Icon": "Icons/ItemCategories/Wood.png",
  "Order": 1,
  "InfoDisplayMode": "Tooltip",
  "Children": [
    {
      "Id": "WoodPlanks",
      "Name": "server.itemCategories.woodPlanks",
      "Icon": "Icons/ItemCategories/WoodPlanks.png",
      "Order": 1
    }
  ]
}
```

Icons are validated against `CommonAssetValidator.ICON_ITEM_CATEGORIES` — must be `.png` files under `Icons/ItemCategories/`.

## 2. How Items Reference Categories

### Item.categories Field

On `Item.java`:

```java
protected String[] categories;   // Deserialized from "Categories" JSON key

// PUBLIC getter (no reflection needed!)
public String[] getCategories() {
    return this.categories;
}
```

The CODEC definition:
```java
.<String[]>appendInherited(
    new KeyedCodec<>("Categories",
        new ArrayCodec<>(Codec.STRING, String[]::new)
            .metadata(new UIEditor(new UIEditor.Dropdown("ItemCategories")))),
    (item, s) -> item.categories = s,
    item -> item.categories,
    (item, parent) -> item.categories = parent.categories
)
.addValidatorLate(() -> ItemCategory.VALIDATOR_CACHE.getArrayValidator().late())
.documentation("A list of categories this item will be shown in on the creative library menu.")
```

**Key facts**:
- `categories` is a `String[]` of ItemCategory IDs (e.g. `["Wood", "WoodPlanks"]`)
- It is **inheritable** — child items inherit parent categories if not overridden
- The values are validated against the ItemCategory asset store at load time
- It may be **null** if no categories are specified in the JSON
- The documentation says: *"A list of categories this item will be shown in on the creative library menu."*

### Item.set Field (contrast)

```java
protected String set;   // Deserialized from "Set" JSON key
// NO public getter — must use reflection
```

The plugin already uses reflection for `Item.set` in `RecipeFilterRegistry.extractItemSet()`.

## 3. Looking Up ItemCategory at Runtime from a Plugin

### Direct Lookup (no reflection needed)

```java
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemCategory;

// Get an Item by ID
Item item = Item.getAssetMap().getAsset("Wood_Hardwood_Planks");

// Get its category IDs (public getter)
String[] categoryIds = item.getCategories();  // e.g. ["Wood", "WoodPlanks"]

// Look up the full ItemCategory asset for each
if (categoryIds != null) {
    for (String catId : categoryIds) {
        ItemCategory category = ItemCategory.getAssetMap().getAsset(catId);
        if (category != null) {
            String name = category.getName();    // localization key
            String icon = category.getIcon();    // icon path
            int order = category.getOrder();
            ItemCategory[] children = category.getChildren();
        }
    }
}
```

### Get All ItemCategories

```java
Map<String, ItemCategory> allCategories = ItemCategory.getAssetMap().getAssetMap();
for (ItemCategory cat : allCategories.values()) {
    System.out.println(cat.getId() + " → " + cat.getName());
}
```

### Build a Reverse Index (Category → Items)

```java
Map<String, List<String>> categoryToItems = new HashMap<>();
for (Item item : Item.getAssetMap().getAssetMap().values()) {
    String[] cats = item.getCategories();
    if (cats != null) {
        for (String catId : cats) {
            categoryToItems.computeIfAbsent(catId, k -> new ArrayList<>()).add(item.getId());
        }
    }
}
```

## 4. How Recipes Relate to ItemCategories

### CraftingRecipe does NOT carry category info

`CraftingRecipe` has:
- `input` (MaterialQuantity[])
- `primaryOutput` (MaterialQuantity)
- `benchRequirement` (BenchRequirement[])

There is **no** category field on CraftingRecipe itself.

### Resolution Path: Recipe → Output Item → Categories

```
CraftingRecipe
  → .getPrimaryOutput().getItemId()
    → Item.getAssetMap().getAsset(outputItemId)
      → item.getCategories()   // String[] of ItemCategory IDs
        → ItemCategory.getAssetMap().getAsset(catId)
```

### CraftingBench.BenchCategory vs ItemCategory

These are **different concepts**:

| Concept | Class | Purpose |
|---------|-------|---------|
| **ItemCategory** | `ItemCategory` asset | Global item classification for the creative library ("Wood", "Rock", "Furniture") |
| **BenchCategory** | `CraftingBench.BenchCategory` inner class | Per-bench tab grouping ("Storage", "Beds", "Lighting") — defined inline in bench JSON |
| **BenchItemCategory** | `CraftingBench.BenchItemCategory` inner class | Per-tab sub-category within a bench tab — has slots, diagram, etc. |

The native `StructuralCraftingWindow` and `DiagramCraftingWindow` use `BenchCategory`/`BenchItemCategory` for their tab navigation. The `ItemCategory` asset system is used by the creative library menu and could be used by custom UIs.

## 5. Item.set vs Item.categories

| Aspect | `Item.set` | `Item.categories` |
|--------|-----------|-------------------|
| Type | `String` | `String[]` |
| Purpose | UI grouping within a bench (e.g. "Wood_Hardwood") | Creative library classification |
| Public getter | **No** (must use reflection) | **Yes** (`getCategories()`) |
| Null when absent | Yes | Yes |
| Inheritable | Yes | Yes |
| Validated against | Nothing (free-form string) | `ItemCategory` asset store |
| Used by native UI | StructuralCraftingWindow uses it for recipe grouping | Creative library menu |

## 6. Protocol-Level ItemCategory

There is also a protocol-level `ItemCategory` at `com.hypixel.hytale.protocol.ItemCategory` — this is the **network packet** representation sent to clients via `UpdateItemCategories` packets. It mirrors the same fields (id, name, icon, order, infoDisplayMode, children). The server `ItemCategory.toPacket()` converts asset → protocol form.

The `ItemCategoryPacketGenerator` handles sending init/update/remove packets for categories to clients. This means **custom ItemCategory assets registered by a plugin would be synced to clients automatically**.

## Gotchas

1. **`getCategories()` returns null, not empty array** — always null-check before iterating
2. **One item can belong to multiple categories** — the array can have 2+ entries
3. **Categories are inherited** — if a parent item defines `"Categories": ["Wood"]`, all children inherit it unless they override
4. **`Item.set` has no getter** — the existing code uses `Field.setAccessible(true)` reflection. `Item.categories` does NOT need this.
5. **ItemCategory children are sorted by `order`** — the CODEC's `afterDecode` handler ensures `Arrays.sort(children, Comparator.comparingInt(value -> value.order))`

## See Also

- [icon-paths-and-images.md](../ui/icon-paths-and-images.md) — ItemCategory icon path resolution
- [recipes.md](../crafting/recipes.md) — CraftingRecipe structure
- [RecipeFilterRegistry.java](../../../src/main/java/com/UnobstructedThirdPerson/resourcecollection/RecipeFilterRegistry.java) — reflection-based `Item.set` extraction
