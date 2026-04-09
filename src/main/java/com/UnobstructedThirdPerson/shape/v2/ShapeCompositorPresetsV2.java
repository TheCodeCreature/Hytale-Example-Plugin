package com.UnobstructedThirdPerson.shape.v2;

import com.UnobstructedThirdPerson.shape.CircularCone;
import com.UnobstructedThirdPerson.shape.SpatialOffset;
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
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;

import javax.annotation.Nonnull;

public class ShapeCompositorPresetsV2 extends ShapeCompositorV2 {

        private int _scale = 7;
        private static final double _yPivotOffset = 1.8;
        private static final double _xOffset = -1.0;
        private final TransformFlags _noMove = TransformFlags.builder()
                        .ignorePitch()
                        .ignoreYaw()
                        .build();
        private final TransformFlags _ignorePitch = TransformFlags.builder()
                        .ignorePitch()
                        .build();

        public ShapeCompositorPresetsV2(@Nonnull Vector3d anchor, int radius) {
                super(new SpatialOffset(_xOffset, _yPivotOffset, 0));
                setAnchor(anchor);
                _scale = radius;
        }

        public ShapeCompositorPresetsV2(int radius) {
                super(new SpatialOffset(_xOffset, _yPivotOffset, 0));
                _scale = radius;
        }

        public ShapeCompositorPresetsV2() {
                super(new SpatialOffset(_xOffset, _yPivotOffset, 0));
        }

