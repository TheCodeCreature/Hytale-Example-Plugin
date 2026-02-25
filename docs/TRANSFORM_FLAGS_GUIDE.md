# Transform Flags Guide

## Overview

`TransformFlags` allows you to control which transformations (anchor offset and rotations) are applied to individual shape operations in the `ShapeCompositor`. This gives you fine-grained control over how shapes behave relative to the camera/player position and orientation.

## Basic Concepts

By default, all shapes in the compositor:
- Follow the **anchor position** (typically the camera/player position)
- Rotate with the **camera view direction** (yaw and pitch)

Sometimes you want shapes to ignore certain transformations. For example:
- A floor exclusion zone should stay level (ignore pitch)
- A world-space marker should stay fixed (ignore all transformations)
- A shape that only rotates horizontally (ignore pitch, apply yaw)

## Usage

### Using Preset Flags

```java
// Apply all transformations (default behavior)
compositor.addOperation("my_shape", shape, OperationType.DEFINE, fillType)
    .withTransformFlags(TransformFlags.ALL)
    .build();

// Ignore all transformations - shape stays in world space
compositor.addOperation("world_marker", shape, OperationType.DEFINE, fillType)
    .withTransformFlags(TransformFlags.NONE)
    .build();

// Only apply rotations, ignore anchor offset
compositor.addOperation("rotating_shape", shape, OperationType.DEFINE, fillType)
    .withTransformFlags(TransformFlags.ROTATIONS_ONLY)
    .build();

// Only follow anchor, don't rotate
compositor.addOperation("following_shape", shape, OperationType.DEFINE, fillType)
    .withTransformFlags(TransformFlags.ANCHOR_ONLY)
    .build();
```

### Custom Transform Flags

Build custom flags for precise control:

```java
// Ignore pitch rotation (keep shape level)
TransformFlags levelShape = TransformFlags.builder()
    .ignorePitch()
    .build();

compositor.addOperation("floor_zone", floorShape, OperationType.EXCLUDE, null)
    .withTransformFlags(levelShape)
    .build();

// Only apply Y anchor offset (vertical follow only)
TransformFlags verticalFollow = TransformFlags.builder()
    .ignoreAnchorX()
    .ignoreAnchorZ()
    .ignoreRotations()
    .build();

// Ignore all anchor offsets but apply yaw rotation
TransformFlags yawOnly = TransformFlags.builder()
    .ignoreAnchor()
    .ignorePitch()
    .ignoreRoll()
    .build();
```

## Available Methods

### Builder Methods

- `ignoreAnchor()` - Ignore all anchor offsets (X, Y, Z)
- `ignoreAnchorX()` - Ignore X anchor offset
- `ignoreAnchorY()` - Ignore Y anchor offset
- `ignoreAnchorZ()` - Ignore Z anchor offset
- `ignoreRotations()` - Ignore all rotations (yaw, pitch, roll)
- `ignoreYaw()` - Ignore yaw rotation (side-to-side)
- `ignorePitch()` - Ignore pitch rotation (up-down)
- `ignoreRoll()` - Ignore roll rotation (barrel roll)

### Query Methods

- `shouldApplyAnchorX()` / `shouldApplyAnchorY()` / `shouldApplyAnchorZ()`
- `shouldApplyYaw()` / `shouldApplyPitch()` / `shouldApplyRoll()`
- `shouldApplyAnyRotation()` - Returns true if any rotation is enabled
- `shouldApplyAnyAnchor()` - Returns true if any anchor offset is enabled

## Common Use Cases

### Floor Exclusion (Ignore Pitch)

```java
// Floor should stay level regardless of camera pitch
TransformFlags levelFloor = TransformFlags.builder()
    .ignorePitch()
    .build();

compositor.addOperation("floor_exclusion",
    new Box(-radius, -radius, -radius, radius, 0, radius),
    OperationType.EXCLUDE,
    null)
    .withTransformFlags(levelFloor)
    .build();
```

### World-Space Marker

```java
// Shape stays at a fixed world position
compositor.addOperation("marker",
    markerShape,
    OperationType.DEFINE,
    fillType)
    .withTransformFlags(TransformFlags.NONE)
    .build();
```

### Horizontal-Only Rotation

```java
// Shape rotates with yaw but ignores pitch (like a compass)
TransformFlags horizontalOnly = TransformFlags.builder()
    .ignorePitch()
    .build();

compositor.addOperation("compass_shape",
    compassShape,
    OperationType.DEFINE,
    fillType)
    .withTransformFlags(horizontalOnly)
    .build();
```

### Vertical Follow Only

```java
// Shape only follows vertical position, stays fixed horizontally
TransformFlags verticalOnly = TransformFlags.builder()
    .ignoreAnchorX()
    .ignoreAnchorZ()
    .ignoreRotations()
    .build();

compositor.addOperation("vertical_indicator",
    indicatorShape,
    OperationType.DEFINE,
    fillType)
    .withTransformFlags(verticalOnly)
    .build();
```

## Implementation Notes

- Transform flags are **immutable** after the operation is built
- If no flags are specified, `TransformFlags.ALL` is used (all transformations enabled)
- Transform flags apply to the shape's geometry evaluation, not the fill type
- Anchor offsets are currently always 0 in the compositor (shapes are evaluated at the anchor), but the flag system is designed to support future offset features
