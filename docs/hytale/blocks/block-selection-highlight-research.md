---
topic: "Block Selection Highlight / Selector Outline"
category: "Blocks / Visual Feedback"
updated: 2026-05-08
sources: ["InteractionConfiguration.java (decompiled)", "PreviewBlockManager.java (plugin)", "PreviewBlockSubCommand.java (plugin)", "PreviewCommand.java (plugin)", "PlaceholderTransparencyUtil.java (plugin)", "Placeholder_Full.json (plugin asset)", "PlaceBlockInteraction.java (decompiled)", "builder-tool-research.md (local docs)", "server-client-boundary.md (local docs)"]
---

# Block Selection Highlight Research

## Question

Can we show a visual "selector" outline or highlight on the block a player is aiming at while holding the Stencil Book?

## Summary

There is **no built-in block selection highlight API** that a server plugin can invoke. The engine's native block outline (`DisplayOutlines`) is controlled per-interaction-config and is a **client-side rendering feature** — the server cannot customize its appearance (color, thickness, scale). However, there are **four viable server-side workarounds**, each with distinct trade-offs.

---

## Finding 1: `DisplayOutlines` — The Built-in Block Outline

### What It Is

Every `InteractionConfiguration` has a `displayOutlines` boolean field (default `true`). When the client runs an interaction with `displayOutlines=true`, the engine renders a wireframe outline around the targeted block.

**Source:** [InteractionConfiguration.java](<../../.tmp_hytale_src/com/hypixel/hytale/server/core/modules/interaction/interaction/config/InteractionConfiguration.java>) (decompiled)

```java
protected boolean displayOutlines = true;
// ...
public InteractionConfiguration(boolean displayOutlines) {
    this.displayOutlines = displayOutlines;
}
```

The field is serialized in the protocol packet:
```java
packet.displayOutlines = this.displayOutlines;
```

### How to Use It

In your item JSON, add an `InteractionConfig` block:

```json
{
  "InteractionConfig": {
    "DisplayOutlines": true,
    "UseDistance": { "Adventure": 8 }
  }
}
```

### Limitations

| Aspect | Status |
|--------|--------|
| Enable/disable outline | ✅ Via `DisplayOutlines: true/false` |
| Customize outline color | ❌ Client-side rendering, not configurable |
| Customize outline thickness | ❌ Client-side rendering |
| Customize outline scale (e.g., 1.1x) | ❌ Client-side rendering |
| Show only when holding specific item | ✅ Per-item `InteractionConfig` |
| Show on non-interaction items | ⚠️ Only works if the item has an interaction that uses this config |

### Verdict

**The Stencil Book already has interactions** (`StencilBook_PickStencil` as Primary, `StencilCrafting_OpenUI` as Secondary). The `DisplayOutlines` flag is inherited from `InteractionConfiguration` and defaults to `true`. **The block outline should already be showing when the player aims at a block while holding the Stencil Book** — as long as one of its interactions is active on the client side.

If the outline is NOT showing, it's because `SimpleInstantInteraction` (which `StencilBookPickStencilInteraction` extends) fires once and completes immediately — the client may not be running a persistent interaction to maintain the outline display.

**Key insight:** `DisplayOutlines` is tied to the **interaction lifecycle**, not the held item. It only renders while the client is in an active interaction loop (e.g., `PlaceBlock` shows outlines because it continuously checks for target blocks). A `SimpleInstantInteraction` does NOT maintain a persistent interaction state.

---

## Finding 2: `EditorBlocksChange` Packet — Ghost Block Preview

### What It Is

The `EditorBlocksChange` packet sends a list of "ghost" block previews to the client. These render as semi-transparent block overlays that don't affect world state. The `advancedPreview` flag controls rendering mode.

**Source:** [PreviewCommand.java](../../src/main/java/com/UnobstructedThirdPerson/command/PreviewCommand.java)

```java
EditorBlocksChange packet = new EditorBlocksChange();
packet.selection = null;
packet.blocksChange = changes.toArray(BlockChange[]::new);
packet.fluidsChange = new FluidChange[0];
packet.blocksCount = changes.size();
packet.advancedPreview = true;
playerRef.getPacketHandler().writeNoCache(packet);
```

Each `BlockChange` specifies a position and block type ID:
```java
changes.add(new BlockChange(worldX, worldY, worldZ, previewBlockId, (byte) 0));
```

### For a Single-Block Selector

You could send an `EditorBlocksChange` with a single `BlockChange` at the aimed block position, using a translucent colored block type. Clear it by sending an empty packet.

### Limitations

| Aspect | Status |
|--------|--------|
| Shows ghost block at position | ✅ |
| Custom block type/texture | ✅ Via block type ID |
| Renders at arbitrary scale | ❌ Block-grid-aligned only (1x1x1) |
| Overlay on existing block | ⚠️ Renders on TOP of existing block — potential z-fighting |
| Per-player | ✅ Sent via `writeNoCache` |
| Performance | ⚠️ Must continuously send packets as player looks around |
| Clear on tool unequip | ⚠️ Must track and clear manually |

