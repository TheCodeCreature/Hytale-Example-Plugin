---
topic: "ResourceType Definitions & Icon Mapping"
category: "Items / Crafting"
updated: 2026-05-06
sources: ["docs/Reference Assets/Assets/Server/Item/ResourceTypes/*.json (181 files)", "docs/Reference Assets/Assets/Server/Item/Items/**/*.json", ".tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/ResourceType.java"]
---

# ResourceType Definitions & "Any_*" Icon Mapping

## Summary

ResourceType definitions are JSON files in `Assets/Server/Item/ResourceTypes/`. Each file's **filename** (minus `.json`) is the **ResourceTypeId**. The file contains only an `Icon` field pointing to a UI image. The "Any_*" icon filenames (e.g., `Any_Bone.png`) are **shared icons** used by multiple ResourceType definitions — they are NOT ResourceTypeId values themselves. Items declare membership in ResourceTypes via their `ResourceTypes` array, and recipe inputs reference these ResourceTypeIds for flexible ingredient matching.

## ResourceType Definition Format

Based on the decompiled `com.hypixel.hytale.server.core.asset.type.item.config.ResourceType`:

```java
public class ResourceType {
    protected String id;          // The ResourceTypeId (from filename)
    protected String name;        // Optional display name (unused in vanilla assets)
    protected String description; // Optional description (unused in vanilla assets)
    protected String icon;        // Path to icon image file
}
```

Every vanilla ResourceType JSON contains **only** an `Icon` field (or is empty `{}`). No vanilla files use `Name` or `Description`.

Example (`Bone.json`):
```json
{
  "Icon": "Icons/ResourceTypes/Any_Bone.png"
}
```

The `id` is derived from the filename: `Bone.json` → ResourceTypeId = `"Bone"`.

## "Any_*" Icon → ResourceTypeId Mapping

### 1. `Any_Bone.png`

| ResourceTypeId | File |
|----------------|------|
| `Bone` | `Bone.json` |

**Only 1 ResourceType** uses this icon. Items like `Deco_Bone_Full` declare `ResourceTypes: [{Id: "Bone"}]`.

### 2. `Any_Book.png`

| ResourceTypeId | File |
|----------------|------|
| `Books` | `Books.json` |

**Only 1 ResourceType** uses this icon. Items like `Recipe_Book_Magic_Air` declare `ResourceTypes: [{Id: "Books"}]`.

### 3. `Any_Meat.png`

| ResourceTypeId | File |
|----------------|------|
| `Meats` | `Meats.json` |

**Only 1 ResourceType** uses this icon. Items like `Food_Beef_Raw` declare `ResourceTypes: [{Id: "Meats"}]`.

### 4. `Any_Mushroom.png`

| ResourceTypeId | File |
|----------------|------|
| `Mushrooms` | `Mushrooms.json` |

**Only 1 ResourceType** uses this icon. Items like `Plant_Crop_Mushroom_Block_Purple` declare `ResourceTypes: [{Id: "Mushrooms"}, {Id: "Vegetables"}]`.

### 5. `Any_Rock.png`

| ResourceTypeId | File |
|----------------|------|
| `Rock` | `Rock.json` |
| `Clays` | `Clays.json` |
| `Sands` | `Sands.json` |
| `Soils` | `Soils.json` |
| `Rock_Slate_Brick` | `Rock_Slate_Brick.json` |
| `Rock_Runic_Teal_Brick` | `Rock_Runic_Teal_Brick.json` |
| `Rock_Runic_Dark_Brick` | `Rock_Runic_Dark_Brick.json` |

**7 ResourceTypes** share this icon. `Rock` is the broad generic; `Clays`, `Sands`, `Soils` are material categories that apparently lack their own icon so they reuse the generic rock icon. A few brick subtypes also fall back to it.

### 6. `Any_Rubble.png`

| ResourceTypeId | File |
|----------------|------|
| `Rubble` | `Rubble.json` |

**Only 1 ResourceType** uses this icon. Items like `Rubble_Stone` declare `ResourceTypes: [{Id: "Rubble"}]`.

### 7. `Any_Trunk.png`

