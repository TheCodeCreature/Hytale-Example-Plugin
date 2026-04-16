---
topic: "Drop List JSON Format"
category: "Asset Formats"
updated: 2026-04-16
sources: ["codebase analysis"]
---

# Drop List JSON Format

## Summary

Drop lists define loot tables with weighted random drops. They are referenced by block gathering configs and can be created programmatically at runtime.

## Reference in Block Gathering

Drop lists are referenced by ID in gathering configs:

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

## Drop List Structure

Drop lists contain containers that define possible drops:

### SingleItemDropContainer

Always drops a specific item with a quantity range:

```
ItemDropList
  └── SingleItemDropContainer
        └── ItemDrop
              ├── itemId: "Plant_Seeds_Wheat"
              ├── quantityMin: 1
              └── quantityMax: 3
```

### MultipleItemDropContainer

Drops one item selected from a weighted list:

```
ItemDropList
  └── MultipleItemDropContainer
        ├── ItemDrop (weight: 3, itemId: "Stick", qty: 1-2)
        ├── ItemDrop (weight: 1, itemId: "Apple", qty: 1-1)
        └── ItemDrop (weight: 1, itemId: null)   ← nothing drops
```

## Programmatic Creation

Plugins can create synthetic drop lists at runtime:

```java
List<ItemDropList> syntheticLists = new ArrayList<>();

// Create and configure drop lists...

// Register them with the asset store:
ItemDropList.getAssetStore().loadAssets(
    "Plugin:MyNamespace",
    syntheticLists
);
```

## See Also

- [Drop Resolution](../../blocks/drop-resolution.md)
- [Block Gathering](../../blocks/gathering.md)
- [Runtime Asset Mutation](../runtime-mutation.md)
