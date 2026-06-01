---
topic: "Item Category Dot-Notation Format"
category: "Items / Categories"
updated: 2026-05-04
sources:
  - "docs/Reference Assets/Assets/Server/Item/Items/ (multiple JSON files)"
  - "src/main/java/.../resourcecollection/ResourceTypeResolver.java (isDeco() confirms dot-notation)"
  - "src/main/java/.../resourcecollection/RecipeFilterRegistry.java (stores raw getCategories())"
  - "docs/hytale/items/item-categories.md (engine Item CODEC)"
---

# Item Category Dot-Notation Format

## Summary

Items in Hytale use **dot-notation** in their `Categories` JSON field: `"Parent.Child"` format (e.g., `"Blocks.Metal"`, `"Furniture.Beds"`). The `Item.getCategories()` API returns these **verbatim as raw strings** — it does NOT split or resolve them. Meanwhile, `ItemCategory.getId()` returns only the simple leaf ID (`"Metal"`, `"Beds"`). This mismatch means naive `categoryInfoMap.get(catId)` lookups fail for all items.

## Evidence: getCategories() Returns Dot-Notation Strings

**Proof from existing code** — `ResourceTypeResolver.isDeco()`:
```java
static boolean isDeco(@Nonnull Item item) {
    String[] cats = item.getCategories();
    if (cats == null) return false;
    for (String cat : cats) {
        if ("Blocks.Deco".equals(cat)) return true;  // ← dot-notation!
    }
    return false;
}
```
This method would never match if `getCategories()` returned split strings like `["Blocks", "Deco"]`.

**Proof from CODEC** — `Item.java`:
```java
new KeyedCodec<>("Categories",
    new ArrayCodec<>(Codec.STRING, String[]::new)  // ← plain String[], no parsing
```
The codec reads the JSON `"Categories"` array as raw strings. No dot-splitting occurs.

## Complete Catalog of Categories Values from Reference JSONs

### Format: `"TopLevelCategory.ChildCategory"`

| Dot-Notation String   | Top-Level (`ItemCategory.getId()`) | Child (`ItemCategory.getId()`) | Example Item |
|------------------------|------------------------------------|-------------------------------|--------------|
| `Blocks.Wood`          | `Blocks`                           | `Wood`                        | Wood_Wisteria_Wild_Trunk_Full |
| `Blocks.Metal`         | `Blocks`                           | `Metal`                       | Metal_Bronze_Decorative |
| `Blocks.Deco`          | `Blocks`                           | `Deco`                        | Wood_Stripped_Deco, Deco_Campfire_Off |
| `Blocks.Soils`         | `Blocks`                           | `Soils`                       | Template_Soil → all soil items |
| `Blocks.Ores`          | `Blocks`                           | `Ores`                        | Ore_Iron |
| `Blocks.Fluids`        | `Blocks`                           | `Fluids`                      | Fluid_Water, Fluid_Slime_Red |
| `Blocks.Portals`       | `Blocks`                           | `Portals`                     | Portal_Device |
| `Items.Ingredients`    | `Items`                            | `Ingredients`                 | Ingredient_Stick, Wood_Sticks |
| `Items.Foods`          | `Items`                            | `Foods`                       | Template_Food → all food items |
| `Items.Potions`        | `Items`                            | `Potions`                     | Potion_Template → all potions |
| `Items.Weapons`        | `Items`                            | `Weapons`                     | Template_Weapon_Sword → all swords |
| `Items.Tools`          | `Items`                            | `Tools`                       | Tool_Pickaxe_Crude → all tools |
| `Items.Armors`         | `Items`                            | `Armors`                      | Armor_Iron_Head |
| `Items.Recipes`        | `Items`                            | `Recipes`                     | Recipe_Page |
| `Furniture.Beds`       | `Furniture`                        | `Beds`                        | Furniture_Ancient_Bed |
| `Furniture.Doors`      | `Furniture`                        | `Doors`                       | Furniture_Ancient_Door |
| `Furniture.Containers` | `Furniture`                        | `Containers`                  | Furniture_Ancient_Chest_Small |
| `Furniture.Signs`      | `Furniture`                        | `Signs`                       | Furniture_Ancient_Sign, Furniture_Ancient_Painting |
| `Furniture.Shelves`    | `Furniture`                        | `Shelves`                     | Furniture_Ancient_Shelf, Furniture_Bookshelf_Single |
| `Furniture.Lighting`   | `Furniture`                        | `Lighting`                    | Furniture_Ancient_Candle, Deco_Lantern |
| `Furniture.Benches`    | `Furniture`                        | `Benches`                     | Bench_WorkBench, Bench_Campfire |
| `Furniture.Furniture`  | `Furniture`                        | `Furniture`                   | Furniture_Ancient_Table, _Chair, _Wardrobe |
| `Tool.BrushFilters`    | `Tool`                             | `BrushFilters`                | Filter_Air_Block |

### Key Observations

