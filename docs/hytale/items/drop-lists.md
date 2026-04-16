---
topic: "Drop Lists"
category: "Items"
updated: 2026-04-16
sources: ["codebase analysis"]
---

# Drop Lists

## Summary

`ItemDropList` defines complex loot tables with weighted random drops, quantity ranges, and multiple possible outcomes. They are referenced by block gathering configs and can be created synthetically at runtime.

## ItemDropList Structure

An `ItemDropList` contains one or more drop containers:

### SingleItemDropContainer

Drops a single item with a quantity range:

```java
ItemDrop drop;           // The item and quantity range
// drop.getItemId()      — what to drop
// drop.getQuantityMin() — minimum quantity
// drop.getQuantityMax() — maximum quantity
```

### MultipleItemDropContainer

Drops one or more items from a weighted list:

```java
List<ItemDrop> drops;    // Multiple possible drops
// Each drop has weight, itemId, quantityMin, quantityMax
```

## Runtime Drop List Creation

Plugins can create synthetic drop lists and register them:

```java
List<ItemDropList> syntheticDropLists = new ArrayList<>();

// Create drop list entries...

// Register all synthetic drop lists at once:
ItemDropList.getAssetStore().loadAssets(
    "Plugin:NaturalIngredients",    // Namespace
    syntheticDropLists              // List of drop lists
);
```

## ItemDrop Properties

| Field | Type | Description |
|-------|------|-------------|
| `itemId` | String | ID of the item to drop |
| `quantityMin` | int | Minimum drop quantity |
| `quantityMax` | int | Maximum drop quantity |

These fields are accessed via reflection for modification (no public setters):

```java
Field dropQuantityMin = ItemDrop.class.getDeclaredField("quantityMin");
Field dropQuantityMax = ItemDrop.class.getDeclaredField("quantityMax");
dropQuantityMin.setAccessible(true);
dropQuantityMax.setAccessible(true);
```

## Drop List IDs in Gathering Config

```json
"Gathering": {
  "Soft": {
    "DropList": "Tree_Leaves"
  },
  "Physics": {
    "DropList": "Tree_Leaves_Physics"
  }
}
```

The `DropList` / `DropListId` field references an `ItemDropList` asset by ID.

## See Also

- [Drop Resolution](../blocks/drop-resolution.md)
- [Block Gathering](../blocks/gathering.md)
- [Runtime Asset Mutation](../assets/runtime-mutation.md)
