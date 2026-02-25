package com.UnobstructedThirdPerson.shape;

import com.UnobstructedThirdPerson.shape.fill.EmptyBlockFill;
import com.UnobstructedThirdPerson.shape.fill.PlaceholderFill;
import com.UnobstructedThirdPerson.shape.operation.OperationType;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.shape.Cylinder;
import com.hypixel.hytale.math.shape.Ellipsoid;
import com.hypixel.hytale.math.vector.Vector3i;

public class ShapeCompositorPresets extends ShapeCompositor{
    private int _radius = 8;

    public ShapeCompositorPresets(Vector3i anchor, int radius){
        super(anchor);
        _radius = radius;
    }

    public ShapeCompositorPresets(int radius){
        Vector3i anchor = new Vector3i(0, 0, 0);
        super(anchor);
        _radius = radius;
    }

    public ShapeCompositorPresets(){
        Vector3i _anchor = new Vector3i(0, 0, 0);
        super(_anchor);
    }

    public ShapeCompositor Test(){
        var x = _radius/2;
        var y = _radius/2;
        var z = _radius-2;
        var shape = new TransformedShape(
                new Ellipsoid(x,y,z),
                0, y/2, z);
//                0,0,0);

        // Operation 1: Define outer ellipsoid boundary (geometry only)
        this.addOperation("camera_volume",
                        shape,
                        OperationType.DEFINE,
                        new PlaceholderFill())
                .build();

//        // Operation 1: Define outer ellipsoid boundary (geometry only)
//        compositor.addOperation("empty_fill",
//                        shape,
//                        OperationType.FILL_REMAINING,
//                        new EmptyBlockFill())
//                .build();

        // Operation 5: Exclude floor
        this.addOperation("floor_exclusion",
                new Box(-_radius, -_radius, -_radius, _radius, 0, _radius),
                OperationType.EXCLUDE,
                null).build();

        return this;
    }

    public ShapeCompositor Original(){
        var shape = new TransformedShape(new Ellipsoid(_radius), 0,0,-_radius/2);

        // Operation 1: Define outer ellipsoid boundary (geometry only)
        this.addOperation("camera_volume",
                        shape,
                        OperationType.DEFINE,
                        null)
                .build();

        // Operation 1: Define outer ellipsoid boundary (geometry only)
        this.addOperation("empty_fill",
                        shape,
                        OperationType.FILL_REMAINING,
                        new EmptyBlockFill())
                .withReference("camera_volume")
                .build();

        // Operation 5: Exclude floor
        this.addOperation("floor_exclusion",
                new Box(-_radius, -_radius, -_radius, _radius, 0, _radius),
                OperationType.EXCLUDE,
                null).build();

        return this;
    }

    public ShapeCompositor HalfHalf(){
        var shape = new TransformedShape(
                new Ellipsoid(_radius),
                0,
                _radius+1,
                0);
        // Operation 1: Define outer ellipsoid boundary (geometry only)
        this.addOperation("camera_volume",
                new Ellipsoid(_radius),
                OperationType.DEFINE,
                null).build();  // No fill - just defines the region

        // Operation 3: Back half = empty blocks (fill remaining in ellipsoid)
        this.addOperation("empty_zone",
                        shape,  // No new geometry
                        OperationType.INTERSECT,
                        new EmptyBlockFill())  // PARAMETER: use air blocks
                .withReference("camera_volume")
                .build();

        // Operation 2: Front half (closer to player) = placeholders with hitbox-based shapes
        this.addOperation("transparent_zone",
                        null,
                        OperationType.FILL_REMAINING,
                        new PlaceholderFill())  // PARAMETER: auto-select placeholders based on hitbox
                .withReference("camera_volume")
                .build();

        // Operation 4: Exclude immediate player area (1x2x1 box around player)
        this.addOperation("player_exclusion",
                new Cylinder(2, 5, 5),
                OperationType.EXCLUDE,
                null).build();

        // Operation 5: Exclude floor
        this.addOperation("floor_exclusion",
                new Box(-_radius, -_radius, -_radius, _radius, 0, _radius),
                OperationType.EXCLUDE,
                null).build();

        return this;
    }

    /**
     * Example demonstrating TransformFlags feature.
     * Creates a camera volume with a floor exclusion that stays level (ignores pitch).
     * This prevents the floor from tilting when looking up/down.
     */
    public ShapeCompositor WithLevelFloor(){
        var x = _radius/2;
        var y = _radius/2;
        var z = _radius-2;
        var shape = new TransformedShape(
                new Ellipsoid(x,y,z),
                0, y/2, z);

        // Operation 1: Define camera volume with placeholder fill
        this.addOperation("camera_volume",
                        shape,
                        OperationType.DEFINE,
                        new PlaceholderFill())
                .build();

        // Operation 2: Floor exclusion that stays level (ignores pitch rotation)
        // This keeps the floor horizontal even when looking up or down
        TransformFlags levelFloor = TransformFlags.builder()
                .ignorePitch()
                .ignoreYaw()
                .build();

        this.addOperation("floor_exclusion",
                new Box(-_radius, -_radius, -_radius, _radius, 0, _radius),
                OperationType.EXCLUDE,
                null)
                .withTransformFlags(levelFloor)
                .build();

        return this;
    }
}