        public ShapeCompositorV2 DefaultViewField() {
                // #region Common Variables
                int idTracker = 0;
                double baseRadius = _scale;
                double baseHeight = _scale * 1.5;

                double yOffset = 0;
                double yOffsetFloor = -_yPivotOffset;
                double zOffset = 0;

                double pitchRadianOffset = -Math.toRadians(15);
                double yawRadianOffset = -Math.toRadians(5);
                // #endregion
                // #region OuterCone
                String outerConeId = idTracker++ + "";
                Shape outerCone = new TransformedShape(
                                new CircularCone(baseRadius, baseHeight),
                                0, yOffset, -(zOffset), yawRadianOffset, pitchRadianOffset, 0);
                this.addOperation(outerConeId,
                                outerCone,
                                OperationTypeV2.DEFINE)
                                .withFill(new EmptyBlockFillV2())
                                // .withShadowCubes(DebugUtils.COLOR_BLACK)
                                .build();

                this.addOperation(idTracker++ + "",
                                outerCone,
                                OperationTypeV2.DEFINE)
                                .withDebugBoundingShape(DebugUtils.COLOR_WHITE, DebugShape.Cone)
                                .build();
                // #endregion
                // #region InnerViewBox
                String innerViewId = idTracker++ + "";
                double innerViewBoxXScale = 1.5;
                Shape innerViewConeShape = new TransformedShape(
                                new CircularCone(innerViewBoxXScale, baseHeight),
                                0, 0, -(zOffset), yawRadianOffset, pitchRadianOffset, 0);
                // Shape innerViewBoxShape = new TransformedShape(
                //                 new Box(-innerViewBoxXScale, 0, -_scale, innerViewBoxXScale, 3, -1),
                //                 -0, 1, 0,
                //                 yawRadianOffset, 0, 0);
                this.addOperation(innerViewId,
                                innerViewConeShape,
                                OperationTypeV2.Cut(outerConeId))
                                .withFill(new EmptyBlockFillV2())
                                // .withShadowCubes(DebugUtils.COLOR_CYAN)
                                // .withDebugBoundingShape(DebugUtils.COLOR_CYAN, DebugShape.Cube)
                                .withDebugBoundingShape(DebugUtils.COLOR_CYAN, DebugShape.Cone)
                                .build();
                // #endregion
                // #region SideViewBox
                // String SideViewBoxId = idTracker++ + "";
                // Shape sideViewBox = new TransformedShape(
                //                 new Box(-2, 0, -1, 2, 1, 0),
                //                 xOffset, 1, -1,
                //                 yawRadianOffset, 0, 0);
                // this.addOperation(SideViewBoxId,
                //                 sideViewBox,
                //                 OperationTypeV2.DEFINE)
                //                 .withFill(new EmptyBlockFillV2())
                //                 .withShadowCubes(DebugUtils.COLOR_PURPLE)
                //                 // .withDebugBoundingShape(DebugUtils.COLOR_PURPLE, DebugShape.Cube)
                //                 .withTransformFlags(_ignorePitch)
                //                 .build();
                // #endregion
                // #region PlayerFacingWall
                String ignorePlayerFacingWallId = idTracker++ + "";
                Shape ignorePlayerFacingWall = new TransformedShape(
                                new Box(-_scale, 1, 0, _scale, 2, _scale),
                                0, yOffsetFloor, 0,
                                yawRadianOffset, 0, 0);
                this.addOperation(ignorePlayerFacingWallId,
                                ignorePlayerFacingWall,
                                OperationTypeV2.EXCLUDE)
                                .withTransformFlags(_ignorePitch)
                                // .withShadowCubes(DebugUtils.COLOR_LIME)
                                .withDebugBoundingShape(DebugUtils.COLOR_LIME, DebugShape.Cube)
                                .build();
                // #endregion
                // #region PlayerFacingWallPlus
                String ignorePlayerFacingWallPlusId = idTracker++ + "";
                Shape ignorePlayerFacingWallPlus = new TransformedShape(
                                new Box(-_scale, 2, 1, _scale, _scale, _scale),
                                0, yOffsetFloor, 0,
                                yawRadianOffset, 0, 0);
                this.addOperation(ignorePlayerFacingWallPlusId,
                                ignorePlayerFacingWallPlus,
                                OperationTypeV2.EXCLUDE)
                                .withTransformFlags(_ignorePitch)
                                // .withShadowCubes(DebugUtils.COLOR_GREEN)
                                .withDebugBoundingShape(DebugUtils.COLOR_GREEN, DebugShape.Cube)
                                .build();
                // #endregion
                // #region PlayerFeetBox
                String IgnorePlayerFeetPlusId = idTracker++ + "";
                Shape IgnoredPlayerFeetPlusBox = new TransformedShape(
                                new Box(-_scale, -_scale, -_scale, _scale, 1, _scale),
                                0, yOffsetFloor, 0,
                                yawRadianOffset, 0, 0);
                this.addOperation(IgnorePlayerFeetPlusId,
                                IgnoredPlayerFeetPlusBox,
                                OperationTypeV2.EXCLUDE)
                                .withTransformFlags(_ignorePitch)
                                // .withShadowCubes(DebugUtils.COLOR_RED)
                                .withDebugBoundingShape(DebugUtils.COLOR_RED, DebugShape.Cube)
                                .build();
                // #endregion
                // #region IgnorePitch UNION (sideViewBox + all ignorePitch EXCLUDE boxes)
                String ignorePitchUnionId = idTracker++ + "";
                this.addOperation(ignorePitchUnionId,
                                null,
                                OperationTypeV2.Union(
                                        // SideViewBoxId,
                                        ignorePlayerFacingWallId,
                                        ignorePlayerFacingWallPlusId,
                                        IgnorePlayerFeetPlusId))
                                .withTransformFlags(_ignorePitch)
                                .build();
                // #endregion
                return this;
        }

        public ShapeCompositorV2 SimpleTestCone() {
                int idTracker = 0;
                double height = _scale * 1.5;
                double radius = _scale;

                CircularCone cone = new CircularCone(radius, height);

                this.addOperation(idTracker++ + "",
                                cone,
                                OperationTypeV2.DEFINE)
                                .withFill(new EmptyBlockFillV2())
                                .withShadowCubes(DebugUtils.COLOR_GRAY)
                                .build();

                this.addOperation(idTracker++ + "",
                                new Box(-_scale, -_scale, -0.5, _scale, -1, _scale),
                                OperationTypeV2.EXCLUDE)
                                .withTransformFlags(_ignorePitch)
                                .build();

                return this;
        }
}
