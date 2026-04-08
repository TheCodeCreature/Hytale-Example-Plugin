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
        private static final double _yPivotOffset = 1.5;
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
                super(new Vector3d(0, 0, 0));
                _scale = radius;
        }

        public ShapeCompositorPresetsV2() {
                super(new Vector3d(0, _yPivotOffset, 0));
        }

        public ShapeCompositorV2 DefaultViewField() {
                // #region Common Variables
                int idTracker = 0;
                double baseRadius = _scale;
                double baseHeight = _scale * 1.5;

                double xOffset = -0.5;
                double yOffset = _scale * 0.5;
                double zOffset = baseHeight - 1;

                double pitchRadianOffset = -Math.toRadians(15);
                double yawRadianOffset = -Math.toRadians(5);
                // #endregion
                // #region OuterCone
                String outerConeId = idTracker++ + "";
                Shape outerCone = new TransformedShape(
                                new CircularCone(baseRadius, baseHeight),
                                xOffset, yOffset, -(zOffset), yawRadianOffset, pitchRadianOffset, 0);
                this.addOperation(outerConeId,
                                outerCone,
                                OperationTypeV2.DEFINE)
                                .withFill(new EmptyBlockFillV2())
                                .withDebugColor(DebugUtils.COLOR_BLACK)
                                .build();
                // #endregion
                // #region InnerViewBox
                String innerViewBoxId = idTracker++ + "";
                double innerViewBoxXScale = 0.75;
                Shape innerViewBoxShape = new TransformedShape(
                                new Box(-innerViewBoxXScale, 0, -_scale, innerViewBoxXScale, 2, 0),
                                -0.25, 1, 0,
                                yawRadianOffset, 0, 0);
                this.addOperation(innerViewBoxId,
                                innerViewBoxShape,
                                OperationTypeV2.Cut(outerConeId))
                                // .withFill(new EmptyBlockFillV2())
                                // .withDebugColor(DebugUtils.COLOR_CYAN)
                                .build();
                // #endregion
                // #region MainView UNION (outerCone after CUT)
                String mainViewId = idTracker++ + "";
                this.addOperation(mainViewId,
                                null,
                                OperationTypeV2.Union(outerConeId))
                                .build();
                // #endregion
                // #region SideViewBox
                String SideViewBoxId = idTracker++ + "";
                Shape sideViewBox = new TransformedShape(
                                new Box(-2, 0, -1, 2, 1, 0),
                                xOffset, 1, -1,
                                yawRadianOffset, 0, 0);
                this.addOperation(SideViewBoxId,
                                sideViewBox,
                                OperationTypeV2.DEFINE)
                                .withFill(new EmptyBlockFillV2())
                                .withDebugColor(DebugUtils.COLOR_BLACK)
                                .withTransformFlags(_ignorePitch)
                                .build();
                // #endregion
                // #region FullView UNION (mainView + sideViewBox)
                String fullViewId = idTracker++ + "";
                this.addOperation(fullViewId,
                                null,
                                OperationTypeV2.Union(mainViewId, SideViewBoxId))
                                .build();
                // #endregion
                // #region PlayerFacingWall
                String ignorePlayerFacingWallId = idTracker++ + "";
                Shape ignorePlayerFacingWall = new TransformedShape(
                                new Box(-_scale, -_scale, -1, _scale, 2, _scale),
                                0, 0, 0,
                                yawRadianOffset, 0, 0);
                this.addOperation(ignorePlayerFacingWallId,
                                ignorePlayerFacingWall,
                                OperationTypeV2.EXCLUDE)
                                .withTransformFlags(_ignorePitch)
                                .withDebugColor(DebugUtils.COLOR_LIME)
                                .build();
                // #endregion
                // #region PlayerFacingWallPlus
                String ignorePlayerFacingWallPlusId = idTracker++ + "";
                Shape ignorePlayerFacingWallPlus = new TransformedShape(
                                new Box(-_scale, 0, 0.5, _scale, _scale, _scale),
                                0, 0, 0,
                                yawRadianOffset, 0, 0);
                this.addOperation(ignorePlayerFacingWallPlusId,
                                ignorePlayerFacingWallPlus,
                                OperationTypeV2.EXCLUDE)
                                .withTransformFlags(_ignorePitch)
                                .withDebugColor(DebugUtils.COLOR_LIME)
                                .build();
                // #endregion
                // #region PlayerFeetBox
                String IgnorePlayerFeetId = idTracker++ + "";
                Shape IgnoredPlayerFeetBox = new TransformedShape(
                                new Box(-_scale, -1, -_scale, _scale, 1, _scale),
                                0, 0, 0,
                                yawRadianOffset, 0, 0);
                this.addOperation(IgnorePlayerFeetId,
                                IgnoredPlayerFeetBox,
                                OperationTypeV2.EXCLUDE)
                                .withTransformFlags(_ignorePitch)
                                .withDebugColor(DebugUtils.COLOR_RED)
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
                                .withDebugColor(DebugUtils.COLOR_GRAY)
                                .build();

                this.addOperation(idTracker++ + "",
                                new Box(-_scale, -_scale, -0.5, _scale, -1, _scale),
                                OperationTypeV2.EXCLUDE)
                                .withTransformFlags(_ignorePitch)
                                .build();

                return this;
        }
}