1. **Always two levels**: Format is always `"TopLevel.Child"` — never deeper (no `"A.B.C"`).
2. **Items can have multiple categories**: `Deco_Lantern` has `["Blocks.Deco", "Furniture.Lighting", "Blocks.Deco"]` (duplicates present in source data).
3. **Many items inherit categories**: Items with a `Parent` field inherit the parent's categories. E.g., `Food_Bread` has parent `Template_Food` which defines `["Items.Foods"]`.
4. **Some items have NO categories**: `Rock_Aqua_Beam` has no `Categories` field and no parent that provides one — these items will have `null` from `getCategories()`.
5. **`Tool` vs `Items.Tools`**: There's a top-level `Tool` category (parent of `BrushFilters`, `BuilderTool`, etc.) AND `Items.Tools`. These are different taxonomies.
6. **`Furniture.Furniture`**: The child category `Furniture` shares the same name as its parent `Furniture` — tables, chairs, wardrobes use this.

## The Bug: Why Category Matching Fails

### Current flow (broken):

```
buildCategoryInfoMap()
  → ItemCategory.getAssetMap().getAssetMap()
  → Recursively collects: { "Blocks" → info, "Wood" → info, "Metal" → info, ... }
  → Keys are SIMPLE IDs: "Wood", "Metal", "Beds", "Deco", etc.

extractMaterialGroups()
  → recipe.categoryIds() returns: ["Blocks.Metal", "Furniture.Beds", ...]
  → categoryInfoMap.get("Blocks.Metal") → null ❌
  → categoryInfoMap.get("Furniture.Beds") → null ❌
  → No groups found!
```

### The mismatch:

| What `recipe.categoryIds()` has | What `categoryInfoMap` has |
|---------------------------------|--------------------------|
| `"Blocks.Metal"`                | `"Metal"` (no match)     |
| `"Furniture.Beds"`              | `"Beds"` (no match)      |
| `"Items.Tools"`                 | `"Tools"` (no match)     |

## Recommended Fix: Build the Info Map with Dot-Notation Keys

### Strategy A: Build dot-notation keys during `buildCategoryInfoMap()` (Recommended)

Instead of keying by `cat.getId()`, key by `"parent.child"` format. This requires walking the tree with parent context:

```java
private Map<String, RecipeFilterPipeline.CategoryInfo> buildCategoryInfoMap() {
    Map<String, ItemCategory> allCats = ItemCategory.getAssetMap().getAssetMap();
    Map<String, RecipeFilterPipeline.CategoryInfo> map = new LinkedHashMap<>();
    
    for (ItemCategory topLevel : allCats.values()) {
        // Add top-level category with its simple ID (for items that might use just "Blocks")
        map.put(topLevel.getId(), new RecipeFilterPipeline.CategoryInfo(
                topLevel.getId(), topLevel.getName(), topLevel.getIcon(), topLevel.getOrder()));
        
        // Add children with dot-notation keys
        ItemCategory[] children = topLevel.getChildren();
        if (children != null) {
            for (ItemCategory child : children) {
                String dotKey = topLevel.getId() + "." + child.getId();
                map.put(dotKey, new RecipeFilterPipeline.CategoryInfo(
                        dotKey, child.getName(), child.getIcon(), child.getOrder()));
            }
        }
    }
    
    LOGGER.info("[BlueprintBook] Built categoryInfoMap with " + map.size()
            + " categories: " + map.keySet());
    return map;
}
```

**Pros**: Direct O(1) lookup, no parsing needed at match time, preserves the engine's own key format.

**Cons**: Assumes only 2-level depth (but the reference data confirms this is always the case).

### Strategy B: Parse dot-notation at match time

Keep the info map keyed by simple IDs, but split `"Blocks.Metal"` → use `"Metal"` for lookup:

```java
// In extractMaterialGroups():
for (String catId : seenCategories) {
    String lookupKey = catId;
    int dot = catId.lastIndexOf('.');
    if (dot >= 0) {
        lookupKey = catId.substring(dot + 1);  // "Blocks.Metal" → "Metal"
    }
    CategoryInfo info = categoryInfoMap.get(lookupKey);
    // ...
}
```

**Cons**: Ambiguity risk — `"Furniture.Furniture"` would collapse to `"Furniture"` (the top-level parent), losing the child meaning. Also, different parents could theoretically have children with the same ID.

### Recommendation

**Use Strategy A** — it's more correct, avoids ambiguity, and aligns with how the engine stores categories on items. The 2-level depth assumption is safe based on all evidence from reference assets and the `ItemCategory` class structure (children have children=null in practice).

## Category Hierarchy (for reference)

```
Items (top-level)
├── Tools
├── Weapons
├── Armors
├── Foods
├── Potions
├── Recipes
└── Ingredients

Blocks (top-level)
├── Rocks
├── Wood
├── Metal
├── Cloth
├── Soils
├── Ores
├── Plants
├── Fluids
├── Portals
└── Deco

Furniture (top-level)
├── Furniture  (generic furniture — tables, chairs, wardrobes)
├── Benches
├── Containers
├── Doors
├── Lighting
├── Beds
├── Shelves
└── Signs

Tool (top-level — editor/debug tools, NOT gameplay tools)
├── BuilderTool
├── BuilderToolSecondPage
├── BrushFilters
├── PrefabEditing
├── Machinima
└── TechnicalBlocks
```

## See Also
- [item-categories.md](./item-categories.md) — ItemCategory API reference
- [ResourceTypeResolver.java](../../src/main/java/com/UnobstructedThirdPerson/resourcecollection/ResourceTypeResolver.java) — `isDeco()` confirms dot-notation
