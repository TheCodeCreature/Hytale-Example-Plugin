package com.UnobstructedThirdPerson.shape.v2;

import com.UnobstructedThirdPerson.shape.SpatialOffset;
import com.hypixel.hytale.math.vector.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShapeCompositorV2Test {

    private static final double EPSILON = 1e-9;

    @Test
    void getVector3d_zeroRotation_anchorPlusOffset() {
        ShapeCompositorV2 comp = new ShapeCompositorV2(new SpatialOffset(-0.5, 1.5, 0));
        comp.setAnchor(new Vector3d(10, 20, 30));
        comp.setRotation(0, 0);

        Vector3d result = comp.getVector3d();

        // No rotation: effective = anchor + offset directly
        // offset = (-0.5, 1.5, 0), rotated(0, 0) = (-0.5, 1.5, 0)
        // x = 10 + (-0.5) = 9.5
        // y = 20 + 1.5 = 21.5
        // z = 30 + 0 = 30.0
        assertEquals(9.5, result.x, EPSILON);
        assertEquals(21.5, result.y, EPSILON);
        assertEquals(30.0, result.z, EPSILON);
    }

    @Test
    void getVector3d_yaw90_rotatesXZOffset() {
        ShapeCompositorV2 comp = new ShapeCompositorV2(new SpatialOffset(-5, 0, 0));
        comp.setAnchor(new Vector3d(0, 0, 0));
        double yaw90 = Math.PI / 2;
        comp.setRotation(yaw90, 0);

        Vector3d result = comp.getVector3d();

        // offset=(-5, 0, 0), yaw=90°, pitch=0
        // pitch=0: no change
        // yaw=90°: cosY=0, sinY=1 → rx = -5*0 - 0*1 = 0, rz = -5*1 + 0*0 = -5
        assertEquals(0.0, result.x, EPSILON);
        assertEquals(0.0, result.y, EPSILON);
        assertEquals(-5.0, result.z, EPSILON);
    }

    @Test
    void getVector3d_yOffsetPitch90_rotatesIntoZ() {
        ShapeCompositorV2 comp = new ShapeCompositorV2(new SpatialOffset(0, 5, 0));
        comp.setAnchor(new Vector3d(0, 0, 0));
        double pitch90 = Math.PI / 2;
        comp.setRotation(0, pitch90);

        Vector3d result = comp.getVector3d();

        // offset=(0, 5, 0), yaw=0, pitch=90°
        // pitch rotation: ry = 5*cos90 = 0, rz = -5*sin90 = -5
        // yaw=0: no change
        // Result: (0, 0, -5)
        assertEquals(0.0, result.x, EPSILON);
        assertEquals(0.0, result.y, EPSILON);
        assertEquals(-5.0, result.z, EPSILON);
    }

    @Test
    void getVector3d_yOffsetPitchZero_staysVertical() {
        ShapeCompositorV2 comp = new ShapeCompositorV2(new SpatialOffset(0, 5, 0));
        comp.setAnchor(new Vector3d(0, 10, 0));
        comp.setRotation(0, 0);

        Vector3d result = comp.getVector3d();

        // offset=(0, 5, 0), yaw=0, pitch=0 — no rotation
        // Y offset stays vertical: result.y = 10 + 5 = 15
        assertEquals(0.0, result.x, EPSILON);
        assertEquals(15.0, result.y, EPSILON);
        assertEquals(0.0, result.z, EPSILON);
    }

    @Test
    void getVector3d_pitchAndYaw_yComponentPivots() {
        ShapeCompositorV2 comp = new ShapeCompositorV2(new SpatialOffset(0, 5, 0));
        comp.setAnchor(new Vector3d(0, 0, 0));
        double yaw90 = Math.PI / 2;
        double pitch45 = Math.PI / 4;
        comp.setRotation(yaw90, pitch45);

        Vector3d result = comp.getVector3d();

        // offset=(0, 5, 0), yaw=90°, pitch=45°
        // pitch: ry = 5*cos45 = 5√2/2, rz = -5*sin45 = -5√2/2
        // yaw=90°: cosY=0, sinY=1
        //   rx = 0*0 - (-5√2/2)*1 = 5√2/2
        //   rz = 0*1 + (-5√2/2)*0 = 0
        // Result: (5√2/2, 5√2/2, 0)
        double s = 5 * Math.sin(Math.PI / 4);
        assertEquals(s, result.x, EPSILON);
        assertEquals(s, result.y, EPSILON);
        assertEquals(0.0, result.z, EPSILON);
    }

    @Test
    void setRotation_yawOnly_pitchRemainsZero() {
        ShapeCompositorV2 comp = new ShapeCompositorV2(SpatialOffset.ZERO);
        comp.setRotation(1.5);

        assertEquals(1.5, comp.getYawRotation(), EPSILON);
        assertEquals(0.0, comp.getPitchRotation(), EPSILON);
    }

    @Test
    void setRotation_yawAndPitch_bothSet() {
        ShapeCompositorV2 comp = new ShapeCompositorV2(SpatialOffset.ZERO);
        comp.setRotation(1.5, 0.8);

        assertEquals(1.5, comp.getYawRotation(), EPSILON);
        assertEquals(0.8, comp.getPitchRotation(), EPSILON);
    }
}
