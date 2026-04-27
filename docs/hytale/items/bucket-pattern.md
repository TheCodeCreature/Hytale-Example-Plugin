---
topic: "Bucket Item Pattern & Item Consumption Control"
category: "Items / Interactions"
updated: 2026-04-26
sources:
  - "run/mods/CodeCreature.Development/Server/Item/Items/Container/Container_Bucket.json"
  - ".tmp_hytale_src/.../PlaceFluidInteraction.java"
  - ".tmp_hytale_src/.../PlaceBlockInteraction.java"
  - ".tmp_hytale_src/.../BlockPlaceUtils.java"
  - ".tmp_hytale_src/.../ModifyInventoryInteraction.java"
  - ".tmp_hytale_src/.../RefillContainerInteraction.java"
  - ".tmp_hytale_src/.../Item.java"
  - ".tmp_hytale_src/.../BlockPlacementSettings.java"
---

# Bucket Item Pattern & Item Consumption Control

## Q1: How Does the Bucket Item Work?

The bucket is a **stateful container item** defined in [Container_Bucket.json](../../../run/mods/CodeCreature.Development/Server/Item/Items/Container/Container_Bucket.json).

### Lifecycle: Empty → Filled → Empty

1. **Empty bucket** (`Container_Bucket`) — base item with `MaxStack: 1`
2. **Right-click water** → `RefillContainer` interaction detects Water_Source fluid → transitions to `Filled_Water` state
3. **Filled bucket** (`Container_Bucket` in state `Filled_Water`) — a state variant with its own icon, BlockType, and interactions
4. **Right-click to place water** → `PlaceFluid` interaction places water, then `ModifyInventory` adjusts durability to -1, which "breaks" the item back to `Container_Bucket` (empty)

### Key: The Bucket Is NOT Consumed When Placing Water

The `Filled_Water` state's interaction chain:
```json
{
  "Type": "PlaceFluid",
  "RemoveItemInHand": false,        // ← KEY: bucket stays in hand
  "FluidToPlace": "Water_Source",
  "Next": {
    "Type": "ModifyInventory",
    "AdjustHeldItemDurability": -1,  // ← reduces durability to 0
    "BrokenItem": "Container_Bucket" // ← when broken, becomes empty bucket
  }
}
```

The pattern:
- `RemoveItemInHand: false` prevents the `PlaceFluid` interaction from consuming the item
- `ModifyInventory` with `AdjustHeldItemDurability: -1` reduces durability to 0
- `BrokenItem: "Container_Bucket"` replaces the broken item with a fresh empty bucket

---

## Q2: The "Container Item" / State Pattern

Hytale does NOT use Minecraft's `craftingRemainingItem` pattern. Instead, it uses a **state-based system**.

### Item States (from Item.java)

Items define a `State` map in JSON, where each state key maps to a **child item definition** that inherits from the parent but overrides specific properties:

```java
// Item.java — State codec registration
new KeyedCodec<>("State", new MapCodec(
    new ContainedAssetCodec<>(Item.class, CODEC, ContainedAssetCodec.Mode.INJECT_PARENT),
    HashMap::new
))
```

Each state entry becomes a **separate registered Item** in the asset map. The parent tracks `stateToBlock` (state name → child item ID) and the child item's `blockToState` (child item ID → state name) for bidirectional lookup.

### Key APIs

| Class | Method | Purpose |
|-------|--------|---------|
| `Item` | `getItemForState(String)` | Returns the Item asset for a given state name |
| `Item` | `getItemIdForState(String)` | Returns the item ID string for a given state name |
| `Item` | `getStateForItem(String)` | Reverse lookup: item ID → state name |
| `Item` | `isState()` | Returns true if this item is a state variant of another |

### How States Work in the Bucket

In `Container_Bucket.json`:
```json
{
  "MaxStack": 1,
  "State": {
    "Filled_Water": {
      "Variant": true,
      "Icon": "Icons/ItemsGenerated/Container_Bucket_Water.png",
      "MaxDurability": 1,
      "Consumable": true,
      "BlockType": { ... bucket_full model with water texture ... },
      "Interactions": { ... PlaceFluid + ModifyInventory chain ... }
    },
    "Filled_Milk": {
      "Variant": true,
      "Icon": "Icons/ItemsGenerated/Container_Bucket_Milk.png",
      "Consumable": true,
      "Interactions": { ... drink interaction ... }
    }
  }
}
```

