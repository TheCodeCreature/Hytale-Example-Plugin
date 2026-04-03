package com.UnobstructedThirdPerson.shape.v2;

import com.UnobstructedThirdPerson.shape.CircularCone;
import com.UnobstructedThirdPerson.shape.TransformFlags;
import com.UnobstructedThirdPerson.shape.v2.fill.EmptyBlockFillV2;
import com.UnobstructedThirdPerson.shape.v2.operation.OperationTypeV2;
import com.UnobstructedThirdPerson.shape.v2.visual.DebugStyle;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import org.jetbrains.annotations.Debug;

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
        double baseHeight = _scale * 1.5;
        double baseRadius = _scale;
        
        double middleRadius = baseRadius * 0.6;
        double middleHeight = baseHeight * 0.6;

        double innerRadius = baseRadius * 0.3;
        double innerHeight = baseHeight * 0.3;
        
        DebugStyle noDebug = DebugStyle.enabled(DebugStyle.EMPTY_BLACK, 0.15f);
        DebugStyle middleDebug = DebugStyle.enabled(DebugStyle.DARK_GREY, 0.5f);
        DebugStyle outerDebug = DebugStyle.enabled(DebugStyle.DARK_GREY, 0.15f);
        
        CircularCone innerCone = new CircularCone(innerRadius, innerHeight);
        CircularCone middleCone = new CircularCone(middleRadius, middleHeight);
        CircularCone outerCone = new CircularCone(baseRadius, baseHeight);
        
        String outerConeId = idTracker++ + "";
        this.addOperation(outerConeId,
                        outerCone,
                        OperationTypeV2.DEFINE,
                        new EmptyBlockFillV2())
                .withDebugStyle(null)
                .build();
        
//        String middleConeId = idTracker++ + "";
//        this.addOperation(middleConeId,
//                        middleCone,
//                        OperationTypeV2.SUBTRACT,
//                        null)
//                .withReference(outerConeId)
//                .build();
        
//        String middleShellId = idTracker++ + "";
//        this.addOperation(middleShellId,
//                        middleCone,
//                        OperationTypeV2.DEFINE,
//                        new EmptyBlockFillV2())
//                .withDebugStyle(middleDebug)
//                .build();
//
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
//                .withDebugStyle(noDebug)
//                .build();
        
//        this.addOperation(idTracker++ + "",
//                        new Box(-_scale, -_scale, -0.5, _scale, -1, _scale),
//                        OperationTypeV2.EXCLUDE,
//                        null)
//                .withTransformFlags(_ignorePitch)
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
