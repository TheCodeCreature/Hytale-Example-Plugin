package com.UnobstructedThirdPerson.shape.v2.operation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperationTypeV2Test {

    @Test
    void subtract_factoryMethod_returnsCorrectTypeAndRef() {
        OperationTypeV2.OperationRef ref = OperationTypeV2.Subtract("base");
        assertEquals(OperationTypeV2.SUBTRACT, ref.getType());
        assertEquals("base", ref.getReferenceId());
    }

    @Test
    void fill_factoryMethod_returnsCorrectTypeAndRef() {
        OperationTypeV2.OperationRef ref = OperationTypeV2.Fill("region1");
        assertEquals(OperationTypeV2.FILL, ref.getType());
        assertEquals("region1", ref.getReferenceId());
    }

    @Test
    void cut_factoryMethod_returnsCorrectTypeAndRef() {
        OperationTypeV2.OperationRef ref = OperationTypeV2.Cut("region2");
        assertEquals(OperationTypeV2.CUT, ref.getType());
        assertEquals("region2", ref.getReferenceId());
    }

    @Test
    void intersect_factoryMethod_returnsCorrectTypeAndRef() {
        OperationTypeV2.OperationRef ref = OperationTypeV2.Intersect("mask");
        assertEquals(OperationTypeV2.INTERSECT, ref.getType());
        assertEquals("mask", ref.getReferenceId());
    }

    @Test
    void fillRemaining_factoryMethod_returnsCorrectTypeAndRef() {
        OperationTypeV2.OperationRef ref = OperationTypeV2.FillRemaining("defined");
        assertEquals(OperationTypeV2.FILL_REMAINING, ref.getType());
        assertEquals("defined", ref.getReferenceId());
    }

    @Test
    void exclude_factoryMethod_returnsCorrectTypeAndRef() {
        OperationTypeV2.OperationRef ref = OperationTypeV2.Exclude("cone");
        assertEquals(OperationTypeV2.EXCLUDE, ref.getType());
        assertEquals("cone", ref.getReferenceId());
    }

    @Test
    void define_doesNotRequireFill() {
        assertFalse(OperationTypeV2.DEFINE.isRequireFill());
    }

    @Test
    void define_doesNotRequireReference() {
        assertFalse(OperationTypeV2.DEFINE.isRequireReference());
    }

    @Test
    void subtract_requiresReference() {
        assertTrue(OperationTypeV2.SUBTRACT.isRequireReference());
    }

    @Test
    void intersect_requiresReference() {
        assertTrue(OperationTypeV2.INTERSECT.isRequireReference());
    }

    @Test
    void fillRemaining_requiresReference() {
        assertTrue(OperationTypeV2.FILL_REMAINING.isRequireReference());
    }

    @Test
    void exclude_doesNotRequireReference() {
        assertFalse(OperationTypeV2.EXCLUDE.isRequireReference());
    }

    @Test
    void operationRef_preservesNullReferenceId() {
        OperationTypeV2.OperationRef ref = new OperationTypeV2.OperationRef(OperationTypeV2.CUT, null);
        assertEquals(OperationTypeV2.CUT, ref.getType());
        assertNull(ref.getReferenceId());
    }
}
