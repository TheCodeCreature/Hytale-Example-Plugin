---
topic: "Block Rotation in Custom Interactions — Why SimpleBlockInteraction Doesn't Receive R-Key Rotation"
category: "Blocks / Interactions / Rotation"
updated: 2026-04-26
sources:
  - "PlaceBlockInteraction (server config): .tmp_hytale_src/.../interaction/config/client/PlaceBlockInteraction.java"
  - "SimpleBlockInteraction (server config): .tmp_hytale_src/.../interaction/config/client/SimpleBlockInteraction.java"
  - "SimpleInteraction (server config): .tmp_hytale_src/.../interaction/config/SimpleInteraction.java"
  - "InteractionSyncData (protocol): .tmp_hytale_src/com/hypixel/hytale/protocol/InteractionSyncData.java"
  - "BlockRotation (protocol): .tmp_hytale_src/com/hypixel/hytale/protocol/BlockRotation.java"
  - "BlockPlacementSettings (config): .tmp_hytale_src/.../blocktype/config/BlockPlacementSettings.java"
  - "BlockPlacementSettings (protocol): .tmp_hytale_src/com/hypixel/hytale/protocol/BlockPlacementSettings.java"
  - "BlockPlacementRotationMode (protocol): .tmp_hytale_src/com/hypixel/hytale/protocol/BlockPlacementRotationMode.java"
  - "BlockPlaceUtils: .tmp_hytale_src/.../interaction/BlockPlaceUtils.java"
  - "PlaceBlockToolInteraction (plugin): src/main/java/.../PlaceBlockToolInteraction.java"
  - "SimpleBlockInteraction (protocol): .tmp_hytale_src/com/hypixel/hytale/protocol/SimpleBlockInteraction.java"
  - "PlaceBlockInteraction (protocol): .tmp_hytale_src/com/hypixel/hytale/protocol/PlaceBlockInteraction.java"
---

# Block Rotation in Custom Interactions

## Summary

**Root cause: The client only computes and sends `blockRotation` for `PlaceBlockInteraction` protocol packets, NOT for `SimpleBlockInteraction` or `SimpleInteraction` protocol packets.** Our `PlaceBlockToolInteraction` generates a `protocol.SimpleBlockInteraction` packet, so the client never populates `blockRotation` in the sync data. The R-key rotation cycle and the ghost preview rotation are tied to the `PlaceBlock` interaction type on the client side.

---

## Q1: Does `InteractionSyncData.blockRotation` Get Populated for Non-PlaceBlock Interactions?

### Answer: **No.** The client only computes and sends meaningful `blockRotation` for `PlaceBlock`-type interactions.

### Evidence

The protocol packet type sent to the client determines what client-side behavior is activated:

| Server interaction class | Protocol packet generated | Client rotation behavior |
|---|---|---|
| `PlaceBlockInteraction` | `protocol.PlaceBlockInteraction` | Client computes rotation from player facing + R-key state; sends `blockRotation` |
| `SimpleBlockInteraction` (and subclasses) | `protocol.SimpleBlockInteraction` | Client does **NOT** compute `blockRotation`; field is likely `null` |
| `SimpleInteraction` | `protocol.SimpleInteraction` | No rotation computation |

**Our `PlaceBlockToolInteraction`** overrides `generatePacket()`:
```java
// PlaceBlockToolInteraction.java line 274-277
@Override
protected com.hypixel.hytale.protocol.Interaction generatePacket() {
    return new com.hypixel.hytale.protocol.SimpleBlockInteraction();
}
```

This means the client sees our interaction as a `SimpleBlockInteraction`, not a `PlaceBlock`. The client's interaction handler for `SimpleBlockInteraction` does not compute block placement rotation.

**The `blockRotation` field on `InteractionSyncData` is `@Nullable`:**
```java
// InteractionSyncData.java line 34
@Nullable
public BlockRotation blockRotation;
```

For non-PlaceBlock interactions, this will be `null`.

