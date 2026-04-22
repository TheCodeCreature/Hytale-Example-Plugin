---
topic: "PlaceBlock Building Tool — Risk Investigation (R1, R2, R3)"
category: "Items / Blocks / Plugin API"
updated: 2026-04-22
sources: ["ItemStack.java (decompiled)", "Item.java (decompiled)", "BlockPlaceUtils.java (decompiled)", "BlockAccessor.java (decompiled)", "WorldChunk.java (decompiled)", "PlaceBlockInteraction.java (decompiled)", "SetBlockSettings.java (decompiled)", "IChunkAccessorSync.java (decompiled)", "ActionPlaceBlock.java (decompiled)", "CursedItems.java (decompiled)", "UseCaptureCrateInteraction.java (decompiled)", "AdventureMetadata.java (decompiled)", "CapturedNPCMetadata.java (decompiled)", "PlacementCostScaler.java (plugin)", "PlaceBlockPlacementSystem.java (plugin)"]
---

# PlaceBlock Building Tool — Risk Investigation

## Summary

This document resolves three critical engine risks for the PlaceBlock Building Tool design. Each risk was investigated against the decompiled Hytale server source and validated against existing codebase patterns.

| Risk | Question | Verdict |
|------|----------|---------|
| R1 | Can ItemStack carry custom data that persists across relog? | **CONFIRMED** |
| R2 | Can an ItemStack's icon change per-instance at runtime? | **DENIED** |
| R3 | Can PlaceBlockEvent handler override which block type is placed? | **CONFIRMED** |

---

## R1: Item State Persistence Across Relog

### Verdict: CONFIRMED

`ItemStack.metadata` (BsonDocument) persists across all scenarios including relog, server restart, item drops, and death. The `withMetadata()` API is immutable (returns a new `ItemStack`). Swapping an ItemStack to a different `itemId` while preserving metadata works correctly.

### Evidence

#### 1. `withMetadata()` is immutable — returns a new ItemStack

All `withMetadata()` overloads return a **new** `ItemStack` instance. The original is never mutated.

From `ItemStack.java` (decompiled):

```java
// Full BsonDocument replacement
@Nonnull
public ItemStack withMetadata(@Nullable BsonDocument metadata) {
    return new ItemStack(this.itemId, this.quantity, this.durability, this.maxDurability, metadata);
}

// Typed with KeyedCodec
@Nonnull
public <T> ItemStack withMetadata(@Nonnull KeyedCodec<T> keyedCodec, @Nullable T data) {
    return this.withMetadata(keyedCodec.getKey(), keyedCodec.getChildCodec(), data);
}

// Typed with key + codec — clones metadata, mutates clone, returns new stack
@Nonnull
public <T> ItemStack withMetadata(@Nonnull String key, @Nonnull Codec<T> codec, @Nullable T data) {
    BsonDocument clonedMeta = this.metadata == null ? new BsonDocument() : this.metadata.clone();
    if (data == null) {
        clonedMeta.remove(key);
    } else {
        BsonValue bsonValue = codec.encode(data);
        boolean empty = bsonValue.isNull() || bsonValue instanceof BsonDocument doc && doc.isEmpty();
        if (empty) { clonedMeta.remove(key); }
        else { clonedMeta.put(key, bsonValue); }
    }
    if (clonedMeta.isEmpty()) { clonedMeta = null; }
    return new ItemStack(this.itemId, this.quantity, this.durability, this.maxDurability, clonedMeta);
}

// Raw BsonValue with key
@Nonnull
public ItemStack withMetadata(@Nonnull String key, @Nullable BsonValue bsonValue) {
    BsonDocument clonedMeta = this.metadata == null ? new BsonDocument() : this.metadata.clone();
    if (bsonValue != null && !bsonValue.isNull()) {
        clonedMeta.put(key, bsonValue);
    } else {
        clonedMeta.remove(key);
    }
    return new ItemStack(this.itemId, this.quantity, this.durability, this.maxDurability, clonedMeta);
}
```

**Implication:** Every call to `withMetadata()` produces a new stack. The caller must replace the old stack in the container:

```java
ItemStack armed = unarmedStack.withMetadata("RecipeId", new BsonString(recipeId));
hotbar.setItemStackForSlot(slotIndex, armed);
```

