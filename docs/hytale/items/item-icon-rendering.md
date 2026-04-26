---
topic: "Item Icon Rendering & Dynamic Updates"
category: "Items / Rendering"
updated: 2026-04-26
sources: ["decompiled Item.java", "decompiled ItemBase.java", "decompiled ItemPacketGenerator.java", "decompiled UpdateItems.java", "decompiled AssetSpecificFunctionality.java", "decompiled UpdateBlockTypes.java", "decompiled CommonAssetValidator.java"]
---

# Item Icon Rendering & Dynamic Updates

## Summary

Item icons in Hytale are **pre-rendered 3D previews** generated client-side from the item's model data, NOT static sprite images. For block-type items (items with a `BlockType`), the client renders the icon from the **block type's 3D model**. Icons are generated once and cached — they do not update live when block types change. Both `UpdateBlockTypes` AND `UpdateItems` (with `updateIcons=true`) are needed to fully reskin a block-type item per-player.

## How Item Icons Work

### Icon Generation Pipeline

```
Item JSON ─── "Icon" field ─────────── PNG path (e.g. "Icons/ItemsGenerated/{assetId}.png")
         ─── "IconProperties" field ── Camera settings (scale, rotation, translation)
         ─── "BlockType" field ──────── Block model reference (for block-type items)
         ─── "Model"/"Texture" fields ─ Item model (for non-block items)
                     │
                     ▼
        Client receives ItemBase via UpdateItems packet
                     │
                     ▼
        If updateIcons=true → Client regenerates icon cache
                     │
                     ├── Has blockId? → Render from BlockType's 3D model
                     └── No blockId?  → Render from Item's Model/Texture
                     │
                     ▼
        Generated icon stored at Icons/ItemsGenerated/{assetId}.png
```

### The `Icon` Field

From `Item.java` codec definition:
```java
.<String>appendInherited(
    new KeyedCodec<>("Icon", Codec.STRING),
    (item, s) -> item.icon = s,
    item -> item.icon,
    (item, parent) -> item.icon = parent.icon
)
.addValidator(CommonAssetValidator.ICON_ITEM)  // validates "png" in "Icons/ItemsGenerated" or "Icons/Items"
.metadata(new UIEditor(new UIEditor.Icon("Icons/ItemsGenerated/{assetId}.png", 64, 64)))
.metadata(new UIRebuildCaches(UIRebuildCaches.ClientCache.ITEM_ICONS))
```

- **Type**: String (PNG file path)
- **Validation**: Must be `.png` in `Icons/ItemsGenerated/` or `Icons/Items/`
- **Purpose**: Path to the client-generated or manually-provided icon image
- Source: `CommonAssetValidator.java:25` — `ICON_ITEM = new CommonAssetValidator("png", "Icons/ItemsGenerated", "Icons/Items")`

### The `IconProperties` Field

From `AssetIconProperties.java`:
```java
public class AssetIconProperties {
    float scale;           // How large the model is rendered
    Vector2f translation;  // X/Y offset of the camera
    Vector3f rotation;     // Camera rotation in degrees (converted to radians)
}
```

These are **camera settings** for rendering the 3D model into the icon image. Defaults vary by item type:
- **Weapons**: scale=0.37, translation=(-24.6, -24.6), rotation=(45°, 90°, 0°)
- **Tools**: scale=0.5, translation=(-17.4, -12.0), rotation=(45°, 270°, 0°)
- **Armor**: varies by slot (Head/Chest/Legs/Hands)
- **Default**: scale=0.58823, translation=(0, -13.5), rotation=(22.5°, 45°, 22.5°)

Source: `AssetSpecificFunctionality.java:461-479` — `getDefaultItemIconProperties()`

### Block-Type Items vs Non-Block Items

From `AssetSpecificFunctionality.getModelPreviewPacketForItem()`:

```java
if (item.getBlockId() != null) {
    BlockType blockType = ((BlockTypeAssetMap) BlockType.getAssetStore().getAssetMap()).getAsset(item.getBlockId());
    if (blockType != null) {
        camera.modelScale = camera.modelScale * blockType.getCustomModelScale();
        // Uses BlockType's model data — item's own model is IGNORED for the icon
        return new AssetEditorUpdateModelPreview(assetPath.toPacket(), null, blockType.toPacket(), camera);
    }
}
// Non-block items: uses item's own model + texture
Model modelPacket = convertToModelPacket(item);
return new AssetEditorUpdateModelPreview(assetPath.toPacket(), modelPacket, null, camera);
```