### Why `blockRotation` is sometimes not null

Even though the client doesn't compute rotation for SimpleBlockInteraction, the `blockRotation` field may occasionally be non-null if it retains stale data from a previous PlaceBlock interaction within the same session packet. However, this is unreliable and the values would be stale/wrong.

---

## Q2: How Does `PlaceBlockInteraction` Receive Rotation?

### Answer: From `clientState.blockRotation`, exactly the same code path we use — but the difference is the client actually populates it.

### Full Code Path

**Step 1: Client sends rotation in sync data** (client-side, not decompiled — inferred from protocol)

The client, when executing a `PlaceBlockInteraction` (identified by the `protocol.PlaceBlockInteraction` packet type), computes rotation from:
- The player's facing direction (auto-rotation based on `RotationMode`)
- The R-key rotation override (if `AllowRotationKey` is `true` in `PlacementSettings`)

This gets written to `InteractionSyncData.blockRotation` before sending the sync packet.

**Step 2: Server reads in `PlaceBlockInteraction.tick0()`**

```java
// PlaceBlockInteraction.java (server config) line 89-91
InteractionSyncData clientState = context.getClientState();
BlockPosition blockPosition = clientState.blockPosition;
BlockRotation blockRotation = clientState.blockRotation;
if (blockPosition != null && blockRotation != null) {
    // ... proceed with placement
```

**Step 3: Passed to `BlockPlaceUtils.placeBlock()`**

```java
// PlaceBlockInteraction.java line 135-148
BlockPlaceUtils.placeBlock(
    ref, heldItemStack, ...,
    targetBlockPosition,
    blockRotation,    // <-- passed directly
    inventory, ...
);
```

**Step 4: `BlockPlaceUtils.placeBlock()` converts to `RotationTuple`**

```java
// BlockPlaceUtils.java line 80-82
RotationTuple targetRotation = RotationTuple.of(
    Rotation.valueOf(blockRotation.rotationYaw),
    Rotation.valueOf(blockRotation.rotationPitch),
    Rotation.valueOf(blockRotation.rotationRoll)
);
```

**Step 5: Fires `PlaceBlockEvent` with rotation**

```java
// BlockPlaceUtils.java line 87
PlaceBlockEvent event = new PlaceBlockEvent(itemStack, blockPosition, targetRotation);
entityStore.invoke(ref, event);
// Event handler can modify rotation via event.setRotation()
targetRotation = event.getRotation();
```

### Key Insight

The code path we use in `PlaceBlockToolInteraction` is **identical** to what `PlaceBlockInteraction` does:
```java
BlockRotation blockRotation = clientState.blockRotation;
RotationTuple rotation = RotationTuple.of(
    Rotation.valueOf(blockRotation.rotationYaw),
    Rotation.valueOf(blockRotation.rotationPitch),
    Rotation.valueOf(blockRotation.rotationRoll)
);
```

The difference is not in how we **read** the rotation — it's in whether the **client populates it**. Our interaction generates a `SimpleBlockInteraction` protocol packet, so the client never writes rotation data.

---

## Q3: Does the R-Key Ghost Preview Rotation Work for Non-PlaceBlock Interactions?

### Answer: **No.** Both the R-key rotation cycle AND the ghost preview rotation are client-side behaviors tied to the `PlaceBlock` protocol interaction type.

### Evidence

The ghost preview system is entirely client-side (see [block-preview-system.md](./block-preview-system.md)). The client renders the preview based on:
1. The item's `blockId` (which block type to preview)
2. The `BlockPlacementSettings` on the block type (visibility, rotation mode)
3. The currently active **protocol interaction type**

The R-key rotation state is maintained by the client's `PlaceBlock` interaction handler. When the client's active interaction is a `PlaceBlockInteraction` protocol type, it:
- Listens for R-key presses to cycle rotation
- Applies the rotation to the ghost preview rendering
- Sends the rotation in `InteractionSyncData.blockRotation`

