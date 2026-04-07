package com.UnobstructedThirdPerson.shape.v2;

import com.hypixel.hytale.math.vector.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShapeCompositorV2Test {

    private static final double EPSILON = 1e-9;

    @Test
    void getVector3d_zeroRotation_anchorPlusOffset() {
        ShapeCompositorV2 comp = new ShapeCompositorV2(new Vector3d(-0.5, 1.5, 0));
        comp.setAnchor(new Vector3d(10, 20, 30));
        comp.setRotation(0, 0);

        Vector3d result = comp.getVector3d();

        // No rotation: effective = anchor + offset directly
        // offset.x=-0.5 rotated by yaw=0: extX = -0.5*cos0 - 0*sin0 = -0.5
        // offset.z=0 rotated by yaw=0: extZ = -0.5*sin0 + 0*cos0 = 0
        // offset.y=1.5 with pitch=0: pivotY = 1.5*sin0 = 0, pivotForward = 1.5*cos0 = 1.5
        // x = 10 + (-0.5) + 1.5*(-sin0) = 10 - 0.5 + 0 = 9.5
        // y = 20 + 0 + 0 = 20  (PITCH_PIVOT_EYE_HEIGHT=0)
        // z = 30 + 0 + 1.5*cos0 = 30 + 1.5 = 31.5
        assertEquals(9.5, result.x, EPSILON);
        assertEquals(20.0, result.y, EPSILON);
        assertEquals(31.5, result.z, EPSILON);
    }

    @Test
    void getVector3d_yaw90_rotatesXZOffset() {
        ShapeCompositorV2 comp = new ShapeCompositorV2(new Vector3d(-5, 0, 0));
        comp.setAnchor(new Vector3d(0, 0, 0));
        double yaw90 = Math.PI / 2;
        comp.setRotation(yaw90, 0);

        Vector3d result = comp.getVector3d();

        // offset=(-5, 0, 0), yaw=90°, pitch=0
        // cosYaw=0, sinYaw=1
        // extX = -5*0 - 0*1 = 0
        // extZ = -5*1 + 0*0 = -5
        // pivotY = 0*sin0 = 0, pivotForward = 0*cos0 = 0
        // x = 0 + 0 + 0 = 0
        // z = 0 + (-5) + 0 = -5
        assertEquals(0.0, result.x, EPSILON);
        assertEquals(0.0, result.y, EPSILON);
        assertEquals(-5.0, result.z, EPSILON);
    }

    @Test
    void getVector3d_yOffsetPitch90_goesVertical() {
        ShapeCompositorV2 comp = new ShapeCompositorV2(new Vector3d(0, 5, 0));
        comp.setAnchor(new Vector3d(0, 0, 0));
        double pitch90 = Math.PI / 2;
        comp.setRotation(0, pitch90);

        Vector3d result = comp.getVector3d();

        // offset=(0, 5, 0), yaw=0, pitch=90°
        // cosPitch=0, sinPitch=1
        // pivotY = 5*1 = 5, pivotForward = 5*0 = 0
        // extX = 0, extZ = 0
        // x = 0 + 0 + 0 = 0
        // y = 0 + 5 + 0 = 5
        // z = 0 + 0 + 0 = 0
        assertEquals(0.0, result.x, EPSILON);
        assertEquals(5.0, result.y, EPSILON);
        assertEquals(0.0, result.z, EPSILON);
    }

    @Test
    void getVector3d_yOffsetPitchZero_goesForward() {
        ShapeCompositorV2 comp = new ShapeCompositorV2(new Vector3d(0, 5, 0));
        comp.setAnchor(new Vector3d(0, 10, 0));
        comp.setRotation(0, 0);

        Vector3d result = comp.getVector3d();

        // offset=(0, 5, 0), yaw=0, pitch=0
        // pivotY = 5*sin0 = 0, pivotForward = 5*cos0 = 5
        // extX=0, extZ=0
        // x = 0 + 0 + 5*(-sin0) = 0
        // y = 10 + 0 + 0 = 10
        // z = 0 + 0 + 5*cos0 = 5
        assertEquals(0.0, result.x, EPSILON);
        assertEquals(10.0, result.y, EPSILON);
        assertEquals(5.0, result.z, EPSILON);
    }

    @Test
    void getVector3d_pitchAndYaw_yComponentPivots() {
        ShapeCompositorV2 comp = new ShapeCompositorV2(new Vector3d(0, 5, 0));
        comp.setAnchor(new Vector3d(0, 0, 0));
        double yaw90 = Math.PI / 2;
        double pitch45 = Math.PI / 4;
        comp.setRotation(yaw90, pitch45);

        Vector3d result = comp.getVector3d();

        // offset=(0, 5, 0), yaw=90°, pitch=45°
        // cosPitch=√2/2, sinPitch=√2/2, cosYaw=0, sinYaw=1
        // pivotY = 5*(√2/2) ≈ 3.536
        // pivotForward = 5*(√2/2) ≈ 3.536
        // extX = 0, extZ = 0
        // x = 0 + 0 + 3.536*(-1) = -3.536
        // y = 0 + 3.536 + 0 = 3.536
        // z = 0 + 0 + 3.536*0 = 0
        double s = 5 * Math.sin(Math.PI / 4);
        assertEquals(-s, result.x, EPSILON);
        assertEquals(s, result.y, EPSILON);
        assertEquals(0.0, result.z, EPSILON);
    }

    @Test
    void setRotation_yawOnly_pitchRemainsZero() {
        ShapeCompositorV2 comp = new ShapeCompositorV2(new Vector3d(0, 0, 0));
        comp.setRotation(1.5);

        assertEquals(1.5, comp.getYawRotation(), EPSILON);
        assertEquals(0.0, comp.getPitchRotation(), EPSILON);
    }

    @Test
    void setRotation_yawAndPitch_bothSet() {
        ShapeCompositorV2 comp = new ShapeCompositorV2(new Vector3d(0, 0, 0));
        comp.setRotation(1.5, 0.8);

        assertEquals(1.5, comp.getYawRotation(), EPSILON);
        assertEquals(0.8, comp.getPitchRotation(), EPSILON);
    }
}
