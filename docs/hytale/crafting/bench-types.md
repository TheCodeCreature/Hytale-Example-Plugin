---
topic: "Bench Types"
category: "Crafting"
updated: 2026-04-16
sources: ["codebase analysis", "asset JSON files"]
---

# Bench Types

## Summary

Workbenches in Hytale determine which crafting recipes are available. Each bench has a type, ID, and categories that organize recipes into tabs.

## Bench Configuration

Benches are defined in the block type JSON of the bench block:

```json
"Bench": {
  "Type": "StructuralCrafting",
  "Id": "Builders",
  "AllowBlockGroupCycling": true,
  "AlwaysShowInventoryHints": true,
  "HeaderCategories": ["WoodPlanks", "OrnatePlanks", "DecorativePlanks"],
  "Categories": [
    "WoodPlanks", "OrnatePlanks", "DecorativePlanks",
    "Bricks", "Decorative", "Ornate", "Smooth",
    "Stairs", "HalfSlab", "SmoothHalfSlab",
    "Beam", "Platform", "Roof", "Pillar", "Wall", "Gate", "Ladder"
  ]
}
```

## Bench Types

| Type | Description | Example |
|------|-------------|---------|
| `Crafting` | General crafting recipes | Basic crafting table |
| `StructuralCrafting` | Building block recipes (planks, stairs, walls) | Builder's Bench |
| `Processing` | Refinement recipes (smelting, cutting) | Stonecutter, Furnace |
| `DiagramCrafting` | Pattern-based crafting | Specialized workbenches |

## BenchRequirement in Recipes

Recipes specify which bench they require:

```json
"BenchRequirement": [
  {
    "Id": "Builders",
    "Type": "StructuralCrafting",
    "Categories": ["WoodPlanks"]
  }
]
```

A recipe can list multiple bench requirements, meaning it's available at any of the listed benches.

## BenchRequirement API

```java
BenchRequirement req = recipe.getBenchRequirement()[0];
String benchId = req.getId();       // e.g., "Builders", "Furniture_Bench", "Fieldcraft"
String benchType = req.getType();   // e.g., "Crafting", "StructuralCrafting"
```

## Block Group Cycling

When `AllowBlockGroupCycling` is true, the bench UI allows players to cycle through different block variants within a resource type group. For example, at the Builder's bench, selecting "Wood Planks" lets you cycle between Softwood, Hardwood, etc.

## Known Bench IDs

| Bench ID | Type | Purpose |
|----------|------|---------|
| `Builders` | StructuralCrafting | Building blocks (planks, stairs, walls) |
| `Furniture_Bench` | Crafting | Furniture items (beds, chairs, tables) |
| `Fieldcraft` | Crafting | Basic recipes, no bench required |
| `Workbench` | Crafting | General crafting station |
| Various processing | Processing | Smelting, cutting, refining |

## See Also

- [Crafting Recipes](./recipes.md)
- [Block Groups & Resource Types](./block-groups.md)
