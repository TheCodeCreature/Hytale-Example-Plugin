---
topic: "Bucket Pattern Feasibility for PlaceBlock Placeholder System"
category: "Items / Interactions / Architecture"
updated: 2026-04-26
sources:
  - "docs/Resources/items/Container/Container_Bucket.json"
  - "src/main/resources/Server/Item/Items/Tool/Block_Placeholder.json"
  - "docs/hytale/items/bucket-pattern.md"
  - "docs/hytale/items/state-placeblock-interaction-research.md"
  - "docs/hytale/items/placeblock-building-tool-api-research.md"
  - "docs/hytale/items/placeblock-risk-investigation.md"
  - "docs/hytale/items/item-state-system.md"
  - "docs/hytale/plugins/interaction-codec-registration-api.md"
  - "docs/hytale/blocks/block-preview-system.md"
  - "docs/hytale/assets/inventory-container-apis.md"
  - "docs/hytale/crafting/craftitem-bypass-analysis.md"
  - ".tmp_hytale_src/.../PlaceBlockInteraction.java"
  - ".tmp_hytale_src/.../PlaceFluidInteraction.java"
  - ".tmp_hytale_src/.../ModifyInventoryInteraction.java"
  - ".tmp_hytale_src/.../SimpleInteraction.java"
  - ".tmp_hytale_src/.../Interaction.java"
  - ".tmp_hytale_src/.../BlockPlaceUtils.java"
  - ".tmp_hytale_src/.../Item.java"
  - ".tmp_hytale_src/.../ItemStack.java"
---

# Bucket Pattern Feasibility for PlaceBlock Placeholder System

## Summary

This document answers 8 specific feasibility questions about adapting the `Container_Bucket` interaction pattern (PlaceFluid → ModifyInventory → BrokenItem transformation) for the PlaceBlock placeholder system. The findings are mixed: the bucket's `Next` chaining pattern is **not available on PlaceBlock**, and `ModifyInventory` cannot consume broader inventory materials. However, the analysis reveals alternative approaches.

---

## Q1: Does `PlaceBlock` Support `Next` Chaining Like `PlaceFluid`?

### Answer: **NO. `PlaceBlockInteraction` does NOT support `Next` chaining.**

### Evidence: Class Hierarchy

From [interaction-codec-registration-api.md](../../hytale/plugins/interaction-codec-registration-api.md), the interaction class hierarchy is:

```
Interaction (abstract)
│   ABSTRACT_CODEC: base fields only (ViewDistance, Effects, RunTime, etc.)
│
├── SimpleInteraction                    ← Has "Next" and "Failed" fields
│   │   CODEC extends ABSTRACT_CODEC, adds "Next" and "Failed"
│   │
│   ├── SimpleBlockInteraction (abstract)
│   │   ├── OpenBenchPageInteraction
│   │   ├── BedInteraction
│   │   └── ...
│   └── [many more SimpleInteraction subclasses]
│
├── PlaceBlockInteraction                ← Extends Interaction DIRECTLY
├── BreakBlockInteraction                ← Extends Interaction DIRECTLY
├── PlaceFluidInteraction                ← Extends SimpleInteraction (has Next/Failed)
├── DamageEntityInteraction              ← Extends Interaction DIRECTLY
└── ... (40+ built-in types)
```

**`PlaceBlockInteraction` extends `Interaction` directly**, NOT `SimpleInteraction`. The `Next` and `Failed` fields are defined on `SimpleInteraction.CODEC`:

```java
// SimpleInteraction.CODEC adds these fields:
.<String>appendInherited(new KeyedCodec<>("Next", Interaction.CHILD_ASSET_CODEC), ...)
.<String>appendInherited(new KeyedCodec<>("Failed", Interaction.CHILD_ASSET_CODEC), ...)
```

Since `PlaceBlockInteraction.CODEC` extends `Interaction.ABSTRACT_CODEC` (not `SimpleInteraction.CODEC`), it does **not** have `Next` or `Failed` fields. Adding `"Next"` to a `PlaceBlock` interaction JSON would be silently ignored.

**`PlaceFluidInteraction` extends `SimpleInteraction`**, which is why the bucket's PlaceFluid → ModifyInventory chain works. PlaceFluid has `Next` because it inherits from SimpleInteraction.

### Implication

You **cannot** write:
```json
{
  "Type": "PlaceBlock",
  "RemoveItemInHand": false,
  "Next": {
    "Type": "ModifyInventory",
    "AdjustHeldItemDurability": -1,
    "BrokenItem": "Block_Placeholder"
  }
}
```