| ResourceTypeId | File |
|----------------|------|
| `Wood_Trunk` | `Wood_Trunk.json` |
| `Wood_All_Trunk` | `Wood_All_Trunk.json` |
| `Wood_Hardwood_Trunk` | `Wood_Hardwood_Trunk.json` |
| `Wood_Amber_Trunk` | `Wood_Amber_Trunk.json` |
| `Wood_Apple_Trunk` | `Wood_Apple_Trunk.json` |
| `Wood_Ash_Trunk` | `Wood_Ash_Trunk.json` |
| `Wood_Aspen_Trunk` | `Wood_Aspen_Trunk.json` |
| `Wood_Azure_Trunk` | `Wood_Azure_Trunk.json` |
| `Wood_Bamboo_Trunk` | `Wood_Bamboo_Trunk.json` |
| `Wood_Banyan_Trunk` | `Wood_Banyan_Trunk.json` |
| `Wood_Beech_Trunk` | `Wood_Beech_Trunk.json` |
| `Wood_Birch_Trunk` | `Wood_Birch_Trunk.json` |
| `Wood_Blackwood_Trunk` | `Wood_Blackwood_Trunk.json` |
| `Wood_Bottletree_Trunk` | `Wood_Bottletree_Trunk.json` |
| `Wood_Camphor_Trunk` | `Wood_Camphor_Trunk.json` |
| `Wood_Cedar_Trunk` | `Wood_Cedar_Trunk.json` |
| `Wood_Crystal_Trunk` | `Wood_Crystal_Trunk.json` |
| `Wood_Darkwood_Trunk` | `Wood_Darkwood_Trunk.json` |
| `Wood_Deadwood_Trunk` | `Wood_Deadwood_Trunk.json` |
| `Wood_Dry_Trunk` | `Wood_Dry_Trunk.json` |
| `Wood_Drywood_Trunk` | `Wood_Drywood_Trunk.json` |
| `Wood_Fig_Blue_Trunk` | `Wood_Fig_Blue_Trunk.json` |
| `Wood_Fir_Trunk` | `Wood_Fir_Trunk.json` |
| `Wood_Goldenwood_Trunk` | `Wood_Goldenwood_Trunk.json` |
| `Wood_Greenwood_Trunk` | `Wood_Greenwood_Trunk.json` |
| `Wood_Gumboab_Trunk` | `Wood_Gumboab_Trunk.json` |
| `Wood_Jungle_Trunk` | `Wood_Jungle_Trunk.json` |
| `Wood_Lightwood_Trunk` | `Wood_Lightwood_Trunk.json` |
| `Wood_Maple_Trunk` | `Wood_Maple_Trunk.json` |
| `Wood_Oak_Trunk` | `Wood_Oak_Trunk.json` |
| `Wood_Palm_Trunk` | `Wood_Palm_Trunk.json` |
| `Wood_Palo_Trunk` | `Wood_Palo_Trunk.json` |
| `Wood_Petrified_Trunk` | `Wood_Petrified_Trunk.json` |
| `Wood_Redwood_Trunk` | `Wood_Redwood_Trunk.json` |
| `Wood_Sallow_Trunk` | `Wood_Sallow_Trunk.json` |
| `Wood_Softwood_Trunk` | `Wood_Softwood_Trunk.json` |
| `Wood_Spiral_Trunk` | `Wood_Spiral_Trunk.json` |
| `Wood_Stormbark_Trunk` | `Wood_Stormbark_Trunk.json` |
| `Wood_Tropicalwood_Trunk` | `Wood_Tropicalwood_Trunk.json` |
| `Wood_Windwillow_Trunk` | `Wood_Windwillow_Trunk.json` |
| `Wood_Wisteria_Wild_Trunk` | `Wood_Wisteria_Wild_Trunk.json` |

**41 ResourceTypes** share this icon. All trunk-type ResourceTypes — from the broad `Wood_Trunk` down to species-specific `Wood_Oak_Trunk` — use the same generic trunk icon.

### 8. `Any_Recipe.png`

**NO ResourceType definition uses this icon.** The file `Any_Recipe.png` exists in `Assets/Common/Icons/ResourceTypes/` but is NOT referenced by any JSON in `Assets/Server/Item/ResourceTypes/`. It is only used by the plugin's custom `ResourceTypeRegistry.java` as a plugin-created icon for filtering recipes.

## How Items Declare ResourceTypes

Items declare an **array** of ResourceType memberships. An item can belong to MULTIPLE ResourceTypes simultaneously, forming an implicit hierarchy:

### Example: `Wood_Oak_Trunk` item

```json
"ResourceTypes": [
  { "Id": "Wood_Oak" },
  { "Id": "Wood_Hardwood" },
  { "Id": "Wood_Hardwood_Trunk" },
  { "Id": "Wood_Trunk" },
  { "Id": "Wood_All" },
  { "Id": "Fuel" },
  { "Id": "Charcoal" }
]
```

This means `Wood_Oak_Trunk` satisfies recipe inputs for ANY of these 7 ResourceTypeIds.

### Example: `Rock_Stone` item

