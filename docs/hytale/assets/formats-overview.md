---
topic: "Asset Formats Overview"
category: "Assets"
updated: 2026-04-16
sources: ["codebase analysis", "asset JSON files"]
---

# Asset Formats Overview

## Summary

Hytale assets are JSON files that define game content. All asset types follow consistent conventions for naming, inheritance, and structure.

## File Organization

Assets are organized under `Server/` in the resource directory:

```
Server/
  └── Item/
      ├── Block/           ← Block-specific items (fluids, etc.)
      │   └── Fluids/
      │       └── Water_Source.json
      ├── Items/           ← General items and placeable blocks
      │   ├── Rock/
      │   │   ├── Rock_Stone.json
      │   │   └── Aqua/
      │   │       └── Rock_Stone_Aqua.json
      │   ├── Wood/
      │   │   └── Wood_Oak_Trunk.json
      │   ├── Furniture/
      │   │   └── Human/
      │   │       └── Furniture_Human_Ruins_Ladder.json
      │   ├── Plant/
      │   │   └── Grass/
      │   │       └── Plant_Grass_Sharp_Wild.json
      │   ├── Fluid/
      │   │   └── Fluid_Water.json
      │   └── _Debug/
      │       └── Placeholders/
      │           └── Placeholder_Full.json
      └── NPC/             ← NPC definitions
          └── ...
```

## Naming Conventions

| Convention | Example |
|------------|---------|
| Category_Material_Variant | `Rock_Stone_Cobble` |
| Category_Species_Part | `Wood_Oak_Trunk` |
| Category_Culture_Type | `Furniture_Kweebec_Bed` |
| Action prefix for recipes | `Salvage_Copper_Ingot` |
| Debug/utility prefix | `Placeholder_Full`, `Debug_Block_Empty` |

## Common JSON Patterns

### Root Object

Every item/block JSON is a root object with these common fields:

```json
{
  "TranslationProperties": { "Name": "server.items.MyItem.name" },
  "ItemLevel": 10,
  "MaxStack": 100,
  "Icon": "Icons/ItemsGenerated/MyItem.png",
  "Categories": ["Blocks.Rocks"],
  "BlockType": { ... },
  "Recipe": { ... },
  "ResourceTypes": [ ... ],
  "Tags": { ... }
}
```

### Texture Paths

All paths are relative to the asset pack root:

- `"Icons/ItemsGenerated/Rock_Stone.png"` — item icon
- `"BlockTextures/Rock_Stone.png"` — block face texture
- `"Blocks/Structures/Fences/Fence_Hardwood.blockymodel"` — 3D model
- `"Blocks/Benches/Builder_Texture.png"` — model texture

### Weight-Based Variants

Multiple texture variants with weighted random selection:

```json
"Textures": [
  { "All": "BlockTextures/Rock_Stone.png", "Weight": 2 },
  { "All": "BlockTextures/Rock_Stone_2.png", "Weight": 1 },
  { "All": "BlockTextures/Rock_Stone_3.png", "Weight": 1 }
]
```

Weight 2 means this variant appears twice as often as weight 1.

## Asset Type Summary

| Asset Type | JSON Location | Java Class | Access |
|------------|---------------|------------|--------|
| Items/Blocks | `Server/Item/Items/` | `Item`, `BlockType` | `Item.getAssetMap()`, `BlockType.getAssetMap()` |
| Crafting Recipes | Inline in item JSON | `CraftingRecipe` | `CraftingRecipe.getAssetMap()` |
| Drop Lists | Separate or synthetic | `ItemDropList` | `ItemDropList.getAssetStore()` |
| NPCs | `Server/Item/NPC/` | Various | NPC asset system |
| Sounds | Various | `SoundEvent` | Sound asset system |
| Particles | Various | `ParticleSystem` | Particle asset system |

## See Also

- [Block Type Format](./formats/block-type.md)
- [Item Format](./formats/item.md)
- [Crafting Recipe Format](./formats/crafting-recipe.md)
- [Asset Pipeline](./asset-pipeline.md)
