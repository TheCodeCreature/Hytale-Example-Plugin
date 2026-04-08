package com.UnobstructedThirdPerson.shape.v2;

import com.UnobstructedThirdPerson.shape.v2.fill.EmptyBlockFillV2;
import com.UnobstructedThirdPerson.shape.v2.operation.OperationTypeV2;
import com.UnobstructedThirdPerson.shape.v2.operation.ShapeOperationV2;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShapeCompositorV2OperationTest {

    private ShapeCompositorV2 createCompositor() {
        return new ShapeCompositorV2(new Vector3d(0, 0, 0));
    }

    // --- OperationRef overload tests ---

    @Test
    void addOperation_withSubtractRef_setsReferenceId() {
        ShapeCompositorV2 comp = createCompositor();
        Box box = new Box(-1, -1, -1, 1, 1, 1);

        comp.addOperation("base", box, OperationTypeV2.DEFINE).withFill(new EmptyBlockFillV2()).build();

        ShapeOperationV2 op = comp.addOperation("sub", box,
                OperationTypeV2.Subtract("base")).build();

        assertEquals(OperationTypeV2.SUBTRACT, op.getType());
        assertEquals("base", op.getReferenceId());
    }

    @Test
    void addOperation_withFillRef_setsReferenceId() {
        ShapeCompositorV2 comp = createCompositor();
        Box box = new Box(-1, -1, -1, 1, 1, 1);

        comp.addOperation("base", box, OperationTypeV2.DEFINE).withFill(new EmptyBlockFillV2()).build();

        ShapeOperationV2 op = comp.addOperation("fill", box,
                OperationTypeV2.Fill("base")).withFill(new EmptyBlockFillV2()).build();

        assertEquals(OperationTypeV2.FILL, op.getType());
        assertEquals("base", op.getReferenceId());
    }

    @Test
    void addOperation_withCutRef_setsReferenceId() {
        ShapeCompositorV2 comp = createCompositor();
        Box box = new Box(-1, -1, -1, 1, 1, 1);

        comp.addOperation("base", box, OperationTypeV2.DEFINE).withFill(new EmptyBlockFillV2()).build();

        ShapeOperationV2 op = comp.addOperation("cut", box,
                OperationTypeV2.Cut("base")).build();

        assertEquals(OperationTypeV2.CUT, op.getType());
        assertEquals("base", op.getReferenceId());
    }

    @Test
    void addOperation_withIntersectRef_setsReferenceId() {
        ShapeCompositorV2 comp = createCompositor();
        Box box = new Box(-1, -1, -1, 1, 1, 1);

        comp.addOperation("base", box, OperationTypeV2.DEFINE).withFill(new EmptyBlockFillV2()).build();

        ShapeOperationV2 op = comp.addOperation("inter", box,
                OperationTypeV2.Intersect("base")).withFill(new EmptyBlockFillV2()).build();

        assertEquals(OperationTypeV2.INTERSECT, op.getType());
        assertEquals("base", op.getReferenceId());
    }

    @Test
    void addOperation_withExcludeRef_setsReferenceId() {
        ShapeCompositorV2 comp = createCompositor();
        Box box = new Box(-1, -1, -1, 1, 1, 1);

        comp.addOperation("base", box, OperationTypeV2.DEFINE).withFill(new EmptyBlockFillV2()).build();

        ShapeOperationV2 op = comp.addOperation("excl", box,
                OperationTypeV2.Exclude("base")).build();

        assertEquals(OperationTypeV2.EXCLUDE, op.getType());
        assertEquals("base", op.getReferenceId());
    }

    @Test
    void addOperation_withFillRemainingRef_setsReferenceId() {
        ShapeCompositorV2 comp = createCompositor();
        Box box = new Box(-1, -1, -1, 1, 1, 1);

        comp.addOperation("base", box, OperationTypeV2.DEFINE).withFill(new EmptyBlockFillV2()).build();

        ShapeOperationV2 op = comp.addOperation("fillrem", null,
                OperationTypeV2.FillRemaining("base")).withFill(new EmptyBlockFillV2()).build();

        assertEquals(OperationTypeV2.FILL_REMAINING, op.getType());
        assertEquals("base", op.getReferenceId());
    }

    // --- DEFINE with null fill acts as mask ---

    @Test
    void addOperation_defineWithNullFill_buildsSuccessfully() {
        ShapeCompositorV2 comp = createCompositor();
        Box box = new Box(-1, -1, -1, 1, 1, 1);

        ShapeOperationV2 op = comp.addOperation("mask", box, OperationTypeV2.DEFINE).build();

        assertEquals(OperationTypeV2.DEFINE, op.getType());
        assertNull(op.getFillType());
    }

    @Test
    void addOperation_defineWithNullFill_appearsInTimeline() {
        ShapeCompositorV2 comp = createCompositor();
        Box box = new Box(-1, -1, -1, 1, 1, 1);

        comp.addOperation("mask", box, OperationTypeV2.DEFINE).build();

        List<ShapeOperationV2> timeline = comp.getTimeline();
        assertEquals(1, timeline.size());
        assertEquals("mask", timeline.get(0).getId());
        assertNull(timeline.get(0).getFillType());
    }

    @Test
    void addOperation_defineWithNullFill_canBeReferencedBySubtract() {
        ShapeCompositorV2 comp = createCompositor();
        Box box = new Box(-1, -1, -1, 1, 1, 1);

        comp.addOperation("mask", box, OperationTypeV2.DEFINE).build();
        ShapeOperationV2 sub = comp.addOperation("sub", box,
                OperationTypeV2.Subtract("mask")).build();

        assertEquals("mask", sub.getReferenceId());
    }

    // --- Timeline ordering with refs ---

    @Test
    void timeline_operationsWithRefs_sortedByPriority() {
        ShapeCompositorV2 comp = createCompositor();
        Box box = new Box(-1, -1, -1, 1, 1, 1);

        comp.addOperation("define", box, OperationTypeV2.DEFINE).withFill(new EmptyBlockFillV2()).build();
        comp.addOperation("subtract", box, OperationTypeV2.Subtract("define")).build();
        comp.addOperation("exclude", box, OperationTypeV2.Exclude("define")).build();

        List<ShapeOperationV2> timeline = comp.getTimeline();
        assertEquals(3, timeline.size());
        // DEFINE(10) < SUBTRACT(40) < EXCLUDE(100)
        assertEquals("define", timeline.get(0).getId());
        assertEquals("subtract", timeline.get(1).getId());
        assertEquals("exclude", timeline.get(2).getId());
    }

    // --- Validation tests ---

    @Test
    void addOperation_duplicateId_throws() {
        ShapeCompositorV2 comp = createCompositor();
        Box box = new Box(-1, -1, -1, 1, 1, 1);

        comp.addOperation("op1", box, OperationTypeV2.DEFINE).withFill(new EmptyBlockFillV2()).build();

        assertThrows(IllegalArgumentException.class, () ->
                comp.addOperation("op1", box, OperationTypeV2.DEFINE));
    }

    @Test
    void addOperation_subtractWithoutReference_throws() {
        ShapeCompositorV2 comp = createCompositor();
        Box box = new Box(-1, -1, -1, 1, 1, 1);

        assertThrows(IllegalArgumentException.class, () ->
                comp.addOperation("sub", box, OperationTypeV2.SUBTRACT).build());
    }

    @Test
    void addOperation_fillRequiresFillType_throws() {
        ShapeCompositorV2 comp = createCompositor();
        Box box = new Box(-1, -1, -1, 1, 1, 1);

        assertThrows(IllegalArgumentException.class, () ->
                comp.addOperation("fill", box,
                        OperationTypeV2.Fill("base")).build());
    }

    @Test
    void addOperation_operationRefOverload_stillValidatesDuplicateId() {
        ShapeCompositorV2 comp = createCompositor();
        Box box = new Box(-1, -1, -1, 1, 1, 1);

        comp.addOperation("base", box, OperationTypeV2.DEFINE).withFill(new EmptyBlockFillV2()).build();

        assertThrows(IllegalArgumentException.class, () ->
                comp.addOperation("base", box, OperationTypeV2.Subtract("base")));
    }
}
