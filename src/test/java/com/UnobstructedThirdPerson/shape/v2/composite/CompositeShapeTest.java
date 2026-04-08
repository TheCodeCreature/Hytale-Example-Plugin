package com.UnobstructedThirdPerson.shape.v2.composite;

import com.UnobstructedThirdPerson.shape.v2.fill.EmptyBlockFillV2;
import com.UnobstructedThirdPerson.shape.v2.visual.DebugStyle;
import com.hypixel.hytale.math.block.BlockUtil;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CompositeShapeTest {

    @Test
    void builder_addPoints_createsCorrectSize() {
        CompositeShape shape = CompositeShape.builder()
                .addPoint(pack(0, 0, 0), new EmptyBlockFillV2(), "op1", DebugStyle.NONE)
                .addPoint(pack(1, 0, 0), new EmptyBlockFillV2(), "op1", DebugStyle.NONE)
                .addPoint(pack(0, 1, 0), new EmptyBlockFillV2(), "op2", DebugStyle.NONE)
                .build();

        assertEquals(3, shape.size());
    }

    @Test
    void builder_duplicatePosition_overwrites() {
        CompositeShape shape = CompositeShape.builder()
                .addPoint(pack(0, 0, 0), new EmptyBlockFillV2(), "op1", DebugStyle.NONE)
                .addPoint(pack(0, 0, 0), new EmptyBlockFillV2(), "op2", DebugStyle.NONE)
                .build();

        assertEquals(1, shape.size());
        assertEquals("op2", shape.getOriginId(0));
    }

    @Test
    void builder_removePoint_reducesSize() {
        CompositeShape.Builder builder = CompositeShape.builder()
                .addPoint(pack(0, 0, 0), new EmptyBlockFillV2(), "op1", DebugStyle.NONE)
                .addPoint(pack(1, 0, 0), new EmptyBlockFillV2(), "op1", DebugStyle.NONE);

        builder.removePoint(pack(0, 0, 0));
        CompositeShape shape = builder.build();

        assertEquals(1, shape.size());
        assertEquals(pack(1, 0, 0), shape.getPosition(0));
    }

    @Test
    void builder_containsPoint_returnsTrueForExisting() {
        CompositeShape.Builder builder = CompositeShape.builder()
                .addPoint(pack(5, 5, 5), new EmptyBlockFillV2(), "op1", DebugStyle.NONE);

        assertTrue(builder.containsPoint(pack(5, 5, 5)));
        assertFalse(builder.containsPoint(pack(0, 0, 0)));
    }

    @Test
    void parallelArrays_aligned() {
        EmptyBlockFillV2 fill1 = new EmptyBlockFillV2();
        EmptyBlockFillV2 fill2 = new EmptyBlockFillV2();

        CompositeShape shape = CompositeShape.builder()
                .addPoint(pack(0, 0, 0), fill1, "op1", DebugStyle.NONE)
                .addPoint(pack(1, 1, 1), fill2, "op2", DebugStyle.NONE)
                .build();

        assertEquals(2, shape.size());
        assertSame(fill1, shape.getFill(0));
        assertEquals("op1", shape.getOriginId(0));
        assertSame(fill2, shape.getFill(1));
        assertEquals("op2", shape.getOriginId(1));
    }

    @Test
    void builder_getPositionSet_returnsAllPositions() {
        CompositeShape.Builder builder = CompositeShape.builder()
                .addPoint(pack(0, 0, 0), null, "a", DebugStyle.NONE)
                .addPoint(pack(1, 0, 0), null, "b", DebugStyle.NONE)
                .addPoint(pack(2, 0, 0), null, "c", DebugStyle.NONE);

        Set<Long> positions = builder.getPositionSet();
        assertEquals(3, positions.size());
        assertTrue(positions.contains(pack(0, 0, 0)));
        assertTrue(positions.contains(pack(1, 0, 0)));
        assertTrue(positions.contains(pack(2, 0, 0)));
    }

    @Test
    void builder_unionOfTwoShapes_mergesWithoutDuplicates() {
        // Simulate what UNION does: add points from two sources with overlap
        CompositeShape.Builder builder = CompositeShape.builder();

        // Shape A points
        long[] shapeA = { pack(0, 0, 0), pack(1, 0, 0), pack(2, 0, 0) };
        for (long pos : shapeA) {
            builder.addPoint(pos, new EmptyBlockFillV2(), "shapeA", DebugStyle.NONE);
        }

        // Shape B points (overlapping at 2,0,0)
        long[] shapeB = { pack(2, 0, 0), pack(3, 0, 0), pack(4, 0, 0) };
        for (long pos : shapeB) {
            if (!builder.containsPoint(pos)) {
                builder.addPoint(pos, new EmptyBlockFillV2(), "shapeB", DebugStyle.NONE);
            }
        }

        CompositeShape result = builder.build();
        assertEquals(5, result.size()); // 0,1,2,3,4 — no duplicate at 2
        // Position 2,0,0 should keep shapeA's origin (first-write wins in UNION)
        Set<Long> positions = new HashSet<>();
        for (int i = 0; i < result.size(); i++) {
            positions.add(result.getPosition(i));
            if (result.getPosition(i) == pack(2, 0, 0)) {
                assertEquals("shapeA", result.getOriginId(i));
            }
        }
        assertEquals(5, positions.size());
    }

    @Test
    void builder_subtractSimulation_removesOverlapping() {
        CompositeShape.Builder builder = CompositeShape.builder()
                .addPoint(pack(0, 0, 0), new EmptyBlockFillV2(), "base", DebugStyle.NONE)
                .addPoint(pack(1, 0, 0), new EmptyBlockFillV2(), "base", DebugStyle.NONE)
                .addPoint(pack(2, 0, 0), new EmptyBlockFillV2(), "base", DebugStyle.NONE);

        // Subtract positions 1,0,0 and 2,0,0
        builder.removePoint(pack(1, 0, 0));
        builder.removePoint(pack(2, 0, 0));

        CompositeShape result = builder.build();
        assertEquals(1, result.size());
        assertEquals(pack(0, 0, 0), result.getPosition(0));
    }

    @Test
    void emptyBuilder_producesEmptyShape() {
        CompositeShape shape = CompositeShape.builder().build();
        assertEquals(0, shape.size());
    }

    private static long pack(int x, int y, int z) {
        return BlockUtil.packUnchecked(x, y, z);
    }
}
