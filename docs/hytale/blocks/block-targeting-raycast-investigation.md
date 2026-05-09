---
topic: "Block Targeting & Raycast Investigation"
category: "blocks"
updated: 2025-05-09
sources:
  - "Decompiled RaycastSelector.java (server interaction config)"
  - "Decompiled TargetUtil.java"
  - "Decompiled BlockIterator.java"
  - "Decompiled SimpleBlockInteraction.java"
  - "Decompiled InteractionModule.java"
  - "Decompiled StabSelector.java"
  - "Decompiled ClientSourcedSelector.java"
  - "Decompiled MouseInteraction.java, WorldInteraction.java, InteractionSyncData.java"
  - "Decompiled BlockBoundingBoxes.java"
  - "Decompiled BlockType.java (hitbox fields)"
  - "Reference asset: Pickaxe_Attack.json, Pickaxe_Mine.json, Pickaxe_Block_Break.json"
---

# Block Targeting & Raycast Investigation

## Executive Summary

**The server NEVER does hitbox-accurate block raycasting for mining tools.** Block targeting for the pickaxe (and all tools using `BreakBlock` / `UseBlock` interactions) is **entirely CLIENT-driven**. The client performs a hitbox-aware raycast, renders the block outline, and sends the resulting block position to the server via `InteractionSyncData.blockPosition`. The server does a distance sanity check, then acts on that position.

`TargetUtil.getTargetBlock()` is a simplified grid-walking DDA algorithm that treats every non-air block as a full 1×1×1 cube. It is used by `RaycastSelector` for server-side entity/block selection (e.g., ranged abilities), NOT for pickaxe block breaking.

The ONLY server-side code that performs shape-aware block intersection is `StabSelector`'s line-of-sight check, and that's for determining if a melee attack can reach an entity — not for targeting blocks.

---

## 1. TargetUtil.getTargetBlock() — The Server's Block Raycast

**File:** [TargetUtil.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/util/TargetUtil.java)

### How It Works

```java
public static Vector3i getTargetBlock(
    World world,
    BiIntPredicate blockIdPredicate,  // (blockId, fluidId) -> boolean
    double originX, double originY, double originZ,
    double directionX, double directionY, double directionZ,
    double maxDistance
)
```

Uses `BlockIterator.iterate()` — a DDA (Digital Differential Analyzer) grid-walking algorithm. Steps through integer block coordinates along the ray, one block at a time, and calls the predicate with `(blockId, fluidId)`.

### Key Limitation

**No hitbox shape checking.** The algorithm:
1. Steps through block grid positions along the ray direction
2. For each grid cell, reads the `blockId` from the chunk's `BlockSection`
3. Passes `(blockId, fluidId)` to the predicate
4. If predicate returns `true`, stops and returns that block position

It treats **every non-air block as a full 1×1×1 cube**. It never checks `BlockBoundingBoxes`, detail boxes, or any hitbox shape. A slab, stair, or fence will "hit" as soon as the ray enters their grid cell, even if the ray would pass through the empty space above/around the actual shape.

### BlockIterator — The Grid-Walking Algorithm

**File:** [BlockIterator.java](../../../.tmp_hytale_src/com/hypixel/hytale/math/iterator/BlockIterator.java)

Standard DDA ray-grid traversal. Computes intersection with the next axis-aligned plane in each direction, advances to the closest one, and reports each block coordinate visited. The callback signature is:

```java
boolean accept(int x, int y, int z, double px, double py, double pz, double qx, double qy, double qz)
```

Where `(px, py, pz)` is the entry point within the block (0-1 local) and `(qx, qy, qz)` is the exit point. These sub-block positions ARE available to the callback but `TargetUtil` ignores them.

---

## 2. RaycastSelector — Server-Side Interaction Selector

**File:** [RaycastSelector.java (server)](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/modules/interaction/interaction/config/selector/RaycastSelector.java)

### Full Block Targeting Code (RuntimeSelector.tick())

