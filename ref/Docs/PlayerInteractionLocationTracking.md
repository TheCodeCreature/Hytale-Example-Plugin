# Hytale Player Interaction Location Tracking - Deep Research

## Overview

This document details how Hytale tracks the player's interaction location in the world, with a focus on understanding the camera/block placement desync bug where the player can move around the world but the block placement preview remains stationary until `player camera reset` is executed.

---

## Architecture Summary

Hytale uses a **client-authoritative interaction targeting system** where the client calculates and sends the target block position to the server. The server validates and processes these interactions. The camera system is separate but tightly coupled to how interaction targets are determined.

### Key Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `CameraManager` | `server.core.entity.entities.player.CameraManager` | Manages camera state, mouse states, and last target block position |
| `TargetUtil` | `server.core.util.TargetUtil` | Server-side raycasting utilities for block/entity targeting |
| `RaycastSelector` | `server.core.modules.interaction.interaction.config.selector.RaycastSelector` | Raycast-based target selection for interactions |
| `InteractionModule` | `server.core.modules.interaction.InteractionModule` | Central module handling all player interactions |
| `MouseInteraction` | `protocol.packets.player.MouseInteraction` | Client-to-server packet containing interaction data |
| `WorldInteraction` | `protocol.WorldInteraction` | Contains target block position and entity ID |
| `ServerCameraSettings` | `protocol.ServerCameraSettings` | Camera configuration sent from server to client |
| `SetServerCamera` | `protocol.packets.camera.SetServerCamera` | Packet to configure client camera |

---

## How Interaction Location Tracking Works

### 1. Client-Side Target Calculation

The **client** performs raycasting to determine:
- Which block the player is looking at (`blockPosition`)
- Which entity the player is targeting (`entityId`)
- The block rotation for placement (`blockRotation`)

This information is packaged into a `WorldInteraction` object:

```java
public class WorldInteraction {
    public int entityId;                    // Target entity network ID (-1 if none)
    @Nullable
    public BlockPosition blockPosition;     // Target block coordinates (x, y, z)
    @Nullable
    public BlockRotation blockRotation;     // Rotation for block placement
}
```

### 2. MouseInteraction Packet

When the player clicks or moves the mouse, the client sends a `MouseInteraction` packet:

```java
public class MouseInteraction implements Packet {
    public long clientTimestamp;
    public int activeSlot;
    @Nullable public String itemInHandId;
    @Nullable public Vector2f screenPoint;        // Screen coordinates
    @Nullable public MouseButtonEvent mouseButton;
    @Nullable public MouseMotionEvent mouseMotion;
    @Nullable public WorldInteraction worldInteraction;  // TARGET LOCATION DATA
}
```

**Critical Point**: The `worldInteraction.blockPosition` is **calculated by the client** based on the client's camera position and look direction.

### 3. Server-Side Processing

The server receives the packet in `GamePacketHandler.handle(MouseInteraction)`:

```java
public void handle(@Nonnull MouseInteraction packet) {
    Ref<EntityStore> ref = this.playerRef.getReference();
    if (ref != null && ref.isValid()) {
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            Player playerComponent = store.getComponent(ref, Player.getComponentType());
            InteractionModule.get().doMouseInteraction(ref, store, packet, playerComponent, this.playerRef);
        });
    }
}
```

### 4. InteractionModule Processing

In `InteractionModule.doMouseInteraction()`:

```java
WorldInteraction worldInteraction_ = packet.worldInteraction;
BlockPosition blockPositionPacket = worldInteraction_.blockPosition;

// Extract target block from CLIENT-PROVIDED data
Vector3i targetBlock = blockPositionPacket == null 
    ? null 
    : new Vector3i(blockPositionPacket.x, blockPositionPacket.y, blockPositionPacket.z);

// Update CameraManager with client-provided target
cameraManagerComponent.setLastBlockPosition(targetBlock);
cameraManagerComponent.setLastScreenPoint(new Vector2d(packet.screenPoint.x, packet.screenPoint.y));
```

