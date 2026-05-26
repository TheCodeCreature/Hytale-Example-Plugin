---
topic: "BlockEntity Interaction Interception"
category: "entities"
updated: 2026-05-22
sources:
  - "Decompiled BlockEntity.java"
  - "Decompiled BlockEntitySystems.java (BlockEntitySetupSystem)"
  - "Decompiled BoundingBox.java (component)"
  - "Decompiled TargetUtil.java (getTargetBlock, getTargetEntity, isHitByRay)"
  - "Decompiled InteractionModule.java (doMouseInteraction)"
  - "Decompiled WorldInteraction.java (protocol)"
  - "block-targeting-raycast-investigation.md (existing local doc)"
---

# BlockEntity Interaction Interception

## Summary

A spawned `BlockEntity` **automatically gets a `BoundingBox` component** derived from the block type's collision hitbox. When scaled to 2.1x, this bounding box extends beyond the underlying block, causing the **client's raycast to hit the entity instead of the block behind it**. This prevents block breaking, block use, and block placement on the underlying block — because the client sends the entity as its target, not the block.

## Question 1: Does a BlockEntity have a default hitbox/bounding box?

**YES.** `BlockEntitySetupSystem.onEntityAdd()` unconditionally creates a `BoundingBox` component for every entity that has a `BlockEntity` component.

**Evidence** — `BlockEntitySystems.java` lines ~53–63:

```java
public void onEntityAdd(Holder<EntityStore> holder, AddReason reason, Store<EntityStore> store) {
    // ...
    BlockEntity blockEntityComponent = holder.getComponent(this.blockEntityComponentType);
    BoundingBox boundingBoxComponent = blockEntityComponent.createBoundingBoxComponent();
    if (boundingBoxComponent == null) {
        LOGGER.log("defaulting to 1x1x1 dimensions for Block Entity bounding box");
        boundingBoxComponent = new BoundingBox(Box.horizontallyCentered(1.0, 1.0, 1.0));
    }
    holder.putComponent(BoundingBox.getComponentType(), boundingBoxComponent);
    // ...
}
```

The `BoundingBox` is created from the referenced block type's **collision hitbox** (`getHitboxTypeIndex()`, NOT `getInteractionHitboxTypeIndex()`):

```java
// BlockEntity.createBoundingBoxComponent()
BlockType blockType = assetMap.getAsset(this.blockTypeKey);
return new BoundingBox(BlockBoundingBoxes.getAssetMap()
    .getAsset(blockType.getHitboxTypeIndex())
    .get(0).getBoundingBox());
```

For a full-cube block, this is a 1×1×1 bounding box. The fallback is also 1×1×1.

**Even though your `spawnHighlightEntity()` does NOT add a BoundingBox component**, the ECS system `BlockEntitySetupSystem` adds one automatically during `addEntity()` via the `HolderSystem.onEntityAdd()` hook. You cannot prevent this.

## Question 2: Would a 2.1x scaled BlockEntity prevent the client from targeting the block behind it?

**YES.** Here's the chain:

1. **The entity gets a bounding box** — 1×1×1 for full-cube blocks (see above).

2. **EntityScaleComponent scales the rendered model** — at 2.1x, the visual block model is 2.1× the normal size, extending 0.55 blocks beyond each face of the underlying block.

3. **The client performs its own entity+block raycast** — the client does hitbox-aware raycasting for both entities and blocks, then picks the **closer** hit. The `WorldInteraction` packet sent to the server contains BOTH:
   - `entityId` — network ID of the hit entity (or -1 if no entity hit)
   - `blockPosition` — position of the hit block (or null if no block hit, or the block behind the entity)

4. **The client prioritizes the entity** — when the entity's bounding box is hit before the block behind it (which it will be at 2.1x scale since it extends beyond the block surface), the client:
   - Sends `entityId = <highlight entity's networkId>`
   - The block outline rendering targets the entity, NOT the underlying block
   - Left-click and right-click are interpreted as interactions with the entity

5. **The server resolves the target** — `InteractionModule.doMouseInteraction()` extracts both:
   ```java
   Vector3i targetBlock = blockPositionPacket == null ? null : new Vector3i(...);
   Entity targetEntity;
   if (worldInteraction_.entityId < 0) {
       targetEntity = null;
   } else {
       Ref<EntityStore> entityReference = entityComponentStore.getRefFromNetworkId(worldInteraction_.entityId);
       targetEntity = EntityUtils.getEntity(entityReference, componentAccessor);
   }
   ```
   When `entityId` is valid, the interaction system treats it as an entity interaction, not a block interaction.

6. **Block interactions (break/use/place) are skipped** — the interaction chain resolves the held item's interactions against the targeted entity. Since the highlight entity has no interaction handlers, the interaction does nothing. The block behind it is never reached.

### Why Pick (BlueprintBookPickStencilInteraction) still works

Pick uses **server-side** `TargetUtil.getTargetBlock()`, which is a **blocks-only** raycast:

```java
// BlueprintBookPickStencilInteraction.java
Vector3i target = TargetUtil.getTargetBlock(ref, 8.0, store);
```

`TargetUtil.getTargetBlock()` iterates through the block grid using `BlockIterator` and only tests `blockId` values from the chunk's `BlockSection`. **It never checks entities.** It is completely unaware of the highlight entity's existence. The ray passes through the entity position and hits the actual block in the grid.

