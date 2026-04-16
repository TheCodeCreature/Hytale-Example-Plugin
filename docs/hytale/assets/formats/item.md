---
topic: "Item JSON Format"
category: "Asset Formats"
updated: 2026-04-16
sources: ["codebase analysis", "asset JSON files"]
---

# Item JSON Format

## Summary

Items are the fundamental asset type in Hytale. Every placeable block, tool, weapon, ingredient, and furniture piece is defined as an item.

## Minimal Item (Non-Placeable)

```json
{
  "TranslationProperties": {
    "Name": "server.items.Ingredient_Fibre.name"
  },
  "MaxStack": 100,
  "Icon": "Icons/ItemsGenerated/Ingredient_Fibre.png",
  "Categories": ["Materials.Ingredients"]
}
```

## Placeable Block Item

```json
{
  "TranslationProperties": {
    "Name": "server.items.Rock_Stone.name"
  },
  "ItemLevel": 10,
  "MaxStack": 100,
  "Icon": "Icons/ItemsGenerated/Rock_Stone.png",
  "Categories": ["Blocks.Rocks"],
  "PlayerAnimationsId": "Block",
  "Set": "Rock_Stone",
  "BlockType": {
    "Material": "Solid",
    "DrawType": "Cube",
    "Group": "Stone",
    "Textures": [
      { "All": "BlockTextures/Rock_Stone.png", "Weight": 2 }
    ]
  },
  "ResourceTypes": [
    { "Id": "Rock" },
    { "Id": "Rock_Stone" }
  ],
  "Tags": {
    "Type": ["Rock"]
  }
}
```

## Inherited Item

```json
{
  "Parent": "Wood_Softwood_Planks",
  "TranslationProperties": {
    "Name": "server.items.Wood_Hardwood_Planks.name"
  },
  "Icon": "Icons/ItemsGenerated/Wood_Hardwood_Planks.png",
  "Set": "Wood_Hardwood_Planks",
  "Recipe": {
    "Input": [{ "ResourceTypeId": "Wood_Hardwood_Trunk", "Quantity": 1 }],
    "BenchRequirement": [
      { "Id": "Builders", "Type": "StructuralCrafting", "Categories": ["WoodPlanks"] }
    ],
    "OutputQuantity": 1
  },
  "BlockType": {
    "Textures": [
      { "Weight": 1, "UpDown": "BlockTextures/Wood_Hardwood_Planks.png",
        "Sides": "BlockTextures/Wood_Hardwood_Planks_Side.png" }
    ]
  }
}
```

## Furniture Item

```json
{
  "TranslationProperties": {
    "Name": "server.items.Furniture_Kweebec_Bed.name"
  },
  "PlayerAnimationsId": "Block",
  "Categories": ["Furniture.Beds"],
  "Set": "Furniture_Kweebec",
  "Recipe": {
    "Input": [
      { "Quantity": 3, "ResourceTypeId": "Wood_All" },
      { "ItemId": "Ingredient_Fibre", "Quantity": 4 }
    ],
    "BenchRequirement": [
      { "Type": "Crafting", "Id": "Furniture_Bench", "Categories": ["Furniture_Beds"] }
    ]
  },
  "Icon": "Icons/ItemsGenerated/Furniture_Kweebec_Bed.png",
  "BlockType": {
    "DrawType": "Model",
    "CustomModel": "Blocks/Decorative_Sets/Kweebec/Bed.blockymodel",
    "CustomModelTexture": [
      { "Texture": "Blocks/Decorative_Sets/Kweebec/Bed_Texture.png", "Weight": 1 }
    ],
    "Beds": [{ "Offset": { "X": -0.1, "Y": 0.4, "Z": 0.7 }, "Yaw": 0 }],
    "Interactions": {
      "Use": { "Interactions": [{ "Type": "Bed" }] }
    }
  }
}
```

## Key Fields Reference

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `TranslationProperties` | Object | Yes | Localization keys |
| `Parent` | String | No | Inherit from another item |
| `ItemLevel` | Integer | No | Level/tier indicator |
| `MaxStack` | Integer | No | Maximum stack size |
| `Icon` | String | Yes | Path to icon texture |
| `Categories` | String[] | No | UI categorization |
| `BlockType` | Object | No | Makes item placeable |
| `Recipe` | Object | No | Inline crafting recipe |
| `ResourceTypes` | Object[] | No | Resource group memberships |
| `Tags` | Object | No | Categorization tags |
| `Set` | String | No | Item set grouping |
| `Quality` | String | No | Rarity level |

## See Also

- [Block Type Format](./block-type.md)
- [Crafting Recipe Format](./crafting-recipe.md)
- [Items](../../items/items.md)
