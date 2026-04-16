---
topic: "Drop Resolution"
category: "Blocks"
updated: 2026-04-16
sources: ["codebase analysis"]
---

# Drop Resolution

## Summary

When a block is broken, the engine determines what items to drop through a multi-step resolution process involving the block's gathering config, drop lists, tool data, and placement state.

## Resolution Pipeline

```
Player breaks block
  │
  ├─ Check: Is block player-placed AND UseDefaultDropWhenPlaced = true?
  │    └─ YES → Drop 1× block's own item → DONE
  │
  ├─ Check: Does the active tool have tool-specific overrides?
  │    └─ YES → Use tool's itemId/dropListId instead
  │
  ├─ Resolve drop source from BlockBreakingDropType:
  │    ├─ dropListId is set → Resolve ItemDropList
  │    ├─ itemId is set → Use direct item
  │    └─ Neither → Fallback to block's own item
  │
  └─ Call BlockHarvestUtils.getDrops(blockType, quantity, itemId, dropListId)
       └─ Returns List<ItemStack> of actual drops
```

## BlockHarvestUtils

The engine's utility class for resolving drops:

```java
List<ItemStack> drops = BlockHarvestUtils.getDrops(
    blockType,    // The block type being broken
    quantity,     // Base quantity from gathering config
    itemId,       // Specific item ID (or null)
    dropListId    // Drop list ID (or null)
);
```

For programmatic block removal:
```java
BlockHarvestUtils.naturallyRemoveBlock(world, x, y, z);
```

## Drop List Resolution

When a `dropListId` is specified, the engine:

1. Looks up the `ItemDropList` from `ItemDropList.getAssetStore()`
2. Evaluates the drop list's containers (single or multiple)
3. Each `ItemDrop` has `quantityMin` and `quantityMax` for random ranges
4. Returns the resolved `ItemStack` list

## Gathering Path Priority

Multiple gathering paths can produce drops:

| Path | Trigger |
|------|---------|
| `Breaking` | Standard block breaking |
| `Soft` | Soft block destruction (grass, flowers) |
| `Harvest` | Harvesting interactions (crops) |
| `Physics` | Physics cascade destruction |
| `ToolData` | Tool-specific overrides |

## See Also

- [Block Gathering](./gathering.md)
- [Drop Lists](../items/drop-lists.md)
- [Items](../items/items.md)
