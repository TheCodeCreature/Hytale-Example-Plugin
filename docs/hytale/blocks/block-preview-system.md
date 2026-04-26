---
topic: "Block Preview / Ghost Block System"
category: "Blocks"
updated: 2026-04-25
sources:
  - "PlaceBlockInteraction (server config): .tmp_hytale_src/com/hypixel/hytale/server/core/modules/interaction/interaction/config/client/PlaceBlockInteraction.java"
  - "PlaceBlockInteraction (protocol): .tmp_hytale_src/com/hypixel/hytale/protocol/PlaceBlockInteraction.java"
  - "BlockPlaceUtils: .tmp_hytale_src/com/hypixel/hytale/server/core/modules/interaction/BlockPlaceUtils.java"
  - "BlockPlacementSettings (config): .tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/blocktype/config/BlockPlacementSettings.java"
  - "BlockPlacementSettings (protocol): .tmp_hytale_src/com/hypixel/hytale/protocol/BlockPlacementSettings.java"
  - "BlockPreviewVisibility: .tmp_hytale_src/com/hypixel/hytale/protocol/BlockPreviewVisibility.java"
  - "BlockType.toPacket(): .tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/blocktype/config/BlockType.java"
  - "Item.getBlockId()/toPacket(): .tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/Item.java"
  - "ItemStack.getBlockKey(): .tmp_hytale_src/com/hypixel/hytale/server/core/inventory/ItemStack.java"
  - "GamePacketHandler: .tmp_hytale_src/com/hypixel/hytale/server/core/io/handlers/game/GamePacketHandler.java"
  - "ServerSetBlock: .tmp_hytale_src/com/hypixel/hytale/protocol/packets/world/ServerSetBlock.java"
  - "UpdateBlockTypes: .tmp_hytale_src/com/hypixel/hytale/protocol/packets/assets/UpdateBlockTypes.java"
  - "PlaceBlockEvent: .tmp_hytale_src/com/hypixel/hytale/server/core/event/events/ecs/PlaceBlockEvent.java"
  - "InteractionSyncData: .tmp_hytale_src/com/hypixel/hytale/protocol/InteractionSyncData.java"
  - "PreviewBlockManager (plugin): src/main/java/com/UnobstructedThirdPerson/preview/PreviewBlockManager.java"
  - "PreviewBlockSubCommand (plugin): src/main/java/com/UnobstructedThirdPerson/command/debug/SubCommands/PreviewBlockSubCommand.java"
---

# Block Preview / Ghost Block System

## Summary

The Hytale engine has a **client-driven block preview system** for normal block placement. When a player holds an item that has a `BlockType` definition (i.e., `Item.hasBlockType == true`), the client renders a semi-transparent "ghost" of that block at the aimed position. This preview is controlled by three factors:

1. **The item's `blockId`** — sent to the client via `Item.toPacket()`, read from the `"BlockType"` section of the item JSON
2. **The block type's `BlockPlacementSettings.BlockPreviewVisibility`** — can be `AlwaysVisible`, `AlwaysHidden`, or `Default`
3. **The client's local rendering** — the preview is rendered entirely client-side; the server never sends preview-specific data

The server has **no native API** to change what block type the client previews. However, there are several viable approaches using existing packet infrastructure.

---

## 1. How the Native Block Preview Works

### Client-Side Preview Rendering

The block preview is entirely client-side. The flow is:

1. **Item → Block type resolution**: When the client holds an item, it reads `item.blockId` (a numeric block type index) from the item's packet data (`ItemBase.blockId`). This is set by `Item.toPacket()`:
   ```java
   // Item.java line 657-662
   if (this.blockId != null) {
       packet.blockId = BlockType.getAssetMap().getIndexOrDefault(this.blockId, 1);
   }
   ```

2. **Block key on ItemStack**: On the server side, `ItemStack.getBlockKey()` resolves the block type:
   ```java
   // ItemStack.java line 146-155
   public String getBlockKey() {
       if (this.isEmpty()) return "Empty";
       Item item = this.getItem();
       if (item == null) return null;
       return item.hasBlockType() ? item.getBlockId() : null;
   }
   ```

3. **Client renders preview**: The client uses the `blockId` from the held item to render a ghost block at the player's aim position. The preview respects `BlockPreviewVisibility` from the block type's `PlacementSettings`.

