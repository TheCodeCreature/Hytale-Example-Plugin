---
topic: "PlaceBlock Building Tool — API Research & Capabilities"
category: "Items / Blocks / Crafting / Plugin API"
updated: 2026-04-22
sources: ["PlaceBlockEvent.java", "PlaceBlockInteraction.java", "ItemStack.java", "Item.java", "ItemQuality.java", "ItemContainerState.java", "BenchWindow.java", "CraftingManager.java", "BlockStateModule.java", "StructuralCraftingWindow.java", "SortType.java", "BlockPlaceUtils.java", "PreviewBlockManager.java (plugin code)", "PlacementCostScaler.java (plugin code)"]
---

# PlaceBlock Building Tool — API Research & Capabilities

## Summary

This document covers the Hytale engine APIs and capabilities relevant to implementing a PlaceBlock Building Tool — a feature where a player selects a recipe at a Builders Bench, arms a placeholder item, previews block placement, and right-clicks to place the block (consuming resources from inventory/nearby chests).

---

## 1. Item Quality / Rarity System

### How It Works

Quality (rarity) in Hytale is a **two-layer system**:

1. **`ItemQuality` asset type** — Defined as JSON assets under `Common/Items/Qualities/`. Each quality asset defines visual properties (tooltip textures, slot textures, text colors) and a `QualityValue` integer for sorting.

2. **`Item.qualityId` field** — Each item definition references a quality by string ID in its JSON `"Quality"` field. At load time, this is resolved to a `qualityIndex` integer via `ItemQuality.getAssetMap().getIndexOrDefault()`.

### ItemQuality Asset Properties

| Field | Type | Description |
|-------|------|-------------|
| `QualityValue` | int | Numeric rank for sorting (0 = lowest) |
| `ItemTooltipTexture` | String | Tooltip background texture path |
| `ItemTooltipArrowTexture` | String | Tooltip arrow texture path |
| `SlotTexture` | String | Inventory slot background texture |
| `BlockSlotTexture` | String | Slot texture for block items |
| `SpecialSlotTexture` | String | Slot for consumables/usables when `RenderSpecialSlot=true` |
| `TextColor` | Color | Text color in inventory |
| `LocalizationKey` | String | Display name of the quality |
| `VisibleQualityLabel` | boolean | Whether to show quality name in tooltip |
| `RenderSpecialSlot` | boolean | Whether to use special slot texture |
| `HideFromSearch` | boolean | Hide from creative library |
| `ItemEntityConfig` | Object | Dropped item entity configuration |

### Known Quality IDs

Based on the codebase references and Contract #13:

| Quality ID | Purpose in PlaceBlock | Visual |
|------------|----------------------|--------|
| `Tool` | Unarmed placeholder (no recipe selected) | Blue highlight |
| `Uncommon` | Armed, resources sufficient | Green highlight |
| `Developer` | Armed, resources insufficient | Red highlight |
| `Default` | Fallback quality (qualityValue = -1) | Default gray |

The full list of available quality IDs is defined in asset files (not in code). The ones above are confirmed usable.

### Can Quality Be Changed at Runtime on an Item Instance?

**No — not on the same ItemStack instance. The item must be replaced.**

The `Quality` field is a property of the **`Item` asset definition** (the type), not of the `ItemStack` (the instance). An `ItemStack` has:
- `itemId` (references the Item asset)
- `quantity`
- `durability` / `maxDurability`
- `metadata` (BsonDocument — per-instance data)

The `qualityId` / `qualityIndex` lives on the `Item` class. All stacks of the same item share the same quality.

**Workaround Options:**

1. **Create multiple item assets** — Define `Block_Placeholder_Blue`, `Block_Placeholder_Green`, `Block_Placeholder_Red` with different `Quality` fields. Swap the ItemStack in the player's hotbar when state changes.

