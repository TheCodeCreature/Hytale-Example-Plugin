---
topic: "SimpleBlockInteraction — Adjacent Position, Rotation, and PlaceBlockEvent.getTargetBlock()"
category: "Blocks / Interactions"
updated: 2026-04-26
sources: ["SimpleBlockInteraction.java (server config, decompiled)", "PlaceBlockInteraction.java (server config, decompiled)", "BlockPlaceUtils.java (decompiled)", "InteractionSyncData.java (protocol, decompiled)", "InteractionContext.java (decompiled)", "InteractionManager.java (decompiled)", "PlaceBlockEvent.java (decompiled)", "BlockFace.java (server + protocol, decompiled)", "IChunkAccessorSync.java (decompiled)"]
---

# SimpleBlockInteraction Research: Adjacent Position, Rotation, PlaceBlockEvent.getTargetBlock()

## Q1: Adjacent Block Position — How to Get It

### Answer: The client sends `blockFace` via `InteractionSyncData`. You must compute the adjacent position yourself.

### Evidence

#### What `InteractionSyncData` (a.k.a. "client state") exposes

From `InteractionSyncData.java` (protocol):

```java
public BlockPosition blockPosition;       // The block the client targeted
public BlockFace blockFace = BlockFace.None;  // Which face was clicked (Up/Down/North/South/East/West)
public BlockRotation blockRotation;       // Rotation data for placement
public int placedBlockId = Integer.MIN_VALUE; // Client-predicted placed block ID
```

**`blockFace` is available.** The protocol `BlockFace` enum has values: `None`, `Up`, `Down`, `North`, `South`, `East`, `West`.

#### How `SimpleBlockInteraction.tick0()` reads `targetBlock`

There are **two code paths** depending on `useLatestTarget`:

**Path A — `useLatestTarget = false` (default):**
The `TARGET_BLOCK` meta is set by `InteractionManager` when it processes the client interaction packet (line ~877):

```java
if (packet.data.blockPosition != null) {
    BlockPosition targetBlock = world.getBaseBlock(packet.data.blockPosition);
    context.getMetaStore().putMetaObject(Interaction.TARGET_BLOCK, targetBlock);
    context.getMetaStore().putMetaObject(Interaction.TARGET_BLOCK_RAW, packet.data.blockPosition);
}
```

`getBaseBlock()` resolves filler blocks to their base position (for multi-block structures). The `TARGET_BLOCK` is then read by `context.getTargetBlock()` → `metaStore.getIfPresentMetaObject(Interaction.TARGET_BLOCK)`.

**Path B — `useLatestTarget = true`:**
`SimpleBlockInteraction.tick0()` reads `clientState.blockPosition` directly and overrides the meta:

```java
BlockPosition latestBlockPos = clientState.blockPosition;
BlockPosition baseBlock = world.getBaseBlock(latestBlockPos);
context.getMetaStore().putMetaObject(Interaction.TARGET_BLOCK, baseBlock);
context.getMetaStore().putMetaObject(Interaction.TARGET_BLOCK_RAW, latestBlockPos);
```

**In both paths, `targetBlock` is the block the player clicked ON (the existing solid block), NOT the adjacent air block.**

The evidence: `SimpleBlockInteraction.tick0()` validates the target is a real block (not air):
```java
int blockId = chunk.getBlock(var21);
if (blockId != 1 && blockId != 0) {  // NOT empty, NOT unknown
    this.interactWithBlock(..., var21, ...);
```

If `targetBlock` were the adjacent air position, this check would always fail.

#### How `PlaceBlockInteraction` gets the adjacent position

`PlaceBlockInteraction` does NOT extend `SimpleBlockInteraction`. It extends `SimpleInteraction` directly. It reads the position from a **completely different source**: `clientState.blockPosition`.

Critical insight: **The client sends DIFFERENT `blockPosition` values for `PlaceBlockInteraction` vs `SimpleBlockInteraction`.**

- For `PlaceBlockInteraction`: the client sends `blockPosition` = the **adjacent air block** where the new block will be placed (the ghost preview position). Evidence: `PlaceBlockInteraction.tick0()` passes `blockPosition` directly to `BlockPlaceUtils.placeBlock()` as the placement target, and it's where `setBlock` is called.
- For `SimpleBlockInteraction`: the `InteractionManager` sets `TARGET_BLOCK` from `packet.data.blockPosition`, which is the **clicked-ON block**. Evidence: the block-exists check in `tick0()`.