```java
this.blockPosition = TargetUtil.getTargetBlock(
    commandBuffer.getExternalData().getWorld(),
    (id, fluidId) -> {
        if (id == 0) return false;
        if (RaycastSelector.this.ignoreFluids || RaycastSelector.this.ignoreEmptyCollisionMaterial) {
            BlockType blockType = BlockType.getAssetMap().getAsset(id);
            if (RaycastSelector.this.ignoreFluids && blockType.getMaterial() == BlockMaterial.Empty && fluidId != 0)
                return false;
            if (RaycastSelector.this.ignoreEmptyCollisionMaterial && blockType.getMaterial() == BlockMaterial.Empty)
                return false;
        }
        return blockTags == null || blockTags.contains(id);
    },
    position.x, position.y, position.z,
    direction.x, direction.y, direction.z,
    RaycastSelector.this.distance
);
```

### Key Fields

| Field | Type | Default | Purpose |
|-------|------|---------|---------|
| `offset` | `Vector3d` | `ZERO` | Offset of ray origin from entity |
| `distance` | `int` | `30` | Max raycast distance |
| `ignoreFluids` | `boolean` | `false` | Skip fluid blocks |
| `ignoreEmptyCollisionMaterial` | `boolean` | `false` | Skip blocks with `BlockMaterial.Empty` (decorations, plants, etc.) |
| `blockTag` | `String` | `null` | Only match blocks with this tag |

### Entity Targeting (DOES use AABB intersection)

For entities, the RaycastSelector does proper ray-AABB intersection:

```java
CollisionMath.intersectRayAABB(position, direction, ePos.getX(), ePos.getY(), ePos.getZ(), 
    boundingBox.getBoundingBox(), this.minMax)
```

Then compares entity hit distance vs block hit distance (block center) to determine which is closer.

### Verdict

RaycastSelector is **NOT shape-aware for blocks**. It uses `TargetUtil.getTargetBlock()` which is pure grid-walking. The `ignoreEmptyCollisionMaterial` flag filters by `BlockMaterial` type, not by hitbox shape.

---

## 3. The Pickaxe Interaction Chain — How Block Mining Actually Works

### Chain Flow

```
Tool_Pickaxe_Crude.json
  → "Primary": "Pickaxe_Attack"         (RootInteraction)
    → Type: "Chaining", Next: ["Pickaxe_Mine"]
      → Pickaxe_Mine.json               (Type: "Simple", RunTime: 0.083)
        → Parallel:
          [1] Simple (RunTime: 0.05) → Pickaxe_Block_Break
          [2] Selector (Horizontal) → HitEntity → Pickaxe_Mine_Damage
          [3] Pickaxe_Mine_Effect
```

### Pickaxe_Block_Break.json — The Actual Block Break

**File:** [Pickaxe_Block_Break.json](../../../docs/Reference%20Assets/Assets/Server/Item/Interactions/Weapons/Pickaxe/Attacks/Pickaxe_Block_Break.json)

```json
{
  "Type": "UseBlock",
  "Failed": {
    "Type": "BreakBlock",
    "Tool": "Pickaxe",
    "UseLatestTarget": true    ← THIS IS THE KEY
  }
}
```

### BreakBlockInteraction extends SimpleBlockInteraction

**File:** [SimpleBlockInteraction.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/modules/interaction/interaction/config/client/SimpleBlockInteraction.java)

When `UseLatestTarget = true`:

```java
protected void tick0(...) {
    if (firstRun) {
        if (this.useLatestTarget) {
            InteractionSyncData clientState = context.getClientState();
            if (clientState == null || clientState.blockPosition == null) {
                context.getState().state = InteractionState.Failed;
                return;
            }
            
            BlockPosition latestBlockPos = clientState.blockPosition;  // ← FROM THE CLIENT
            
            // Distance sanity check
            double distanceSquared = transformComponent.getPosition()
                .distanceSquaredTo(latestBlockPos.x + 0.5, latestBlockPos.y + 0.5, latestBlockPos.z + 0.5);
            
            BlockPosition baseBlock = world.getBaseBlock(latestBlockPos);
            context.getMetaStore().putMetaObject(Interaction.TARGET_BLOCK, baseBlock);
            context.getMetaStore().putMetaObject(Interaction.TARGET_BLOCK_RAW, latestBlockPos);
        }
        // ... then proceeds to break that block
    }
}
```