### Verdict

**Viable for a "ghost block" selector**, but NOT for an outline/wireframe effect. You'd see a translucent colored cube at the target position, not a wireframe outline. Z-fighting with the existing block is a significant visual issue.

---

## Finding 3: `ServerSetBlock` + `UpdateBlockTypes` — Fake Block Overlay

### What It Is

The `PreviewBlockManager` and `PreviewBlockSubCommand` demonstrate sending a `ServerSetBlock` packet to visually replace a block on the client side without modifying world state. Combined with `UpdateBlockTypes`, you can redefine a placeholder block's appearance to be transparent/colored.

**Source:** [PreviewBlockManager.java](../../src/main/java/com/UnobstructedThirdPerson/preview/PreviewBlockManager.java)

```java
playerRef.getPacketHandler().writeNoCache(new ServerSetBlock(
    position.x, position.y, position.z,
    blockId, filler, rotation
));
```

**Source:** [PlaceholderTransparencyUtil.java](../../src/main/java/com/UnobstructedThirdPerson/shape/v1/placeholder/PlaceholderTransparencyUtil.java) — demonstrates redefining block textures to `Editor_Empty.png`:

```java
modifiedPacket.drawType = DrawType.Cube;
modifiedPacket.requiresAlphaBlending = true;
modifiedPacket.material = BlockMaterial.Empty;
```

### For a Block Selector

1. Define a "Selector" placeholder block with a colored semi-transparent texture (not `Editor_Empty` which is fully transparent)
2. When the player aims at a block, send `ServerSetBlock` to visually replace it with the selector block
3. When the player looks away, send `ServerSetBlock` to restore the original block

### Limitations

| Aspect | Status |
|--------|--------|
| Visual control | ✅ Full control via `UpdateBlockTypes` textures |
| Alpha blending | ✅ Via `requiresAlphaBlending = true` |
| Replaces the existing block visually | ⚠️ The original block disappears — not an overlay |
| Must snapshot & restore | ✅ `PreviewBlockManager` pattern handles this |
| Performance | ⚠️ Must send 2 packets per frame (restore old + set new) |
| Scale > 1.0 | ❌ Blocks are always 1x1x1 in the world grid |
| Race conditions | ⚠️ If real block changes while preview is active |

### Verdict

**Viable but destructive to visual state.** The aimed block gets visually replaced, not outlined. This is the heaviest approach. Useful for showing a "tinted" version of the block but not an outline effect.

---

## Finding 4: Spawn Entity at Block Position (Editor_Empty at 1.1 Scale)

### Investigation: Can We Spawn a Scaled Block Entity?

**No evidence of block-scale control in the protocol layer.** The `com.hypixel.hytale.protocol.BlockType` packet has these fields (from codebase analysis):

- `drawType` (Cube, Model, CubeWithModel)
- `cubeTextures` / `modelTexture`
- `requiresAlphaBlending`
- `material` (BlockMaterial)
- **No scale field**

Blocks in the world grid are always 1×1×1. There is no `blockScale`, `renderScale`, or `hitboxScale` field in the `BlockType` protocol packet.

**Entity-based approach:** Entities (non-block) can potentially have transform scale, but:
- The server plugin API does not expose a `spawnEntity()` with custom model + scale
- There is no known "wireframe cube entity" prefab in the engine
- Entity rendering is client-side — the server can set entity position but not rendering properties like wireframe mode

### Verdict

**Not feasible.** Blocks cannot be rendered at non-1.0 scale. No wireframe entity primitive is available via the server plugin API.

---

## Finding 5: The Engine's Native Placement Preview (Ghost Block)

### What It Is

When a player holds a placeable block item, the engine's **client-side** `PlaceBlockInteraction` renders a ghost preview of the block at the placement target. This is the semi-transparent block you see before placing.

**Source:** [PlaceBlockInteraction.java](../../.tmp_hytale_src/com/hypixel/hytale/server/core/modules/interaction/interaction/config/client/PlaceBlockInteraction.java) (decompiled)

```java
public class PlaceBlockInteraction extends SimpleInteraction {
    // WaitForDataFrom.Client — client sends target position
    // Client renders ghost preview BEFORE server confirmation
}
```

### Can We Hijack It?

The placement preview is entirely client-side. The server has no API to:
- Enable preview without a `PlaceBlockInteraction`
- Change preview appearance (color, scale, wireframe)
- Show preview for non-placeable items

The Stencil Book's interaction type is `SimpleInstantInteraction`, not `PlaceBlockInteraction`. Switching to `PlaceBlockInteraction` would show a ghost preview but also trigger placement logic.

### Verdict

**Not usable for a non-placement selector tool.** The preview system is tightly coupled to block placement.

---

## Recommended Approach: `EditorBlocksChange` Single-Block Ghost