**The client determines what `blockPosition` means based on the interaction type.** The `PlaceBlock` interaction type causes the client to compute the adjacent position (clicked block + face offset) before sending. `SimpleBlockInteraction` types receive the clicked-ON block.

#### How to compute the adjacent position in `SimpleBlockInteraction.interactWithBlock()`

`blockFace` is available from `context.getClientState().blockFace`. Use it like `PlaceBlockInteraction` does:

```java
BlockFace face = BlockFace.fromProtocolFace(context.getClientState().blockFace);
if (face != null) {
    Vector3i adjacentPos = targetBlock.clone().add(face.getDirection());
}
```

Where `BlockFace.getDirection()` returns a `Vector3i` offset:
- `UP` → (0, 1, 0)
- `DOWN` → (0, -1, 0)  
- `NORTH` → (0, 0, -1)
- `SOUTH` → (0, 0, 1)
- `EAST` → (1, 0, 0)
- `WEST` → (-1, 0, 0)

**Note:** `PlaceBlockInteraction` reads `blockFace` from clientState (line 160) and passes `BlockFace.fromProtocolFace(context.getClientState().blockFace).getDirection()` as the `placementNormal` parameter to `BlockPlaceUtils.placeBlock()`. This is used for connected block rendering, NOT for position computation — because `PlaceBlockInteraction` already receives the adjacent position from the client.

#### Gotcha: `blockFace` may be `BlockFace.None`

If the client doesn't send a face (e.g., the interaction wasn't triggered by clicking a block face), `fromProtocolFace(None)` returns `null`. Always null-check.

---

## Q2: Block Rotation

### Answer: `context.getClientState().blockRotation` provides the rotation. The client computes it based on player facing direction.

### Evidence

`InteractionSyncData` has:
```java
public BlockRotation blockRotation;
```

`BlockRotation` contains:
```java
public byte rotationYaw;
public byte rotationPitch;
public byte rotationRoll;
```

#### How `PlaceBlockInteraction` uses rotation

`PlaceBlockInteraction.tick0()` reads it directly:
```java
BlockRotation blockRotation = clientState.blockRotation;
```

And passes it to `BlockPlaceUtils.placeBlock()`, which converts it:
```java
RotationTuple targetRotation = RotationTuple.of(
    Rotation.valueOf(blockRotation.rotationYaw),
    Rotation.valueOf(blockRotation.rotationPitch),
    Rotation.valueOf(blockRotation.rotationRoll)
);
```

#### Can you use rotation=0 for all blocks?

`RotationTuple.of(Rotation.None, Rotation.None, Rotation.None)` (all zeros) produces `rotationIndex = 0`, which is the default un-rotated orientation. This works for blocks that don't have directional models (cubes, etc.). For blocks with directional models (stairs, slabs, logs, etc.), they will all face the same direction — they won't match what the player expects.

#### For `SimpleBlockInteraction`: use `clientState.blockRotation`

```java
InteractionSyncData clientState = context.getClientState();
BlockRotation blockRotation = clientState.blockRotation;
RotationTuple rotation;
if (blockRotation != null) {
    rotation = RotationTuple.of(
        Rotation.valueOf(blockRotation.rotationYaw),
        Rotation.valueOf(blockRotation.rotationPitch),
        Rotation.valueOf(blockRotation.rotationRoll)
    );
} else {
    rotation = RotationTuple.NONE; // fallback to no rotation
}
```

**Caveat:** The client may or may not populate `blockRotation` for `SimpleBlockInteraction` interactions. It depends on whether the client's interaction handler computes rotation for this interaction type. This needs empirical testing. If the client doesn't send rotation for non-PlaceBlock interactions, you may need to compute it from the player's facing direction using `TransformComponent`.

---

## Q3: `PlaceBlockEvent.getTargetBlock()` — Adjacent or Clicked?

### Answer: It's the **adjacent air block** (where the new block will be placed). Your current code is correct.

### Evidence — Full trace

**Step 1: Client sends `blockPosition` to `PlaceBlockInteraction`**