2. **Swap the ItemStack at runtime** — Create a new `ItemStack` with the appropriate item ID and transfer the metadata:
   ```java
   ItemStack newStack = new ItemStack(newItemId, oldStack.getQuantity(), oldStack.getMetadata());
   hotbar.setItemStack(slotIndex, newStack);
   ```

3. **Mutate the Item asset's qualityId via reflection** — DANGEROUS. Would affect ALL instances of that item for ALL players. Not viable for per-player state.

**Recommendation:** Option 1 (multiple item assets) is the correct approach. The ItemStack swap preserves metadata and is a standard pattern used by the engine (see `ItemStack.withState()` which does exactly this for tool state changes).

---

## 2. Block Preview System

### How the Engine's Built-In Preview Works

The `PlaceBlockInteraction` class handles block placement. The block preview (ghost block) is **entirely client-side**:

1. The client determines which block the held item would place by reading the item's `BlockType` definition
2. The client renders a transparent preview at the targeted position
3. On click, the client sends a `PlaceBlock` interaction with `clientState.blockPosition`, `clientState.blockFace`, and `clientState.placedBlockId`
4. The server validates and places the block

The server has access to `clientState.placedBlockId` — the block type ID the client believes it's placing. The server uses this for validation:

```java
String interactionBlockTypeKey = this.blockTypeKey != null ? this.blockTypeKey : heldItemStack.getBlockKey();
```

### What Determines Which Block to Preview

The client determines the preview block from:
1. The `PlaceBlockInteraction`'s `BlockTypeToPlace` field (if set in the interaction JSON)
2. Otherwise, the held item's `getBlockKey()` — which reads the item's `BlockType` definition

### Can the Preview Be Overridden Per-Item?

**Yes, via the item's BlockType definition or via PlaceBlockInteraction.**

If the placeholder item has a `BlockType` definition, the client will preview that block type. To preview the *armed recipe's* output block instead, options are:

1. **Swap the placeholder item** to an item whose `BlockType` matches the target block — this automatically changes what the client previews. However, this means the placeholder must become a different item.

2. **Use `PlaceBlockInteraction` with `BlockTypeToPlace` override** — But this is set at interaction definition time, not per-item-instance. You'd need a different interaction per target block, which isn't practical.

3. **Server-side preview via fake packets** — The existing `PreviewBlockManager` in this plugin uses `ServerSetBlock` packets to show ghost blocks to individual players. This is server-controlled but requires tracking player aim position server-side.

### Limitation

**There is no API to tell the client "preview block type X for this specific item instance."** The client derives the preview from the item's asset definition. The only way to change it per-instance is to swap the item to one whose `BlockType` matches.

**This is a significant constraint.** If the placeholder can be armed with different target block types, each would either need:
- A dedicated placeholder item asset per target block type (impractical for hundreds of block types)
- The server-side `PreviewBlockManager` approach (viable but adds complexity and latency)
- The item swap to use the actual target block item (e.g., give the player `Wood_Oak_Planks` but with metadata marking it as a PlaceBlock tool — the preview would be correct, but the item quality and behavior must be handled separately)

---

## 3. Right-Click / Use Interaction for Tools

### How Right-Click Works

Item interactions are defined in JSON:

```json
"Interactions": {
  "Use": {
    "Interactions": [{ "Type": "PlaceBlock" }]
  }
}
```

The `InteractionType` enum includes:
- `Primary` — Left-click
- `Secondary` — Right-click / Use
- `Alternate` — Q key
- `Offhand` — Offhand action

For block items, the default `Secondary` interaction is `PlaceBlock`, which triggers the `PlaceBlockInteraction` class.

### PlaceBlockInteraction Details

The built-in `PlaceBlockInteraction` (type `"PlaceBlock"`):
1. Waits for client sync data (`WaitForDataFrom.Client`)
2. Validates placement range (≤ 6 blocks in Adventure mode)
3. Calls `BlockPlaceUtils.placeBlock()` which:
   - Fires `PlaceBlockEvent` (cancellable)
   - If not cancelled, places the block and consumes 1 item from held container