The `Next` field would be silently discarded. After PlaceBlock executes, no follow-up interaction runs.

### Workaround

To chain behavior after PlaceBlock, you must handle the post-placement logic in your `PlaceBlockEvent` ECS handler (`PlaceBlockPlacementSystem`), which is what you're already doing.

---

## Q2: What Determines Which Block Type `PlaceBlock` Places?

### Answer: The block type is resolved from either `PlaceBlockInteraction.blockTypeKey` or the held item's `BlockType`.

### Resolution Order

From [block-preview-system.md](../../hytale/blocks/block-preview-system.md) and [placeblock-building-tool-api-research.md](./placeblock-building-tool-api-research.md):

1. **`PlaceBlockInteraction.blockTypeKey`** — if the interaction JSON defines `"BlockTypeToPlace": "Some_Block"`, that block type is used:
   ```java
   String interactionBlockTypeKey = this.blockTypeKey != null
       ? this.blockTypeKey
       : heldItemStack.getBlockKey();
   ```

2. **`ItemStack.getBlockKey()`** — if `blockTypeKey` is null, falls back to the held item's `BlockType` definition:
   ```java
   public String getBlockKey() {
       Item item = this.getItem();
       return item.hasBlockType() ? item.getBlockId() : null;
   }
   ```

3. **`Item.getBlockId()`** — returns the item's `blockId` field, which is the string ID of the `BlockType` defined inline in the item JSON (or referenced from a parent).

### Can `BlockTypeToPlace` Override the Placed Block?

**Yes, but it's static per interaction definition.** You cannot change `BlockTypeToPlace` per item instance at runtime. The field is baked into the `PlaceBlockInteraction` asset at load time.

There is **no** per-instance override analogous to `PlaceFluidInteraction`'s `FluidToPlace`. The PlaceBlock interaction does not read from ItemStack metadata to determine the block type.

### Implication

For the PlaceBlock placeholder, the engine would always try to place the placeholder's own BlockType (the green/blue cube). Your current approach — cancelling `PlaceBlockEvent` and manually placing the target block from metadata — is the correct workaround.

---

## Q3: Can `ModifyInventory` Change an Item's State?

### Answer: **Yes, via `BrokenItem` — but with limitations.**

### How `BrokenItem` Works

From [bucket-pattern.md](./bucket-pattern.md) and the `Container_Bucket.json`:

```json
{
  "Type": "ModifyInventory",
  "AdjustHeldItemDurability": -1,
  "BrokenItem": "Container_Bucket"
}
```

When `AdjustHeldItemDurability` reduces durability to 0 and the item has `"Consumable": true`, the item "breaks." If `BrokenItem` is set, instead of being destroyed, the held item is **replaced** with a new `ItemStack` of the specified item ID.

### Does `BrokenItem` Accept State Item IDs?

**Almost certainly yes.** `BrokenItem` is a string item ID, and state variants are registered items with IDs like `*Block_Placeholder_State_Armed_Green_3`. The engine would look up `Item.getAssetMap().getAsset("*Block_Placeholder_State_Armed_Green_3")` and create a new ItemStack with that ID.

**However**, this creates a **fresh** ItemStack. The metadata (BSON data with recipe ID, target block ID) from the original stack is **NOT preserved**. The broken item replacement constructs a new ItemStack from scratch — it does not copy metadata from the old one.

### Can You Transform Back to "Self"?

If `Armed_Green_3` has `BrokenItem: "*Block_Placeholder_State_Armed_Green_3"`, the result would be a **new, blank** Armed_Green_3 item — without any of the recipe metadata that was on the original. This defeats the purpose since the metadata carrying the target block ID would be lost.

### Verdict