**The `blockPosition` comes from `InteractionSyncData` which is CLIENT-provided state.** The server trusts it after a basic distance check.

The documentation string in the codec confirms this:
> `"UseLatestTarget"` — *"Determines whether to use the clients latest target block position for this interaction."*

---

## 4. Client→Server Block Targeting Flow

### MouseInteraction Packet

**File:** [MouseInteraction.java](../../../.tmp_hytale_src/com/hypixel/hytale/protocol/packets/player/MouseInteraction.java)

Contains `WorldInteraction worldInteraction` which has:
- `int entityId` — targeted entity network ID
- `BlockPosition blockPosition` — **client's targeted block** (from client-side hitbox raycast)
- `BlockRotation blockRotation`

### InteractionSyncData

**File:** [InteractionSyncData.java](../../../.tmp_hytale_src/com/hypixel/hytale/protocol/InteractionSyncData.java)

Sent with each interaction state update. Key fields for targeting:

| Field | Type | Purpose |
|-------|------|---------|
| `blockPosition` | `BlockPosition` | Client's targeted block at interaction time |
| `blockFace` | `BlockFace` | Which face the client hit |
| `hitEntities` | `SelectedHitEntity[]` | Client-selected entity hits |
| `attackerPos` | `Position` | Client's position snapshot |
| `attackerRot` | `Direction` | Client's rotation snapshot |
| `raycastHit` | `Position` | Precise raycast hit point |
| `raycastDistance` | `float` | Distance of raycast hit |
| `raycastNormal` | `Vector3f` | Surface normal at hit point |

### InteractionModule.doMouseInteraction()

**File:** [InteractionModule.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/modules/interaction/InteractionModule.java) (line 351)

```java
WorldInteraction worldInteraction_ = packet.worldInteraction;
BlockPosition blockPositionPacket = worldInteraction_.blockPosition;
Vector3i targetBlock = blockPositionPacket == null 
    ? null 
    : new Vector3i(blockPositionPacket.x, blockPositionPacket.y, blockPositionPacket.z);

// Stores into CameraManager for later use
cameraManagerComponent.handleMouseButtonState(packet.mouseButton.mouseButtonType, packet.mouseButton.state, targetBlock);
cameraManagerComponent.setLastBlockPosition(targetBlock);
```

The client-provided `blockPosition` flows through `CameraManager.lastTargetBlock` and is accessible to subsequent interaction ticks.

### The Full Flow

```
CLIENT:
  1. Client does hitbox-aware raycast using BlockBoundingBoxes detail boxes
  2. Renders block outline on the hit block
  3. Sends MouseInteraction packet with WorldInteraction.blockPosition = hit block
  4. When interaction starts, sends InteractionSyncData.blockPosition = current target

SERVER:
  5. InteractionModule.doMouseInteraction() stores blockPosition in CameraManager
  6. When Pickaxe_Block_Break runs with UseLatestTarget=true:
     → reads context.getClientState().blockPosition (from InteractionSyncData)
     → distance check
     → breaks that block
```

---

## 5. The ONLY Server-Side Shape-Aware Code: StabSelector Line-of-Sight

**File:** [StabSelector.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/modules/interaction/interaction/config/selector/StabSelector.java) (lines ~210-240)

This is used ONLY for line-of-sight checking (can a melee attack reach an entity through blocks):

```java
if (blockType.getMaterial() == BlockMaterial.Solid) {
    int hitboxTypeIndex = blockType.getHitboxTypeIndex();  // ← uses collision hitbox, NOT interaction hitbox
    BlockBoundingBoxes blockHitboxes = BlockBoundingBoxes.getAssetMap().getAsset(hitboxTypeIndex);
    if (blockHitboxes == null) return true;  // no hitbox = transparent
    
    Vector3d lineFrom = new Vector3d(fromX, fromY, fromZ);
    Vector3d lineTo = new Vector3d(toX, toY, toZ);
    BlockBoundingBoxes.RotatedVariantBoxes rotatedHitboxes = blockHitboxes.get(accessor.getBlockRotationIndex(x, y, z));
    
    for (Box box : rotatedHitboxes.getDetailBoxes()) {
        Box offsetBox = box.clone().offset(x, y, z);
        if (offsetBox.intersectsLine(lineFrom, lineTo)) {
            return false;  // line of sight blocked
        }
    }
}
```