Key fields on `PlaceBlockInteraction`:
| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `BlockTypeToPlace` | String | null | Overrides the placed block type |
| `RemoveItemInHand` | boolean | true | Whether to consume the held item |
| `AllowDragPlacement` | boolean | true | Allow click-drag to place multiple |

### Custom Interactions

Plugins can register custom interaction types via:
```java
this.getCodecRegistry(Interaction.CODEC)
    .register("PlaceBlock_Menu", PlaceBlockMenuInteraction.class, PlaceBlockMenuInteraction.CODEC);
```

This means the placeholder item's JSON can reference `"Type": "PlaceBlock_Menu"` for its secondary interaction, routing right-clicks to custom plugin logic.

---

## 4. PlaceBlockEvent Details

### Event Class

```java
public class PlaceBlockEvent extends CancellableEcsEvent {
    @Nullable private final ItemStack itemInHand;
    @Nonnull  private Vector3i targetBlock;       // Mutable via setter
    @Nonnull  private RotationTuple rotation;     // Mutable via setter
}
```

### Available Fields

| Method | Returns | Description |
|--------|---------|-------------|
| `getItemInHand()` | `ItemStack?` | The item being placed (before consumption) |
| `getTargetBlock()` | `Vector3i` | World position where block will be placed |
| `setTargetBlock(Vector3i)` | void | **Can change the placement position** |
| `getRotation()` | `RotationTuple` | Block rotation |
| `setRotation(RotationTuple)` | void | **Can change the block rotation** |
| `cancel()` | void | **Cancels the placement entirely** |
| `isCancelled()` | boolean | Check if another handler cancelled it |

### Execution Order

1. Engine creates `PlaceBlockEvent` — item NOT yet consumed
2. All registered `EntityEventSystem<EntityStore, PlaceBlockEvent>` handlers run
3. If not cancelled: engine consumes 1 item from active slot, places block in world
4. `PlaceBlockEvent` does NOT carry the block type being placed — you must derive it from `itemInHand.getBlockKey()`

### Can We Change the Block Type That Gets Placed?

**Not directly via PlaceBlockEvent.** The event has no `setBlockType()` method. The block type is derived from the held item's `getBlockKey()`.

**Workaround:** If your `PlaceBlockEvent` handler needs to place a different block type:
1. Cancel the original event
2. Manually place the desired block using `world.setBlock()` or similar
3. Manually consume resources

This is the approach the PlaceBlock tool will need: cancel the vanilla PlaceBlockEvent, then place the armed recipe's target block type directly.

---

## 5. Chest Block Entity APIs

### ItemContainerState — How Chests Work

Chests in Hytale are blocks with an `ItemContainerState` (extends `BlockState`):

```java
public class ItemContainerState extends BlockState 
    implements ItemContainerBlockState, DestroyableBlockState, MarkerBlockState {
    
    protected SimpleItemContainer itemContainer;  // The chest's inventory
    protected boolean custom;
    protected boolean allowViewing;
}
```

The chest's inventory is a `SimpleItemContainer` with configurable capacity (default 20 slots, can be set per-block-type via `ItemContainerStateData.capacity`).

### Can Plugins Read Chest Inventories?

**Yes.** Via `ItemContainerState.getItemContainer()`:

```java
BlockState state = world.getState(x, y, z, true);
if (state instanceof ItemContainerState chestState) {
    ItemContainer chestInventory = chestState.getItemContainer();
    chestInventory.forEach((slot, itemStack) -> {
        // Read chest contents
    });
}
```

### Scanning for Chests Within a Radius

**Yes — the engine has a spatial index for ItemContainerStates.**

`BlockStateModule` registers a spatial resource (`KDTree`) for `ItemContainerState` blocks. The `CraftingManager.getContainersAroundBench()` method demonstrates the exact pattern:

```java
// Get the spatial structure
SpatialResource<Ref<ChunkStore>, ChunkStore> spatialStructure = 
    store.getResource(BlockStateModule.get().getItemContainerSpatialResourceType());

// Query nearby container blocks
ObjectList<Ref<ChunkStore>> results = SpatialResource.getThreadLocalReferenceList();
spatialStructure.getSpatialStructure()
    .ordered3DAxis(blockPos, horizontalRadius, verticalRadius, horizontalRadius, results);
```

The search returns chunk-level references. Each result must be resolved to an `ItemContainerState` to access its `ItemContainer`.

### How BenchWindow Uses Nearby Chests

`CraftingManager.feedExtraResourcesSection()` demonstrates the full flow:
1. Call `getContainersAroundBench(benchState)` to find nearby `ItemContainerState` blocks
2. Extract `ItemContainer` from each chest state
3. Wrap in `DelegateItemContainer` with `ALLOW_OUTPUT_ONLY` filter (read-only access)
4. Combine all containers via `CombinedItemContainer`
5. Enumerate materials for the `ExtraResources` packet section

**This same pattern can be reused** for the PlaceBlock tool's resource scanning, either from a bench position or from the player's current position.

### Limitation: No Simple "Get All Blocks of Type X in Radius" API

There is no generic `world.getBlocksOfType(blockTypeId, center, radius)` method. The spatial index only covers `ItemContainerState` blocks. For other block types, you'd need to iterate chunk sections manually. For chests specifically, the spatial index is sufficient.

---

## 6. Item Instance Customization (Per-Instance Data)

### BsonDocument Metadata — Hytale's "NBT Equivalent"