#### 2. Metadata is serialized with the ItemStack CODEC

`ItemStack.CODEC` includes the `Metadata` field:

```java
.append(
    new KeyedCodec<>("Metadata", Codec.BSON_DOCUMENT),
    (itemStack, bsonDocument) -> itemStack.metadata = bsonDocument,
    itemStack -> itemStack.metadata
)
```

This means metadata is included when ItemStacks are:
- Saved to disk (chunk/player serialization uses the CODEC)
- Sent over the network (via `toPacket()` which includes `this.metadata.toJson()`)
- Loaded on server startup

#### 3. Swapping item ID preserves metadata

The `ItemStack` constructor accepts metadata as a parameter:

```java
public ItemStack(@Nonnull String itemId, int quantity, @Nullable BsonDocument metadata) { ... }
```

So swapping from `Block_Placeholder_Blue` to `Block_Placeholder_Green` while preserving the armed recipe:

```java
BsonDocument meta = oldStack.getMetadata(); // returns clone
ItemStack newStack = new ItemStack("Block_Placeholder_Green", 1, meta);
hotbar.setItemStackForSlot(slotIndex, newStack);
```

This is the same pattern `ItemStack.withState()` uses internally:

```java
public ItemStack withState(@Nonnull String state) {
    String newItemId = this.getItem().getItemIdForState(state);
    return new ItemStack(newItemId, this.quantity, this.durability, this.maxDurability, this.metadata);
}
```

#### 4. Engine usage examples confirm the pattern

**AdventureMetadata (curse system):**
```java
// CursedItems.java — uncurseAll
adventureMeta.setCursed(false);
return existing.withMetadata("Adventure", AdventureMetadata.CODEC, adventureMeta);

// CursedHeldItemCommand.java — add curse to held item
ItemStack edited = inHandItemStack.withMetadata(AdventureMetadata.KEYED_CODEC, adventureMeta);
```

**CapturedNPCMetadata (capture crate):**
```java
// UseCaptureCrateInteraction.java — store NPC data on item
ItemStack itemWithNPC = inHandItemStack.withMetadata(CapturedNPCMetadata.KEYED_CODEC, meta);
```

These items persist through inventory operations, drops, and server restarts — confirming the persistence mechanism.

#### 5. Reading metadata — typed API

```java
// Read with KeyedCodec
AdventureMetadata meta = stack.getFromMetadataOrNull(AdventureMetadata.KEYED_CODEC);

// Read with key + codec
AdventureMetadata meta = stack.getFromMetadataOrNull("Adventure", AdventureMetadata.CODEC);

// Read raw (deprecated but functional)
BsonDocument meta = stack.getMetadata(); // returns clone or null
```

#### 6. Metadata affects stacking

`ItemStack.isStackableWith()` checks metadata equality:

```java
public boolean isStackableWith(@Nullable ItemStack itemStack) {
    ...
    return this.metadata != null ? this.metadata.equals(itemStack.metadata) : itemStack.metadata == null;
}
```

**This is correct behavior** — an armed placeholder (with metadata) won't stack with an unarmed one (without metadata), preventing accidental merging.

#### 7. No documented size limits on BsonDocument

The BSON library's `BsonDocument` has no intrinsic size limit. The MongoDB wire protocol imposes a 16MB limit, but Hytale's serialization is not MongoDB — it uses `BsonUtil.writeToBytes()` / `readFromBytes()` directly. For a recipe ID string (typically 30-60 characters), size is not a concern.

### Recommended Approach

Define a `PlaceBlockMetadata` POJO with a `KeyedCodec`, mirroring the `AdventureMetadata` / `CapturedNPCMetadata` pattern:

```java
public class PlaceBlockData {
    public static final String KEY = "PlaceBlock";
    public static final BuilderCodec<PlaceBlockData> CODEC = BuilderCodec.builder(PlaceBlockData.class, PlaceBlockData::new)
        .appendInherited(
            new KeyedCodec<>("RecipeId", Codec.STRING),
            (d, s) -> d.recipeId = s, d -> d.recipeId,
            (d, p) -> d.recipeId = p.recipeId)
        .add()
        .appendInherited(
            new KeyedCodec<>("OutputBlockId", Codec.STRING),
            (d, s) -> d.outputBlockId = s, d -> d.outputBlockId,
            (d, p) -> d.outputBlockId = p.outputBlockId)
        .add()
        .build();
    public static final KeyedCodec<PlaceBlockData> KEYED_CODEC = new KeyedCodec<>(KEY, CODEC);

    private String recipeId;
    private String outputBlockId;

    // getters/setters
}
```

Usage:
```java
// Arm the placeholder
PlaceBlockData data = new PlaceBlockData();
data.setRecipeId("Bench_Builders_WoodPlanks_Oak_Wall");
data.setOutputBlockId("Wood_Oak_Planks");
ItemStack armed = unarmedStack.withMetadata(PlaceBlockData.KEYED_CODEC, data);
hotbar.setItemStackForSlot(slotIndex, armed);

// Read armed state
PlaceBlockData data = stack.getFromMetadataOrNull(PlaceBlockData.KEYED_CODEC);
if (data != null) {
    String recipeId = data.getRecipeId();
}

// Disarm
ItemStack disarmed = armedStack.withMetadata(PlaceBlockData.KEYED_CODEC, null);
```

---

## R2: Placeholder Icon Transformation

### Verdict: DENIED

The inventory icon **cannot** be changed per-instance. Icon, quality, display name, and block type preview are all asset-level properties of the `Item` class, not per-instance properties of `ItemStack`.

### Evidence

#### 1. Icon is an asset-level field

From `Item.java`:

```java
// Asset-level field
protected String icon;

// Set from JSON asset definition
.appendInherited(
    new KeyedCodec<>("Icon", Codec.STRING),
    (item, s) -> item.icon = s,
    item -> item.icon,
    (item, parent) -> item.icon = parent.icon
)

// Sent to client as part of ItemBase (asset definition packet)
public ItemBase toPacket() {
    ...
    if (this.icon != null) {
        packet.icon = this.icon;
    }
    ...
}
```

The client receives the icon path from the `ItemBase` packet (sent once per item type at join), NOT from the `ItemWithAllMetadata` packet (sent per stack instance).

#### 2. Per-instance data in ItemStack is limited

`ItemStack.toPacket()` sends only:

| Field | Per-Instance? |
|-------|--------------|
| `itemId` | Yes (but changes the item type) |
| `quantity` | Yes |
| `durability` | Yes |
| `maxDurability` | Yes |
| `overrideDroppedItemAnimation` | Yes |
| `metadata` | Yes (but client does not render it) |

None of these control the visual icon.

#### 3. No CustomModelData equivalent

Hytale has no `CustomModelData` field or similar mechanism. The `ItemAppearanceConditions` system exists but is driven by `EntityStat` values, not per-instance metadata:

```java
protected Map<String, ItemAppearanceCondition[]> itemAppearanceConditions;
```

This is asset-level and stat-driven — not suitable for per-instance icon changes.

#### 4. TranslationProperties is asset-level

```java
// Item.java
protected ItemTranslationProperties translationProperties;

// Set from JSON, sent in ItemBase packet
.appendInherited(
    new KeyedCodec<>("TranslationProperties", ItemTranslationProperties.CODEC),
    ...
)
```

The tooltip/display name cannot be changed per-instance. The client reads `TranslationProperties` from the `ItemBase` asset definition.

#### 5. CapturedNPCMetadata stores icon data — but for custom UI only

`CapturedNPCMetadata` stores `iconPath` and `fullItemIcon` in metadata, but these are used by server-side window rendering code (the Capture Crate UI), NOT by the standard inventory icon system. The server builds custom JSON payloads for specific windows that reference these metadata values. The standard inventory slot still shows the item asset's icon.

### What This Means for PlaceBlock

1. **The 3-variant approach (Blue/Green/Red) is the only way to change visual quality indicators.** Each variant is a separate `Item` asset with a different `Quality` field.

2. **The icon will always show the generic placeholder texture** regardless of which recipe is armed. There is no per-instance icon override.

3. **The tooltip will always show the placeholder item's name.** There is no way to dynamically show "Armed: Oak Planks" in the standard tooltip.