### Key Details
- Uses `getHitboxTypeIndex()` (collision hitbox), NOT `getInteractionHitboxTypeIndex()`
- Only checks `BlockMaterial.Solid` blocks
- Gets rotated variant hitboxes via `blockHitboxes.get(accessor.getBlockRotationIndex(x, y, z))`
- Uses `Box.intersectsLine()` for line-segment vs box intersection
- Purpose: line-of-sight for melee combat, NOT block targeting

---

## 6. BlockType: Two Hitbox Types

**File:** [BlockType.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/blocktype/config/BlockType.java)

### Collision Hitbox (`HitboxType`)
```java
protected String hitboxType;           // e.g. "Full", "Slab_Bottom"
protected transient int hitboxTypeIndex;
```
Used by: physics/collision (`BlockCollisionProvider`), `StabSelector` line-of-sight

### Interaction Hitbox (`InteractionHitboxType`)
```java
protected String interactionHitboxType;           // e.g. could be "Full" for a slab
protected transient int interactionHitboxTypeIndex;
```
Sent to the client via `packet.interactionHitbox = this.interactionHitboxTypeIndex`. **Not used anywhere on the server.** This is the hitbox the CLIENT uses for block outline rendering and crosshair targeting.

### Implication
The interaction hitbox can differ from the collision hitbox. For example, a trapdoor might have a thin collision hitbox but a full-block interaction hitbox to make it easier to click. The client uses `interactionHitboxTypeIndex` for targeting, and it's sent in the `BlockType` packet to the client.

---

## 7. ClientSourcedSelector — Entity Targeting from Client

**File:** [ClientSourcedSelector.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/modules/interaction/interaction/config/selector/ClientSourcedSelector.java)

```java
@Deprecated  // ← marked deprecated
public class ClientSourcedSelector implements Selector {
    // For ENTITIES: uses client-provided hit entities
    public void selectTargetEntities(...) {
        SelectedHitEntity[] hits = this.context.getClientState().hitEntities;
        // ... uses client data
    }
    
    // For BLOCKS: delegates to parent selector (server-computed)
    public void selectTargetBlocks(...) {
        this.parent.selectTargetBlocks(commandBuffer, ref, consumer);
    }
}
```

Created in `SelectInteraction.tick0()` when `SNAPSHOT_SOURCE == CLIENT` and the actor is a player:
```java
if (playerComponent != null && SNAPSHOT_SOURCE == SelectInteraction.SnapshotSource.CLIENT) {
    selector = new ClientSourcedSelector(selector, context);
}
```

Note: `SNAPSHOT_SOURCE` defaults to `CLIENT`, so for players, entity targeting always uses client data. Block targeting uses the server's selector (which uses `TargetUtil.getTargetBlock()` for `RaycastSelector`). But this doesn't matter for the pickaxe because the pickaxe's block break path uses `UseLatestTarget=true` which reads directly from `InteractionSyncData`.

---

## 8. Implications for Our Plugin

### Why `TargetUtil.getTargetBlock()` Causes False Hits

Our plugin uses:
```java
Vector3i target = TargetUtil.getTargetBlock(ref, 8.0, store);
```

This is the simplified overload that treats every non-air block as a full 1×1×1 cube. For non-full blocks (slabs, stairs, fences, etc.), the ray hits the grid cell even when the crosshair is in the empty space above/around the actual block shape.

### The Engine's Solution: Trust the Client

The engine solves this by having the CLIENT do the accurate raycast:
1. Client has `BlockBoundingBoxes` detail boxes for each block type (sent via the `BlockType` packet)
2. Client uses `interactionHitboxTypeIndex` to get the right hitbox shape
3. Client does a detailed raycast against the rotated bounding boxes
4. Client sends the result to the server via `InteractionSyncData.blockPosition`
5. Server trusts it (with distance validation)

### Options for Our Plugin

1. **Trust the client** — Read `blockPosition` from `InteractionSyncData` or `WorldInteraction` in the `MouseInteraction` packet. This is what the vanilla interaction system does.