`ItemStack` has a `metadata` field of type `BsonDocument` (from MongoDB's BSON library). This provides **per-instance custom data**:

```java
// Create stack with metadata
BsonDocument meta = new BsonDocument();
meta.put("RecipeId", new BsonString("Wood_Oak_Planks"));
meta.put("TargetBlockId", new BsonString("Wood_Oak_Planks"));
ItemStack armed = new ItemStack("Block_Placeholder_Green", 1, meta);

// Read metadata
BsonDocument meta = stack.getMetadata(); // Returns a clone (safe)

// Create new stack with updated metadata (ItemStack is effectively immutable)
ItemStack updated = stack.withMetadata("RecipeId", new BsonString("Rock_Stone"));
```

### Typed Metadata API

`ItemStack` provides typed metadata methods:

```java
// Using KeyedCodec for typed access
ItemStack updated = stack.withMetadata(keyedCodec, data);
ItemStack updated = stack.withMetadata("key", codec, data);
ItemStack updated = stack.withMetadata("key", bsonValue);
```

### What Can Be Customized Per-Instance?

| Property | Per-Instance? | Mechanism |
|----------|--------------|-----------|
| Metadata (custom key-value data) | **Yes** | `BsonDocument metadata` field |
| Quantity | **Yes** | `withQuantity(int)` |
| Durability | **Yes** | `withDurability(double)` |
| Max Durability | **Yes** | `withMaxDurability(double)` |
| Item ID | **No** (changes the item type) | Must create new ItemStack |
| Quality/Rarity | **No** (asset-level) | Must swap to different item ID |
| Icon | **No** (asset-level) | Cannot change per-instance |
| Display Name | **No** (asset-level localization) | Cannot change per-instance |
| Block Type | **No** (asset-level) | Cannot change per-instance |
| Interactions | **No** (asset-level) | Cannot change per-instance |

### Metadata Persistence

Metadata survives:
- Inventory movements (slot changes, chest storage)
- Server restarts (persisted with chunk/player data via BSON serialization)
- Item drops and pickups
- Death and respawn (item drops with metadata intact)

### Engine Usage Examples

The engine uses metadata extensively:
- `AdventureMetadata` — Cursed item properties
- `CapturedNPCMetadata` — NPC capture crate data  
- `StartObjectiveInteraction` — Quest item data

**Metadata is the correct mechanism for storing the armed recipe on a placeholder.**

### getMetadata() is @Deprecated

Note: `getMetadata()` is marked `@Deprecated` in the decompiled source. It returns a clone. The typed `withMetadata()` methods are the preferred API. However, the method still works and is used throughout the engine.

---

## 7. Bench / Crafting Window APIs

### Can a Plugin Intercept the Builders Bench UI?

**Yes — but with constraints.** From the crafting window architecture analysis:

1. **Custom Window class** — A plugin can create a `Window` subclass that uses `WindowType.StructuralCrafting` and implements `ItemContainerWindow` + `MaterialContainerWindow`. The `PortableStructuralWindow` in this codebase already does this successfully.

2. **Interaction-based opening** — A custom interaction can open the window when the player uses the bench item:
   ```java
   playerComponent.getPageManager().setPageWithWindows(
       ref, store, Page.Bench, true, customWindow);
   ```

3. **`handleAction()` override** — The custom window class can override `handleAction()` to intercept all `WindowAction` types (crafting, slot selection, category changes).

### Can We Prevent Input Item Consumption?

**Yes.** The `StructuralCraftingWindow` pattern uses an input container with a filter. The `CraftItemAction` handler controls what happens when the player crafts. In a custom window, you fully control:
- Whether items are consumed from the input slot
- What the output is
- Whether the item container is modified at all

The key is: **you own the `handleAction()` method.** Nothing happens unless your code does it.

### Can We Customize Output Slot Behavior?

**Yes.** The options container in `StructuralCraftingWindow` is set to `DENY_ALL` filter (read-only for the client). The server populates it programmatically. When the player clicks an option slot:
1. Client sends `SelectSlotAction` or `CraftItemAction`
2. Your `handleAction()` processes it however you want

The existing `PlaceBlockSelectorWindow` referenced in the codebase is already designed for this — it's a structural crafting window that populates options with recipe outputs and arms the placeholder instead of crafting.

### WindowAction Limitations

**Only 9 fixed action types exist** (0–8). No custom actions. The available ones for a StructuralCrafting window:
- `SelectSlotAction(2)` — player clicks an option slot
- `CraftItemAction(5)` — player confirms crafting
- `ChangeBlockAction(3)` — player cycles block variant
- `UpdateCategoryAction(6)` — player switches category tab

### Adding a "Recipe Browsing" Mode

Two approaches:
1. **Category-based** — Populate the window's categories JSON with recipe categories. The client renders category tabs natively. Player clicks tabs to browse.
2. **Input-based** — Like the vanilla structural bench: player puts an item in the input slot, matching recipes appear in the options. The PlaceBlockSelectorWindow could show all recipes for the placeholder's resource type.

---

## 8. Inventory Change Events

### ItemContainer.registerChangeEvent()

The primary inventory monitoring hook:

```java
EventRegistration reg = container.registerChangeEvent(
    EventPriority.LAST, 
    event -> { /* handle change */ }
);
```

Available overloads:
- `registerChangeEvent(Consumer<ItemContainerChangeEvent>)` — default priority
- `registerChangeEvent(EventPriority, Consumer<ItemContainerChangeEvent>)` — with priority
- `registerChangeEvent(short priority, Consumer<ItemContainerChangeEvent>)` — raw priority

### Other Inventory Monitoring Hooks

| Hook | Scope | When It Fires |
|------|-------|---------------|
| `ItemContainer.registerChangeEvent()` | Specific container | Any item add/remove/modify in that container |
| `SwitchActiveSlotEvent` (ECS) | Any player | Player changes active hotbar slot |
| `InteractivelyPickupItemEvent` (ECS) | Any player | Player picks up a world item |
| `DropItemEvent` (ECS) | Any player | Player drops an item |
| `CraftRecipeEvent.Post` (ECS) | Any player | Player completes crafting |
| `PlaceBlockEvent` (ECS) | Any player | Player places a block (item consumed after) |
| `BreakBlockEvent` (ECS) | Any player | Player breaks a block (items may be added) |

### Monitoring Player Inventory for Resource Changes

For the PlaceBlock tool's quality state machine (green/red indicator), you need to detect when the player's available resources change. The recommended approach:

1. Register `ItemContainer.registerChangeEvent()` on the player's combined inventory when they equip a PlaceBlock tool
2. Listen for `SwitchActiveSlotEvent` to detect when the player equips/unequips the tool
3. On each inventory change, re-evaluate resource availability and update the quality indicator

### Monitoring Nearby Chest Changes

**This is harder.** `ItemContainerState` blocks have their own `ItemContainer` instances. To monitor chest changes:
- You'd need to discover nearby chests (via spatial index) and register change events on each
- Re-register when chests are added/removed from the radius
- This adds significant complexity and may not be necessary if you only check at placement time

---

## Critical Limitations Summary

These limitations will affect system design:

| # | Limitation | Impact | Workaround |
|---|-----------|--------|------------|
| 1 | **Quality is asset-level, not per-instance** | Cannot change a single ItemStack's rarity color | Define multiple item assets (Blue/Green/Red) and swap ItemStacks |
| 2 | **Block preview is client-derived from item's BlockType** | Cannot show preview for a different block than what the item defines | Swap the entire item to match target block, OR use server-side fake blocks |
| 3 | **PlaceBlockEvent has no setBlockType()** | Cannot change what block gets placed via the event | Cancel event + manually place the correct block type |
| 4 | **No per-instance icon override** | Cannot change the placeholder's icon to show the target block | Must swap to an item whose icon matches, or accept a generic icon |
| 5 | **No custom WindowAction types** | Cannot add custom buttons to bench UI | Use existing actions (category tabs, slot selection) creatively |
| 6 | **No generic "blocks of type X in radius" API** | Can only spatially query ItemContainerState blocks specifically | Sufficient for chests; other block types need manual iteration |
| 7 | **9 fixed WindowAction types** | Limited interaction vocabulary with bench UI | Repurpose category tabs or slot actions for custom behavior |
| 8 | **ItemStack.getMetadata() is @Deprecated** | May be removed in future versions | Use typed `withMetadata()` methods instead |

## Capabilities That DO Exist

| Capability | Confirmed Source |
|-----------|-----------------|
| Per-instance metadata via BsonDocument | `ItemStack.metadata`, used throughout engine |
| Cancelling PlaceBlockEvent | `CancellableEcsEvent.cancel()` |
| Custom interaction types for items | `Interaction.CODEC` registration |
| Custom window classes with full action control | `PortableStructuralWindow` in this codebase |
| Spatial chest discovery | `CraftingManager.getContainersAroundBench()` |
| Reading chest inventories | `ItemContainerState.getItemContainer()` |
| Inventory change monitoring | `ItemContainer.registerChangeEvent()` |
| Swapping items in player inventory | `ItemContainer.setItemStack()` |
| Server-side fake block packets | `ServerSetBlock` via `PacketHandler.send()` |
| Manual block placement | `world.setBlock()` or `BlockPlaceUtils.placeBlock()` |
| Item state transitions | `ItemStack.withState()` pattern for tool state changes |

## See Also

- [PlaceBlock Building Tool Contract](../../product/contracts/placeblock-building-tool.md)
- [Crafting Window Architecture](../crafting/crafting-window-architecture.md)
- [Window Actions & Updates](../plugins/window-actions-and-updates.md)
- [Item Stacks & Containers](./stacks-and-containers.md)
- [Runtime Asset Mutation](../assets/runtime-mutation.md)
- [Plugin Events](../plugins/events.md)
- [Portable Bench Feasibility](../crafting/portable-bench-feasibility.md)