The most practical approach combines **two mechanisms**:

### Phase 1: Minimum Viable — Use `DisplayOutlines` (Zero Code)

Add `InteractionConfig` to the Stencil Book JSON to ensure the native block outline shows:

```json
{
  "InteractionConfig": {
    "DisplayOutlines": true,
    "UseDistance": { "Adventure": 8, "Creative": 8 }
  }
}
```

**Caveat:** This only works if the client is running a continuous interaction. For `SimpleInstantInteraction` (fires once on key press), the outline may only flash briefly. Test this first.

### Phase 2: Server-Side Ghost — `EditorBlocksChange` Selector

If the native outline is insufficient, implement a per-player tick loop that:

1. Raycasts from the player's look direction via `TargetUtil.getTargetBlock()`
2. Compares the new target to the last-sent target
3. If changed, sends an `EditorBlocksChange` with a single `BlockChange` at the new position using a semi-transparent colored block type
4. Clears the ghost when the player unequips the Stencil Book or disconnects

```
┌─────────────┐     raycast     ┌──────────────┐
│ Player Look  │──────────────→│ Target Block   │
└──────┬──────┘                 └──────┬────────┘
       │                               │
       │ changed?                      │
       ▼                               ▼
┌──────────────────┐          ┌───────────────────────┐
│ Clear old ghost   │          │ Send EditorBlocksChange│
│ (empty packet)    │          │ at new target position  │
└──────────────────┘          └───────────────────────┘
```

**Pattern to follow:** The `CameraTransparencyVolumeV2` already implements this exact tick-loop-with-raycast pattern using `HytaleServer.SCHEDULED_EXECUTOR` and `world.execute()`.

### Phase 3 (Advanced): Custom Semi-Transparent Selector Block

Define a custom block type with a colored semi-transparent texture (e.g., blue-tinted glass effect):

```json
{
  "TranslationProperties": { "Name": "Selector_Highlight" },
  "Icon": "Icons/ItemsGenerated/Editor_Empty.png",
  "Quality": "Developer",
  "BlockType": {
    "Material": "Empty",
    "DrawType": "Cube",
    "HitboxType": "Full",
    "Textures": [{ "All": "BlockTextures/Selector_Blue.png" }],
    "RequiresAlphaBlending": true,
    "Opacity": "Transparent"
  }
}
```

Use this block type ID in the `EditorBlocksChange` packet for the ghost preview.

---

## Comparison Matrix

| Approach | Visual Effect | Performance | Complexity | Works for Tool Items |
|----------|--------------|-------------|------------|---------------------|
| `DisplayOutlines` in InteractionConfig | Native wireframe outline | ⭐⭐⭐⭐⭐ Zero cost | ⭐⭐⭐⭐⭐ JSON only | ⚠️ Only during active interaction |
| `EditorBlocksChange` ghost block | Semi-transparent cube overlay | ⭐⭐⭐ 1 packet/target change | ⭐⭐⭐ Moderate | ✅ |
| `ServerSetBlock` replacement | Replaces block visually | ⭐⭐ 2 packets/target change | ⭐⭐ Heavy — snapshot/restore | ✅ |
| Scaled entity spawn | Wireframe at 1.1x | N/A | N/A | ❌ Not feasible |
| Native placement preview | Ghost block at placement pos | ⭐⭐⭐⭐⭐ | ❌ Requires PlaceBlock interaction | ❌ Triggers placement |

---

## Gotchas

1. **`DisplayOutlines` is interaction-lifecycle-bound** — it only renders while the client is actively running an interaction loop. `SimpleInstantInteraction` fires once and completes, so the outline may not persist.

2. **`EditorBlocksChange` z-fighting** — showing a ghost block at the same position as an existing block will cause visual z-fighting. The ghost renders slightly differently than world blocks, but overlap artifacts are possible.

3. **`ServerSetBlock` contention** — if another system (CameraTransparencyVolume, StencilVisualManager) also sends `ServerSetBlock` for the same position, visual glitches occur. Coordinate with existing preview systems.

4. **Tick rate for raycasting** — the server tick rate limits how responsively the highlight follows the player's aim. A 50ms tick (20 TPS) may feel laggy compared to client-side rendering.

5. **No block scale in the protocol** — blocks are always 1×1×1. There is no way to render a block at 1.1× scale to create an outline effect that "wraps" the target block.

## See Also

- [Server-Client Boundary](../server-client-boundary.md) — what the server can vs cannot control
- [Builder Tool Research](../items/builder-tool-research.md) — `InteractionConfiguration`, `DisplayOutlines`, `UseDistance`
- [PreviewBlockManager.java](../../src/main/java/com/UnobstructedThirdPerson/preview/PreviewBlockManager.java) — server-side ghost block management
- [PreviewCommand.java](../../src/main/java/com/UnobstructedThirdPerson/command/PreviewCommand.java) — `EditorBlocksChange` usage pattern