Each state is a **separate item** (e.g., `Container_Bucket_Filled_Water` internally) that inherits from the parent `Container_Bucket` but overrides icon, interactions, BlockType model, etc.

---

## Q3: How Does the Bucket Render Different Contents?

**They are separate items (state variants), NOT one item with runtime state.**

- `Container_Bucket` — empty bucket (base item)
- `Container_Bucket` state `Filled_Water` — registered as a separate Item asset with:
  - Different `Icon` (water bucket icon)
  - Different `BlockType` (bucket_full model with water texture)
  - Different `Interactions` (PlaceFluid instead of RefillContainer)
- `Container_Bucket` state `Filled_Milk` — registered as a separate Item asset with:
  - Different `Icon` (milk bucket icon)
  - Different `BlockType` (bucket_full model with milk texture)
  - Different `Interactions` (drink/consume instead of RefillContainer)

The `Variant: true` flag in each state indicates this is a visual variant of the parent item.

### Transition Mechanism

| Transition | Mechanism | How |
|-----------|-----------|-----|
| Empty → Water | `RefillContainer` interaction | Raycasts for water fluid, transitions to `Filled_Water` state via `Item.getItemForState()` |
| Water → Empty | `PlaceFluid` + `ModifyInventory` chain | Places water, reduces durability to 0, `BrokenItem` creates fresh empty bucket |
| Milk → Empty | Consume drink interaction | Drinking reduces durability via `ModifyInventory`, `BrokenItem: "Container_Bucket"` |

---

## Q4: Block/Fluid Placement Without Item Consumption

### The `RemoveItemInHand` Field

Both `PlaceBlockInteraction` and `PlaceFluidInteraction` have a `RemoveItemInHand` field:

```java
// PlaceBlockInteraction.java
new KeyedCodec<>("RemoveItemInHand", Codec.BOOLEAN)
// Default: true — item IS consumed on placement
```

```java
// PlaceFluidInteraction.java
new KeyedCodec<>("RemoveItemInHand", Codec.BOOLEAN)
// Default: true — item IS consumed on placement
```

**Setting `RemoveItemInHand: false` prevents the item from being consumed when placing a block or fluid.**

### How Consumption Works in Detail (BlockPlaceUtils.placeBlock)

The consumption flow in [BlockPlaceUtils.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/modules/interaction/BlockPlaceUtils.java):

1. `PlaceBlockInteraction.tick0()` calls `BlockPlaceUtils.placeBlock()` with `this.removeItemInHand` parameter
2. Inside `placeBlock()`, if `isAdventureMode && removeItemInHand`:
   ```java
   ItemStackSlotTransaction transaction = itemContainer.removeItemStackFromSlot(
       activeSlot, itemStack, 1);
   ```
3. After `placeBlock()` returns, `PlaceBlockInteraction` also does:
   ```java
   if (isAdventure && heldItemStack.getQuantity() == 1 && this.removeItemInHand) {
       context.setHeldItem(null);
   }
   ```

**Two-phase consumption:**
- Phase 1 (inside `BlockPlaceUtils.placeBlock`): Removes 1 from the stack via `removeItemStackFromSlot`
- Phase 2 (in `PlaceBlockInteraction.tick0`): If stack was size 1 and now empty, clears the hand

Both phases are gated on `removeItemInHand`. Setting it to `false` skips both.

---

## Q5: PlacementSettings and All Available Fields

### BlockPlacementSettings (on BlockType)

From [BlockPlacementSettings.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/blocktype/config/BlockPlacementSettings.java):

| JSON Field | Type | Default | Purpose |
|-----------|------|---------|---------|
| `AllowRotationKey` | boolean | `true` | Whether the player can rotate the block with a key |
| `PlaceInEmptyBlocks` | boolean | `false` | Whether this block can replace blocks with Empty material |
| `RotationMode` | enum | `DEFAULT` | How block rotation is determined on placement |
| `BlockPreviewVisibility` | enum | `DEFAULT` | Override for block preview (ghost) visibility |
| `WallPlacementOverrideBlockId` | string | null | Alternative block to place on walls |
| `FloorPlacementOverrideBlockId` | string | null | Alternative block to place on floors |
| `CeilingPlacementOverrideBlockId` | string | null | Alternative block to place on ceilings |