**`BrokenItem` is unsuitable for the placeholder cycling pattern** because:
1. It destroys metadata (recipe ID, target block type)
2. It creates a fresh item, not a mutation of the existing one
3. Even if you could chain PlaceBlock → ModifyInventory (you can't, per Q1), the metadata loss would break the system

---

## Q4: Can `ModifyInventory` Consume Materials from Broader Inventory?

### Answer: **NO. `ModifyInventory` only operates on the held item.**

### Known `ModifyInventory` Fields

From analysis of `ModifyInventoryInteraction.java` and its usage in `Container_Bucket.json`:

| Field | Type | Purpose |
|-------|------|---------|
| `AdjustHeldItemDurability` | int | Adds to the held item's durability (negative = reduce) |
| `BrokenItem` | string | Item ID to replace the held item with when it breaks |

**There are no fields for:**
- Removing items from specific inventory slots
- Consuming items by ID or ResourceType from the backpack
- Scanning the broader inventory for materials
- Any crafting-recipe-style material consumption

### How Broader Material Consumption Works in Hytale

Material consumption from inventory is handled by a completely separate API:

```java
// ItemContainer — the inventory container API
ItemContainer.canRemoveMaterials(List<MaterialQuantity> materials)  // dry-run check
ItemContainer.removeMaterials(List<MaterialQuantity> materials, boolean allOrNothing, boolean exactAmount, boolean filter)  // actual removal
```

From [inventory-container-apis.md](../../hytale/assets/inventory-container-apis.md) and [craftitem-bypass-analysis.md](../../hytale/crafting/craftitem-bypass-analysis.md), these are methods on `ItemContainer`, not interaction types. They are called programmatically from server-side code (e.g., `CraftingManager`, your `PlaceBlockPlacementSystem`).

### Verdict

`ModifyInventory` is a narrow interaction type that only adjusts the held item's durability and optionally replaces it on break. **It cannot consume recipe materials.** Your current approach of calling `container.removeMaterials()` from `PlaceBlockPlacementSystem` is the correct and only way to consume materials from inventory.

---

## Q5: Is There a `RemoveMaterials` or `ConsumeMaterials` Interaction Type?

### Answer: **NO. No built-in interaction type consumes crafting materials.**

### Evidence

Searching the registered interaction types from `InteractionModule`:
- `Simple`, `PlaceBlock`, `BreakBlock`, `DamageEntity`, `Charging`, `OpenCustomUI`, `OpenPage`, `PlaceFluid`, `ModifyInventory`, `Condition`, `RefillContainer`, `ApplyEffect`, `SendMessage`, `Bed`, `Seating`, `SpawnNPC`, `SpawnMinecart`, `EnterPortal`, `ReturnPortal`, `TeleportConfigInstance`, `OpenBenchPage`, `OpenProcessingBench`, etc.

**None of these interaction types perform recipe material consumption.** Material consumption is:
1. Handled by `CraftingManager` during bench crafting workflows
2. Called programmatically via `ItemContainer.removeMaterials()` from plugin code

### Implication

There is no JSON-declarable interaction that says "consume 3 Wood_Planks and 2 Iron_Bars from inventory." This must be implemented in server-side plugin code, which is exactly what `PlaceBlockPlacementSystem` does.

---

## Q6: Can Interactions Be Dynamically Modified at Runtime via Plugin API?

### Answer: **No native API exists for per-ItemStack interaction modification.**

### What CAN Be Modified

| Target | Modifiable? | How |
|--------|-------------|-----|
| `Item` asset's interactions map | Reflection only | `Item.interactions` is protected, finalized at load |
| `Interaction` asset fields | Reflection only | Fields are set by codec, no setter API |
| `RootInteraction` asset | Reflection only | Can load new ones via `AssetRegistry.getAssetStore(RootInteraction.class).loadAssets()` |
| Per-ItemStack interaction override | **Not possible** | `ItemStack` has no interaction override field |

### What Exists

- **`ItemStack.withMetadata()`** — can set BSON metadata per-stack, but no engine code reads metadata to modify interaction behavior
- **`Item.getInteraction(InteractionType)`** — returns the interaction ID for the item asset (shared across all stacks of that item)
- **No `ItemStack.setInteraction()`**, `ItemStack.setBlockType()`, or `InteractionContext.setBlockType()` API

### Programmatic Interaction Registration

You CAN register new interaction types and load new `RootInteraction`/`Interaction` assets at setup time:

```java
// Register new interaction type
this.getCodecRegistry(Interaction.CODEC)
    .register("PlaceBlock_Menu", PlaceBlockMenuInteraction.class, PlaceBlockMenuInteraction.CODEC);

// Load programmatic RootInteraction
AssetRegistry.getAssetStore(RootInteraction.class)
    .loadAssets("MyMod:MyMod", List.of(new RootInteraction("*MyRoot", "*MyInteraction")));
```

But this creates **static interaction definitions** — you cannot create per-player or per-item-instance interactions.

### Verdict

**Runtime interaction modification is not feasible.** You cannot dynamically set "this specific item stack should place Block_Oak_Planks instead of Block_Placeholder." The metadata + event handler approach (reading recipe from BSON, cancelling event, manually placing target block) remains the correct pattern.

---

## Q7: How Does the Engine Resolve PlaceBlock for State Items?

### Answer: The engine uses the **state variant's own `BlockType`**, not the parent's.

### Resolution Flow

When a player right-clicks holding `*Block_Placeholder_State_Armed_Green_3`:

1. **Interaction lookup**: `InteractionManager` resolves the item's `Secondary` interaction from `item.getInteractions().get(InteractionType.Secondary)`. Since this is the state variant's Item asset (separate registered entry), it uses the state variant's interaction map.

2. **Block type lookup**: Inside `PlaceBlockInteraction.tick0()`:
   ```java
   String interactionBlockTypeKey = this.blockTypeKey != null
       ? this.blockTypeKey
       : heldItemStack.getBlockKey();
   ```
   Since `blockTypeKey` is null (not set in the interaction JSON), it calls `heldItemStack.getBlockKey()`.

3. **`ItemStack.getBlockKey()`** → `item.getBlockId()`:
   ```java
   public String getBlockKey() {
       Item item = this.getItem();  // Returns the state variant Item
       return item.hasBlockType() ? item.getBlockId() : null;
   }
   ```
   `this.getItem()` returns the state variant's Item (e.g., `*Block_Placeholder_State_Armed_Green_3`), which has its own inline `BlockType`. The `blockId` is the state variant's block type ID.

4. **Result**: The engine tries to place the **state variant's BlockType** (the green placeholder cube), NOT the parent's BlockType (the blue cube), and NOT the armed recipe's target block.

### `stateToBlock` Is Not Used for Placement

The `stateToBlock` map (`Item.getItemIdForState()`) is a **registration mapping** (state name → child item ID), not a placement mapping. It has no role in PlaceBlock resolution. Per [state-placeblock-interaction-research.md](./state-placeblock-interaction-research.md), "`stateToBlock` does NOT create any interaction."

### Implication

The engine always places the placeholder block itself. To place the actual target block, the `PlaceBlockEvent` handler must:
1. Cancel the event
2. Read the target block type from ItemStack metadata
3. Manually call `WorldChunk.setBlock()` with the correct block type

This is your current architecture and is correct.

---

## Q8: Why Does `RemoveItemInHand: false` Work for Bucket's PlaceFluid but NOT for PlaceBlock?

### Answer: **The consumption mechanism is the same, but there may be a timing/ordering bug specific to PlaceBlock.**

### Consumption Flow Comparison

Both `PlaceBlockInteraction` and `PlaceFluidInteraction` gate consumption on `this.removeItemInHand`:

**PlaceFluidInteraction (bucket):**
```java
// PlaceFluidInteraction likely extends SimpleInteraction
// The fluid placement + item handling follows SimpleInteraction patterns
// RemoveItemInHand: false → fluid is placed, item stays, Next chain fires
```

**PlaceBlockInteraction:**
```java
// PlaceBlockInteraction.tick0() calls BlockPlaceUtils.placeBlock()
// Inside BlockPlaceUtils.placeBlock() (line ~107-113):
if (isAdventureMode && removeItemInHand) {
    ItemStackSlotTransaction transaction =
        itemContainer.removeItemStackFromSlot(activeSlot, itemStack, 1);
}

// After placeBlock() returns, in PlaceBlockInteraction.tick0() (line ~171):
if (isAdventure && heldItemStack.getQuantity() == 1 && this.removeItemInHand) {
    context.setHeldItem(null);
}
```

**Both phases are gated on `removeItemInHand`.** With `RemoveItemInHand: false`, neither runs.

### Why It Might Still Fail for PlaceBlock

Per [state-placeblock-interaction-research.md](./state-placeblock-interaction-research.md), three hypotheses remain:

#### Hypothesis A: Client-Side Predictive Consumption
The client receives `removeItemInHand` via `configurePacket()`. If the client's cached interaction data has the wrong value (e.g., from the UnarmedInteractions default), it may locally predict item consumption even though the server doesn't consume.

#### Hypothesis B: Consumption Before `BlockPlaceUtils.placeBlock()`
If another system or the interaction chain itself clears the held item before `placeBlock()` is called, the item is gone before the `removeItemInHand` check. This could happen if:
- The `InteractionManager` pre-processes the item
- A different interaction in the chain runs before PlaceBlock

#### Hypothesis C: Wrong Interaction Instance Executing
If the engine resolves the "Block" `UnarmedInteractions` default Secondary (which has `RemoveItemInHand: true`) instead of the state's explicit Secondary (which has `RemoveItemInHand: false`), consumption fires.

**The diagnostic checklist** from [state-placeblock-interaction-research.md](./state-placeblock-interaction-research.md#diagnostic-checklist) should be run:
1. Log `PlaceBlockInteraction.removeItemInHand` at execution time
2. Log hotbar slot contents before/after `BlockPlaceUtils.placeBlock()`
3. Verify the state item's `InteractionType.Secondary` points to the correct `RootInteraction`

### Key Structural Difference

The bucket's PlaceFluid succeeds because:
1. `PlaceFluidInteraction` extends `SimpleInteraction` → proper `Next` chaining is available
2. The `ModifyInventory` Next handles the state transition cleanly
3. There's no competing UnarmedInteractions default for PlaceFluid

The placeholder's PlaceBlock struggles because:
1. `PlaceBlockInteraction` extends `Interaction` directly → no `Next` chaining
2. The "Block" `PlayerAnimationsId` provides a competing default Secondary PlaceBlock with `RemoveItemInHand: true`
3. Although the explicit Secondary should override the default (per `putIfAbsent` logic), if the wrong instance executes at runtime, consumption occurs

---

## Summary: Feasibility Matrix

| Question | Feasible? | Notes |
|----------|-----------|-------|
| Q1: PlaceBlock `Next` chaining | **NO** | PlaceBlockInteraction extends Interaction directly, not SimpleInteraction. No Next/Failed fields. |
| Q2: Override placed block type | **Partial** | `BlockTypeToPlace` exists but is static per interaction definition. No per-instance override. |
| Q3: ModifyInventory state change | **NO (for our use)** | `BrokenItem` creates a fresh ItemStack, destroying metadata. Can't preserve recipe data. |
| Q4: ModifyInventory consume materials | **NO** | Only adjusts held item durability. No broader inventory access. |
| Q5: ConsumeMaterials interaction type | **NO** | No such built-in interaction type exists. Must use `ItemContainer.removeMaterials()` programmatically. |
| Q6: Runtime interaction modification | **NO** | No per-ItemStack interaction override API. Interactions are baked at asset load time. |
| Q7: State item block type resolution | **State's own BlockType** | Engine uses the state variant's BlockType, not the parent's. Correct for preview, but still places placeholder. |
| Q8: RemoveItemInHand discrepancy | **Unresolved** | Architecturally should work. If it doesn't, it's a runtime resolution bug requiring diagnostic logging. |

---

## Architectural Conclusion

**The bucket pattern cannot be directly adapted for PlaceBlock placement.** The fundamental blockers are:

1. **No `Next` chaining on PlaceBlock** — you can't chain PlaceBlock → ModifyInventory → state transformation
2. **No material consumption interaction** — there's no JSON-declarable way to consume recipe materials
3. **No per-instance interaction override** — you can't dynamically set the block type to place per item stack

**Your current architecture is the correct approach:**

```
┌─────────────────────────────────────────────────┐
│  Current System (CORRECT)                       │
│                                                 │
│  1. Item has PlaceBlock interaction              │
│     (RemoveItemInHand: false)                   │
│  2. PlaceBlockEvent fires                        │
│  3. PlaceBlockPlacementSystem:                   │
│     a. Cancels the event                        │
│     b. Reads target block from BSON metadata     │
│     c. Checks affordability via canRemoveMaterials│
│     d. Consumes materials via removeMaterials    │
│     e. Places target block via setBlock()        │
│     f. Restores placeholder in hand              │
│                                                 │
│  This is the ONLY viable approach given engine   │
│  constraints.                                    │
└─────────────────────────────────────────────────┘
```

The bucket pattern is elegant but only works because PlaceFluid inherits `Next` chaining from SimpleInteraction, and because the bucket only needs to transform itself (no external material consumption). Our use case requires both external material consumption AND dynamic block type selection, neither of which the interaction system supports declaratively.

---

## See Also

- [bucket-pattern.md](./bucket-pattern.md) — Full bucket lifecycle analysis
- [state-placeblock-interaction-research.md](./state-placeblock-interaction-research.md) — RemoveItemInHand resolution trace
- [placeblock-building-tool-api-research.md](./placeblock-building-tool-api-research.md) — PlaceBlock API capabilities
- [item-state-system.md](./item-state-system.md) — State variant system deep research
- [interaction-codec-registration-api.md](../../hytale/plugins/interaction-codec-registration-api.md) — Interaction class hierarchy
- [inventory-container-apis.md](../../hytale/assets/inventory-container-apis.md) — removeMaterials API