When the active interaction is a `SimpleBlockInteraction` protocol type, the client's block placement rotation system is **not active**. The ghost preview still renders (because the item has a `BlockType` with `BlockPreviewVisibility: Default`), but:
- R-key rotation is **not processed**
- The preview always shows with default rotation (facing direction)
- `blockRotation` is **not populated** in the sync data

### Implication

The user sees a ghost preview that appears to face a certain direction (computed by the client's auto-rotation from player facing), but pressing R does nothing. When they place the block, the server receives `null` for `blockRotation` and falls back to `rotationIndex = 0`, which may differ from what the ghost was showing.

---

## Q4: `InteractionSyncData` Full Field List

### Answer: Complete field inventory

```java
// InteractionSyncData.java — all fields
@Nonnull  public InteractionState state = InteractionState.Finished;
          public float progress;
          public int operationCounter;
          public int rootInteraction;
          public int totalForks;
          public int entityId;
          public int enteredRootInteraction = Integer.MIN_VALUE;
@Nullable public BlockPosition blockPosition;           // Target block position
@Nonnull  public BlockFace blockFace = BlockFace.None;   // Which face was clicked
@Nullable public BlockRotation blockRotation;            // Yaw/Pitch/Roll for placement
          public int placedBlockId = Integer.MIN_VALUE;  // Client-predicted placed block
          public float chargeValue = -1.0F;
@Nullable public Map<InteractionType, Integer> forkCounts;
          public int chainingIndex = -1;
          public int flagIndex = -1;
@Nullable public SelectedHitEntity[] hitEntities;
@Nullable public Position attackerPos;
@Nullable public Direction attackerRot;
@Nullable public Position raycastHit;
          public float raycastDistance;
@Nullable public Vector3f raycastNormal;
@Nonnull  public MovementDirection movementDirection = MovementDirection.None;
@Nonnull  public ApplyForceState applyForceState = ApplyForceState.Waiting;
          public int nextLabel;
@Nullable public UUID generatedUUID = null;
```

**There is NO separate `placementRotation`, `selectedRotation`, or `playerRotation` field.** The only rotation field for block placement is `blockRotation`.

---

## Q5: How `PlaceBlockInteraction` Computes `RotationTuple` from Client Data

### Answer: Identical to our code. `clientState.blockRotation` → `RotationTuple.of(yaw, pitch, roll)`.

See Q2 above for the full trace. The exact code in `BlockPlaceUtils.placeBlock()`:

```java
RotationTuple targetRotation = RotationTuple.of(
    Rotation.valueOf(blockRotation.rotationYaw),
    Rotation.valueOf(blockRotation.rotationPitch),
    Rotation.valueOf(blockRotation.rotationRoll)
);
```

`RotationTuple.of()` converts three `Rotation` enum values into a packed index. `Rotation.valueOf()` converts a protocol byte to a `Rotation` enum. `RotationTuple.index()` returns the packed integer used in `worldChunk.setBlock()`.

---

## Q6: `BlockRotation` Type and Fields

### Answer: Protocol class with three `Rotation` enum fields.

```java
// BlockRotation.java (protocol)
public class BlockRotation {
    @Nonnull public Rotation rotationYaw = Rotation.None;
    @Nonnull public Rotation rotationPitch = Rotation.None;
    @Nonnull public Rotation rotationRoll = Rotation.None;
}
```

**Fields are `Rotation` enum values** (not raw bytes), though they serialize as single bytes:
```java
obj.rotationYaw = Rotation.fromValue(buf.getByte(offset + 0));
obj.rotationPitch = Rotation.fromValue(buf.getByte(offset + 1));
obj.rotationRoll = Rotation.fromValue(buf.getByte(offset + 2));
```

The `Rotation` enum values are: `None(0)`, `Deg90(1)`, `Deg180(2)`, `Deg270(3)` (exact names may vary but the ordinals are 0-3).

---

## Q7: Can We Make Our Custom Interaction Receive Rotation Data?

### Answer: Not via JSON config. The only viable approach is to change the protocol packet type our interaction generates.

### Option A: Override `generatePacket()` to return `PlaceBlockInteraction` protocol packet ⚠️ RISKY

```java
@Override
protected com.hypixel.hytale.protocol.Interaction generatePacket() {
    return new com.hypixel.hytale.protocol.PlaceBlockInteraction();
}

@Override
protected void configurePacket(com.hypixel.hytale.protocol.Interaction packet) {
    super.configurePacket(packet);
    com.hypixel.hytale.protocol.PlaceBlockInteraction p =
        (com.hypixel.hytale.protocol.PlaceBlockInteraction) packet;
    p.blockId = -1;  // or resolve from held item
    p.removeItemInHand = false;
    p.allowDragPlacement = false;
}
```

**Risk:** This tells the client to treat the interaction as a `PlaceBlock` type. The client will:
- Activate the block placement rotation system (R-key works ✅)
- Rotate the ghost preview (✅)
- Populate `blockRotation` in sync data (✅)
- **Potentially send the adjacent position as `blockPosition`** instead of the clicked-on block (⚠️)
- **May trigger client-side PlaceBlock prediction behavior** that conflicts with our SimpleBlockInteraction server-side handling (⚠️)

The danger: `SimpleBlockInteraction.tick0()` expects `blockPosition` to be the **clicked-on block** (validated by `blockId != 1 && blockId != 0`). But if the client sends the **adjacent air block** (as it does for PlaceBlock), the validation will fail (the adjacent position is air → `blockId == 1`), and the interaction will fail with `InteractionState.Failed`.

**This approach requires careful testing.** The client's behavior when seeing a `PlaceBlockInteraction` protocol packet may include position computation that is incompatible with `SimpleBlockInteraction.tick0()`.

### Option B: Compute rotation server-side from player facing direction ✅ SAFE

Instead of relying on the client to send rotation, compute it on the server from the player's `TransformComponent`:

```java
// In PlaceBlockToolInteraction.interactWithBlock():
TransformComponent transform = commandBuffer.getComponent(
    context.getEntity(), TransformComponent.getComponentType());
Vector3f playerRotation = transform.getRotation();
// Convert player facing to block rotation
float yaw = playerRotation.y;  // degrees
Rotation blockYaw = facingToRotation(yaw);
RotationTuple rotation = RotationTuple.of(blockYaw, Rotation.None, Rotation.None);
int rotationIndex = rotation.index();
```

**Limitation:** This only gives auto-rotation based on player facing. The R-key manual rotation override would NOT work because that state is only maintained client-side.

### Option C: Store rotation state on the player entity via command ✅ WORKAROUND

Create a command (e.g., `/rotate`) that cycles through rotation states and stores the current rotation in a server-side map (`Map<UUID, RotationTuple>`). The interaction reads from this map instead of from `clientState.blockRotation`.

**Limitation:** The ghost preview would NOT reflect the rotation (the preview is client-side and doesn't know about the server-side rotation state). Visual mismatch would remain.

### Option D: Extend `PlaceBlockInteraction` directly instead of `SimpleBlockInteraction` ✅ BEST

Refactor `PlaceBlockToolInteraction` to extend `PlaceBlockInteraction` (server config) instead of `SimpleBlockInteraction`. This would:
- Generate `protocol.PlaceBlockInteraction` packets → client activates rotation ✅
- Receive rotation data in `clientState.blockRotation` ✅
- Ghost preview shows correct rotation ✅
- Need to override `tick0()` to inject custom logic (recipe lookup, material consumption) before/after block placement

**Risk:** `PlaceBlockInteraction.tick0()` has hardcoded behavior (calling `BlockPlaceUtils.placeBlock()`, handling `removeItemInHand`, etc.). Overriding `tick0()` means replicating or suppressing this behavior. But since `tick0()` is `final` in `PlaceBlockInteraction` — it **cannot be overridden**.

Wait — checking: `PlaceBlockInteraction.tick0()` is declared `protected final void tick0(...)`. **It IS final.** So extending `PlaceBlockInteraction` and overriding `tick0()` is **not possible**.

### Option E: Use `PlaceBlock` interaction type with `RemoveItemInHand: false` + event interception 🔄 REVERSION

Go back to using the native `PlaceBlock` interaction type in JSON:
```json
"Interactions": {
    "Secondary": {
        "Interactions": [{
            "Type": "PlaceBlock",
            "RemoveItemInHand": false
        }]
    }
}
```

Then intercept `PlaceBlockEvent` in an ECS event handler to perform custom logic (recipe lookup, material consumption, block type override). This is the approach your original `PlaceBlockPlacementSystem` used.

**Benefit:** The client handles ALL placement behavior natively — ghost preview, R-key rotation, position computation, rotation sync. The server receives correct `blockRotation` in the event because `PlaceBlockInteraction.tick0()` passes it through to `BlockPlaceUtils.placeBlock()` → `PlaceBlockEvent`.

**Trade-off:** Returns to event interception pattern, which you moved away from for complexity reasons.

---

## Q8: `PlacementSettings` Fields That Control Rotation

### Answer: Two relevant fields: `AllowRotationKey` and `RotationMode`.

From `BlockPlacementSettings.java` (server config):

```java
private boolean allowRotationKey = true;   // Default: rotation key (R) IS enabled
private RotationMode rotationMode = RotationMode.DEFAULT;  // Default: facing player
```

### `AllowRotationKey` (boolean, default `true`)

Controls whether the client allows the player to cycle rotation with the R key. When `false`, the R key has no effect and the block always uses auto-rotation.

**Sent to client** in the `BlockPlacementSettings` protocol packet (part of `BlockType.toPacket()`). The client reads this to decide whether to process R-key input.

### `RotationMode` (enum)

Server-side enum (in `BlockPlacementSettings.java`):
```java
// Values inferred from codec; inner enum not fully decompiled
RotationMode.DEFAULT  // Use engine default (facing player)
```

Protocol enum `BlockPlacementRotationMode`:
```java
public enum BlockPlacementRotationMode {
    FacingPlayer(0),     // Rotate to face the player
    StairFacingPlayer(1),// Stair-specific facing (includes pitch)
    BlockNormal(2),      // Align to the placed face normal
    Default(3);          // Engine default
}
```

These control **auto-rotation** (how the block is oriented when placed without R-key override). The client uses the `RotationMode` to compute the initial rotation, which the player can then cycle with R.

### Full `PlacementSettings` Field List

| JSON Field | Type | Default | Purpose |
|---|---|---|---|
| `AllowRotationKey` | boolean | `true` | Enable/disable R-key rotation cycling |
| `RotationMode` | enum | `DEFAULT` | Auto-rotation algorithm (`FacingPlayer`, `StairFacingPlayer`, `BlockNormal`, `Default`) |
| `BlockPreviewVisibility` | enum | `DEFAULT` | Ghost preview visibility (`Default`, `AlwaysVisible`, `AlwaysHidden`) |
| `PlaceInEmptyBlocks` | boolean | `false` | Allow placing inside blocks with Empty material |
| `WallPlacementOverrideBlockId` | string | `null` | Substitute block type when placed on a wall face |
| `FloorPlacementOverrideBlockId` | string | `null` | Substitute block type when placed on floor |
| `CeilingPlacementOverrideBlockId` | string | `null` | Substitute block type when placed on ceiling |

**Important:** These settings are on the `BlockType`, not the interaction. They affect client behavior ONLY when the active interaction is `PlaceBlock` type. For `SimpleBlockInteraction` types, the client ignores `AllowRotationKey` and `RotationMode` because the placement rotation system is not active.

---

## Q9: Can We Read Placement Rotation from the Player Entity?

### Answer: **No.** There is no `Player.getPlacementRotation()` or equivalent API.

The R-key rotation state is maintained **exclusively on the client**. It is not stored on any server-side component. The only way the server learns about the rotation is through `InteractionSyncData.blockRotation`, which is only populated for `PlaceBlock` interactions.

The `TransformComponent` gives the player's entity rotation (head/body facing direction), which can be used to compute auto-rotation matching what the client does for `RotationMode.FacingPlayer`. But it does NOT include the R-key override.

---

## Recommendation

### Short Term: Compute server-side auto-rotation (Option B)

For blocks that don't need R-key rotation (uniform cubes, non-directional blocks), compute rotation from the player's facing direction on the server:

```java
TransformComponent transform = commandBuffer.getComponent(
    context.getEntity(), TransformComponent.getComponentType());
float yaw = transform.getRotation().y;
Rotation blockYaw = computeBlockYaw(yaw);
int rotationIndex = RotationTuple.of(blockYaw, Rotation.None, Rotation.None).index();
```

This matches the ghost preview's auto-rotation (since the client computes it the same way from facing direction) but does NOT support R-key cycling.

### Long Term: Use `PlaceBlock` with `RemoveItemInHand: false` + event interception (Option E)

If R-key rotation is required:
1. Change the armed state interaction from `"Type": "PlaceBlockTool"` to `"Type": "PlaceBlock"` with `"RemoveItemInHand": false`
2. Use a `PlaceBlockEvent` handler to intercept placement, perform recipe resolution and material consumption
3. This gives full native rotation support (R-key, ghost preview, sync data)

### Not Recommended: Changing `generatePacket()` to return `PlaceBlockInteraction` (Option A)

This would cause `SimpleBlockInteraction.tick0()` to fail because the client sends the adjacent air position (not the clicked-on block), and the air-block validation check fails. Would require significant rework of the base class behavior.

---

## Gotchas

- **`blockRotation` is `@Nullable`** — always null-check before reading. For `SimpleBlockInteraction` types, expect it to be `null`.
- **Ghost preview rotation ≠ placed rotation** when using `SimpleBlockInteraction` — the ghost shows auto-rotation based on facing, but the server may use `rotationIndex = 0` if `blockRotation` is null.
- **`PlaceBlockInteraction.tick0()` is `final`** — cannot be overridden by subclasses. Extending `PlaceBlockInteraction` requires fully replacing its behavior, not augmenting it.
- **`PlacementSettings.AllowRotationKey` only matters for `PlaceBlock` interactions** — the client checks this setting only in the PlaceBlock interaction handler.
- **`PlaceBlockInteraction.simulateTick0()` explicitly zeros rotation**: `clientState.blockRotation = new BlockRotation(Rotation.None, Rotation.None, Rotation.None)` — this is the simulation/fallback path.

---

## Diagram: Rotation Data Flow Comparison

```
PlaceBlock Interaction (native):
  Client R-key → client rotation state
       ↓
  Client computes blockRotation from facing + R-key state
       ↓
  Client populates InteractionSyncData.blockRotation
       ↓
  Server PlaceBlockInteraction.tick0() reads clientState.blockRotation
       ↓
  Server BlockPlaceUtils.placeBlock() → RotationTuple → setBlock

SimpleBlockInteraction (our custom):
  Client R-key → ❌ NOT PROCESSED (wrong interaction type)
       ↓
  Client does NOT compute blockRotation
       ↓
  InteractionSyncData.blockRotation = null
       ↓
  Server PlaceBlockToolInteraction reads null → falls back to rotationIndex=0
       ↓
  Block placed with default rotation (may not match ghost preview)
```

## See Also

- [simpleblockinteraction-research.md](./simpleblockinteraction-research.md) — Adjacent position and blockFace research
- [block-preview-system.md](./block-preview-system.md) — Ghost preview rendering system
- [../items/bucket-pattern.md](../items/bucket-pattern.md) — PlacementSettings field reference
- [../items/state-placeblock-interaction-research.md](../items/state-placeblock-interaction-research.md) — PlaceBlockInteraction.tick0() flow