### BlockPreviewVisibility Enum

From [BlockPreviewVisibility.java](../../../.tmp_hytale_src/com/hypixel/hytale/protocol/BlockPreviewVisibility.java):

| Value | Description |
|-------|-------------|
| `AlwaysVisible` (0) | Block preview always shown |
| `AlwaysHidden` (1) | Block preview never shown |
| `Default` (2) | Engine decides based on context |

### PlaceBlockInteraction Fields (on Item Interaction)

| JSON Field | Type | Default | Purpose |
|-----------|------|---------|---------|
| `BlockTypeToPlace` | string | null | Overrides the placed block type (uses held item's block if null) |
| `RemoveItemInHand` | boolean | `true` | Whether to consume the item on placement |
| `AllowDragPlacement` | boolean | `true` | Whether holding click continues placing |

**There is NO `ConsumeItem`, `ReplaceWith`, or `ConsumeOnPlace` field. The only control is `RemoveItemInHand`.**

---

## Q6: Can an Item Have BlockType for Preview but NOT Be Consumed?

**YES — and the bucket proves it.**

The `Filled_Water` state of `Container_Bucket` has:
- A `BlockType` definition (for the 3D model when placed in-world as decoration)
- `RemoveItemInHand: false` on its `PlaceFluid` interaction (not consumed)
- A `ModifyInventory` follow-up that transforms it back to empty

### For the PlaceBlock Placeholder

The placeholder items have `BlockType` (for the ghost preview) and use the `Use: PlaceBlock_Menu` interaction rather than the default `Secondary: PlaceBlock` interaction. The `PlaceBlockPlacementSystem` event handler cancels the `PlaceBlockEvent` so the placeholder block is never actually placed.

**Two approaches to prevent consumption:**

1. **Current approach**: Cancel `PlaceBlockEvent` in the ECS system → the engine never reaches the consumption code in `BlockPlaceUtils.placeBlock()` because the event is cancelled before placement proceeds
2. **Bucket approach**: Use `RemoveItemInHand: false` on a `PlaceBlock` interaction → the engine places the block but doesn't consume the item

### Important Nuance

When `PlaceBlockEvent` is cancelled in `BlockPlaceUtils.placeBlock()`:
```java
PlaceBlockEvent event = new PlaceBlockEvent(itemStack, blockPosition, targetRotation);
entityStore.invoke(ref, event);
if (event.isCancelled()) {
    targetBlockSection.invalidateBlock(...); // just invalidates, no consumption
    return; // exits before any consumption logic
}
```

The item removal code runs BEFORE `placeBlock()` is called (in `PlaceBlockInteraction.tick0()`):
```java
// This runs in PlaceBlockInteraction BEFORE calling placeBlock():
if (isAdventureMode && removeItemInHand) {
    // This happens BEFORE PlaceBlockEvent is even fired!
    ItemStackSlotTransaction transaction = itemContainer.removeItemStackFromSlot(...);
}
```

**CRITICAL FINDING:** The consumption happens in TWO places:
1. **BEFORE** `BlockPlaceUtils.placeBlock()` — in `PlaceBlockInteraction.tick0()` at line ~133-140 (Adventure mode pre-removal)
2. **AFTER** `BlockPlaceUtils.placeBlock()` — at line ~171 (clears hand if stack=1)

Cancelling `PlaceBlockEvent` only prevents the block from being placed. The item stack was already decremented in step 1. However, `placeBlock()` calls `onPlaceBlockFailure()` which **adds the item back** if the event was cancelled.

Wait — actually re-reading more carefully: the pre-removal at line ~133 is INSIDE `BlockPlaceUtils.placeBlock()`, not in `PlaceBlockInteraction.tick0()`. So cancelling the event means the code falls through to `invalidateBlock()` and returns, and the item IS decremented but then `onPlaceBlockFailure()` is never called because the function returns early after `invalidateBlock()`.

**This means cancelling PlaceBlockEvent DOES consume the item unless the item is restored elsewhere.**

---

## Q7: MaxStack and Consumption Behavior

### How Stack Size Interacts with Placement

From `PlaceBlockInteraction.tick0()`:
```java
// After placeBlock() call:
boolean isAdventure = playerComponent == null || playerComponent.getGameMode() == GameMode.Adventure;
if (isAdventure && heldItemStack.getQuantity() == 1 && this.removeItemInHand) {
    context.setHeldItem(null);  // Only clears hand when stack=1
}
```

From `BlockPlaceUtils.placeBlock()`:
```java
if (isAdventureMode && removeItemInHand) {
    ItemStackSlotTransaction transaction = itemContainer.removeItemStackFromSlot(activeSlot, itemStack, 1);
    // Removes exactly 1 from the stack
}
```

### Behavior Matrix (Adventure Mode, RemoveItemInHand=true)

| MaxStack | Quantity | On Placement | Result |
|----------|----------|-------------|--------|
| 1 | 1 | Removes 1, clears hand | Item gone |
| 64 | 1 | Removes 1, clears hand | Item gone |
| 64 | 2 | Removes 1, hand stays | Stack of 1 remains |
| 64 | 64 | Removes 1, hand stays | Stack of 63 remains |

**MaxStack itself does NOT affect consumption behavior.** What matters is:
- `RemoveItemInHand` (boolean flag on the interaction)
- Current `quantity` in the stack
- Game mode (Creative mode skips consumption)

### Creative Mode

In Creative mode, `isAdventureMode` is `false`, so neither the pre-removal in `placeBlock()` nor the hand-clearing in `tick0()` executes. Items are never consumed in Creative.

---

## Relevance to PlaceBlock Placeholder

### Current Situation

The placeholder items:
1. Have `BlockType` config (provides ghost block preview)
2. Use `Use: PlaceBlock_Menu` (custom plugin interaction)
3. Don't define a `Secondary` interaction explicitly
4. `PlaceBlockPlacementSystem` cancels `PlaceBlockEvent` and manually places the target block

### Potential Issue

If the engine auto-assigns a `PlaceBlock` interaction for `Secondary` (because the item has a `BlockType`), then the standard consumption path fires. The `PlaceBlockPlacementSystem` cancels the event, but the item may already have been decremented inside `BlockPlaceUtils.placeBlock()`.

### Recommended Approach Using the Bucket Pattern

If consumption is occurring, add `RemoveItemInHand: false` to the placeholder's interaction config:

```json
{
  "Interactions": {
    "Secondary": {
      "Interactions": [
        {
          "Type": "PlaceBlock",
          "RemoveItemInHand": false
        }
      ]
    },
    "Use": "PlaceBlock_Menu"
  }
}
```

This would:
1. Keep the block preview ghost (from `BlockType`)
2. Fire `PlaceBlockEvent` (which `PlaceBlockPlacementSystem` intercepts)
3. NOT consume the item (`RemoveItemInHand: false`)
4. Let the ECS system handle actual block placement and resource consumption

---

## Summary: Key Engine Patterns

```
┌─────────────────────────────────────────────────┐
│  Item Consumption Control in Hytale             │
├─────────────────────────────────────────────────┤
│                                                 │
│  PlaceBlockInteraction                          │
│    ├── RemoveItemInHand: true  (default)        │
│    │   → consumes 1 from stack on placement     │
│    └── RemoveItemInHand: false                  │
│        → item stays in hand after placement     │
│                                                 │
│  PlaceFluidInteraction                          │
│    ├── RemoveItemInHand: true  (default)        │
│    │   → consumes item when placing fluid       │
│    └── RemoveItemInHand: false                  │
│        → item stays (bucket pattern)            │
│                                                 │
│  ModifyInventory (chained via Next)             │
│    ├── AdjustHeldItemDurability: -1             │
│    │   → reduce durability                      │
│    └── BrokenItem: "SomeItem"                   │
│        → transform to different item at 0 dur   │
│                                                 │
│  PlaceBlockEvent (ECS)                          │
│    ├── cancel() → block not placed              │
│    │   BUT item already decremented if          │
│    │   RemoveItemInHand was true                │
│    └── setTargetBlock() → redirect placement    │
│                                                 │
│  GameMode                                       │
│    ├── Creative → never consumes                │
│    └── Adventure → consumes if flag=true        │
│                                                 │
└─────────────────────────────────────────────────┘
```
