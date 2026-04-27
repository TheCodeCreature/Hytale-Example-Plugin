---
topic: "BlockPlacementSettings & Ghost Block Rotation"
category: "Blocks"
updated: 2026-04-26
sources: ["decompiled BlockPlacementSettings (server config)", "decompiled BlockPlacementSettings (protocol)", "decompiled BlockPlacementRotationMode (protocol)", "decompiled BlockType.toPacket()", "BlockPreviewReskinManager.java"]
---

# BlockPlacementSettings & Ghost Block Rotation

## Summary

`BlockPlacementSettings` controls how the client renders the ghost block preview
(rotation, visibility) and how placement overrides work (wall/floor/ceiling).
The **`RotationMode`** field is the key to understanding why R-key rotation
snaps back after one tick when using reskinned block types.

## Root Cause of R-Key Snap-Back

**The `UpdateBlockTypes` packet sent by `BlockPreviewReskinManager.reskinVariant()`
clones the target block type's full packet, including its `PlacementSettings`.**

When the target block (e.g., furniture like a shelf) has `RotationMode: FACING_PLAYER`
in its definition, the reskin packet delivers `BlockPlacementRotationMode.FacingPlayer`
to the client. The client's ghost block preview system then **auto-computes rotation
based on player facing direction every tick**, overriding any R-key rotation input
after a single frame.

### Evidence Chain

1. `BlockPreviewReskinManager.reskinVariant()` calls:
   ```java
   com.hypixel.hytale.protocol.BlockType targetPacket =
       new com.hypixel.hytale.protocol.BlockType(targetType.toPacket());
   ```

2. `BlockType.toPacket()` (server config → protocol) includes:
   ```java
   if (this.placementSettings != null) {
       packet.placementSettings = this.placementSettings.toPacket();
   }
   ```

3. The protocol `BlockType` copy constructor copies `placementSettings` by reference:
   ```java
   this.placementSettings = other.placementSettings;
   ```

4. The target block's `RotationMode` is faithfully transmitted to the client,
   overriding whatever the Green variant originally had.

## PlacementSettings Fields (Complete Reference)

### Server-Side Config Class
`com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockPlacementSettings`

| JSON Key | Type | Default | Description |
|----------|------|---------|-------------|
| `AllowRotationKey` | `boolean` | `true` | Whether R-key rotation is enabled for this block |
| `PlaceInEmptyBlocks` | `boolean` | `false` | If this block can be placed inside blocks with Empty material |
| `RotationMode` | `RotationMode` enum | `DEFAULT` | How rotation is computed during placement preview |
| `BlockPreviewVisibility` | `BlockPreviewVisibility` enum | `DEFAULT` | Override for ghost block visibility |
| `WallPlacementOverrideBlockId` | `String` | `null` | Override block type when placed on a wall face |
| `FloorPlacementOverrideBlockId` | `String` | `null` | Override block type when placed on a floor face |
| `CeilingPlacementOverrideBlockId` | `String` | `null` | Override block type when placed on a ceiling face |

### Protocol Packet Class
`com.hypixel.hytale.protocol.BlockPlacementSettings`

Fixed 16-byte structure:
- Byte 0: `allowRotationKey` (boolean)
- Byte 1: `placeInEmptyBlocks` (boolean)
- Byte 2: `previewVisibility` (BlockPreviewVisibility enum ordinal)
- Byte 3: `rotationMode` (BlockPlacementRotationMode enum ordinal)
- Bytes 4-7: `wallPlacementOverrideBlockId` (int, block type index)
- Bytes 8-11: `floorPlacementOverrideBlockId` (int, block type index)
- Bytes 12-15: `ceilingPlacementOverrideBlockId` (int, block type index)

**CRITICAL**: The protocol packet's default constructor sets
`rotationMode = BlockPlacementRotationMode.FacingPlayer` (ordinal 0).
This means if you construct a raw packet without explicitly setting rotationMode,
it defaults to auto-facing — NOT the same as the server config default (DEFAULT).

## RotationMode Enum

### Server-Side (JSON Config)
`BlockPlacementSettings.RotationMode`:

| Value | Description |
|-------|-------------|
| `DEFAULT` | R-key manual rotation. Client shows ghost block, player cycles rotation with R. **This is what you want.** |
| `FACING_PLAYER` | Auto-rotates ghost to face the player every tick. R-key press is overridden immediately. **This causes the snap-back.** |
| `STAIR_FACING_PLAYER` | Like FacingPlayer but with stair-specific rotation logic (also considers pitch for up/down stairs) |
| `BLOCK_NORMAL` | Rotation determined by the face normal of the clicked block surface |

### Protocol (Wire Format)
`BlockPlacementRotationMode`:

