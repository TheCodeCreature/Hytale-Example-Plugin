---
topic: "Item Stacks & Containers"
category: "Items"
updated: 2026-04-16
sources: ["codebase analysis"]
---

# Item Stacks & Containers

## Summary

`ItemStack` represents a quantity of a specific item, while `ItemContainer` provides inventory-like operations for adding, removing, and querying items.

## ItemStack

An `ItemStack` is an immutable quantity of a specific item:

```java
ItemStack stack = itemInHand;
String itemId = stack.getItemId();         // e.g., "Rock_Stone"
String blockKey = stack.getBlockKey();      // Block type ID if placeable
int quantity = stack.getQuantity();

// Create a new stack with different quantity
ItemStack newStack = stack.withQuantity(12);
```

## ItemContainer

`ItemContainer` represents a collection of item slots (hotbar, storage, etc.):

```java
Player player = archetypeChunk.getComponent(index, Player.getComponentType());

// Get combined hotbar + storage inventory
ItemContainer combined = player.getInventory().getCombinedHotbarFirst();

// Check if removal is possible
boolean canRemove = combined.canRemoveItemStack(removalStack);

// Remove items
combined.removeItemStack(removalStack);

// Partial removal (remove as many as possible, don't fail if not enough)
combined.removeItemStack(removalStack, false, true);
```

## Inventory Structure

A player's inventory is organized into:

- **Hotbar** — the active item bar
- **Storage** — the main inventory grid
- **Combined** — hotbar + storage accessed as one container

The `getCombinedHotbarFirst()` method returns a view that removes items from the hotbar first, then storage.

## PlaceBlockEvent Integration

When handling `PlaceBlockEvent`:

```java
@Override
public void handle(int index, ArchetypeChunk<EntityStore> chunk, 
                   Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                   PlaceBlockEvent event) {
    ItemStack itemInHand = event.getItemInHand();
    if (itemInHand == null) return;
    
    String blockTypeId = itemInHand.getBlockKey();
    // The engine consumes 1 item after this handler runs
    // To consume additional items, use ItemContainer.removeItemStack()
}
```

## See Also

- [Items](./items.md)
- [ECS Event Systems](../ecs/event-systems.md)
