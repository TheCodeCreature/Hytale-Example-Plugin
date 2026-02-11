# Hytale Camera Settings Reference

This document provides a comprehensive reference for all 30 camera settings available in `ServerCameraSettings`. These settings control how the player's camera behaves, including position, rotation, movement, and input handling.

## Table of Contents

- [Overview](#overview)
- [Basic Settings](#basic-settings)
- [Position Settings](#position-settings)
- [Model Camera Settings (Yaw & Pitch)](#model-camera-settings-yaw--pitch)
- [Rotation Settings (ServerCameraSettings)](#rotation-settings-servercamerasettings)
- [Movement Settings](#movement-settings)
- [Input Settings](#input-settings)
- [Visual Settings](#visual-settings)
- [Complete Examples](#complete-examples)

---

## Overview

Hytale has **two separate camera systems** that work together:

### 1. Model's CameraSettings (Default Camera)

Defined in `Player.json`'s Camera section and sent as part of the Model data. Only has **3 fields**:

| Field | Type | Description |
|-------|------|-------------|
| `PositionOffset` | Vector3f | Camera offset from player's eye (X=right, Y=up, Z=behind) |
| `Yaw` | CameraAxis | Horizontal rotation limits and target nodes |
| `Pitch` | CameraAxis | Vertical rotation limits and target nodes |

**Source**: `com.hypixel.hytale.server.core.asset.type.model.config.camera.CameraSettings`

```java
public static final BuilderCodec<CameraSettings> CODEC = BuilderCodec.builder(CameraSettings.class, CameraSettings::new)
   .addField(new KeyedCodec<>("PositionOffset", ProtocolCodecs.VECTOR3F), ...)
   .addField(new KeyedCodec<>("Yaw", CameraAxis.CODEC), ...)
   .addField(new KeyedCodec<>("Pitch", CameraAxis.CODEC), ...)
   .build();
```

This is what the **client uses by default**. The `PositionOffset.Z` value acts as the third-person distance, and the offset automatically rotates with the player's look direction.

### 2. ServerCameraSettings (Override Camera)

Sent via the `SetServerCamera` packet with **30+ fields**. This **overrides** the Model's camera when sent.

| Component | Description |
|-----------|-------------|
| `ClientCameraView` | The view mode (`FirstPerson`, `ThirdPerson`, or `Custom`) |
| `isLocked` | Whether the player can change camera modes |
| `ServerCameraSettings` | The detailed camera configuration (30 fields) |

**Source**: `com.hypixel.hytale.protocol.ServerCameraSettings`

### Why PositionOffset Works Without Distance

When you set `PositionOffset` in `Player.json`:

```json
"Camera": {
  "PositionOffset": { "X": 0.5, "Y": 0.5, "Z": 5 }
}
```

The **client** interprets `PositionOffset.Z` as the distance behind the player. This is built into the client's default camera behavior:
1. Takes `PositionOffset` from the model
2. Applies it relative to the player's eye position
3. **Rotates the offset with the player's look direction** (smooth, built-in)

When you use `SetServerCamera` with `ServerCameraSettings.distance`, you're **overriding** this with a server-controlled camera that requires explicit `PositionDistanceOffsetType: DistanceOffset` to achieve similar behavior.

**Key Insight**: Model's `PositionOffset.Z` = Client's built-in smooth third-person distance. ServerCameraSettings `Distance` = Server-controlled, requires more configuration.

**Important**: When using `ClientCameraView.FirstPerson` or `ClientCameraView.ThirdPerson`, most `ServerCameraSettings` fields are **ignored**. Use `ClientCameraView.Custom` to have full control over all settings.

---

## Basic Settings

### `positionLerpSpeed`
- **Type**: `float`
- **Default**: `1.0`
- **Description**: Controls how quickly the camera position interpolates (smooths) to its target position. Lower values create a more "floaty" camera that lags behind the player.
- **Range**: `0.0` (no movement) to `1.0` (instant)

```json
"PositionLerpSpeed": 0.1
```

### `rotationLerpSpeed`
- **Type**: `float`
- **Default**: `1.0`
- **Description**: Controls how quickly the camera rotation interpolates to its target rotation. Lower values create smoother, slower rotation transitions.
- **Range**: `0.0` (no rotation) to `1.0` (instant)

```json
"RotationLerpSpeed": 0.2
```

### `distance`
- **Type**: `float`
- **Default**: `0.0`
- **Description**: The distance behind the player for third-person view. Only used when `PositionDistanceOffsetType` is `DistanceOffset` or `DistanceOffsetRaycast`.
- **Note**: Can cause laggy camera behavior if combined with low `PositionLerpSpeed`. Consider using `PositionOffset` instead.

```json
"Distance": 5.0
```

### `speedModifier`
- **Type**: `float`
- **Default**: `1.0`
- **Description**: Multiplier for camera movement speed.

```json
"SpeedModifier": 1.5
```

### `isFirstPerson`
- **Type**: `boolean`
- **Default**: `true`
- **Description**: When `true`, the player model is hidden (as if viewing from inside the player's head). Set to `false` for third-person views where you want to see the player.

```json
"IsFirstPerson": false
```

---

## Position Settings

### `positionType`
- **Type**: `enum`
- **Default**: `AttachedToPlusOffset`
- **Values**:
  - `AttachedToPlusOffset`: Camera follows the player position plus any offset
  - `Custom`: Camera uses absolute world coordinates from the `Position` field

```json
"PositionType": "AttachedToPlusOffset"
```

### `position`
- **Type**: `Position` (X, Y, Z)
- **Default**: `null`
- **Description**: Absolute world position for the camera. **Only used when `PositionType` is `Custom`**.

```json
"Position": {
  "X": 100.0,
  "Y": 50.0,
  "Z": 100.0
}
```

### `positionOffset`
- **Type**: `Position` (X, Y, Z)
- **Default**: `null`
- **Description**: Offset from the attachment point. When `EyeOffset` is `true` and `PositionDistanceOffsetType` is `DistanceOffset`, this offset rotates with the player's look direction.
  - **X**: Left/Right (positive = right)
  - **Y**: Up/Down (positive = up)
  - **Z**: Forward/Back (positive = behind player when using DistanceOffset)

```json
"PositionOffset": {
  "X": 0.5,
  "Y": 1.0,
  "Z": 5.0
}
```

### `positionDistanceOffsetType`
- **Type**: `enum`
- **Default**: `DistanceOffset`
- **Values**:
  - `DistanceOffset`: Offset rotates with player look direction (over-the-shoulder style)
  - `DistanceOffsetRaycast`: Same as DistanceOffset but with collision detection
  - `None`: Offset is applied in world space (doesn't rotate with player)

```json
"PositionDistanceOffsetType": "DistanceOffset"
```

### `eyeOffset`
- **Type**: `boolean`
- **Default**: `false`
- **Description**: When `true`, the camera offset is calculated from the player's eye position rather than their feet. Essential for proper third-person camera positioning.

```json
"EyeOffset": true
```

### `attachedToType`
- **Type**: `enum`
- **Default**: `LocalPlayer`
- **Values**:
  - `LocalPlayer`: Camera follows the local player
  - `Entity`: Camera follows a specific entity (use with `AttachedToEntityId`)
  - `None`: Camera is not attached to anything

```json
"AttachedToType": "LocalPlayer"
```

### `attachedToEntityId`
- **Type**: `int`
- **Default**: `0`
- **Description**: The entity ID to attach the camera to. Only used when `AttachedToType` is `Entity`.

```json
"AttachedToEntityId": 12345
```

---

## Model Camera Settings (Yaw & Pitch)

These settings are part of the **Model's CameraSettings** (defined in Player.json's Camera section) and control how the character's head/body rotates to follow the camera. They are separate from the `ServerCameraSettings` fields but work together with them.

### `Yaw`
- **Type**: `CameraAxis` object
- **Description**: Controls horizontal (left/right) head rotation constraints and which body nodes rotate.
- **Sub-fields**:
  - `AngleRange`: Limits how far the head can turn horizontally
  - `TargetNodes`: Which body parts rotate with yaw

```json
"Yaw": {
  "AngleRange": {
    "Min": -90,
    "Max": 90
  },
  "TargetNodes": [
    "Head"
  ]
}
```

### `Pitch`
- **Type**: `CameraAxis` object
- **Description**: Controls vertical (up/down) head rotation constraints and which body nodes rotate.
- **Sub-fields**:
  - `AngleRange`: Limits how far the head can tilt up/down
  - `TargetNodes`: Which body parts rotate with pitch

```json
"Pitch": {
  "AngleRange": {
    "Min": -45,
    "Max": 80
  },
  "TargetNodes": [
    "Head",
    "LShoulder"
  ]
}
```

### `AngleRange`
- **Type**: `Rangef` object with `Min` and `Max`
- **Description**: Defines the minimum and maximum rotation angles in **degrees**.
- **Usage**: Restricts how far the character's head/body can rotate in that axis.

| Field | Type | Description |
|-------|------|-------------|
| `Min` | float | Minimum angle in degrees (negative = left/down) |
| `Max` | float | Maximum angle in degrees (positive = right/up) |

```json
"AngleRange": {
  "Min": -60,
  "Max": 60
}
```

### `TargetNodes`
- **Type**: Array of `CameraNode` enum values
- **Description**: Specifies which body parts rotate when the camera moves in that axis.
- **Available Values**:

| Value | Description |
|-------|-------------|
| `None` | No body part rotates |
| `Head` | Only the head rotates |
| `LShoulder` | Left shoulder rotates (for aiming) |
| `RShoulder` | Right shoulder rotates (for aiming) |
| `Belly` | Torso/belly rotates |

```json
"TargetNodes": ["Head", "LShoulder"]
```

**Common Configurations**:

| Use Case | Yaw TargetNodes | Pitch TargetNodes |
|----------|-----------------|-------------------|
| Standard FPS | `["Head"]` | `["Head"]` |
| Aiming with weapon | `["Head", "LShoulder"]` | `["Head", "LShoulder"]` |
| Full body rotation | `["Head", "Belly"]` | `["Head"]` |
| Static head (cutscene) | `["None"]` | `["None"]` |

### Complete Yaw/Pitch Example

```json
"Camera": {
  "IsFirstPerson": false,
  "EyeOffset": true,
  "PositionOffset": {
    "X": 0.5,
    "Y": 0.5,
    "Z": 5
  },
  "Yaw": {
    "AngleRange": {
      "Min": -90,
      "Max": 90
    },
    "TargetNodes": ["Head"]
  },
  "Pitch": {
    "AngleRange": {
      "Min": -45,
      "Max": 80
    },
    "TargetNodes": ["Head", "LShoulder"]
  },
  "DisplayReticle": true,
  "SendMouseMotion": true,
  "MouseInputType": "LookAtTarget"
}
```

---

## Rotation Settings (ServerCameraSettings)

### `rotationType`
- **Type**: `enum`
- **Default**: `AttachedToPlusOffset`
- **Values**:
  - `AttachedToPlusOffset`: Camera rotation follows player look direction plus any offset
  - `Custom`: Camera uses fixed rotation from the `Rotation` field

```json
"RotationType": "AttachedToPlusOffset"
```

### `rotation`
- **Type**: `Direction` (Yaw, Pitch, Roll)
- **Default**: `null`
- **Description**: Fixed camera rotation in **radians**. **Only used when `RotationType` is `Custom`**.
  - **Yaw**: Horizontal rotation (0 = North, π/2 = East, π = South, -π/2 = West)
  - **Pitch**: Vertical rotation (-π/2 = straight down, 0 = forward, π/2 = straight up)
  - **Roll**: Tilt rotation (rarely used)

```json
"Rotation": {
  "Yaw": 0.0,
  "Pitch": -1.5708,
  "Roll": 0.0
}
```

**Common Pitch Values**:
| Degrees | Radians | Description |
|---------|---------|-------------|
| -90° | -1.5708 | Looking straight down |
| -45° | -0.7854 | Looking down at 45° |
| 0° | 0.0 | Looking forward |
| 45° | 0.7854 | Looking up at 45° |
| 90° | 1.5708 | Looking straight up |

### `rotationOffset`
- **Type**: `Direction` (Yaw, Pitch, Roll)
- **Default**: `null`
- **Description**: Offset added to the camera rotation. Used with `RotationType: AttachedToPlusOffset`.

```json
"RotationOffset": {
  "Yaw": 0.0,
  "Pitch": -0.3,
  "Roll": 0.0
}
```

---

## Movement Settings

### `canMoveType`
- **Type**: `enum`
- **Default**: `AttachedToLocalPlayer`
- **Values**:
  - `AttachedToLocalPlayer`: Movement is tied to the local player
  - `Custom`: Custom movement behavior

```json
"CanMoveType": "AttachedToLocalPlayer"
```

### `applyMovementType`
- **Type**: `enum`
- **Default**: `CharacterController`
- **Values**:
  - `CharacterController`: Normal player movement
  - `None`: No movement applied

```json
"ApplyMovementType": "CharacterController"
```

### `movementMultiplier`
- **Type**: `Vector3f` (X, Y, Z)
- **Default**: `null`
- **Description**: Multiplier for movement in each axis.

```json
"MovementMultiplier": {
  "X": 1.0,
  "Y": 1.0,
  "Z": 1.0
}
```

### `movementForceRotationType`
- **Type**: `enum`
- **Default**: `AttachedToHead`
- **Values**:
  - `AttachedToHead`: Movement is relative to where the player is looking
  - `Custom`: Movement uses custom rotation from `MovementForceRotation`

```json
"MovementForceRotationType": "AttachedToHead"
```

### `movementForceRotation`
- **Type**: `Direction` (Yaw, Pitch, Roll)
- **Default**: `null`
- **Description**: Custom rotation for movement direction. Only used when `MovementForceRotationType` is `Custom`.

```json
"MovementForceRotation": {
  "Yaw": 0.0,
  "Pitch": 0.0,
  "Roll": 0.0
}
```

### `skipCharacterPhysics`
- **Type**: `boolean`
- **Default**: `false`
- **Description**: When `true`, character physics are skipped (useful for fly cameras).

```json
"SkipCharacterPhysics": true
```

---

## Input Settings

### `applyLookType`
- **Type**: `enum`
- **Default**: `LocalPlayerLookOrientation`
- **Values**:
  - `LocalPlayerLookOrientation`: Mouse input controls player look direction
  - `Rotation`: Mouse input applies to camera rotation only

```json
"ApplyLookType": "LocalPlayerLookOrientation"
```

### `lookMultiplier`
- **Type**: `Vector2f` (X, Y)
- **Default**: `null`
- **Description**: Multiplier for look sensitivity.

```json
"LookMultiplier": {
  "X": 1.0,
  "Y": 1.0
}
```

### `mouseInputType`
- **Type**: `enum`
- **Default**: `LookAtTarget`
- **Values**:
  - `LookAtTarget`: Standard targeting (raycast from camera)
  - `LookAtTargetBlock`: Only target blocks
  - `LookAtTargetEntity`: Only target entities
  - `LookAtPlane`: Target a plane (use with `PlaneNormal`)

```json
"MouseInputType": "LookAtTarget"
```

### `mouseInputTargetType`
- **Type**: `enum`
- **Default**: `Any`
- **Values**:
  - `Any`: Can target blocks and entities
  - `Block`: Only target blocks
  - `Entity`: Only target entities

```json
"MouseInputTargetType": "Any"
```

### `sendMouseMotion`
- **Type**: `boolean`
- **Default**: `false`
- **Description**: When `true`, the client sends mouse position data to the server. Required for proper targeting with custom camera views.

```json
"SendMouseMotion": true
```

### `planeNormal`
- **Type**: `Vector3f` (X, Y, Z)
- **Default**: `null`
- **Description**: Normal vector for plane targeting. Only used when `MouseInputType` is `LookAtPlane`.

```json
"PlaneNormal": {
  "X": 0.0,
  "Y": 1.0,
  "Z": 0.0
}
```

### `allowPitchControls`
- **Type**: `boolean`
- **Default**: `false`
- **Description**: When `true`, allows vertical camera pitch control.

```json
"AllowPitchControls": true
```

---

## Visual Settings

### `displayCursor`
- **Type**: `boolean`
- **Default**: `false`
- **Description**: When `true`, displays the mouse cursor on screen.

```json
"DisplayCursor": true
```

### `displayReticle`
- **Type**: `boolean`
- **Default**: `false`
- **Description**: When `true`, displays the targeting reticle (crosshair).

```json
"DisplayReticle": true
```

---

## Complete Examples

### Standard Third-Person Camera

```json
"Camera": {
  "IsFirstPerson": false,
  "EyeOffset": true,
  "PositionDistanceOffsetType": "DistanceOffset",
  "PositionType": "AttachedToPlusOffset",
  "PositionOffset": {
    "X": 0.5,
    "Y": 0.5,
    "Z": 3.0
  },
  "DisplayReticle": true,
  "SendMouseMotion": true,
  "MouseInputType": "LookAtTarget",
  "MouseInputTargetType": "Any",
  "ApplyLookType": "LocalPlayerLookOrientation",
  "AttachedToType": "LocalPlayer"
}
```

### Top-Down Camera (RTS Style)

```json
"Camera": {
  "IsFirstPerson": false,
  "EyeOffset": true,
  "PositionDistanceOffsetType": "DistanceOffset",
  "Distance": 20.0,
  "RotationType": "Custom",
  "Rotation": {
    "Yaw": 0.0,
    "Pitch": -1.5708,
    "Roll": 0.0
  },
  "DisplayCursor": true,
  "SendMouseMotion": true,
  "MouseInputType": "LookAtPlane",
  "PlaneNormal": {
    "X": 0.0,
    "Y": 1.0,
    "Z": 0.0
  },
  "MovementForceRotationType": "Custom",
  "MovementForceRotation": {
    "Yaw": 0.0,
    "Pitch": 0.0,
    "Roll": 0.0
  }
}
```

### Fixed Security Camera

```json
"Camera": {
  "IsFirstPerson": false,
  "PositionType": "Custom",
  "Position": {
    "X": 100.0,
    "Y": 50.0,
    "Z": 100.0
  },
  "RotationType": "Custom",
  "Rotation": {
    "Yaw": 0.785,
    "Pitch": -0.5,
    "Roll": 0.0
  },
  "AttachedToType": "None"
}
```

### Smooth Follow Camera

```json
"Camera": {
  "IsFirstPerson": false,
  "EyeOffset": true,
  "PositionLerpSpeed": 0.1,
  "RotationLerpSpeed": 0.15,
  "PositionDistanceOffsetType": "DistanceOffset",
  "PositionOffset": {
    "X": 1.0,
    "Y": 1.5,
    "Z": 4.0
  },
  "DisplayReticle": true,
  "SendMouseMotion": true,
  "MouseInputType": "LookAtTarget"
}
```

### Entity Follow Camera

```json
"Camera": {
  "IsFirstPerson": false,
  "AttachedToType": "Entity",
  "AttachedToEntityId": 12345,
  "EyeOffset": true,
  "PositionDistanceOffsetType": "DistanceOffset",
  "PositionOffset": {
    "X": 0.0,
    "Y": 2.0,
    "Z": 5.0
  }
}
```

---

## Field Interactions

### Position Fields Relationship

```
PositionType
├── AttachedToPlusOffset
│   ├── Uses: AttachedToType, EyeOffset, PositionOffset, PositionDistanceOffsetType, Distance
│   └── Ignores: Position
└── Custom
    ├── Uses: Position
    └── Ignores: AttachedToType, EyeOffset, PositionOffset, PositionDistanceOffsetType, Distance
```

### Rotation Fields Relationship

```
RotationType
├── AttachedToPlusOffset
│   ├── Uses: RotationOffset
│   └── Ignores: Rotation
└── Custom
    ├── Uses: Rotation
    └── Ignores: RotationOffset
```

### Input Fields Relationship

```
MouseInputType
├── LookAtTarget/LookAtTargetBlock/LookAtTargetEntity
│   ├── Uses: MouseInputTargetType, SendMouseMotion
│   └── Ignores: PlaneNormal
└── LookAtPlane
    ├── Uses: PlaneNormal, SendMouseMotion
    └── Ignores: MouseInputTargetType
```

---

## Troubleshooting

### Player is invisible
- Set `IsFirstPerson: false`

### Camera doesn't follow player rotation
- Ensure `PositionDistanceOffsetType: "DistanceOffset"`
- Set `EyeOffset: true`

### Targeting doesn't work / stuck on one block
- Set `SendMouseMotion: true`
- Set `MouseInputType: "LookAtTarget"`
- Set `ApplyLookType: "LocalPlayerLookOrientation"`

### Camera feels laggy
- Increase `PositionLerpSpeed` (closer to 1.0)
- Avoid using `Distance` with low lerp speeds; use `PositionOffset` instead

### Camera position is in world space instead of relative to player
- Set `PositionType: "AttachedToPlusOffset"` (not `Custom`)
- Set `PositionDistanceOffsetType: "DistanceOffset"` (not `None`)
