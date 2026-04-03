package com.UnobstructedThirdPerson.shape.v2;

import com.UnobstructedThirdPerson.shape.CircularCone;
import com.UnobstructedThirdPerson.shape.SquarePyramid;
import com.UnobstructedThirdPerson.shape.TransformFlags;
import com.UnobstructedThirdPerson.shape.TransformedShape;
import com.UnobstructedThirdPerson.shape.v2.fill.EmptyBlockFillV2;
import com.UnobstructedThirdPerson.shape.v2.operation.OperationTypeV2;
import com.UnobstructedThirdPerson.shape.v2.visual.DebugStyle;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.shape.Shape;
import com.hypixel.hytale.math.vector.Vector3i;

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

    public ShapeCompositorPresetsV2(@Nonnull Vector3i anchor, int radius) {
        super(anchor);
        _scale = radius;
    }

    public ShapeCompositorPresetsV2(int radius) {
        super(new Vector3i(0, 0, 0));
        _scale = radius;
    }

    public ShapeCompositorPresetsV2() {
        super(new Vector3i(0, 0, 0));
    }

    public ShapeCompositorV2 LayeredConePreset() {
        int idTracker = 0;
        double baseRadius = _scale;
        double baseHeight = _scale;
        double baseHeightOffset = baseHeight - 2;

        double middleRadius = baseRadius * 0.6;
        double middleHeight = baseHeight * 0.6;

        double innerRadius = baseRadius * 0.3;
        double innerHeight = baseHeight;
        
//        DebugStyle middleDebug = DebugStyle.enabled(DebugStyle.DARK_GREY, 0.5f);
//        DebugStyle outerDebug = DebugStyle.enabled(DebugStyle.PLACEHOLDER_CYAN, 0.15f);


        Shape outerCone = new TransformedShape(
                new CircularCone(baseRadius, baseHeight),
                0.5, 3.5, -(baseHeightOffset)
                ,0,-Math.toRadians(15),0);
        Shape middleCone = new TransformedShape(
                new CircularCone(baseRadius, baseHeight),
                0.5, 3, -(baseHeightOffset));
        Shape innerCone = new CircularCone(innerRadius, innerHeight);

        String outerConeId = "OuterCone";
        this.addOperation(outerConeId,
                        outerCone,
                        OperationTypeV2.DEFINE,
                        new EmptyBlockFillV2())
//                .withDebugStyle(DebugStyle.LIGHT_GREY_STYLE)
                .build();
        
//        String middleConeId = idTracker++ + "";
//        this.addOperation(middleConeId,
//                        middleCone,
//                        OperationTypeV2.CUT,
//                        new EmptyBlockFillV2())
//                .withReference(outerConeId)
//                .build();
        
//        String middleShellId = idTracker++ + "";
//        this.addOperation(middleShellId,
//                        middleCone,
//                        OperationTypeV2.DEFINE,
//                        new EmptyBlockFillV2())
////                .withDebugStyle(DebugStyle.CYAN_STYLE)
//                .build();

//        String innerConeId = idTracker++ + "";
//        this.addOperation(innerConeId,
//                        innerCone,
//                        OperationTypeV2.SUBTRACT,
//                        null)
//                .withReference(middleShellId)
//                .build();
        
//        String innerShellId = idTracker++ + "";
//        this.addOperation(innerShellId,
//                        innerCone,
//                        OperationTypeV2.DEFINE,
//                        new EmptyBlockFillV2())
//                .build();

//        String FrontViewBoxId = idTracker++ + "";
//        Shape FrontViewBox = new TransformedShape(
//                new Box(-1, 0,0,2,1,2),
//                0, 0, 0);
//
//        this.addOperation(FrontViewBoxId,
//                        FrontViewBox,
//                        OperationTypeV2.DEFINE,
//                        new EmptyBlockFillV2())
//                .withDebugStyle(DebugStyle.CYAN_STYLE)
//                .withTransformFlags(_ignorePitch)
//                .build();

        String IgnorePlayerSpaceId = idTracker++ + "";
        Shape IgnoredPlayerSpaceBox = new TransformedShape(
                new Box(-_scale, -_scale, -0, _scale, 2, _scale),
                0, 0, 0);

//        this.addOperation(IgnorePlayerSpaceId,
//                        IgnoredPlayerSpaceBox,
////                        OperationTypeV2.EXCLUDE,
////                        null)
//                        OperationTypeV2.DEFINE,
//                        new EmptyBlockFillV2())
//                .withDebugStyle(DebugStyle.CYAN_STYLE)
//                .withTransformFlags(_ignorePitch)
//                .build();

        this.addOperation(IgnorePlayerSpaceId,
                        IgnoredPlayerSpaceBox,
//                        OperationTypeV2.EXCLUDE,
                       OperationTypeV2.SUBTRACT,
                        // OperationTypeV2.DEFINE,
//                        new EmptyBlockFillV2())
                        null)
//                .withDebugStyle(DebugStyle.CYAN_STYLE)
                .withTransformFlags(_ignorePitch)
                .withReference(outerConeId)
                .build();




        String SideViewBoxId = idTracker++ + "";
        Shape sideViewBox = new TransformedShape(
                new Box(-3, 1,-2,1,2,0),
                0, 0, 0.5);

        this.addOperation(SideViewBoxId,
                        sideViewBox,
                        OperationTypeV2.DEFINE,
                        new EmptyBlockFillV2())
                .withDebugStyle(DebugStyle.MAGENTA_STYLE)
                .withTransformFlags(_ignorePitch)
                .build();
        
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
                .withDebugStyle(DebugStyle.DEFAULT_GREY)
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
