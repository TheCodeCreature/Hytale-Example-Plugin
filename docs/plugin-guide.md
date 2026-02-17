# Unobstructed Third Person Camera Plugin

A Hytale server plugin that provides a fully functional third-person camera system with server-authoritative block/entity interaction correction, automatic block transparency around the camera, and pitch-based first-person toggling.

## Features

- **Custom Third-Person Camera** — Configurable camera with adjustable distance, position offset, and lerp speeds
- **Server-Side Interaction Fixing** — Rewrites client block/entity targeting to match the camera's reticle, eliminating desync between what the player sees and what the server processes
- **Camera Transparency Volume** — Automatically makes blocks transparent in an ellipsoid around the camera so the view is never obstructed by nearby geometry
- **Pitch-Based First-Person Toggle** — Automatically switches to first-person view when looking steeply up or down (beyond ±55°), then back to third-person when the angle normalizes
- **Asset Editor Schema Extension** — Exposes all 30 `ServerCameraSettings` fields in the Hytale Asset Editor for JSON-based camera configuration
- **Debug Visualization** — A debug command that renders colored shapes showing camera position, look direction, server/client targets, placement positions, and hit faces

## Commands

### `/UnobstructedCamera` (aliases: `/NoClipCamera`, `/UCamera`, `/UC`)

The main command group for controlling the camera.

| Subcommand | Description | Usage |
|---|---|---|
| `Start` | Activates the third-person camera | `/UC Start [distance]` |
| `Stop` | Deactivates the camera and restores defaults | `/UC Stop` |

**Start** accepts an optional `Distance` float argument to override the default camera distance (default: `3.0`). The distance must be greater than 0.

**Examples:**
```
/UC Start          — Start with default distance (3.0)
/UC Start --distance 6.0      — Start with camera 6 blocks behind
/UC Stop           — Return to default camera
```

### `/DebugTarget`

Displays a detailed diagnostic overlay showing interaction targeting state. Renders debug shapes in-world for 10 seconds and prints a full report to chat.

**Debug shape legend:**

| Shape | Color | Meaning |
|---|---|---|
| Sphere | Cyan | Player foot position and eye position |
| Sphere | Yellow | Camera position (behind player) |
| Cube | Red | Client's last reported target block |
| Cube | Green | Server's computed target block |
| Cube | Lime | Client reticle target (from CameraManager) |
| Cube | Magenta | Computed placement position (adjacent block) |
| Sphere | White | Precise ray hit location on block surface |
| Arrow | Orange | Look direction from eye position |
| Arrow | Yellow | Camera-to-server-target direction |
| Arrow | Blue | Body facing direction |
| Arrow | Purple | Head look direction |

## How It Works

### Camera Activation Flow

1. Player runs `/UC Start`
2. `CustomCameraSettings` creates a `ServerCameraSettings` with:
   - `distance = 3.0` (or custom), `positionOffset = (0, 1, 0)`, `eyeOffset = true`
   - `isFirstPerson = false`, `displayReticle = true`
   - Position/rotation lerp speeds of `0.9`
3. `InteractionPositionFixer` is enabled for the player and receives the camera settings
4. A `CameraTransparencyVolume` with a radius-5 ellipsoid is created
5. The `SetServerCamera` packet is sent to the client

### Interaction Position Fixing

Hytale's interaction system is client-authoritative: the client calculates which block the reticle is on and sends that position to the server. In a custom third-person camera, the client's target can differ from what the server expects because the server doesn't natively account for the camera offset.

`InteractionPositionFixer` solves this by:

1. Running a server-side raycast every 100ms from the camera origin (`eyePos + positionOffset`) along the player's look direction
2. The raycast skips blocks made transparent by `CameraTransparencyVolume` (using `TargetUtil.getTargetBlockAvoidLocations`)
3. Intercepting inbound `SyncInteractionChains` packets and rewriting block/entity positions to match the server's computed target
4. Killing duplicate `ClientPlaceBlock` packets and resyncing ghost blocks at the client's predicted position
5. Correcting the `BlockFace` for placement interactions so connected blocks orient correctly

### Camera Transparency Volume

To prevent the camera from being inside solid blocks (common in third-person views near walls/ceilings), `CameraTransparencyVolume`:

1. Computes the camera's block-space position each tick
2. Maintains an ellipsoidal volume of block positions around the camera anchor
3. Sends fake transparent block types to the client via `UpdateBlockTypes` packets
4. Uses `ServerSetBlock` to swap real blocks for transparent variants on the client
5. Restores original blocks when they leave the volume or the camera is deactivated
6. Resets all tracking state on player reconnect to avoid stale texture references

### Pitch-Based First-Person Toggle

When the player looks steeply up or down (beyond ±55° from horizontal), the plugin automatically switches removes the player character from the view. This prevents awkward camera angles when looking at the player's feet or straight up. The toggle only sends a `SetServerCamera` packet when the state actually changes, avoiding packet spam.

## Default Camera Settings

| Setting | Value |
|---|---|
| `distance` | `3.0` |
| `positionOffset` | `(0, 1, 0)` |
| `eyeOffset` | `true` |
| `isFirstPerson` | `false` |
| `displayReticle` | `true` |
| `positionLerpSpeed` | `0.9` |
| `rotationLerpSpeed` | `0.9` |

## Project Structure

```
src/main/java/com/
├── UnobstructedThirdPersonPlugin.java          — Plugin entry point
└── UnobstructedThirdPerson/
    ├── AssetEditor/
    │   └── CameraSchemaExtension.java          — Asset Editor schema for camera fields
    ├── Commands/
    │   ├── UnobstructedCamera/
    │   │   ├── UnobstructedCameraCommand.java  — /UnobstructedCamera command group
    │   │   ├── Settings/
    │   │   │   └── CustomCameraSettings.java   — Default camera configuration
    │   │   └── SubCommands/
    │   │       ├── StartCommand.java           — Activates the camera
    │   │       └── StopCommand.java            — Deactivates the camera
    │   └── debug/
    │       └── DebugTargetCommand.java         — /DebugTarget visualization
    ├── camera/
    │   ├── BlockSnapshot.java                  — Snapshot of a block's state
    │   ├── CameraPositionUtil.java             — Camera position calculations
    │   ├── CameraTransparencyVolume.java       — Transparent block volume manager
    │   └── TransparentBlockUtils.java          — Fake transparent block type utilities
    └── fix/
        ├── BlockInteractionEventSystems.java   — Place/break event logging
        └── InteractionPositionFixer.java       — Packet-level interaction correction
```

## Requirements

- Hytale Server SDK
- Java 17+
- Gradle (wrapper included)

## Building

```bash
./gradlew build
```

The compiled plugin JAR will be in `build/libs/`.