| Name | Ordinal | Maps From Server |
|------|---------|-----------------|
| `FacingPlayer` | 0 | `FACING_PLAYER` |
| `StairFacingPlayer` | 1 | `STAIR_FACING_PLAYER` |
| `BlockNormal` | 2 | `BLOCK_NORMAL` |
| `Default` | 3 | `DEFAULT` |

### Server → Protocol Mapping (from toPacket() bytecode)

| Server RotationMode | Protocol BlockPlacementRotationMode |
|---------------------|-------------------------------------|
| `DEFAULT` | `Default` (3) |
| `FACING_PLAYER` | `FacingPlayer` (0) |
| `STAIR_FACING_PLAYER` | `StairFacingPlayer` (1) |
| `BLOCK_NORMAL` | `BlockNormal` (2) |
| `null` (not set) | `Default` (3) — null-safe fallback |

## BlockPreviewVisibility Enum

### Server-Side (JSON Config)
`BlockPlacementSettings.BlockPreviewVisibility`:

| Value | Description |
|-------|-------------|
| `DEFAULT` | Standard behavior: shows ghost when holding a placeable block |
| `ALWAYS_VISIBLE` | Ghost block always visible |
| `ALWAYS_HIDDEN` | Ghost block never visible |

### Protocol
`BlockPreviewVisibility`:

| Name | Ordinal |
|------|---------|
| `AlwaysVisible` | 0 |
| `AlwaysHidden` | 1 |
| `Default` | 2 |

### Mapping (from toPacket() bytecode)

| Server | Protocol |
|--------|----------|
| `DEFAULT` | `Default` (2) |
| `ALWAYS_HIDDEN` | `AlwaysHidden` (1) |
| `ALWAYS_VISIBLE` | `AlwaysVisible` (0) |
| `null` | `Default` (2) |

## RandomRotation (BlockType-level, NOT PlacementSettings)

`RandomRotation` is a separate field on `BlockType` itself (not in PlacementSettings).
It controls cosmetic random rotation applied when blocks are placed naturally (e.g., by world gen).

| Value | Description |
|-------|-------------|
| `None` | No random rotation (default) |
| `YawPitchRollStep1` | Random rotation on all axes |
| `YawStep1` | Random yaw rotation only |
| `YawStep1XZ` | Random yaw rotation on XZ plane |
| `YawStep90` | Random 90° yaw steps |

This does NOT affect the ghost block preview rotation system.

## The Fix for Snap-Back

### Option A: Preserve Original Variant's PlacementSettings (Recommended)

In `BlockPreviewReskinManager.reskinVariant()`, after cloning the target's packet,
**override `placementSettings` with the original Green variant's settings**:

```java
com.hypixel.hytale.protocol.BlockType targetPacket =
    new com.hypixel.hytale.protocol.BlockType(targetType.toPacket());

// Preserve original variant's PlacementSettings to keep RotationMode: Default
// and AllowRotationKey: true, preventing FacingPlayer auto-rotation
if (originalVariantPackets[variantIndex].placementSettings != null) {
    targetPacket.placementSettings = originalVariantPackets[variantIndex].placementSettings.clone();
}
```

This ensures:
- `RotationMode` stays `Default` (R-key manual rotation works)
- `AllowRotationKey` stays `true`
- `BlockPreviewVisibility` stays `Default` (as configured on the Green variant)
- Target block's visual appearance (textures, model, etc.) is still cloned correctly

### Option B: Explicitly Set RotationMode on the Packet

```java
if (targetPacket.placementSettings == null) {
    targetPacket.placementSettings = new com.hypixel.hytale.protocol.BlockPlacementSettings();
}
targetPacket.placementSettings.rotationMode = BlockPlacementRotationMode.Default;
targetPacket.placementSettings.allowRotationKey = true;
targetPacket.placementSettings.previewVisibility = BlockPreviewVisibility.Default;
```

### Why Option A is Better

Option A preserves ALL original variant settings holistically, including any future
fields added to PlacementSettings. Option B is fragile and requires updating if new
fields are added.

## Current Green Variant Configuration

From `Block_Placeholder.json`, each Armed_Green_N state has:
```json
"PlacementSettings": {
    "BlockPreviewVisibility": "Default"
}
```

Note: `RotationMode` is NOT explicitly set in the JSON, so it defaults to `DEFAULT`
(from the server-side field default: `BlockPlacementSettings.RotationMode.DEFAULT`).

When `toPacket()` converts this, it correctly produces `BlockPlacementRotationMode.Default` (ordinal 3).
The original variant packets captured by `captureOriginalPackets()` therefore have
`rotationMode = Default`, which is exactly what we want to preserve.

## See Also
- [BlockType reference](./block-types.md)
- [Server-Client Boundary](../server-client-boundary.md)