### Recommended UX Fallback

Since the icon and tooltip are fixed per-item-type, the fallback strategy is:

1. **Quality/color communicates status** (the 3 variants):
   - Blue (Tool quality) = unarmed
   - Green (Uncommon quality) = armed, resources sufficient
   - Red (Developer quality) = armed, resources insufficient

2. **Recipe name in chat/action bar** — When the player arms a recipe or switches, send a message via `playerRefComponent.sendMessage()` confirming which recipe is selected.

3. **Recipe name in selector window** — The `PlaceBlockSelectorWindow` shows the recipe list with icons. The last-selected recipe is visually highlighted.

4. **Block preview communicates the target** — The player sees the ghost block preview of the actual target block type during placement (via `PreviewBlockManager` or similar). This is the primary visual feedback for "what block will I place."

5. **Metadata stores the recipe ID for server logic** — The server reads `PlaceBlockData.recipeId` from metadata to determine behavior, even though the client can't render it visually.

### Alternative Considered and Rejected

**Swap to the actual target block item** (e.g., give the player `Wood_Oak_Planks` with PlaceBlock metadata):
- Pro: Client automatically shows the correct icon and block preview
- Con: The item's quality, interactions, and behavior are all from the `Wood_Oak_Planks` asset, not the placeholder
- Con: Would need to swap interactions to `PlaceBlock_Menu` via reflection (fragile)
- Con: Quality indicator (Blue/Green/Red) would be lost — `Wood_Oak_Planks` has its own quality
- **Verdict: Not viable.** The interaction system, quality, and behavior are all asset-level.

---

## R3: PlaceBlockEvent Block Type Override

### Verdict: CONFIRMED (via cancel + manual placement)

Cancelling the `PlaceBlockEvent` prevents both item consumption and block placement. We can then manually place the desired block type via `world.setBlock()` or `WorldChunk.placeBlock()`. The manually placed block is indistinguishable from one placed through normal flow.

### Evidence

#### 1. Cancellation prevents item consumption

From `BlockPlaceUtils.placeBlock()`:

```java
PlaceBlockEvent event = new PlaceBlockEvent(itemStack, blockPosition, targetRotation);
entityStore.invoke(ref, event);
if (event.isCancelled()) {
    targetBlockSection.invalidateBlock(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
    // Returns here — NO item consumption, NO block placement
} else {
    // Item removal happens HERE, after the event check:
    if (isAdventureMode && removeItemInHand) {
        ItemStackSlotTransaction transaction = itemContainer.removeItemStackFromSlot(activeSlot, itemStack, 1);
        ...
    }
    // Block placement happens after item removal
    ...
}
```

**Key finding:** Item consumption occurs AFTER the event dispatch. Cancelling the event means:
- ✅ No item consumed from the active slot
- ✅ No block placed in the world
- ✅ The client's ghost block is cleared via `invalidateBlock()`
- ✅ The placeholder stays in the player's hotbar

#### 2. `world.setBlock()` API — multiple overloads

The `World` class implements `IChunkAccessorSync` which provides:

```java
// Simplest — place by block type key string
default void setBlock(int x, int y, int z, String blockTypeKey)

// With settings flags
default void setBlock(int x, int y, int z, String blockTypeKey, int settings)
```

`WorldChunk` (implements `BlockAccessor`) provides richer placement:

```java
// Full control — with block ID, rotation, filler, and settings
boolean setBlock(int x, int y, int z, int id, BlockType blockType, int rotation, int filler, int settings)

// Place with validation, rotation support, and filler handling
default boolean placeBlock(int x, int y, int z, String blockTypeKey,
    Rotation yaw, Rotation pitch, Rotation roll, int settings)

// Lower-level placeBlock with RotationTuple
default boolean placeBlock(int x, int y, int z, String blockTypeKey,
    RotationTuple rotationTuple, int settings, boolean validatePlacement)
```

#### 3. `placeBlock()` handles rotation and validation

From `BlockAccessor.java`:

```java
default boolean placeBlock(int x, int y, int z, String originalBlockTypeKey,
        RotationTuple rotationTuple, int settings, boolean validatePlacement) {
    BlockTypeAssetMap<String, BlockType> assetMap = BlockType.getAssetMap();
    BlockType placedBlockType = assetMap.getAsset(originalBlockTypeKey);
    int rotationIndex = rotationTuple.index();
    if (validatePlacement && !this.testPlaceBlock(x, y, z, placedBlockType, rotationIndex)) {
        return false;
    }
    int setBlockSettings = 0;
    if ((settings & 2) != 0) {
        setBlockSettings |= 256; // PERFORM_BLOCK_UPDATE
    }
    this.setBlock(x, y, z, assetMap.getIndex(originalBlockTypeKey),
        placedBlockType, rotationIndex, 0, setBlockSettings);
    return true;
}
```

This validates placement position, handles filler blocks for multi-block structures, and applies rotation — everything the normal placement flow does.

#### 4. Manually placed blocks get proper block state

`WorldChunk.setBlock()` (line 326+) automatically:
- Updates block section (ID, rotation, filler)
- Creates `BlockState` if the block type defines one (e.g., `ItemContainerState` for chests)
- Handles filler blocks for multi-tile hitboxes
- Updates lighting
- Sets block physics (support, deco)
- Sends build/break particles to clients

The only things it does NOT do (that `BlockPlaceUtils.tryPlaceBlock` adds):
- `PlacedByBlockState` — marks who placed the block (for creative mode tracking)
- `BlockPhysics.markDeco()` — marks the block as player-placed deco
- Item metadata-to-BlockState transfer (the `onPlaceBlockSuccess` logic)

For PlaceBlock tool, none of these missing steps are problematic — the target block is a standard block, not a chest or deco.

#### 5. SetBlockSettings flags

```java
public class SetBlockSettings {
    public static final int NONE = 0;
    public static final int NO_NOTIFY = 1;
    public static final int NO_UPDATE_STATE = 2;
    public static final int NO_SEND_PARTICLES = 4;
    public static final int NO_SET_FILLER = 8;
    public static final int NO_BREAK_FILLER = 16;
    public static final int PHYSICS = 32;
    public static final int FORCE_CHANGED = 64;
    public static final int NO_UPDATE_NEIGHBOR_CONNECTIONS = 128;
    public static final int PERFORM_BLOCK_UPDATE = 256;
    public static final int NO_UPDATE_HEIGHTMAP = 512;
    public static final int NO_SEND_AUDIO = 1024;
    public static final int NO_DROP_ITEMS = 2048;
}
```

The normal `BlockPlaceUtils.tryPlaceBlock` uses settings `10` (= `NO_UPDATE_STATE | NO_SET_FILLER`). Our manual placement should use the same.

#### 6. NPC ActionPlaceBlock demonstrates the pattern

NPCs place blocks without any event or item consumption:

```java
// ActionPlaceBlock.execute()
WorldChunk chunk = world.getNonTickingChunk(
    ChunkUtil.indexChunkFromBlock(this.target.getX(), this.target.getZ()));
chunk.setBlock(
    MathUtil.floor(this.target.getX()),
    MathUtil.floor(this.target.getY()),
    MathUtil.floor(this.target.getZ()),
    role.getWorldSupport().getBlockToPlace()
);
```

This confirms that direct `setBlock()` is a supported, stable pattern used by the engine itself.

#### 7. PlacementCostScaler is naturally mutually exclusive

`PlacementCostScaler.handle()` early-exits for non-natural blocks:

```java
String blockTypeId = itemInHand.getBlockKey();
if (blockTypeId == null) return;                              // Placeholder has no block key → exit
if (!NaturalResourceRegistry.isNaturalBlock(blockTypeId)) return;  // Even if it has one, it's not natural → exit
```

The placeholder item's `getBlockKey()` returns either `null` (if the placeholder has no `BlockType` definition) or a non-natural block key. Either way, `PlacementCostScaler` skips it — no explicit mutual exclusion logic needed (though Contract #12 may still want a guard for safety).

#### 8. Accessing World from the event handler

Within `PlaceBlockPlacementSystem.handle()`, the `World` is accessible via:

```java
World world = store.getExternalData().getWorld();
```

But `setBlock()` must run on the world thread. Since PlaceBlockEvent handlers run on the entity store's event dispatch thread, we need to ensure we're on the correct thread. The `World` class is also a `ChunkAccessor`, so:

```java
// Option A: If already on world thread (which ECS event systems typically are)
WorldChunk chunk = world.getNonTickingChunk(ChunkUtil.indexChunkFromBlock(x, z));
chunk.placeBlock(x, y, z, outputBlockTypeKey,
    event.getRotation(), SetBlockSettings.NONE, false);

// Option B: Deferred execution if thread safety is a concern
world.execute(() -> {
    WorldChunk chunk = world.getNonTickingChunk(ChunkUtil.indexChunkFromBlock(x, z));
    chunk.placeBlock(x, y, z, outputBlockTypeKey,
        event.getRotation(), SetBlockSettings.NONE, false);
});
```

### Recommended Approach

```java
@Override
public void handle(int index,
                   @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                   @Nonnull Store<EntityStore> store,
                   @Nonnull CommandBuffer<EntityStore> commandBuffer,
                   @Nonnull PlaceBlockEvent event) {

    ItemStack itemInHand = event.getItemInHand();
    if (itemInHand == null) return;
    if (!PlaceBlockMetadata.isPlaceBlock(itemInHand)) return;
    if (!PlaceBlockMetadata.isArmed(itemInHand)) {
        event.setCancelled(true);
        return;
    }

    // 1. Read armed recipe from metadata
    PlaceBlockData data = itemInHand.getFromMetadataOrNull(PlaceBlockData.KEYED_CODEC);
    if (data == null) { event.setCancelled(true); return; }

    String outputBlockTypeKey = data.getOutputBlockId();
    if (outputBlockTypeKey == null) { event.setCancelled(true); return; }

    // 2. Get player and world
    Player player = archetypeChunk.getComponent(index, Player.getComponentType());
    if (player == null) { event.setCancelled(true); return; }
    World world = store.getExternalData().getWorld();

    // 3. Scan resources and attempt atomic consumption
    // ResourceSnapshot snapshot = ResourceScanner.scanAvailableResources(player, world, hR, vR);
    // CraftingRecipe recipe = lookupRecipe(data.getRecipeId());
    // if (!ResourceScanner.consumeAtomically(snapshot, recipe.getInput())) {
    //     event.setCancelled(true);
    //     return;
    // }

    // 4. Cancel the vanilla placement (prevents placeholder consumption)
    event.setCancelled(true);

    // 5. Manually place the actual target block with rotation
    Vector3i pos = event.getTargetBlock();
    RotationTuple rotation = event.getRotation();

    world.execute(() -> {
        WorldChunk chunk = world.getNonTickingChunk(
            ChunkUtil.indexChunkFromBlock(pos.getX(), pos.getZ()));
        chunk.placeBlock(
            pos.getX(), pos.getY(), pos.getZ(),
            outputBlockTypeKey,
            rotation,
            10,    // Same settings as BlockPlaceUtils.tryPlaceBlock
            true   // Validate placement
        );
    });
}
```

### Key Detail: `invalidateBlock` After Cancel

When the event is cancelled, `BlockPlaceUtils.placeBlock()` calls `invalidateBlock()` on the original position. This tells the client to reset its local block prediction. Our manual `placeBlock` (in `world.execute()`) will then set the correct block, which gets synced to the client via the chunk notification system. There may be a brief flicker — if this is noticeable, we can skip the deferred execution and place directly if we're already on the world thread.

---

## Cross-Risk Integration

The three risks interact as follows:

```
R1 (metadata) ──→ Armed recipe ID persists on ItemStack
                    ↓
R2 (icon) ────→ Item swap (Blue→Green→Red) preserves metadata via R1
                 Icon stays as generic placeholder
                 Color/quality communicates status
                    ↓
R3 (placement) → Event handler reads recipe ID from metadata (R1)
                  Cancels event → placeholder NOT consumed
                  Manually places actual target block
                  Placeholder stays in hotbar for next placement
```

The system works end-to-end:
1. Player selects recipe → metadata written via `withMetadata()` (R1) → item swapped to Green variant (R2)
2. Player right-clicks → handler reads metadata (R1) → cancels event → places real block (R3)
3. Placeholder stays in hand → indicator updates (R2) → ready for next placement
