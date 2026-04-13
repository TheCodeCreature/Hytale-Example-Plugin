package com.UnobstructedThirdPerson.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static com.UnobstructedThirdPerson.resourcecollection.AssetTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PlacedBlockDropModifier}.
 * Validates that natural blocks get {@code useDefaultDropWhenPlaced = true}
 * so that player-placed copies drop 1x while world-generated copies keep
 * their scaled drops.
 */
class PlacedBlockDropModifierTest {

    private TestDataSet data;

    @BeforeEach
    void setUp() {
        data = new TestDataSet();
        data.install();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void naturalBlockWithBreakingGetsFlagSet() {
        PlacedBlockDropModifier.apply();

        BlockGathering gathering = data.rockStone.getGathering();
        assertTrue(readUseDefaultDropWhenPlaced(gathering),
                "Rock_Stone (natural, breaking) should have useDefaultDropWhenPlaced=true");
    }

    @Test
    void naturalBlockWithSoftOnlyGetsFlagSet() {
        PlacedBlockDropModifier.apply();

        BlockGathering gathering = data.dirt.getGathering();
        assertTrue(readUseDefaultDropWhenPlaced(gathering),
                "Dirt (natural, soft-only) should have useDefaultDropWhenPlaced=true");
    }

    @Test
    void naturalBlockWithDropListGetsFlagSet() {
        PlacedBlockDropModifier.apply();

        BlockGathering gathering = data.bushBerry.getGathering();
        assertTrue(readUseDefaultDropWhenPlaced(gathering),
                "Bush_Berry (natural, dropList) should have useDefaultDropWhenPlaced=true");
    }

    @Test
    void naturalBlockWithHarvestGetsFlagSet() {
        PlacedBlockDropModifier.apply();

        BlockGathering gathering = data.leavesOak.getGathering();
        assertTrue(readUseDefaultDropWhenPlaced(gathering),
                "Leaves_Oak (natural, harvest) should have useDefaultDropWhenPlaced=true");
    }

    @Test
    void recipeBlockDoesNotGetFlag() {
        PlacedBlockDropModifier.apply();

        BlockGathering slabGathering = data.woodSlabOak.getGathering();
        assertFalse(readUseDefaultDropWhenPlaced(slabGathering),
                "Wood_Slab_Oak (recipe block) should NOT get useDefaultDropWhenPlaced");

        BlockGathering railGathering = data.railIron.getGathering();
        assertFalse(readUseDefaultDropWhenPlaced(railGathering),
                "Rail_Iron (recipe block) should NOT get useDefaultDropWhenPlaced");
    }

    @Test
    void blockWithNoGatheringSkipped() {
        BlockType noGathering = blockType("NoGather", null);
        data.blockTypes.put("NoGather", noGathering);
        data.naturalBlockIds.add("NoGather");
        data.install();

        assertDoesNotThrow(() -> PlacedBlockDropModifier.apply());
    }

    @Test
    void alreadySetFlagNotDoubleApplied() {
        // Apply twice — should still be true without error
        PlacedBlockDropModifier.apply();
        PlacedBlockDropModifier.apply();

        assertTrue(readUseDefaultDropWhenPlaced(data.rockStone.getGathering()));
    }

    @Test
    void blockMissingFromAssetStoreSkipped() {
        data.naturalBlockIds.add("Ghost_Block");
        setNaturalRegistry(data.naturalBlockIds, data.naturalItemIds);

        assertDoesNotThrow(() -> PlacedBlockDropModifier.apply());
    }

    @Test
    void emptyRegistryDoesNothing() {
        setNaturalRegistry(new HashSet<>(), new HashSet<>());

        assertDoesNotThrow(() -> PlacedBlockDropModifier.apply());

        // Original blocks unchanged
        assertFalse(readUseDefaultDropWhenPlaced(data.rockStone.getGathering()));
    }

    @Test
    void sandWithPhysicsGetsFlagSet() {
        PlacedBlockDropModifier.apply();

        BlockGathering gathering = data.sand.getGathering();
        assertTrue(readUseDefaultDropWhenPlaced(gathering),
                "Sand (natural, soft+physics) should have useDefaultDropWhenPlaced=true");
    }

    @Test
    void allNaturalBlocksGetFlag() {
        PlacedBlockDropModifier.apply();

        for (String blockTypeId : NaturalResourceRegistry.getNaturalBlockTypes()) {
            if (BlockRecipeRegistry.hasRecipe(blockTypeId)) continue;
            BlockType bt = BlockType.getAssetMap().getAssetMap().get(blockTypeId);
            if (bt == null || bt.getGathering() == null) continue;

            assertTrue(readUseDefaultDropWhenPlaced(bt.getGathering()),
                    blockTypeId + " should have useDefaultDropWhenPlaced=true");
        }
    }
}