### 5. CameraManager State Storage

The `CameraManager` component stores:

```java
public class CameraManager implements Component<EntityStore> {
    private final Map<MouseButtonType, MouseButtonState> mouseStates;
    private final Map<MouseButtonType, Vector3i> mousePressedPosition;
    private final Map<MouseButtonType, Vector3i> mouseReleasedPosition;
    private Vector2d lastScreenPoint = Vector2d.ZERO;
    private Vector3i lastTargetBlock;  // LAST KNOWN TARGET BLOCK
    
    public void setLastBlockPosition(Vector3i targetBlock) {
        this.lastTargetBlock = targetBlock;
    }
    
    public Vector3i getLastTargetBlock() {
        return this.lastTargetBlock;
    }
}
```

---

## The Camera/Block Placement Desync Bug

### Root Cause Analysis

The bug occurs when **the client's camera state becomes desynchronized from the server's expected state**, causing the client to calculate incorrect target positions.

#### Normal Flow:
1. Client camera follows player position
2. Client calculates raycast from camera → target block
3. Client sends `MouseInteraction` with correct `blockPosition`
4. Server uses client-provided position for interactions

#### Bug Flow:
1. **Something causes the client camera to become "stuck"** at a fixed position/orientation
2. Client continues calculating raycast from the **stuck camera position**
3. Client sends `MouseInteraction` with **stale/incorrect** `blockPosition`
4. Server receives outdated target positions
5. Block placement preview appears frozen because the client is sending the same position

### Why `player camera reset` Fixes It

The reset command sends:

```java
public void resetCamera(@Nonnull PlayerRef ref) {
    ref.getPacketHandler().writeNoCache(
        new SetServerCamera(ClientCameraView.Custom, false, null)
    );
    this.mouseStates.clear();
}
```

This sends a `SetServerCamera` packet with:
- `clientCameraView = ClientCameraView.Custom`
- `isLocked = false`
- `cameraSettings = null`

**Effect**: The `null` camera settings tells the client to **reset to default camera behavior**, which:
1. Re-attaches the camera to the player
2. Resets any custom camera offsets/rotations
3. Clears any stuck camera state
4. Resumes normal raycast calculations from the player's actual position

---

## Potential Causes of Camera Desync

### 1. Custom Camera Settings Not Properly Cleared

When applying custom `ServerCameraSettings`, certain combinations can cause the camera to become "detached":

```java
public class ServerCameraSettings {
    public AttachedToType attachedToType = AttachedToType.LocalPlayer;
    public PositionType positionType = PositionType.AttachedToPlusOffset;
    public RotationType rotationType = RotationType.AttachedToPlusOffset;
    public boolean isLocked;  // If true, camera is locked to settings
    // ...
}
```

**Problem scenarios**:
- `attachedToType` set to something other than `LocalPlayer`
- `positionType` set to `Custom` with a fixed position
- `rotationType` set to `Custom` with a fixed rotation
- `isLocked = true` preventing player control

### 2. Camera Settings Applied Without Proper Reset

If a plugin applies camera settings and then the player's state changes (teleport, death, respawn), the camera may not properly re-sync.

### 3. Race Conditions in Camera Updates

The camera position updates and interaction packets may arrive out of order, causing the server to process interactions with stale camera state.

### 4. Client-Side Camera Interpolation Issues

The `positionLerpSpeed` and `rotationLerpSpeed` settings control camera smoothing:

```java
public float positionLerpSpeed = 1.0F;  // 1.0 = instant, lower = smoother
public float rotationLerpSpeed = 1.0F;
```

If these are set very low, the camera may lag significantly behind the player, causing targeting issues.

---

## Server-Side Raycasting (Fallback/Validation)

The server has its own raycasting capabilities in `TargetUtil`:

```java
public static Vector3i getTargetBlock(
    @Nonnull Ref<EntityStore> ref, 
    double maxDistance, 
    @Nonnull ComponentAccessor<EntityStore> componentAccessor
) {
    World world = componentAccessor.getExternalData().getWorld();
    Transform transform = getLook(ref, componentAccessor);  // Gets player look direction
    Vector3d pos = transform.getPosition();
    Vector3d dir = transform.getDirection();
    return getTargetBlock(world, (id, _fluidId) -> id != 0, 
        pos.x, pos.y, pos.z, dir.x, dir.y, dir.z, maxDistance);
}
```

The `getLook()` method calculates the look direction from:
- `TransformComponent` - Player position
- `ModelComponent` - Eye height
- `HeadRotation` - Where the player is looking

**Important**: This server-side calculation uses the **player's head rotation**, not the camera position. In third-person cameras, these can differ significantly.

---

## RaycastSelector for Interactions

The `RaycastSelector` performs raycasting for interaction targeting:

```java
public class RaycastSelector extends SelectorType {
    protected Vector3d offset = Vector3d.ZERO;
    protected int distance = 30;
    protected boolean ignoreFluids = false;
    protected boolean ignoreEmptyCollisionMaterial = false;
    
    public Vector3d selectTargetPosition(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> attacker) {
        TransformComponent transformComponent = commandBuffer.getComponent(attacker, TransformComponent.getComponentType());
        Vector3d position = transformComponent.getPosition();
        
        if (this.offset.x != 0.0 || this.offset.y != 0.0 || this.offset.z != 0.0) {
            position = this.offset.clone();
            HeadRotation headRotation = commandBuffer.getComponent(attacker, HeadRotation.getComponentType());
            position.rotateY(headRotation.getRotation().getYaw());
            position.add(transformComponent.getPosition());
        }
        
        return position;
    }
}
```

**Key Observation**: The raycast origin is based on `TransformComponent.getPosition()` (player position) and `HeadRotation` (look direction), **not the camera position**.

---

## The Disconnect: Camera vs. Player Position

### The Core Issue

In Hytale's architecture:

| System | Uses Position From |
|--------|-------------------|
| **Client block preview** | Camera position + look direction |
| **Client MouseInteraction packet** | Camera position + look direction |
| **Server TargetUtil** | Player position + head rotation |
| **Server RaycastSelector** | Player position + head rotation |

When the camera is **not attached to the player** (custom camera settings), the client and server can calculate different target positions.

### Block Placement Flow

1. **Client**: Calculates target from camera → sends in `MouseInteraction.worldInteraction.blockPosition`
2. **Server**: Receives client position, may validate against server-calculated position
3. **PlaceBlockInteraction**: Uses `clientState.blockPosition` from the packet

```java
// In PlaceBlockInteraction.tick0()
BlockPosition blockPosition = clientState.blockPosition;  // FROM CLIENT
BlockRotation blockRotation = clientState.blockRotation;

if (blockPosition != null && blockRotation != null) {
    // Validate distance from player
    if (playerComponent.getGameMode() != GameMode.Creative) {
        Vector3d position = transformComponent.getPosition();
        Vector3d blockCenter = new Vector3d(blockPosition.x + 0.5, blockPosition.y + 0.5, blockPosition.z + 0.5);
        if (position.distanceSquaredTo(blockCenter) > 36.0) {  // 6 block max range
            return;  // Reject if too far
        }
    }
    // ... proceed with placement
}
```

---

## Debugging the Bug

### Symptoms to Look For

1. Block placement preview stays in one location while player moves
2. Interactions target the wrong block/entity
3. Issue persists until `player camera reset` is run
4. May be triggered by:
   - Custom camera settings
   - Teleportation
   - Death/respawn
   - World transitions

### Diagnostic Steps

