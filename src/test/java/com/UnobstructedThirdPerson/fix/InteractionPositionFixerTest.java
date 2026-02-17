package com.UnobstructedThirdPerson.fix;

import com.UnobstructedThirdPerson.fix.InteractionPositionFixer.PlacementResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for InteractionPositionFixer — the core placement/face detection logic
 * and interaction correction behavior. This file exercises the pure math extracted
 * into calculatePlacementFromHit() which has no engine dependencies.
 */
class InteractionPositionFixerTest {

    // ===== Face Detection: Exact boundary hits =====

    @Nested
    @DisplayName("Face detection — exact boundary hits")
    class ExactFaceDetection {

        @Test
        @DisplayName("Hit at West face (dx ≈ 0.0) → offset (-1,0,0), face=West")
        void westFace() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                10.0, 64.5, 10.5
            );
            assertEquals(9, r.x);
            assertEquals(64, r.y);
            assertEquals(10, r.z);
            assertEquals("West", r.blockFaceName);
            assertTrue(r.face.contains("WEST"));
        }

        @Test
        @DisplayName("Hit at East face (dx ≈ 1.0) → offset (+1,0,0), face=East")
        void eastFace() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                11.0, 64.5, 10.5
            );
            assertEquals(11, r.x);
            assertEquals(64, r.y);
            assertEquals(10, r.z);
            assertEquals("East", r.blockFaceName);
            assertTrue(r.face.contains("EAST"));
        }

        @Test
        @DisplayName("Hit at Bottom face (dy ≈ 0.0) → offset (0,-1,0), face=Down")
        void bottomFace() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                10.5, 64.0, 10.5
            );
            assertEquals(10, r.x);
            assertEquals(63, r.y);
            assertEquals(10, r.z);
            assertEquals("Down", r.blockFaceName);
            assertTrue(r.face.contains("BOTTOM"));
        }

        @Test
        @DisplayName("Hit at Top face (dy ≈ 1.0) → offset (0,+1,0), face=Up")
        void topFace() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                10.5, 65.0, 10.5
            );
            assertEquals(10, r.x);
            assertEquals(65, r.y);
            assertEquals(10, r.z);
            assertEquals("Up", r.blockFaceName);
            assertTrue(r.face.contains("TOP"));
        }

        @Test
        @DisplayName("Hit at North face (dz ≈ 0.0) → offset (0,0,-1), face=North")
        void northFace() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                10.5, 64.5, 10.0
            );
            assertEquals(10, r.x);
            assertEquals(64, r.y);
            assertEquals(9, r.z);
            assertEquals("North", r.blockFaceName);
            assertTrue(r.face.contains("NORTH"));
        }

        @Test
        @DisplayName("Hit at South face (dz ≈ 1.0) → offset (0,0,+1), face=South")
        void southFace() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                10.5, 64.5, 11.0
            );
            assertEquals(10, r.x);
            assertEquals(64, r.y);
            assertEquals(11, r.z);
            assertEquals("South", r.blockFaceName);
            assertTrue(r.face.contains("SOUTH"));
        }
    }

    // ===== Face Detection: Epsilon tolerance =====

    @Nested
    @DisplayName("Face detection — epsilon tolerance")
    class EpsilonTolerance {

        @Test
        @DisplayName("Hit slightly inside West face (dx = 0.0005) still detects West")
        void westFaceWithEpsilon() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                10.0005, 64.5, 10.5
            );
            assertEquals(9, r.x);
            assertEquals("West", r.blockFaceName);
        }

        @Test
        @DisplayName("Hit slightly inside East face (dx = 0.9995) still detects East")
        void eastFaceWithEpsilon() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                10.9995, 64.5, 10.5
            );
            assertEquals(11, r.x);
            assertEquals("East", r.blockFaceName);
        }

        @Test
        @DisplayName("Hit slightly inside Top face (dy = 0.9998) still detects Up")
        void topFaceWithEpsilon() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                10.5, 64.9998, 10.5
            );
            assertEquals(65, r.y);
            assertEquals("Up", r.blockFaceName);
        }
    }

    // ===== Face Detection: Fallback (not on any boundary) =====

    @Nested
    @DisplayName("Face detection — fallback to closest face")
    class FallbackDetection {

        @Test
        @DisplayName("Hit interior point closest to West face → fallback to West")
        void fallbackWest() {
            // dx=0.05 is closer to West (0.0) than any other boundary
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                10.05, 64.5, 10.5
            );
            assertEquals(9, r.x);
            assertEquals(64, r.y);
            assertEquals(10, r.z);
            assertEquals("West", r.blockFaceName);
            assertTrue(r.face.contains("[fallback]"));
        }

        @Test
        @DisplayName("Hit interior point closest to Top face → fallback to Up")
        void fallbackTop() {
            // dy=0.98 is closer to Top (1.0) than to any other boundary
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                10.5, 64.98, 10.5
            );
            assertEquals(10, r.x);
            assertEquals(65, r.y);
            assertEquals(10, r.z);
            assertEquals("Up", r.blockFaceName);
            assertTrue(r.face.contains("[fallback]"));
        }

        @Test
        @DisplayName("Hit interior point closest to South face → fallback to South")
        void fallbackSouth() {
            // dz=0.97 is closer to South (1.0) than any other boundary
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                10.5, 64.5, 10.97
            );
            assertEquals(10, r.x);
            assertEquals(64, r.y);
            assertEquals(11, r.z);
            assertEquals("South", r.blockFaceName);
            assertTrue(r.face.contains("[fallback]"));
        }
    }

    // ===== Placement Position Calculation =====

    @Nested
    @DisplayName("Placement position calculation")
    class PlacementPositionCalculation {

        @Test
        @DisplayName("Target at (10,64,10), hit East face → placement at (11,64,10)")
        void placementEastFace() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                11.0, 64.5, 10.5
            );
            assertEquals(11, r.x);
            assertEquals(64, r.y);
            assertEquals(10, r.z);
        }

        @Test
        @DisplayName("Target at (0,0,0), hit Bottom face → placement at (0,-1,0)")
        void placementAtOriginBottom() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                0, 0, 0,
                0.5, 0.0, 0.5
            );
            assertEquals(0, r.x);
            assertEquals(-1, r.y);
            assertEquals(0, r.z);
        }

        @Test
        @DisplayName("Target at negative coords (-5,30,-5), hit North face → placement at (-5,30,-6)")
        void placementNegativeCoords() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                -5, 30, -5,
                -4.5, 30.5, -5.0
            );
            assertEquals(-5, r.x);
            assertEquals(30, r.y);
            assertEquals(-6, r.z);
        }

        @Test
        @DisplayName("Large coordinates: target at (10000,200,10000), hit Top → placement at (10000,201,10000)")
        void placementLargeCoords() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10000, 200, 10000,
                10000.5, 201.0, 10000.5
            );
            assertEquals(10000, r.x);
            assertEquals(201, r.y);
            assertEquals(10000, r.z);
            assertEquals("Up", r.blockFaceName);
        }
    }

    // ===== All Six Faces: Comprehensive offset verification =====

    @Nested
    @DisplayName("All faces produce correct offsets")
    class AllFaceOffsets {

        @Test
        @DisplayName("West face: only X offset is -1")
        void westOffset() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                5, 5, 5, 5.0, 5.5, 5.5);
            assertEquals(4, r.x);
            assertEquals(5, r.y);
            assertEquals(5, r.z);
        }

        @Test
        @DisplayName("East face: only X offset is +1")
        void eastOffset() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                5, 5, 5, 6.0, 5.5, 5.5);
            assertEquals(6, r.x);
            assertEquals(5, r.y);
            assertEquals(5, r.z);
        }

        @Test
        @DisplayName("Down face: only Y offset is -1")
        void downOffset() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                5, 5, 5, 5.5, 5.0, 5.5);
            assertEquals(5, r.x);
            assertEquals(4, r.y);
            assertEquals(5, r.z);
        }

        @Test
        @DisplayName("Up face: only Y offset is +1")
        void upOffset() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                5, 5, 5, 5.5, 6.0, 5.5);
            assertEquals(5, r.x);
            assertEquals(6, r.y);
            assertEquals(5, r.z);
        }

        @Test
        @DisplayName("North face: only Z offset is -1")
        void northOffset() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                5, 5, 5, 5.5, 5.5, 5.0);
            assertEquals(5, r.x);
            assertEquals(5, r.y);
            assertEquals(4, r.z);
        }

        @Test
        @DisplayName("South face: only Z offset is +1")
        void southOffset() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                5, 5, 5, 5.5, 5.5, 6.0);
            assertEquals(5, r.x);
            assertEquals(5, r.y);
            assertEquals(6, r.z);
        }
    }

    // ===== Edge/Corner Disambiguation with Look Direction =====

    @Nested
    @DisplayName("Edge/corner disambiguation with look direction")
    class EdgeDisambiguation {

        @Test
        @DisplayName("North face edge (dx=0, dz=0), facing south → North wins over West")
        void northFaceEdgeFacingSouth() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                10.0, 64.5, 10.0,
                0.0, 0.0, 1.0
            );
            assertEquals("North", r.blockFaceName);
            assertEquals(10, r.x);
            assertEquals(64, r.y);
            assertEquals(9, r.z);
        }

        @Test
        @DisplayName("South face edge (dx=0, dz=1), facing north → South wins over West")
        void southFaceEdgeFacingNorth() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                10.0, 64.5, 11.0,
                0.0, 0.0, -1.0
            );
            assertEquals("South", r.blockFaceName);
            assertEquals(10, r.x);
            assertEquals(64, r.y);
            assertEquals(11, r.z);
        }

        @Test
        @DisplayName("West face edge (dx=0, dz=0), facing east → West wins over North")
        void westFaceEdgeFacingEast() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                10.0, 64.5, 10.0,
                1.0, 0.0, 0.0
            );
            assertEquals("West", r.blockFaceName);
            assertEquals(9, r.x);
            assertEquals(64, r.y);
            assertEquals(10, r.z);
        }

        @Test
        @DisplayName("Top-North edge (dy=1, dz=0), facing south+down → North wins over Up")
        void topNorthEdgeFacingSouthDown() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                10.5, 65.0, 10.0,
                0.0, -0.3, 0.95
            );
            assertEquals("North", r.blockFaceName);
            assertEquals(10, r.x);
            assertEquals(64, r.y);
            assertEquals(9, r.z);
        }

        @Test
        @DisplayName("Bottom-East edge (dy=0, dx=1), facing west+up → East wins over Down")
        void bottomEastEdgeFacingWestUp() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                11.0, 64.0, 10.5,
                -0.95, 0.3, 0.0
            );
            assertEquals("East", r.blockFaceName);
            assertEquals(11, r.x);
            assertEquals(64, r.y);
            assertEquals(10, r.z);
        }

        @Test
        @DisplayName("Corner hit with look direction facing south → North face wins")
        void cornerFacingSouth() {
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                10.0, 64.0, 10.0,
                0.0, 0.0, 1.0
            );
            assertEquals("North", r.blockFaceName);
            assertEquals(10, r.x);
            assertEquals(64, r.y);
            assertEquals(9, r.z);
        }

        @Test
        @DisplayName("Regression: facing south, looking slightly up, hit on north face at west edge")
        void regressionSouthLookingUp() {
            // This is the scenario described in the bug report:
            // Player facing south (+Z), looking slightly up, ray hits north face
            // at the west edge of the block. Without fix, West face was detected
            // instead of North, causing interaction failure.
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                10.0, 64.7, 10.0,
                0.0, 0.2, 0.98
            );
            assertEquals("North", r.blockFaceName);
            assertEquals(10, r.x);
            assertEquals(64, r.y);
            assertEquals(9, r.z);
        }
    }

    // ===== Edge Cases =====

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Hit exactly on block corner (0,0,0) → detects West face (X checked first)")
        void cornerHit() {
            // When hit is at exact corner (0,0,0) relative to block,
            // dx=0, dy=0, dz=0 — X is checked first so West wins
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                10.0, 64.0, 10.0
            );
            assertEquals("West", r.blockFaceName);
            assertEquals(9, r.x);
        }

        @Test
        @DisplayName("Hit exactly on opposite corner (1,1,1) → detects East face (X checked first)")
        void oppositeCornerHit() {
            // dx=1, dy=1, dz=1 — X is checked first so East wins
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                11.0, 65.0, 11.0
            );
            assertEquals("East", r.blockFaceName);
            assertEquals(11, r.x);
        }

        @Test
        @DisplayName("Hit at center of block → fallback picks closest boundary")
        void centerHit() {
            // All deltas are 0.5, all distances to boundaries are 0.5
            // With exact tie, West wins (first checked in fallback chain)
            PlacementResult r = InteractionPositionFixer.calculatePlacementFromHit(
                10, 64, 10,
                10.5, 64.5, 10.5
            );
            assertNotNull(r);
            assertTrue(r.face.contains("[fallback]"));
        }

        @Test
        @DisplayName("Result is non-null for any valid input")
        void neverReturnsNull() {
            // Test a variety of inputs to ensure we never get null
            assertNotNull(InteractionPositionFixer.calculatePlacementFromHit(0, 0, 0, 0.5, 0.5, 0.5));
            assertNotNull(InteractionPositionFixer.calculatePlacementFromHit(-100, -100, -100, -99.5, -99.5, -99.5));
            assertNotNull(InteractionPositionFixer.calculatePlacementFromHit(Integer.MAX_VALUE - 1, 0, 0,
                (double)(Integer.MAX_VALUE - 1) + 0.5, 0.5, 0.5));
        }
    }
}