**Evidence** — `TargetUtil.java` line ~72:
```java
BlockSection blockSection = iBuffer.currentBlockChunk.getSectionAtBlockY(y);
int blockId = blockSection.get(x, y, z);
int fluidId = WorldUtil.getFluidIdAtPosition(...);
return !blockIdPredicate.test(blockId, fluidId);
```

No entity queries. No `BoundingBox` checks. Pure block-grid traversal.

## Question 3: Is there a component or flag to make an entity pass-through for raycasts?

**No known flag exists in the decompiled server code.** The key observations:

- `BoundingBox` is added unconditionally by `BlockEntitySetupSystem` — there is no opt-out
- `TargetUtil.isHitByRay()` checks for `BoundingBox` and returns `false` if absent, but the setup system ensures it's always present for BlockEntities
- There is no `NonInteractive`, `PassThrough`, `IgnoreRaycast`, or similar component in the decompiled source
- The `Visible` component controls network sync (entity tracker), not interaction targeting
- Entity targeting on the **client** is a sealed system — plugins cannot modify client raycast behavior

### Possible workarounds

| Approach | Feasibility | Notes |
|----------|-------------|-------|
| Remove `BoundingBox` after spawn | **Unlikely** | `BlockEntitySetupSystem` adds it during `onEntityAdd()`. You could try removing it via `CommandBuffer` in a post-setup system, but the setup system may re-add it. Untested. |
| Set BoundingBox to zero-size | **Possible** | After entity spawns, set `boundingBox` to `Box.horizontallyCentered(0, 0, 0)`. Client may still receive the original size via the spawn packet. Untested. |
| Use particles instead of entity | **Recommended** | `SpawnParticleSystem` packets have zero interaction hitbox. Per existing research in `research-particle-highlight-and-container-events.md`. Limitation: particles can't render the block model with textures. |
| Use `ServerSetBlock` (fake block) | **Possible** | Send a fake block at an offset position. The "block" would be a real block in the world grid and fully interactable. But this modifies the actual world state. |
| Spawn entity at offset position | **Possible** | Spawn the highlight entity slightly above or to the side of the target block so it doesn't occlude the block's interaction hitbox. Cosmetic tradeoff. |

## Question 4: Does TargetUtil.getTargetBlock() ignore entities?

**YES — definitively confirmed.** `TargetUtil.getTargetBlock()` operates exclusively on the block grid via `BlockIterator` and `BlockSection.get(x, y, z)`. It has zero awareness of entities, bounding boxes, or the spatial index.

In contrast, `TargetUtil.getTargetEntity()` is a separate method that operates exclusively on entities via the `SpatialResource` spatial index and `isHitByRay()` → `BoundingBox` → `CollisionMath.intersectRayAABB()`.

The two raycasting systems are completely independent on the server:

```
TargetUtil.getTargetBlock()   → BlockIterator → BlockSection → blockId predicate
TargetUtil.getTargetEntity()  → SpatialResource → BoundingBox → CollisionMath.intersectRayAABB
```

The **client** merges both results, picking the closer hit. The **server** keeps them separate.

## Root Cause Diagram

```
Player looks at block with highlight entity
         │
         ├─── CLIENT RAYCAST ──────────────────────────────┐
         │    1. Ray hits entity BoundingBox (1×1×1 @ 2.1x scale)  │
         │    2. Ray would hit block behind, but entity is closer   │
         │    3. WorldInteraction.entityId = highlight entity       │
         │    4. Sends MouseInteraction packet with entity target   │
         │                                                          │
         │    Result: Block interactions (break/use/place) BLOCKED  │
         │            because target is entity, not block           │
         └──────────────────────────────────────────────────────────┘
         │
         ├─── SERVER RAYCAST (TargetUtil.getTargetBlock) ──┐
         │    1. Ray walks block grid via BlockIterator             │
         │    2. Checks blockId at each position                    │
         │    3. Entity is invisible to block grid                  │
         │    4. Returns the actual block position                  │
         │                                                          │
         │    Result: Pick interaction WORKS because it uses        │
         │            server-side block raycast                     │
         └──────────────────────────────────────────────────────────┘
```

## Recommendation

The cleanest fix is to **stop using a `BlockEntity` entity as a visual highlight**. The entity unavoidably intercepts client interactions. Alternatives:

1. **Particle-based highlight** — Use `SpawnParticleSystem` packets (see `research-particle-highlight-and-container-events.md`). No interaction hitbox, zero ECS overhead. Limitation: no block model rendering.

2. **Entity effect only (no BlockEntity component)** — Spawn an entity with just `TransformComponent`, `NetworkId`, `Visible`, and `EffectControllerComponent` — but WITHOUT `BlockEntity`. Without `BlockEntity`, `BlockEntitySetupSystem` won't add a `BoundingBox`. Test whether the client still renders something useful (it may not render anything without a model source). 

3. **Offset the entity** — Position the highlight entity slightly offset (e.g., Y+0.01) so the client's raycast hits the block face before the entity bounding box. This is fragile and depends on camera angle.

## See Also

- [Block Targeting & Raycast Investigation](../blocks/block-targeting-raycast-investigation.md)
- [Research: Particle Highlight & Container Events](../research-particle-highlight-and-container-events.md)
- [Server vs Client Boundary](../server-client-boundary.md)
