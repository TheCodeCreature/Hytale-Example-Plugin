# Shape Transformation Examples

The `TransformedShape` class allows you to apply translation and rotation to any shape in the compositor system.

## Basic Translation

Move a shape by an offset:

```java
Shape cylinder = new Cylinder(5.0, 10.0);

// Move 10 blocks forward (Z), 5 blocks up (Y)
Shape translated = new TransformedShape(cylinder, 0, 5, 10);

compositor.addOperation("moved_cylinder", translated, OperationType.DEFINE, new EmptyBlockFill());
```

## Rotation (Radians)

Rotate shapes using radians:

```java
Shape box = new Box(-5, -5, -5, 5, 5, 5);

// Rotate 45 degrees (π/4 radians) around Y axis (yaw)
Shape rotated = new TransformedShape(box, 0, 0, 0, Math.PI / 4, 0, 0);

compositor.addOperation("rotated_box", rotated, OperationType.DEFINE, new EmptyBlockFill());
```

## Using the Fluent Builder API

The builder provides a cleaner syntax:

```java
Shape ellipsoid = new Ellipsoid(8.0, 4.0, 8.0);

// Translate and rotate using builder
Shape transformed = TransformedShape.builder(ellipsoid)
    .translate(10, 0, -5)           // Move 10 blocks right, 5 blocks back
    .rotateYawDegrees(90)            // Rotate 90° horizontally
    .rotatePitchDegrees(15)          // Tilt 15° up
    .build();

compositor.addOperation("fancy_shape", transformed, OperationType.DEFINE, new AutoPlaceholderFill());
```

## Rotation Axes Explained

- **Yaw** (rotation around Y axis): Horizontal rotation (like turning your head left/right)
- **Pitch** (rotation around X axis): Vertical tilt (like nodding your head up/down)
- **Roll** (rotation around Z axis): Barrel roll (like tilting your head left/right)

## Complete Example: Camera Transparency with Rotated Shapes

```java
ShapeCompositor compositor = new ShapeCompositor(new Vector3i(0, 0, 0));

// Create a cylinder pointing forward (along Z axis)
Shape cylinder = new Cylinder(3.0, 15.0);

// Rotate it to point upward (90° pitch) and move it in front of camera
Shape rotatedCylinder = TransformedShape.builder(cylinder)
    .translate(0, 0, 5)              // 5 blocks in front
    .rotatePitchDegrees(90)          // Point upward
    .build();

// Front zone: rotated cylinder with placeholders
compositor.addOperation("front", rotatedCylinder, OperationType.DEFINE, new AutoPlaceholderFill());

// Back zone: ellipsoid behind camera with empty blocks
Shape backEllipsoid = TransformedShape.builder(new Ellipsoid(4.0, 4.0, 4.0))
    .translate(0, 0, -8)             // 8 blocks behind
    .build();

compositor.addOperation("back", backEllipsoid, OperationType.DEFINE, new EmptyBlockFill());

// Create volume
CameraTransparencyVolume volume = CameraTransparencyVolume.getOrCreate(playerRef, world, compositor);
```

## Advanced: Combining Multiple Transformations

```java
// Create a "tunnel" effect by rotating and positioning multiple cylinders
for (int i = 0; i < 5; i++) {
    Shape cylinder = new Cylinder(2.0, 8.0);
    
    Shape transformed = TransformedShape.builder(cylinder)
        .translate(0, 0, i * 10)                    // Space them out
        .rotateYawDegrees(i * 15)                   // Rotate each one
        .rotatePitchDegrees(10)                     // Slight upward tilt
        .build();
    
    compositor.addOperation("tunnel_" + i, transformed, 
        OperationType.DEFINE, new AutoPlaceholderFill());
}
```

## Tips

1. **Rotation Order**: Rotations are applied in order: Roll → Pitch → Yaw
2. **Degrees vs Radians**: Use `rotatePitchDegrees()` for degrees, or `rotatePitch()` for radians
3. **Performance**: Rotation calculations are cached per-block, so complex rotations don't significantly impact performance
4. **Bounding Box**: The bounding box is conservative (slightly larger than needed) to ensure all rotated blocks are included
5. **Translation First**: When combining translation and rotation, the shape rotates around its local origin (0,0,0), then translates

## Common Use Cases

### Angled Camera View Cone
```java
Shape cone = new Cylinder(2.0, 20.0); // Narrow cone
Shape viewCone = TransformedShape.builder(cone)
    .rotatePitchDegrees(-10)  // Angle slightly downward
    .build();
```

### Side-by-Side Zones
```java
// Left zone
Shape leftBox = TransformedShape.builder(new Box(-5, -5, -5, 5, 5, 5))
    .translate(-10, 0, 0)
    .build();

// Right zone  
Shape rightBox = TransformedShape.builder(new Box(-5, -5, -5, 5, 5, 5))
    .translate(10, 0, 0)
    .build();
```

### Rotating Platform
```java
// Rotate a flat box to create an angled platform
Shape platform = TransformedShape.builder(new Box(-10, -1, -10, 10, 1, 10))
    .rotateRollDegrees(15)    // Tilt sideways
    .translate(0, 5, 0)        // Raise it up
    .build();
```