The client, when a `PlaceBlock` interaction is active, sends `blockPosition` = the adjacent air block position (where the ghost preview shows). This is the position the client computed by raycasting to the clicked block face and offsetting by the face normal.

**Step 2: `PlaceBlockInteraction.tick0()` reads it**

```java
BlockPosition blockPosition = clientState.blockPosition;  // adjacent air block
...
Vector3i targetBlockPosition = new Vector3i(blockPosition.x, blockPosition.y, blockPosition.z);
```

**Step 3: Passes to `BlockPlaceUtils.placeBlock()`**

```java
BlockPlaceUtils.placeBlock(
    ref, heldItemStack, ...,
    targetBlockPosition,   // <-- this is the adjacent position
    blockRotation, ...
);
```

**Step 4: `BlockPlaceUtils.placeBlock()` creates `PlaceBlockEvent` with this position**

```java
PlaceBlockEvent event = new PlaceBlockEvent(itemStack, blockPosition, targetRotation);
entityStore.invoke(ref, event);
```

Where `blockPosition` is the `targetBlockPosition` from step 3 — the adjacent air block.

**Step 5: After event dispatch, uses `event.getTargetBlock()` for placement**

```java
if (event.isCancelled()) {
    targetBlockSection.invalidateBlock(...);
} else {
    Vector3i targetBlockPosition = event.getTargetBlock();
    // ... tryPlaceBlock at targetBlockPosition
}
```

The event handler can **modify** the target via `event.setTargetBlock()`, which is why `BlockPlaceUtils` reads it back from the event after invoking it.

### Conclusion for current `PlaceBlockPlacementSystem`

Your code:
```java
Vector3i pos = event.getTargetBlock();
...
worldChunk.setBlock(pos.x, pos.y, pos.z, ...);
```

**This is correct.** `event.getTargetBlock()` returns the adjacent air block position (already offset by the client). No face-based computation is needed.

### Implication for `SimpleBlockInteraction`

In `SimpleBlockInteraction.interactWithBlock()`, `targetBlock` is the **clicked-ON** block. To replicate `PlaceBlockEvent.getTargetBlock()` behavior, you must compute:

```java
Vector3i adjacentPos = targetBlock.clone().add(
    BlockFace.fromProtocolFace(context.getClientState().blockFace).getDirection()
);
```

---

## Summary Table

| Question | Answer | Source |
|----------|--------|--------|
| What does `SimpleBlockInteraction.targetBlock` represent? | The clicked-ON solid block | `tick0()` validates `blockId != 1 && blockId != 0` |
| Is `blockFace` available in `SimpleBlockInteraction`? | Yes, via `context.getClientState().blockFace` | `InteractionSyncData.blockFace` field |
| How to get adjacent position? | `targetBlock + BlockFace.fromProtocolFace(clientState.blockFace).getDirection()` | Same pattern used by `PlaceBlockInteraction` line 160 |
| Is `blockRotation` available? | Yes, via `context.getClientState().blockRotation` | `InteractionSyncData.blockRotation` field |
| Can rotation=0 work for all blocks? | Only for non-directional blocks. Directional blocks will face wrong direction. | `BlockPlaceUtils.tryPlaceBlock()` uses rotation for `worldChunkComponent.placeBlock()` |
| What does `PlaceBlockEvent.getTargetBlock()` return? | The **adjacent air block** (placement target) | `BlockPlaceUtils.placeBlock()` passes `clientState.blockPosition` (from `PlaceBlockInteraction`) which is already the adjacent position |
| Is `PlaceBlockPlacementSystem`'s use of `event.getTargetBlock()` correct? | **Yes** — it's already the placement position | Full trace from client → `PlaceBlockInteraction.tick0()` → `BlockPlaceUtils.placeBlock()` → `PlaceBlockEvent` |

## See Also

- [design-custom-placeblock-interaction.md](../../Plans/design-custom-placeblock-interaction.md) — Design doc referencing these open questions
- [state-placeblock-interaction-research.md](../items/state-placeblock-interaction-research.md) — `PlaceBlockInteraction.tick0()` and `BlockPlaceUtils.placeBlock()` flow
- [placeblock-risk-investigation.md](../items/placeblock-risk-investigation.md) — R3 risk investigation