4. **BlockPreviewVisibility enum** controls whether the preview shows:
   ```java
   // BlockPreviewVisibility.java
   public enum BlockPreviewVisibility {
       AlwaysVisible(0),   // Always show ghost
       AlwaysHidden(1),    // Never show ghost
       Default(2);         // Show based on default rules
   }
   ```

5. **BlockPlacementSettings** (sent in the `BlockType` packet):
   ```java
   // protocol/BlockPlacementSettings.java fields:
   public boolean allowRotationKey;
   public boolean placeInEmptyBlocks;
   public BlockPreviewVisibility previewVisibility;      // Controls ghost visibility
   public BlockPlacementRotationMode rotationMode;
   public int wallPlacementOverrideBlockId;              // Override block when placed on wall
   public int floorPlacementOverrideBlockId;             // Override block when placed on floor
   public int ceilingPlacementOverrideBlockId;           // Override block when placed on ceiling
   ```

### Key Insight: The preview block type is the item's `blockId`

The client previews whatever block type corresponds to `Item.blockId`. For the placeholder items, this is the placeholder block itself (e.g., `Block_Placeholder_Blue`). The client has no knowledge of the "armed recipe" stored in server-side BSON metadata.

### Server-Side Placement Flow

When the player clicks to place, the server receives the block placement packet and processes it in `GamePacketHandler`:

```java
// GamePacketHandler.java line 553-568
String heldBlockKey = itemInHand.getBlockKey();  // Gets Item.blockId
if (heldBlockKey == null) {
    section.invalidateBlock(...);
} else {
    if (packet.placedBlockId != -1) {
        // Client may send a different block (e.g., wall/floor/ceiling override)
        String clientPlacedBlockTypeKey = BlockType.getAssetMap().getAsset(packet.placedBlockId).getId();
        BlockType heldBlockType = BlockType.getAssetMap().getAsset(heldBlockKey);
        if (heldBlockType != null && BlockPlaceUtils.canPlaceBlock(heldBlockType, clientPlacedBlockTypeKey)) {
            heldBlockKey = clientPlacedBlockTypeKey;
        }
    }
    BlockPlaceUtils.placeBlock(ref, itemInHand, heldBlockKey, ...);
}
```

The `canPlaceBlock` check only allows the client's `placedBlockId` if it matches the held block OR is one of the `PlacementSettings` override block IDs (wall/floor/ceiling). **This is a validation gate — the client cannot arbitrarily place any block type.**

---

## 2. Can We Override the Preview Block Type?

### The Core Problem

Our `Block_Placeholder_Blue.json` defines an inline `BlockType`:
```json
{
    "BlockType": {
        "Material": "Solid",
        "DrawType": "Cube",
        "Opacity": "Transparent",
        "Textures": [{ "All": "BlockTextures/Editor_Empty.png", "Weight": 1 }],
        "Tint": ["#4488cc"]
    }
}
```

The client previews this placeholder block — a transparent blue cube. When armed with "Oak_Planks", we want the preview to show Oak_Planks instead.

### Why Native Override Won't Work Directly

1. **`Item.blockId` is baked at asset load time** — `Item.toPacket()` caches the packet (`cachedPacket`) and sends `blockId` as the numeric index of whatever `BlockType` is in the item JSON. There is no per-stack override for `blockId`.

2. **`ItemStack.getBlockKey()`** delegates to `Item.getBlockId()`, which returns the Item-level `blockId` field. It does **not** check item metadata (BSON). There is no mechanism for metadata to override the block key.

3. **`InteractionSyncData.placedBlockId`** — the client sends this when placing, and it can differ from the held block (for wall/floor/ceiling overrides), but the server validates it against `BlockPlaceUtils.canPlaceBlock()`.

4. **`PlaceBlockInteraction.blockTypeKey`** — an interaction-level override that can specify a different block type key via `"BlockTypeToPlace"` in JSON config. But this is static per interaction definition, not dynamic per item stack.

---

## 3. Viable Approaches for Armed Placeholder Preview

### Approach A: Server-Controlled Ghost via `ServerSetBlock` (Recommended)

**This is already implemented** in `PreviewBlockManager`. The server sends `ServerSetBlock` packets to show a ghost block at the player's aim position, replacing the client's view of whatever block is there.

**How it works:**
```java
// PreviewBlockManager.java
private void sendBlockUpdate(Vector3i position, int blockId, short filler, byte rotation) {
    playerRef.getPacketHandler().writeNoCache(new ServerSetBlock(
        position.x, position.y, position.z,
        blockId, filler, rotation
    ));
}
```

