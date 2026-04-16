---
topic: "Block Groups & Resource Types"
category: "Crafting"
updated: 2026-04-16
sources: ["codebase analysis", "asset JSON files"]
---

# Block Groups & Resource Types

## Summary

Resource types and block groups enable flexible crafting recipe matching. Instead of requiring a specific item, recipes can accept any item from a resource type group.

## Resource Types

Items declare resource type memberships:

```json
"ResourceTypes": [
  { "Id": "Rock" },
  { "Id": "Rock_Stone" }
]
```

This item is a member of both `Rock` and `Rock_Stone` groups.

## How Recipes Use Resource Types

When a recipe input specifies `ResourceTypeId`:

```json
"Input": [
  {
    "ResourceTypeId": "Wood_Hardwood_Trunk",
    "Quantity": 1
  }
]
```

Any item with `"ResourceTypes": [{"Id": "Wood_Hardwood_Trunk"}]` satisfies this input.

## BlockGroup

`BlockGroup` is the engine class that resolves resource type IDs to specific block types. When the crafting system evaluates a recipe:

1. Look up the `ResourceTypeId` (e.g., `"Rock"`)
2. Find all items with matching resource types
3. Any matching item can be used as the input

## Common Resource Type Hierarchies

```
Wood_All
  ├── Wood_Trunk
  │     ├── Wood_Softwood_Trunk
  │     └── Wood_Hardwood_Trunk
  ├── Wood_Planks
  ├── Wood_Softwood
  └── Wood_Hardwood

Rock
  ├── Rock_Stone
  ├── Rock_Aqua
  ├── Rock_Shale
  └── ...

Fuel
Charcoal
```

## Tags vs Resource Types

| Feature | Resource Types | Tags |
|---------|----------------|------|
| Purpose | Crafting recipe matching | General categorization |
| Field | `ResourceTypes` | `Tags` |
| Recipe field | `ResourceTypeId` | `TagIndex` |
| Example | `"Rock"`, `"Wood_Trunk"` | `"Type": ["Rock"]`, `"Family": ["Hardwood"]` |

## See Also

- [Crafting Recipes](./recipes.md)
- [Items](../items/items.md)
