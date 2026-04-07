package com.UnobstructedThirdPerson.shape;

import com.hypixel.hytale.math.shape.Box;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CircularConeTest {

    @Test
    void constructor_negativeRadius_throws() {
        assertThrows(IllegalArgumentException.class, () -> new CircularCone(-1.0, 10.0));
        assertThrows(IllegalArgumentException.class, () -> new CircularCone(0.0, 10.0));
    }

    @Test
    void constructor_zeroHeight_throws() {
        assertThrows(IllegalArgumentException.class, () -> new CircularCone(5.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new CircularCone(5.0, -1.0));
    }

    @Test
    void containsPosition_atBase_insideRadius() {
        CircularCone cone = new CircularCone(5.0, 10.0);
        // At z=0 (base), full radius is available
        assertTrue(cone.containsPosition(0, 0, 0));
        assertTrue(cone.containsPosition(4.9, 0, 0));
        assertTrue(cone.containsPosition(0, 4.9, 0));
        assertFalse(cone.containsPosition(5.1, 0, 0));
    }

    @Test
    void containsPosition_atTip_onlyCenter() {
        CircularCone cone = new CircularCone(5.0, 10.0);
        // At z=height (tip), radius is 0 — only exact center is contained
        assertTrue(cone.containsPosition(0, 0, 10.0));
        assertFalse(cone.containsPosition(0.1, 0, 10.0));
        assertFalse(cone.containsPosition(0, 0.1, 10.0));
    }

    @Test
    void containsPosition_outsideHeight_returnsFalse() {
        CircularCone cone = new CircularCone(5.0, 10.0);
        assertFalse(cone.containsPosition(0, 0, -0.1));
        assertFalse(cone.containsPosition(0, 0, 10.1));
    }

    @Test
    void containsPosition_midHeight_halfRadius() {
        CircularCone cone = new CircularCone(6.0, 10.0);
        // At z=5 (half height), radiusScale = 1 - 5/10 = 0.5, effective radius = 3.0
        assertTrue(cone.containsPosition(0, 0, 5.0));
        assertTrue(cone.containsPosition(2.9, 0, 5.0));
        assertFalse(cone.containsPosition(3.1, 0, 5.0));
    }

    @Test
    void getBox_dimensions() {
        CircularCone cone = new CircularCone(5.0, 10.0);
        Box box = cone.getBox(0, 0, 0);
        assertEquals(-5.0, box.min.x, 1e-9);
        assertEquals(-5.0, box.min.y, 1e-9);
        assertEquals(0.0, box.min.z, 1e-9);
        assertEquals(5.0, box.max.x, 1e-9);
        assertEquals(5.0, box.max.y, 1e-9);
        assertEquals(10.0, box.max.z, 1e-9);
    }

    @Test
    void expand_throwsUnsupported() {
        CircularCone cone = new CircularCone(5.0, 10.0);
        assertThrows(UnsupportedOperationException.class, () -> cone.expand(1.0));
    }
}