**Integration with PlaceBlock tool:**
1. When the player holds an armed placeholder, a tick system tracks the player's aim position
2. Each tick, call `PreviewBlockManager.addPreviewByName(aimPosition, "Oak_Planks")` to show the target block at the aim
3. When the aim moves, `removePreview(oldPos)` and `addPreview(newPos, blockId)` — or use `updatePreview()`
4. When the player places, `PlaceBlockPlacementSystem` handles the substitution (already working)
5. When the player unequips or disarms, `clearAll()` restores everything

**Limitations:**
- The server must track aim position → requires per-tick raycast from the server using `TargetUtil.getTargetBlock()`
- The preview is "real" to the client — it sees a placed block, not a translucent ghost
- Latency between aim movement and ghost update (server tick rate)
- The client's built-in ghost (from the placeholder's `BlockType`) will still render alongside the server's ghost

**Mitigations for the dual-ghost problem:**
- Set `BlockPreviewVisibility: AlwaysHidden` on the placeholder's `BlockType` to suppress the client's native preview
- This requires adding `PlacementSettings` to the placeholder block type config

### Approach B: `UpdateBlockTypes` Packet — Dynamic Block Type Reskinning

**Already prototyped** in `PreviewBlockSubCommand`. Send an `UpdateBlockTypes` packet to the specific player that changes the placeholder block type's visual appearance to match the armed block.

**How it works:**
1. When arming, clone the target block's packet data (`targetBlockType.toPacket()`)
2. Send `UpdateBlockTypes` with the placeholder's numeric ID mapped to the cloned packet
3. The client now thinks the placeholder block type *looks like* the target block
4. The native preview system will show the target block's appearance

**Code pattern (from PreviewBlockSubCommand):**
```java
UpdateBlockTypes update = new UpdateBlockTypes();
update.type = UpdateType.AddOrUpdate;
update.maxId = BlockType.getAssetMap().getNextIndex();
Map<Integer, com.hypixel.hytale.protocol.BlockType> blockTypes = new HashMap<>();
blockTypes.put(placeholderNumericId, targetBlockPacket);  // Reskin placeholder
update.blockTypes = blockTypes;
update.updateBlockTextures = true;
update.updateModelTextures = true;
update.updateModels = true;
update.updateMapGeometry = true;
playerRef.getPacketHandler().writeNoCache(update);
```

**Advantages:**
- Uses the engine's native preview rendering (translucent ghost)
- No per-tick server raycast needed
- No latency — client handles preview position natively
- Clean visual — it's the actual engine ghost block system

**Limitations:**
- Changes the block type globally for that player — all instances of the placeholder block type will look like the target
- Since we have 3 placeholder variants (Blue/Green/Red), we'd need to use the armed color (Green) exclusively for preview
- When disarming, must send `UpdateBlockTypes` again to restore the original appearance
- Any existing placeholder blocks in the world will also change appearance for that player
- `updateMapGeometry = true` may cause a brief visual flicker

### Approach C: Hybrid — `UpdateBlockTypes` + Preview Visibility Control

Combine Approach B with proper `PlacementSettings` to get the cleanest result:

1. **Item config change**: Add `PlacementSettings` with `BlockPreviewVisibility: Default` to the placeholder's `BlockType` section
2. **On arm**: Send `UpdateBlockTypes` to reskin the armed placeholder (Green) to look like the target block
3. **On disarm**: Send `UpdateBlockTypes` to restore the original placeholder appearance
4. **On place**: `PlaceBlockPlacementSystem` handles substitution (already working)

### Approach D: Dynamic Item Replacement (Not Recommended)

Create a new `ItemStack` with a different `itemId` when arming — e.g., create a temporary item whose `Item.blockId` points to "Oak_Planks". 

**Why not:**
- `Item.blockId` is set at asset load time and cached
- You'd need to register new `Item` assets at runtime for every possible recipe output
- The client needs to receive the new item definition via packets
- Overly complex for marginal benefit

---

## 4. PlaceBlockEvent and Block Type Substitution

The preview system and the placement system are **completely independent**:

| Aspect | Preview | Placement |
|--------|---------|-----------|
| Where | Client-side rendering | Server-side ECS event |
| Block type source | `Item.blockId` | `PlaceBlockEvent` → `PlaceBlockPlacementSystem` |
| Timing | Continuous (every frame) | On click |
| Can override? | Only via packet tricks | Yes, via `event.setCancelled(true)` + manual setBlock |

