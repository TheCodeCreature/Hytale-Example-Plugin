package com.UnobstructedThirdPerson.shape.v2;

import com.UnobstructedThirdPerson.shape.CircularCone;
import com.UnobstructedThirdPerson.shape.TransformFlags;
import com.UnobstructedThirdPerson.shape.TransformedShape;
import com.UnobstructedThirdPerson.shape.v2.fill.EmptyBlockFillV2;
import com.UnobstructedThirdPerson.shape.v2.operation.OperationTypeV2;
import com.UnobstructedThirdPerson.shape.v2.visual.DebugStyle;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.shape.Ellipsoid;
import com.hypixel.hytale.math.shape.Shape;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;

import javax.annotation.Nonnull;

public class ShapeCompositorPresetsV2 extends ShapeCompositorV2 {

    private int _scale = 7;
    private final TransformFlags _noMove = TransformFlags.builder()
            .ignorePitch()
            .ignoreYaw()
            .build();
    private final TransformFlags _ignorePitch = TransformFlags.builder()
            .ignorePitch()
            .build();

    public ShapeCompositorPresetsV2(@Nonnull Vector3d anchor, int radius) {
        super(anchor);
        _scale = radius;
    }

    public ShapeCompositorPresetsV2(int radius) {
        super(new Vector3d(0, 0.5, 0));
        _scale = radius;
    }

    public ShapeCompositorPresetsV2() {
        super(new Vector3d(0, -7, 0));
    }

    public ShapeCompositorV2 LayeredConePreset() {
        int idTracker = 0;
        double baseRadius = _scale;
        double baseHeight = _scale;
        double baseHeightOffset = baseHeight - 2;

//        double middleRadius = baseRadius * 0.6;
//        double middleHeight = baseHeight;

        double innerRadius = baseRadius * 0.5;
        double innerHeight = baseHeight;

        double xOffset = 0;
        double yOffset = 0;

        double pitchDegrees = 0;
        
        Shape outerCone = new TransformedShape(
                new CircularCone(baseRadius, baseHeight),
                xOffset, yOffset, -(baseHeightOffset)
                ,0,-Math.toRadians(pitchDegrees),0);
//        Shape outerSphere = new TransformedShape(
//                new Ellipsoid(baseRadius),
//                xOffset, yOffset, -(baseHeightOffset)
//                ,0,-Math.toRadians(pitchDegrees),0);
//        Shape middleCone = new TransformedShape(
//                new CircularCone(middleRadius, middleHeight),
//                xOffset, yOffset, -(baseHeightOffset+1)
//                ,0,-Math.toRadians(pitchDegrees),0);
        Shape innerCone = new TransformedShape(
                new CircularCone(innerRadius, innerHeight),
                xOffset, yOffset-1, -(baseHeightOffset)
                ,0,-Math.toRadians(pitchDegrees),0);

        String outerConeId = idTracker++ + "";
        this.addOperation(outerConeId,
                        outerCone,
                        OperationTypeV2.DEFINE,
                        new EmptyBlockFillV2())
//                .withDebugColor(DebugUtils.COLOR_BLACK)
                .build();
        
//        String middleShellId = idTracker++ + "";
//        this.addOperation(middleShellId,
//                        middleCone,
//                        OperationTypeV2.DEFINE,
//                        new EmptyBlockFillV2())
//                .withDebugColor(DebugUtils.COLOR_GRAY)
//                .withReference(outerConeId)
//                .build();

        String innerConeId = idTracker++ + "";
        this.addOperation(innerConeId,
                        innerCone,
                        OperationTypeV2.DEFINE,
                        new EmptyBlockFillV2())
//                .withDebugColor(DebugUtils.COLOR_WHITE)
                .build();

        String SideViewBoxId = idTracker++ + "";
        Shape sideViewBox = new TransformedShape(
                new Box(-3, 1,-1,1,1,0),
                0, -4, 0);
        this.addOperation(SideViewBoxId,
                        sideViewBox,
                        OperationTypeV2.DEFINE,
                        new EmptyBlockFillV2())
                .withDebugColor(DebugUtils.COLOR_PURPLE)
                .withTransformFlags(_ignorePitch)
                .build();

//        String IgnorePlayerSpaceId = idTracker++ + "";
//        Shape IgnoredPlayerSpaceBox = new TransformedShape(
//                new Box(-_scale, -_scale, 1, _scale, 0, _scale),
//                0, 0, 0);
////                -Math.toRadians(15),0,0);
//        this.addOperation(IgnorePlayerSpaceId,
//                        IgnoredPlayerSpaceBox,
//                        OperationTypeV2.EXCLUDE,
////                        OperationTypeV2.DEFINE,
////                        new EmptyBlockFillV2())
//                null)
//                .withTransformFlags(_ignorePitch)
//                .withDebugColor(DebugUtils.COLOR_LIME)
////                .withReference(outerConeId)
//                .build();
//
//        String IgnorePlayerFeetId = idTracker++ + "";
//        Shape IgnoredPlayerFeetBox = new TransformedShape(
//                new Box(-1, -1, -0, 1, 0, 1),
//                0, -4, 0);
//        this.addOperation(IgnorePlayerFeetId,
//                        IgnoredPlayerFeetBox,
//                        OperationTypeV2.EXCLUDE,
////                        OperationTypeV2.DEFINE,
////                        new EmptyBlockFillV2())
//                null)
//                .withTransformFlags(_ignorePitch)
//                .withDebugColor(DebugUtils.COLOR_RED)
////                .withReference(outerConeId)
//                .build();

//        this.addOperation(IgnorePlayerSpaceId,
//                        IgnoredPlayerSpaceBox,
//                        OperationTypeV2.SUBTRACT,
//                        null)
//                .withTransformFlags(_ignorePitch)
//                .withReference(Inner)
//                .build();
        
        return this;
    }

    public ShapeCompositorV2 SimpleTestCone() {
        int idTracker = 0;
        double height = _scale * 1.5;
        double radius = _scale;
        
        CircularCone cone = new CircularCone(radius, height);
        
        this.addOperation(idTracker++ + "",
                        cone,
                        OperationTypeV2.DEFINE,
                        new EmptyBlockFillV2())
                .withDebugColor(DebugUtils.COLOR_GRAY)
                .build();
        
        this.addOperation(idTracker++ + "",
                        new Box(-_scale, -_scale, -0.5, _scale, -1, _scale),
                        OperationTypeV2.EXCLUDE,
                        null)
                .withTransformFlags(_ignorePitch)
                .build();
        
        return this;
    }
}