2. **Implement server-side shape-aware raycast** — Follow the `StabSelector` pattern:
   ```java
   BlockType blockType = ...;
   int hitboxTypeIndex = blockType.getInteractionHitboxTypeIndex(); // use interaction hitbox
   BlockBoundingBoxes hitboxes = BlockBoundingBoxes.getAssetMap().getAsset(hitboxTypeIndex);
   RotatedVariantBoxes rotated = hitboxes.get(rotationIndex);
   for (Box box : rotated.getDetailBoxes()) {
       Box offsetBox = box.clone().offset(blockX, blockY, blockZ);
       if (offsetBox.intersectsLine(rayFrom, rayTo)) { ... }
   }
   ```

3. **Hybrid** — Use `TargetUtil.getTargetBlock()` for the grid-walk, then validate the hit against the actual hitbox detail boxes. If the ray doesn't intersect any detail box, continue the grid-walk to the next block.

### Recommendation

Option 3 (hybrid) gives the best of both worlds:
- Grid-walk for fast traversal (existing `BlockIterator` infrastructure)
- Detail box intersection for accuracy at each candidate block
- Server-authoritative, no client trust needed
- Uses `getInteractionHitboxTypeIndex()` for the interaction-appropriate hitbox shape

---

## 9. Key File Reference

| File | Path | Purpose |
|------|------|---------|
| `TargetUtil.java` | `.tmp_hytale_src/.../server/core/util/TargetUtil.java` | Server grid-walking block raycast |
| `BlockIterator.java` | `.tmp_hytale_src/.../math/iterator/BlockIterator.java` | DDA grid traversal algorithm |
| `RaycastSelector.java` (server) | `.tmp_hytale_src/.../interaction/config/selector/RaycastSelector.java` | Interaction selector using TargetUtil |
| `RaycastSelector.java` (protocol) | `.tmp_hytale_src/.../protocol/RaycastSelector.java` | Wire format for client sync |
| `StabSelector.java` | `.tmp_hytale_src/.../interaction/config/selector/StabSelector.java` | Only server-side shape-aware intersection |
| `ClientSourcedSelector.java` | `.tmp_hytale_src/.../interaction/config/selector/ClientSourcedSelector.java` | Wraps selector with client entity hits |
| `SimpleBlockInteraction.java` | `.tmp_hytale_src/.../interaction/config/client/SimpleBlockInteraction.java` | Base for UseBlock/BreakBlock, reads client blockPosition |
| `BreakBlockInteraction.java` | `.tmp_hytale_src/.../interaction/config/client/BreakBlockInteraction.java` | Block breaking, extends SimpleBlockInteraction |
| `InteractionModule.java` | `.tmp_hytale_src/.../interaction/InteractionModule.java` | doMouseInteraction() processes client packets |
| `MouseInteraction.java` | `.tmp_hytale_src/.../protocol/packets/player/MouseInteraction.java` | Client→server mouse event packet |
| `WorldInteraction.java` | `.tmp_hytale_src/.../protocol/WorldInteraction.java` | Contains entityId + blockPosition from client |
| `InteractionSyncData.java` | `.tmp_hytale_src/.../protocol/InteractionSyncData.java` | Client state sync including blockPosition, hitEntities |
| `BlockBoundingBoxes.java` | `.tmp_hytale_src/.../asset/type/blockhitbox/BlockBoundingBoxes.java` | Detail box definitions per rotation variant |
| `BlockType.java` | `.tmp_hytale_src/.../asset/type/blocktype/config/BlockType.java` | Has both hitboxTypeIndex and interactionHitboxTypeIndex |
| `CameraManager.java` | `.tmp_hytale_src/.../entity/entities/player/CameraManager.java` | Stores lastTargetBlock from client |
| `Pickaxe_Block_Break.json` | `docs/Reference Assets/.../Pickaxe/Attacks/Pickaxe_Block_Break.json` | UseBlock → BreakBlock with UseLatestTarget=true |
| `Pickaxe_Mine.json` | `docs/Reference Assets/.../Pickaxe/Attacks/Pickaxe_Mine.json` | Parallel: block break + entity hit + effect |