```json
"ResourceTypes": [
  { "Id": "Rock" },
  { "Id": "Rock_Stone" }
]
```

### Example: `Rock_Stone_Brick` item

```json
"ResourceTypes": [
  { "Id": "Bricks" },
  { "Id": "Rock_Stone_Brick" }
]
```

Note: the brick does NOT declare `{Id: "Rock"}` — so a recipe requiring `ResourceTypeId: "Rock"` will NOT match stone bricks. The hierarchy is **explicit per item**, not engine-inferred.

### Example: `Rubble_Stone` item

```json
"ResourceTypes": [
  { "Id": "Rubble" }
]
```

Only one membership — rubble items have a simpler type graph.

### Example: `Plant_Crop_Mushroom_Block_Purple` item

```json
"ResourceTypes": [
  { "Id": "Mushrooms" },
  { "Id": "Vegetables" }
]
```

Cross-category membership — this mushroom block counts as both a mushroom AND a vegetable for recipe matching.

### Example: `Food_Beef_Raw` item

```json
"ResourceTypes": [
  { "Id": "Meats" }
]
```

## How Recipe Inputs Use ResourceTypeIds

Recipe inputs use `ResourceTypeId` for flexible ingredient matching:

```json
"Recipe": {
  "Input": [
    { "ResourceTypeId": "Rock_Stone", "Quantity": 1 }
  ]
}
```

The engine's `CraftingManager.matches()` performs **exact string equality** against items' `ResourceTypes[].Id` values. There is no substring matching, no prefix matching, no hierarchy inference.

Recipe inputs can also use direct `ItemId` for exact-item requirements:
```json
"Input": [
  { "ItemId": "Wood_Darkwood_Planks", "Quantity": 3 }
]
```

## Key Findings

1. **"Any_*" strings are NEVER ResourceTypeId values** — no item declares `{Id: "Any_Bone"}` or similar. They are purely icon filenames.

2. **Items declare MULTIPLE ResourceTypes** — this creates a flat, explicit membership graph (not an engine-inferred hierarchy).

3. **Shared icons indicate visual grouping, not identity** — multiple ResourceTypeIds can share the same icon (e.g., all 41 trunk types share `Any_Trunk.png`).

4. **The icon is a property OF the ResourceType definition** — it comes from the JSON at `Assets/Server/Item/ResourceTypes/{ResourceTypeId}.json`, not from the item.

5. **Recipe inputs reference actual ResourceTypeId strings** — values like `"Wood_Trunk"`, `"Rock_Stone"`, `"Meats"`, never `"Any_Trunk"` or `"Any_Rock"`.

6. **`Any_Recipe.png` has no engine ResourceType** — it is a custom plugin asset, not used by any vanilla ResourceType definition.

7. **No vanilla ResourceType uses `Name` or `Description`** — all 181 definitions contain only `Icon` (or are empty `{}`).

## Complete Icon Usage Summary

| Icon File | # ResourceTypes Using It | Notable ResourceTypeIds |
|-----------|--------------------------|------------------------|
| `Any_Bone.png` | 1 | `Bone` |
| `Any_Book.png` | 1 | `Books` |
| `Any_Meat.png` | 1 | `Meats` |
| `Any_Mushroom.png` | 1 | `Mushrooms` |
| `Any_Rock.png` | 7 | `Rock`, `Clays`, `Sands`, `Soils`, + 3 brick subtypes |
| `Any_Rubble.png` | 1 | `Rubble` |
| `Any_Trunk.png` | 41 | `Wood_Trunk`, `Wood_All_Trunk`, `Wood_Hardwood_Trunk`, + 38 species trunks |
| `Any_Recipe.png` | 0 | *(not used by any engine ResourceType)* |
| `Wood_Planks.png` | ~30 | `Wood_All`, `Wood_Planks`, `Wood_Oak`, most `Wood_*` species (non-trunk) |
| `Rock.png` | ~15 | `Bricks`, `Charcoal`, `Ice`, `Metal_Bars`, all `Salvage_*` types |
| `Rock_*.png` (specific) | 1 each | Individual rock variants (e.g., `Rock_Basalt` → `Rock_Basalt_Cobble.png`) |
| `Rubble.png` | 3 | `Vegetables`, `Fruits`, `Plant_Red` |

## ResourceTypes with No Icon (empty `{}`)

| ResourceTypeId | File |
|----------------|------|
| `Foods` | `Foods.json` |

## See Also

- [ResourceTypeId Resolution](../crafting/resourcetypeid-resolution.md) — how the engine matches recipe inputs to item ResourceTypes
- [Server/Client Boundary](../server-client-boundary.md) — what the server plugin can modify
