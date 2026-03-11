package com.UnobstructedThirdPerson.shape;

import com.UnobstructedThirdPerson.shape.fill.EmptyBlockFill;
import com.UnobstructedThirdPerson.shape.fill.PlaceholderFill;
import com.UnobstructedThirdPerson.shape.operation.OperationType;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.shape.Ellipsoid;
import com.hypixel.hytale.math.vector.Vector3i;

public class ShapeCompositorPresets extends ShapeCompositor{
    private int _scale = 7;
    private final TransformFlags _noMove = TransformFlags.builder()
            .ignorePitch()
            .ignoreYaw()
            .build();
    private final TransformFlags _ignorePitch = TransformFlags.builder()
            .ignorePitch()
            .build();

    public ShapeCompositorPresets(Vector3i anchor, int radius){
        super(anchor);
        _scale = radius;
    }

    public ShapeCompositorPresets(int radius){
        Vector3i anchor = new Vector3i(0, 0, 0);
        super(anchor);
        _scale = radius;
    }

    public ShapeCompositorPresets(){
        Vector3i _anchor = new Vector3i(0, 0, 0);
        super(_anchor);
    }

    public ShapeCompositor Test(){
        var x = _scale /2;
        var y = _scale /2;
        var z = _scale -2;
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
                new Box(-_scale, -_scale, -_scale, _scale, 0, _scale),
                OperationType.EXCLUDE,
                null).build();

