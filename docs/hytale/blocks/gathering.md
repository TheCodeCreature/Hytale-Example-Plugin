---
topic: "Block Gathering"
category: "Blocks"
updated: 2026-04-16
sources: ["codebase analysis", "asset JSON files"]
---

# Block Gathering

## Summary

`BlockGathering` defines how a block is broken, what it drops, and what tools interact with it. It's the most complex sub-system of `BlockType` and the primary target for plugin-based drop modification.

## Gathering Configuration

A `BlockGathering` object contains:

| Field | Type | Description |
|-------|------|-------------|
| `Breaking` | `BlockBreakingDropType` | What drops when the block is broken normally |
| `Soft` | `SoftBlockDropType` | What drops when a soft block is destroyed |
| `Harvest` | `HarvestingDropType` | What drops when harvested (e.g., crops) |
| `Physics` | `PhysicsDropType` | What drops when destroyed by physics cascade |
| `Tools` | Array | Tool-specific interactions |
| `ToolData` | Map | Tool-keyed overrides for item/drop list |
| `UseDefaultDropWhenPlaced` | Boolean | When true, player-placed blocks drop 1× of themselves |

## Drop Type Classes

### BlockBreakingDropType

The standard drop when a block is broken:

```json
"Breaking": {
  "GatherType": "Rocks",
  "Quality": 1,
  "Quantity": 1,
  "ItemId": "Rock_Stone_Cobble",
  "DropListId": null
}
```

| Field | Description |
|-------|-------------|
| `GatherType` | Skill/tool category: `Rocks`, `Woods`, `Plants`, etc. |
| `Quality` | Quality level for tool requirement matching |
| `Quantity` | Number of items dropped |
| `ItemId` | Specific item to drop (null = drop the block's own item) |
| `DropListId` | Reference to an `ItemDropList` for complex drop tables |

**Rule**: `ItemId` and `DropListId` are mutually exclusive. If both are null, the block drops its own item.

### SoftBlockDropType

For blocks destroyed softly (e.g., grass, flowers):

```json
"Soft": {
  "ItemId": "Plant_Seeds_Wheat",
  "DropList": "Tree_Leaves"
}
```

### HarvestingDropType

For crop-like blocks:

```json
"Harvest": {
  "ItemId": "Food_Wheat",
  "Quantity": 3
}
```

### PhysicsDropType

For blocks destroyed by physics cascade (support removed):

```json
"Physics": {
  "ItemId": "Wood_Oak_Trunk"
}
```

### Tool-Specific Interactions

```json
"Tools": [
  {
    "Type": "Scraper",
    "State": "Stripped",
    "DropList": "Bark"
  },
  {
    "Type": "Shears"
  }
]
```

## UseDefaultDropWhenPlaced

When `true`, blocks placed by players drop 1× of themselves instead of following the normal gathering config. This is critical for the natural resource economy:

- **World-generated** oak logs drop scaled resources (e.g., 12× trunk)
- **Player-placed** oak logs drop 1× oak log (the item they were placed with)

The engine uses `BlockPhysics.isDeco()` / `markDeco()` to track whether a block was player-placed.

## Drop Resolution Flow

```
Block Broken
  ├── Is player-placed AND UseDefaultDropWhenPlaced?
  │     └── YES → Drop 1× of block's own item
  │     └── NO  ↓
  ├── Has Breaking config?
  │     ├── Has DropListId?
  │     │     └── Resolve ItemDropList → drop items
  │     ├── Has ItemId?
  │     │     └── Drop Quantity × ItemId
  │     └── Neither?
  │           └── Drop 1× of block's own item (fallback)
  └── No Breaking config?
        └── Drop 1× of block's own item (fallback)
```

## Shared Instance Warning

**Critical**: Hytale shares `BlockGathering` objects between block types via JSON inheritance (`Parent` field). Multiple `BlockType` objects may reference the same `BlockGathering` Java instance.

**If you mutate a shared gathering, ALL blocks sharing it are affected.**

Safe pattern:
```java
BlockGathering original = bt.getGathering();
BlockGathering clone = cloneGathering(original);  // Deep copy
gatheringField.set(bt, clone);                      // Set clone on this block type only
// Now safe to mutate 'clone'
```

## Runtime Access

```java
BlockGathering gathering = blockType.getGathering();
BlockBreakingDropType breaking = gathering.getBreaking();
SoftBlockDropType soft = gathering.getSoft();
HarvestingDropType harvest = gathering.getHarvest();
PhysicsDropType physics = gathering.getPhysics();

// Breaking fields
String itemId = breaking.getItemId();
String dropListId = breaking.getDropListId();
int quantity = breaking.getQuantity();
String gatherType = breaking.getGatherType();
int quality = breaking.getQuality();
```

## See Also

- [Block Types](./block-types.md)
- [Drop Resolution](./drop-resolution.md)
- [Runtime Asset Mutation](../assets/runtime-mutation.md)
