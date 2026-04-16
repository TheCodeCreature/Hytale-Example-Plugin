---
topic: "Crafting Recipe JSON Format"
category: "Asset Formats"
updated: 2026-04-16
sources: ["codebase analysis", "asset JSON files"]
---

# Crafting Recipe JSON Format

## Summary

Crafting recipes are defined inline within item JSON files under the `Recipe` field.

## Full Schema

```json
"Recipe": {
  "Input": [
    {
      "ItemId": "Specific_Item_Id",
      "ResourceTypeId": "Resource_Group_Id",
      "TagIndex": 0,
      "Quantity": 3
    }
  ],
  "BenchRequirement": [
    {
      "Id": "Builders",
      "Type": "StructuralCrafting",
      "Categories": ["WoodPlanks", "Stairs"]
    }
  ],
  "OutputQuantity": 1,
  "TimeSeconds": 0
}
```

## Input Matching Modes

Each input uses exactly one matching mode:

| Field | Match Type | Example |
|-------|-----------|---------|
| `ItemId` | Exact item | `"Ingredient_Fibre"` — only this specific item |
| `ResourceTypeId` | Resource group | `"Wood_All"` — any wood item |
| `TagIndex` | Tag-based | Matches items by tag value |

## Examples

### Simple Recipe (Specific Items)

```json
"Recipe": {
  "Input": [
    { "ItemId": "Ingredient_Fibre", "Quantity": 4 }
  ],
  "BenchRequirement": [
    { "Id": "Furniture_Bench", "Type": "Crafting", "Categories": ["Furniture_Beds"] }
  ]
}
```

### Resource Type Recipe (Any Matching Item)

```json
"Recipe": {
  "Input": [
    { "ResourceTypeId": "Wood_Hardwood_Trunk", "Quantity": 1 }
  ],
  "BenchRequirement": [
    { "Id": "Builders", "Type": "StructuralCrafting", "Categories": ["WoodPlanks"] }
  ],
  "OutputQuantity": 1
}
```

### Multi-Input Recipe

```json
"Recipe": {
  "Input": [
    { "ResourceTypeId": "Wood_Trunk", "Quantity": 6 },
    { "ResourceTypeId": "Rock", "Quantity": 3 }
  ],
  "BenchRequirement": [
    { "Id": "Fieldcraft", "Type": "Crafting", "Categories": ["Tools"] },
    { "Id": "Workbench", "Type": "Crafting", "Categories": ["Workbench_Crafting"] }
  ]
}
```

### Timed Processing Recipe

```json
"Recipe": {
  "Input": [
    { "ResourceTypeId": "Rock_Aqua", "Quantity": 2 }
  ],
  "BenchRequirement": [
    { "Id": "Builders", "Type": "Crafting", "Categories": ["Structural-Rock"] }
  ],
  "OutputQuantity": 1,
  "TimeSeconds": 0
}
```

## Bench Requirement Fields

| Field | Type | Description |
|-------|------|-------------|
| `Id` | String | Bench identifier |
| `Type` | String | Bench type: `Crafting`, `StructuralCrafting`, `Processing` |
| `Categories` | String[] | Which bench tabs this recipe appears in |

## See Also

- [Crafting Recipes](../../crafting/recipes.md)
- [Bench Types](../../crafting/bench-types.md)