        return this;
    }

    public ShapeCompositor Original(){
        var shape = new TransformedShape(new Ellipsoid(_scale), 0,0,-_scale /2);

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
                new Box(-_scale, -_scale, -_scale, _scale, 0, _scale),
                OperationType.EXCLUDE,
                null).build();

        return this;
    }

    public ShapeCompositor HalfHalf(){
        int idTracker = 0;
//        var shape = new TransformedShape(
//                new Ellipsoid(_scale),
//                0,
//                _scale+1,
//                0);
        // Operation 1: Define outer ellipsoid boundary (geometry only)
        var camera_volume = idTracker++ + "";
        this.addOperation(camera_volume,
                new Ellipsoid(_scale),
                OperationType.DEFINE,
                new EmptyBlockFill()).build();  // No fill - just defines the region

//        // Operation 3: Back half = empty blocks (fill remaining in ellipsoid)
//        this.addOperation("empty_zone",
//                        shape,  // No new geometry
//                        OperationType.INTERSECT,
//                        new EmptyBlockFill())  // PARAMETER: use air blocks
//                .withReference(camera_volume)
//                .build();

        // Operation 2: Front half (closer to player) = placeholders with hitbox-based shapes
        this.addOperation("transparent_zone",
                        new Box(-_scale, _scale, -_scale, _scale, _scale, 0),
                        OperationType.INTERSECT,
                        new PlaceholderFill())  // PARAMETER: auto-select placeholders based on hitbox
                .withReference(camera_volume)
                .build();

        // Operation 4: Exclude immediate player area (1x2x1 box around player)
//        this.addOperation("player_exclusion",
//                new Cylinder(2, 5, 5),
//                OperationType.EXCLUDE,
//                null).build();

        // Operation 5: Exclude floor
        this.addOperation("floor_exclusion",
                new Box(-_scale, -_scale, -_scale, _scale, 0, _scale),
                OperationType.EXCLUDE,
                null)
                .withTransformFlags(_noMove)
                .build();

        return this;
    }

    public ShapeCompositor WithPyramidShape(){
        int idTracker = 0;
        double height = _scale*1.5;
        double xWidth = (_scale *2)-1;
        double yWidth = _scale;

        // Square-bottom pyramid shape (base centered around anchor).
        var firstShape = idTracker++ + "";
        this.addOperation(firstShape,
                            new TransformedShape(
                                new SquarePyramid(xWidth, yWidth, height),
                                0, 2, -(height-4)),
//                                0, -(Math.toRadians(5)),0),
                        OperationType.DEFINE,
                        new EmptyBlockFill())
//                .withTransformFlags(_noMove)
                .build();
        
        
//        //Define negative space
//        this.addOperation(idTracker++ + "",
//                        new TransformedShape(
//                                new SquarePyramid(xWidth, yWidth, scaledRadius),
//                                0, 1, -(scaledRadius-1.5)),
////                                Math.toRadians(135),0,0),
//                        OperationType.FILL,
//                        new EmptyBlockFill())
////                .withTransformFlags(_noMove)
//                .withReference(firstShape)
//                .build();

//        this.addOperation(idTracker++ + "",
//                        null,
//                        OperationType.FILL_REMAINING,
//                        new PlaceholderFill())
////                .withTransformFlags(_noMove)
//                .withReference(firstShape)
//                .build();
//
//        //Define negative space
//        this.addOperation(idTracker++ + "",
//                        new TransformedShape(
//                                new SquarePyramid(xWidth, yWidth*1.2, scaledRadius),
//                                0, 1, -(scaledRadius-1)),
////                                Math.toRadians(135),0,0),
//                        OperationType.CUT,
//                        null)
////                .withTransformFlags(_noMove)
//                .withReference(firstShape)
//                .build();
//
//        // Placeholder test
//        this.addOperation(idTracker++ + "",
//                        new TransformedShape(
//                            new Box(-3, -3, 0, 3, 3, 3),
//                            0,1,0.5),
//                        OperationType.INTERSECT,
//                        new PlaceholderFill())
////                .withTransformFlags(_noMove)
//                .withReference(firstShape)
//                .build();

        //Long Ellipsoid
//        this.addOperation(idTracker++ + "",
//                        new TransformedShape(
//                                        new Ellipsoid(_scale, halfRadius*1.5, zOffset+2),
//                                -1, 2, -(zOffset)),
//                                //Math.toRadians(135),0,0),
//                        OperationType.DEFINE,
//                        new EmptyBlockFill())
//                .build();

//        //Extend box base
//        this.addOperation(idTracker++ + "",
//                        new Box(-(scaledRadius+3), -(scaledRadius+3), -(scaledRadius+3), -scaledRadius, -scaledRadius, -scaledRadius),
//                        OperationType.DEFINE,
//                        new EmptyBlockFill())
//                .build();

////        Cut base of shape
//        this.addOperation(idTracker++ + "",
//                            new Box(-_scale, -_scale, -_scale,_scale,0,_scale),
//                        OperationType.EXCLUDE,
//                        null)
//                .build();
//
////        Ignore tiny area around player minus back
//        this.addOperation(idTracker++ + "",
//                        new Box(-1, -1, -1,1,1,1),
//                        OperationType.EXCLUDE,
//                        null)
//                .withTransformFlags(_noMove)
//                .build();

        //Cut back of shape
//        this.addOperation(idTracker++ + "",
//                        new Box(-_scale, -_scale, -(_scale+zOffset),_scale,_scale,-zOffset),
//                        OperationType.EXCLUDE,
//                        null)
//                .build();


        //Cut front of shape
        this.addOperation(idTracker++ + "",
                        new Box(-2.5, -2, 0.5, 2.5, 3, 3),
                        OperationType.DEFINE,
                        new PlaceholderFill())
                .withTransformFlags(_ignorePitch)
                .build();

        //Cut front of palyer view
        this.addOperation(idTracker++ + "",
                        new Box(-5, -5, 0.5, 5, 1, _scale),
                        OperationType.EXCLUDE,
                        null)
                .withTransformFlags(_ignorePitch)
                .build();

        // Operation Final: Floor exclusion that stays level (ignores pitch rotation)
        // This keeps the floor horizontal even when looking up or down
        // leave ground cutout for camera behind player
        this.addOperation(idTracker + "",
                new Box(-_scale, -_scale, -0.5,_scale,-1,_scale),
                OperationType.EXCLUDE,
                null)
                .withTransformFlags(_ignorePitch)
                .build();

        //ignore player space
//        this.addOperation(idTracker + "",
//                        new Box(-1, -1, 1,1,1,1),
//                        OperationType.EXCLUDE,
//                        null)
//                .withTransformFlags(_noMove)
//                .build();

        return this;
    }

    /**
     * Example demonstrating TransformFlags feature.
     * Creates a camera volume with a floor exclusion that stays level (ignores pitch).
     * This prevents the floor from tilting when looking up/down.
     */
    public ShapeCompositor WithLevelFloor(){
        int idTracker = 0;
        double halfRadius = _scale /2d;

        //Rotated Cube for a cone/pyramid like shape.
        //TODO: implement cone/pyramid shape
        //TODO: Define is now acting as a fill of empty
        // and not technically functional with intersect as a shape refinement cutting tool
        double halfRightAngleRad = Math.toRadians(45);
        this.addOperation(idTracker++ + "",
                        new TransformedShape(
                                new TransformedShape(
                                    new Box(-(halfRadius-1), -(halfRadius-1), -(halfRadius-1),
                                            halfRadius, halfRadius, halfRadius),
                                    0,0,0,
                                    halfRightAngleRad, halfRightAngleRad, -halfRightAngleRad),
                                0, 1, -((int)halfRadius),
                                Math.toRadians(135),0,0),
                        OperationType.DEFINE,
                        new EmptyBlockFill())
                .build();

        //Long Ellipsoid
        int zOffset = 2* _scale;
//        this.addOperation(idTracker++ + "",
//                        new TransformedShape(
//                                        new Ellipsoid(_scale, halfRadius*1.5, zOffset+2),
//                                -1, 2, -(zOffset)),
//                                //Math.toRadians(135),0,0),
//                        OperationType.DEFINE,
//                        new EmptyBlockFill())
//                .build();

        //Cut base of top shape
        this.addOperation(idTracker++ + "",
                        new Box(-_scale, -_scale, -_scale, _scale,0, _scale),
                        OperationType.EXCLUDE,
                        null)
                .build();

        //Cut back of top shape
        this.addOperation(idTracker++ + "",
                        new Box(-_scale, -_scale, -(_scale +zOffset), _scale, _scale,-zOffset),
                        OperationType.EXCLUDE,
                        null)
                .build();

        //Cut front of top shape
        this.addOperation(idTracker++ + "",
                        new Box(-_scale, -_scale, 1, _scale, _scale, _scale),
                        OperationType.EXCLUDE,
                        null)
                .build();

        // Operation Final: Floor exclusion that stays level (ignores pitch rotation)
        // This keeps the floor horizontal even when looking up or down
        var ignorePitch = TransformFlags.builder()
                .ignorePitch()
                .build();
        this.addOperation(idTracker + "",
                new Box(-_scale, -_scale, -2, _scale,1, _scale),
                OperationType.EXCLUDE,
                null)
                .withTransformFlags(ignorePitch)
                .build();

        return this;
    }
}
