package com.CodeCreature.scaling;

import org.junit.jupiter.api.Test;

import com.CodeCreature.scaling.ResourceConstants;

import static org.junit.jupiter.api.Assertions.*;

class ResourceConstantsTest {

    @Test
    void multiplierIsPositiveNonZero() {
        assertTrue(ResourceConstants.RESOURCE_MULTIPLIER > 0,
                "RESOURCE_MULTIPLIER must be a positive integer");
    }

    @Test
    void multiplierIsTwelve() {
        assertEquals(12, ResourceConstants.RESOURCE_MULTIPLIER,
                "Default multiplier for the 12x economy should be 12");
    }

    @Test
    void multiplierIsEvenlyDivisible() {
        // 12 is divisible by 1, 2, 3, 4, 6, 12 — covers common recipe output sizes
        int m = ResourceConstants.RESOURCE_MULTIPLIER;
        assertEquals(0, m % 2, "Multiplier should be divisible by 2 (half slabs)");
        assertEquals(0, m % 3, "Multiplier should be divisible by 3 (stair patterns)");
        assertEquals(0, m % 4, "Multiplier should be divisible by 4 (quarter blocks)");
    }
}
