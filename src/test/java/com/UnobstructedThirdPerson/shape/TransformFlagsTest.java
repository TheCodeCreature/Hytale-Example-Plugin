package com.UnobstructedThirdPerson.shape;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransformFlagsTest {

    @Test
    void allPreset_allFlagsTrue() {
        TransformFlags flags = TransformFlags.ALL;
        assertTrue(flags.shouldApplyAnchorX());
        assertTrue(flags.shouldApplyAnchorY());
        assertTrue(flags.shouldApplyAnchorZ());
        assertTrue(flags.shouldApplyYaw());
        assertTrue(flags.shouldApplyPitch());
        assertTrue(flags.shouldApplyRoll());
    }

    @Test
    void nonePreset_allFlagsFalse() {
        TransformFlags flags = TransformFlags.NONE;
        assertFalse(flags.shouldApplyAnchorX());
        assertFalse(flags.shouldApplyAnchorY());
        assertFalse(flags.shouldApplyAnchorZ());
        assertFalse(flags.shouldApplyYaw());
        assertFalse(flags.shouldApplyPitch());
        assertFalse(flags.shouldApplyRoll());
    }

    @Test
    void rotationsOnlyPreset_rotationsTrueAnchorFalse() {
        TransformFlags flags = TransformFlags.ROTATIONS_ONLY;
        assertFalse(flags.shouldApplyAnchorX());
        assertFalse(flags.shouldApplyAnchorY());
        assertFalse(flags.shouldApplyAnchorZ());
        assertTrue(flags.shouldApplyYaw());
        assertTrue(flags.shouldApplyPitch());
        assertTrue(flags.shouldApplyRoll());
    }

    @Test
    void anchorOnlyPreset_anchorTrueRotationsFalse() {
        TransformFlags flags = TransformFlags.ANCHOR_ONLY;
        assertTrue(flags.shouldApplyAnchorX());
        assertTrue(flags.shouldApplyAnchorY());
        assertTrue(flags.shouldApplyAnchorZ());
        assertFalse(flags.shouldApplyYaw());
        assertFalse(flags.shouldApplyPitch());
        assertFalse(flags.shouldApplyRoll());
    }

    @Test
    void builder_ignorePitch_onlyPitchFalse() {
        TransformFlags flags = TransformFlags.builder()
                .ignorePitch()
                .build();
        assertTrue(flags.shouldApplyAnchorX());
        assertTrue(flags.shouldApplyAnchorY());
        assertTrue(flags.shouldApplyAnchorZ());
        assertTrue(flags.shouldApplyYaw());
        assertFalse(flags.shouldApplyPitch());
        assertTrue(flags.shouldApplyRoll());
    }

    @Test
    void builder_ignoreAnchor_allAnchorFalse() {
        TransformFlags flags = TransformFlags.builder()
                .ignoreAnchor()
                .build();
        assertFalse(flags.shouldApplyAnchorX());
        assertFalse(flags.shouldApplyAnchorY());
        assertFalse(flags.shouldApplyAnchorZ());
        assertTrue(flags.shouldApplyYaw());
        assertTrue(flags.shouldApplyPitch());
        assertTrue(flags.shouldApplyRoll());
    }

    @Test
    void shouldApplyAnyRotation_noneSet_returnsFalse() {
        assertFalse(TransformFlags.NONE.shouldApplyAnyRotation());
        assertTrue(TransformFlags.ALL.shouldApplyAnyRotation());
        assertTrue(TransformFlags.ROTATIONS_ONLY.shouldApplyAnyRotation());
        assertFalse(TransformFlags.ANCHOR_ONLY.shouldApplyAnyRotation());
    }

    @Test
    void shouldApplyAnyAnchor_rotationsOnly_returnsFalse() {
        assertFalse(TransformFlags.ROTATIONS_ONLY.shouldApplyAnyAnchor());
        assertTrue(TransformFlags.ALL.shouldApplyAnyAnchor());
        assertTrue(TransformFlags.ANCHOR_ONLY.shouldApplyAnyAnchor());
        assertFalse(TransformFlags.NONE.shouldApplyAnyAnchor());
    }
}