**Key insight**: For block-type items, the icon is rendered from `blockType.toPacket()` (the block's visual definition), NOT from the item's Model/Texture fields. The item's Model/Texture are used for the **hand-held in-world model**, not the inventory icon.

## The `UpdateItems` Packet

**Packet ID**: 54 (compressed)
**Class**: `com.hypixel.hytale.protocol.packets.assets.UpdateItems`

### Fields

| Field | Type | Purpose |
|-------|------|---------|
| `type` | `UpdateType` | Init, AddOrUpdate, or Remove |
| `items` | `Map<String, ItemBase>` | Item ID → full item data |
| `removedItems` | `String[]` | Items to remove |
| `updateModels` | `boolean` | Force client to rebuild item 3D models |
| `updateIcons` | `boolean` | **Force client to regenerate item icon cache** |

### ItemBase Protocol Fields (sent to client)

| Field | Type | Notes |
|-------|------|-------|
| `id` | `String` | Item identifier |
| `icon` | `String` | PNG path for the icon |
| `iconProperties` | `AssetIconProperties` | Camera settings for 3D→icon rendering |
| `blockId` | `int` | **BlockType index** (0 if no block type) |
| `model` | `String` | 3D model path (for hand-held rendering) |
| `texture` | `String` | Texture path (for hand-held rendering) |
| `scale` | `float` | Item scale |
| `categories` | `String[]` | Creative menu categories |
| `tool` | `ItemTool` | Tool properties |
| `weapon` | `ItemWeapon` | Weapon properties |
| `armor` | `ItemArmor` | Armor properties |
| `interactions` | `Map<InteractionType, Integer>` | Interaction bindings |
| `itemAppearanceConditions` | `Map<Integer, ItemAppearanceCondition[]>` | Conditional appearance changes |
| ... | ... | Many more fields |

### How to Send UpdateItems Per-Player

Following the same pattern as `UpdateBlockTypes` (see `TransparentBlockUtils.java`):

```java
UpdateItems update = new UpdateItems();
update.type = UpdateType.AddOrUpdate;
update.items = new Object2ObjectOpenHashMap<>();
update.items.put("MyItemId", modifiedItemBase);
update.updateModels = false;   // true if model changed
update.updateIcons = true;     // true to regenerate icon
playerRef.getPacketHandler().writeNoCache(update);
```

### ItemPacketGenerator (how the engine sends it)

From `ItemPacketGenerator.java`:
```java
public Packet generateUpdatePacket(DefaultAssetMap<String, Item> assetMap, Map<String, Item> loadedAssets, AssetUpdateQuery query) {
    UpdateItems packet = new UpdateItems();
    packet.type = UpdateType.AddOrUpdate;
    packet.items = new Object2ObjectOpenHashMap<>();
    for (Entry<String, Item> entry : loadedAssets.entrySet()) {
        packet.items.put(entry.getKey(), entry.getValue().toPacket());
    }
    AssetUpdateQuery.RebuildCache rebuildCache = query.getRebuildCache();
    packet.updateModels = rebuildCache.isBlockTextures() || rebuildCache.isModels();
    packet.updateIcons = rebuildCache.isItemIcons();
    return packet;
}
```

## Critical: `UpdateBlockTypes` Does NOT Auto-Update Item Icons

### The Problem

When you send `UpdateBlockTypes` to reskin a block (e.g., changing `Block_Placeholder_Green_0`'s textures), the client updates:
- ✅ The block's appearance in the world
- ✅ The block's 3D model when placed
- ❌ **The item's icon in the hotbar/inventory** (cached from previous generation)

### Why?

1. Item icons are **pre-rendered and cached** (stored in `Icons/ItemsGenerated/`)
2. The `updateIcons` flag on `UpdateItems` is what triggers icon regeneration
3. `UpdateBlockTypes` has no `updateIcons` flag — it has `updateBlockTextures`, `updateModelTextures`, `updateModels`, `updateMapGeometry`
4. The item icon is rendered from the `BlockType` data **at icon generation time**, not live

### The Solution

To fully reskin a block-type item per-player, you need to send BOTH packets:

1. **`UpdateBlockTypes`** — reskins the block's in-world appearance
2. **`UpdateItems`** with `updateIcons = true` — forces the client to re-render the item icon from the (now-updated) block type data

```java
// Step 1: Reskin the block type
UpdateBlockTypes blockUpdate = new UpdateBlockTypes();
blockUpdate.type = UpdateType.AddOrUpdate;
blockUpdate.maxId = BlockType.getAssetMap().getNextIndex();
blockUpdate.blockTypes = Map.of(blockTypeIndex, modifiedBlockPacket);
blockUpdate.updateBlockTextures = true;
playerRef.getPacketHandler().writeNoCache(blockUpdate);

// Step 2: Force item icon regeneration
UpdateItems itemUpdate = new UpdateItems();
itemUpdate.type = UpdateType.AddOrUpdate;
itemUpdate.items = Map.of("Item_Block_Placeholder_Green_0", existingItemBase);
itemUpdate.updateIcons = true;  // Critical!
itemUpdate.updateModels = false;
playerRef.getPacketHandler().writeNoCache(itemUpdate);
```

### Alternative: Change the `icon` Field Directly

You can also override the `icon` field in `ItemBase` to point to a different PNG:
```java
ItemBase modified = new ItemBase(originalItemBase);
modified.icon = "Icons/Items/MyCustomIcon.png";  // Must exist client-side
```
But this only works if the PNG already exists in the client's assets.

## `ItemAppearanceCondition` — Conditional Visual Changes

Items support **conditional appearance changes** based on runtime values (like durability):

```java
public class ItemAppearanceCondition {
    ModelParticle[] particles;
    ModelParticle[] firstPersonParticles;
    String model;                    // Override model
    String texture;                  // Override texture
    String modelVFXId;               // Visual effects
    FloatRange condition;            // When to activate (e.g., durability range)
    ValueType conditionValueType;    // Percent or absolute
    int localSoundEventId;
    int worldSoundEventId;
}
```

This changes the **hand-held 3D model**, NOT the inventory icon. It's driven by conditions like durability percentage, not server-side per-player control.

## Complete Item-Related Packets in Protocol

| Packet | ID | Purpose |
|--------|----|---------|
| `UpdateBlockTypes` | 40 | Block type visual data (textures, models, etc.) |
| `UpdateItems` | 54 | **Full item data including icon, blockId, model** |
| `UpdateItemQualities` | — | Item quality definitions |
| `UpdateItemReticles` | — | Item reticle/crosshair configs |
| `UpdateItemSoundSets` | 43 | Item sound set configs |

## Item.toPacket() — What Gets Sent to Client

From `Item.java:619`:
```java
public ItemBase toPacket() {
    ItemBase packet = new ItemBase();
    packet.id = this.id;
    if (this.icon != null) {
        packet.icon = this.icon;                    // PNG path
    }
    if (this.iconProperties != null) {
        packet.iconProperties = this.iconProperties.toPacket();  // Camera settings
    }
    if (this.model != null) {
        packet.model = this.model;                  // 3D model path
    }
    packet.scale = this.scale;
    if (this.texture != null) {
        packet.texture = this.texture;              // Texture path
    }
    // ...
    if (this.blockId != null) {
        packet.blockId = BlockType.getAssetMap().getIndexOrDefault(this.blockId, 1);
        // blockId is the NUMERIC INDEX, not the string ID
    }
    // ... many more fields
}
```

## Server/Client Boundary

| Aspect | Server Can Control? | How |
|--------|-------------------|-----|
| Block appearance in world | ✅ | `UpdateBlockTypes` per-player |
| Item icon in hotbar | ✅ | `UpdateItems` with `updateIcons=true` per-player |
| Item 3D hand model | ✅ | `UpdateItems` with modified model/texture |
| Icon PNG path override | ✅ | Set `icon` in ItemBase (PNG must exist client-side) |
| Custom icon from scratch | ❌ | Cannot inject new PNG textures to client |
| Item appearance conditions | ⚠️ | Defined at config time, driven by runtime values |

## Gotchas

1. **Icon caching**: Icons are cached client-side. Without `updateIcons=true`, the client shows the old icon even after the block type changes.
2. **Packet ordering**: Send `UpdateBlockTypes` BEFORE `UpdateItems` — the client needs the updated block type data to render the correct icon.
3. **blockId is numeric**: `ItemBase.blockId` is an `int` (the block type's index from `AssetMap`), not the string ID.
4. **Shared Item instances**: Like BlockGathering, Item objects may be shared. Clone before modifying for per-player sends.
5. **Icons/ItemsGenerated vs Icons/Items**: The former is client-auto-generated; the latter may be hand-crafted overrides. The `Icon` field can reference either directory.

## See Also

- [Block Types](../blocks/block-types.md)
- [Server-Client Boundary](../server-client-boundary.md)
