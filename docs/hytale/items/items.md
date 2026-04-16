---
topic: "Items"
category: "Items"
updated: 2026-04-16
sources: ["codebase analysis", "asset JSON files"]
---

# Items

## Summary

Items in Hytale represent everything a player can hold, place, craft, or interact with. Items are defined in JSON files under `Server/Item/` and can reference block types, recipes, resource types, and interactions.

## Item Properties

| Field | Type | Description |
|-------|------|-------------|
| `TranslationProperties` | Object | Localization keys for name and description |
| `ItemLevel` | Integer | Level requirement or tier indicator |
| `MaxStack` | Integer | Maximum stack size (default varies) |
| `Icon` | String | Path to icon image |
| `Categories` | Array | UI categorization: `Blocks.Rocks`, `Furniture.Beds`, etc. |
| `SubCategory` | String | Secondary categorization |
| `Set` | String | Item set grouping (e.g., `Rock_Stone`, `Wood_Oak`) |
| `FuelQuality` | Float | Burn value when used as fuel |
| `PlayerAnimationsId` | String | Animation set: `Block`, `Item`, etc. |
| `Quality` | String | Rarity: `Developer`, `Common`, etc. |
| `Interactions` | Object | Primary/Secondary interaction definitions |
| `Recipe` | Object | Inline crafting recipe (see [Recipes](../crafting/recipes.md)) |
| `BlockType` | Object | Inline block type definition (makes the item placeable) |
| `ResourceTypes` | Array | Resource type memberships for crafting |
| `Tags` | Object | Categorization tags for filtering |
| `Parent` | String | Inherit from another item definition |
| `IconProperties` | Object | Icon rendering overrides (scale, rotation, translation) |
| `ItemSoundSetId` | String | Sound set for item interactions |

## Runtime Access

```java
// Get item by ID
Item item = Item.getAssetMap().getAsset("Rock_Stone");

// Item properties
String blockId = item.getBlockId();      // Block type this item places (may be null)
int maxStack = item.getMaxStack();       // Maximum stack size
String id = item.getId();                // Item identifier
```

## Items ↔ Blocks Relationship

Items and blocks are linked but distinct:

- An **item** can have an inline `BlockType` definition — placing the item creates that block
- A **block type** references its item via `getItem()` — breaking the block can return this item
- Some items reference blocks via `getBlockId()` without `hasBlockType()` being true (e.g., rails, doors)
- Not all items are placeable (weapons, tools, ingredients)
- Not all blocks have corresponding items in normal gameplay

## Resource Types

Items declare membership in resource type groups used by crafting recipes:

```json
"ResourceTypes": [
  { "Id": "Rock" },
  { "Id": "Rock_Stone" }
]
```

A recipe input with `"ResourceTypeId": "Rock"` accepts any item in the `Rock` resource type group.

## Tags

Tags provide another categorization layer:

```json
"Tags": {
  "Type": ["Rock"],
  "Family": ["Hardwood"]
}
```

Tags are used by crafting recipes with `tagIndex` matching and by NPC/gameplay systems.

## Item Inheritance

Items can inherit from a parent item, overriding only specific fields:

```json
{
  "Parent": "Wood_Softwood_Planks",
  "Icon": "Icons/ItemsGenerated/Wood_Hardwood_Planks.png",
  "BlockType": {
    "Textures": [{ "UpDown": "BlockTextures/Wood_Hardwood_Planks.png" }]
  }
}
```

The child inherits all properties from the parent and only overrides the specified fields.

## See Also

- [Item Stacks & Containers](./stacks-and-containers.md)
- [Drop Lists](./drop-lists.md)
- [Crafting Recipes](../crafting/recipes.md)
- [Item Format](../assets/formats/item.md)
