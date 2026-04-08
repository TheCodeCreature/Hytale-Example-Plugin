package com.UnobstructedThirdPerson.shape.v2.operation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperationTypeV2Test {

    @Test
    void subtract_factoryMethod_returnsCorrectTypeAndRef() {
        OperationTypeV2.OperationRef ref = OperationTypeV2.Subtract("base");
        assertEquals(OperationTypeV2.SUBTRACT, ref.getType());
        assertArrayEquals(new String[]{"base"}, ref.getReferenceIds());
    }

    @Test
    void fill_factoryMethod_returnsCorrectTypeAndRef() {
        OperationTypeV2.OperationRef ref = OperationTypeV2.Fill("region1");
        assertEquals(OperationTypeV2.FILL, ref.getType());
        assertArrayEquals(new String[]{"region1"}, ref.getReferenceIds());
    }

    @Test
    void cut_factoryMethod_returnsCorrectTypeAndRef() {
        OperationTypeV2.OperationRef ref = OperationTypeV2.Cut("region2");
        assertEquals(OperationTypeV2.CUT, ref.getType());
        assertArrayEquals(new String[]{"region2"}, ref.getReferenceIds());
    }

    @Test
    void intersect_factoryMethod_returnsCorrectTypeAndRef() {
        OperationTypeV2.OperationRef ref = OperationTypeV2.Intersect("mask");
        assertEquals(OperationTypeV2.INTERSECT, ref.getType());
        assertArrayEquals(new String[]{"mask"}, ref.getReferenceIds());
    }

    @Test
    void fillRemaining_factoryMethod_returnsCorrectTypeAndRef() {
        OperationTypeV2.OperationRef ref = OperationTypeV2.FillRemaining("defined");
        assertEquals(OperationTypeV2.FILL_REMAINING, ref.getType());
        assertArrayEquals(new String[]{"defined"}, ref.getReferenceIds());
    }

    @Test
    void exclude_factoryMethod_returnsCorrectTypeAndRef() {
        OperationTypeV2.OperationRef ref = OperationTypeV2.Exclude("cone");
        assertEquals(OperationTypeV2.EXCLUDE, ref.getType());
        assertArrayEquals(new String[]{"cone"}, ref.getReferenceIds());
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
    void operationRef_emptyRefs_producesEmptyArray() {
        OperationTypeV2.OperationRef ref = new OperationTypeV2.OperationRef(OperationTypeV2.CUT);
        assertEquals(OperationTypeV2.CUT, ref.getType());
        assertEquals(0, ref.getReferenceIds().length);
    }

    // --- UNION factory method tests ---

    @Test
    void union_factoryMethod_returnsCorrectType() {
        OperationTypeV2.OperationRef ref = OperationTypeV2.Union("a", "b");
        assertEquals(OperationTypeV2.UNION, ref.getType());
    }

    @Test
    void union_factoryMethod_preservesMultipleRefs() {
        OperationTypeV2.OperationRef ref = OperationTypeV2.Union("shape1", "shape2", "shape3");
        String[] refs = ref.getReferenceIds();
        assertEquals(3, refs.length);
        assertEquals("shape1", refs[0]);
        assertEquals("shape2", refs[1]);
        assertEquals("shape3", refs[2]);
    }

    @Test
    void union_factoryMethod_singleRef() {
        OperationTypeV2.OperationRef ref = OperationTypeV2.Union("only");
        assertEquals("only", ref.getReferenceIds()[0]);
        assertEquals(1, ref.getReferenceIds().length);
    }

    @Test
    void union_requiresReference() {
        assertTrue(OperationTypeV2.UNION.isRequireReference());
    }

    @Test
    void union_doesNotRequireShape() {
        assertFalse(OperationTypeV2.UNION.isRequireShape());
    }

    @Test
    void union_doesNotRequireFill() {
        assertFalse(OperationTypeV2.UNION.isRequireFill());
    }

    @Test
    void union_defaultPriority_between_define_and_fill() {
        assertTrue(OperationTypeV2.UNION.getDefaultPriority() > OperationTypeV2.DEFINE.getDefaultPriority());
        assertTrue(OperationTypeV2.UNION.getDefaultPriority() < OperationTypeV2.FILL.getDefaultPriority());
    }

    @Test
    void operationRef_multiRef_constructor_clonesArray() {
        String[] input = { "a", "b" };
        OperationTypeV2.OperationRef ref = new OperationTypeV2.OperationRef(OperationTypeV2.UNION, input);
        input[0] = "mutated";
        assertEquals("a", ref.getReferenceIds()[0]); // should not be affected
    }
}