**Our current `PlaceBlockPlacementSystem`** already handles placement correctly:
1. Intercepts `PlaceBlockEvent`
2. Cancels it (prevents placeholder from being placed)
3. Reads `TargetBlockId` from metadata
4. Manually places the target block via `WorldChunk.setBlock()`

The client's native preview does **not** affect what block gets placed. The server always decides. So even if the preview shows the wrong block, the placed block will be correct.

---

## 5. Recommended Approach for S2604221125

### Primary: Approach C — `UpdateBlockTypes` Reskinning + Preview Visibility

This gives the most polished user experience:

```
┌─────────────────────────────────────────────────────┐
│ Player arms placeholder with "Oak_Planks"           │
│                                                     │
│  1. Store recipe in BSON metadata (already done)    │
│  2. Send UpdateBlockTypes: reskin Green placeholder  │
│     to look like Oak_Planks (textures, model, tint) │
│  3. Client's native preview now shows Oak_Planks    │
│     ghost wherever the player aims                  │
│                                                     │
│ Player places block:                                │
│  4. PlaceBlockPlacementSystem cancels event         │
│  5. Places actual Oak_Planks block                  │
│                                                     │
│ Player disarms:                                     │
│  6. Send UpdateBlockTypes: restore Green placeholder │
│     to original appearance                          │
└─────────────────────────────────────────────────────┘
```

### Fallback: Approach A — `ServerSetBlock` Ghost

If `UpdateBlockTypes` proves unreliable (flicker, timing issues):

```
┌─────────────────────────────────────────────────────┐
│ Player holds armed placeholder:                     │
│                                                     │
│  1. Suppress native preview (AlwaysHidden)          │
│  2. Per-tick: raycast to find aim position           │
│  3. Send ServerSetBlock with target block type       │
│  4. Track and restore blocks as aim moves            │
│                                                     │
│ This is the PreviewBlockManager approach already    │
│ in the codebase.                                    │
└─────────────────────────────────────────────────────┘
```

### Required Config Change for Either Approach

The placeholder items need `PlacementSettings` on their `BlockType` to control preview visibility. Add to the block type JSON:

```json
{
    "BlockType": {
        "Material": "Solid",
        "DrawType": "Cube",
        "Opacity": "Transparent",
        "Textures": [{ "All": "BlockTextures/Editor_Empty.png", "Weight": 1 }],
        "Tint": ["#44cc66"],
        "PlacementSettings": {
            "BlockPreviewVisibility": "AlwaysHidden"
        }
    }
}
```

Use `AlwaysHidden` for Approach A (server-controlled ghost replaces native preview).
Use `Default` or `AlwaysVisible` for Approach C (let native preview render the reskinned block).

---

## 6. Risks and Gotchas

| Risk | Severity | Mitigation |
|------|----------|------------|
| `UpdateBlockTypes` reskins ALL instances of that block type for the player | Medium | Use Green placeholder exclusively for armed state; Blue for unarmed |
| `UpdateBlockTypes` may cause brief flicker during reskin | Low | Test with real client; may need to send in specific order |
| Server `ServerSetBlock` ghost is opaque, not translucent | Medium | Could use `UpdateBlockTypes` to add `requiresAlphaBlending` to the preview block type |
| Per-tick raycast for Approach A has CPU cost | Low | Only runs when player holds armed tool |
| `PlacementSettings` JSON key may not be recognized in inline `BlockType` | Low | Test; the codec is registered on `BlockType.CODEC` so should work for contained assets |
| `Item.toPacket()` caches results | None | Cache is a `SoftReference`, cleared by GC; or call during `LoadAssetEvent` before cache is created |
| Metadata (`TargetBlockId`) is server-only; client never reads it | None | By design — we control the preview via packets, not metadata |

---

## See Also

- [Block Types](./block-types.md)
- [Server-Client Boundary](../server-client-boundary.md)
- [PreviewBlockManager](../../../src/main/java/com/UnobstructedThirdPerson/preview/PreviewBlockManager.java)
- [PlaceBlockPlacementSystem](../../../src/main/java/com/UnobstructedThirdPerson/placeblock/PlaceBlockPlacementSystem.java)
- [PreviewBlockSubCommand](../../../src/main/java/com/UnobstructedThirdPerson/command/debug/SubCommands/PreviewBlockSubCommand.java)