1. **Check CameraManager state**:
   ```java
   CameraManager cm = store.getComponent(ref, CameraManager.getComponentType());
   Vector3i lastTarget = cm.getLastTargetBlock();
   // Log and compare with expected position
   ```

2. **Compare client vs server raycast**:
   ```java
   // Server-side calculation
   Vector3i serverTarget = TargetUtil.getTargetBlock(ref, 30, store);
   // Compare with packet.worldInteraction.blockPosition
   ```

3. **Check for active camera settings**:
   - Look for any `SetServerCamera` packets sent to the player
   - Check if `isLocked = true`
   - Verify `attachedToType`, `positionType`, `rotationType`

### Potential Fixes

1. **Ensure camera reset on state changes**:
   ```java
   // On teleport, death, world change, etc.
   CameraManager cm = store.getComponent(ref, CameraManager.getComponentType());
   if (cm != null) {
       cm.resetCamera(playerRef);
   }
   ```

2. **Validate client target against server calculation**:
   ```java
   Vector3i clientTarget = new Vector3i(packet.blockPosition.x, packet.blockPosition.y, packet.blockPosition.z);
   Vector3i serverTarget = TargetUtil.getTargetBlock(ref, 30, store);
   
   if (clientTarget.distanceTo(serverTarget) > TOLERANCE) {
       // Log warning, potentially reject or correct
   }
   ```

3. **Force camera re-attachment periodically**:
   ```java
   // If custom camera is not intentionally active
   if (!hasCustomCameraIntent(playerRef)) {
       playerRef.getPacketHandler().writeNoCache(
           new SetServerCamera(ClientCameraView.Custom, false, null)
       );
   }
   ```

---

## Related Code Locations

### Core Files

| File | Purpose |
|------|---------|
| `CameraManager.java` | Camera state management |
| `TargetUtil.java` | Server-side raycasting |
| `RaycastSelector.java` | Interaction target selection |
| `InteractionModule.java` | Interaction processing |
| `MouseInteraction.java` | Client interaction packet |
| `WorldInteraction.java` | Target position data |
| `GamePacketHandler.java` | Packet handling |
| `PlaceBlockInteraction.java` | Block placement logic |
| `BlockPlaceUtils.java` | Block placement utilities |
| `ServerCameraSettings.java` | Camera configuration |
| `SetServerCamera.java` | Camera control packet |
| `PlayerCameraResetCommand.java` | Reset command implementation |

### Plugin Files (UnobstructedThirdPerson)

| File | Purpose |
|------|---------|
| `CameraSettingsApplier.java` | Applies custom camera settings |
| `CameraPositionUtil.java` | Camera position calculations |
| `ExtendedCameraSettings.java` | Extended camera configuration |

---

## Recommendations

### For Plugin Developers

1. **Always provide a way to reset camera state**
2. **Be cautious with `isLocked = true`** - it prevents player camera control
3. **Test camera settings with movement and interactions**
4. **Consider the difference between camera position and player position** when designing third-person cameras

### For Bug Investigation

1. **Log MouseInteraction packets** to see if `blockPosition` is updating
2. **Compare client-sent positions with server-calculated positions**
3. **Track when SetServerCamera packets are sent** and their settings
4. **Monitor for stuck camera states** after teleports/deaths

### For Potential Fixes

1. **Add camera state validation** on player state changes
2. **Implement server-side target override** when client data is clearly stale
3. **Add periodic camera sync checks** to detect desync early
4. **Consider adding a "camera health check"** that auto-resets on detected issues

---

## References

- [Hytale Interaction System Documentation](https://hytale-docs.pages.dev/modding/systems/interactions/)
- [Customizing Camera Controls Guide](https://hytalemodding.dev/en/docs/guides/plugin/customizing-camera-controls)
- Decompiled Hytale Server Source (`.tmp_hytale_src/`)

---

*Document created: February 2026*
*Based on Hytale Early Access server code analysis*
