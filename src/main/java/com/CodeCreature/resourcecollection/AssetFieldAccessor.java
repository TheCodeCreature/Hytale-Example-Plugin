package com.CodeCreature.resourcecollection;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.PhysicsDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;

import java.lang.reflect.Field;

/**
 * Centralized resolution of all reflective field references used by
 * {@link DropScaler} for asset mutation. Resolves every field once at
 * construction time; throws immediately if any field is missing so that
 * Hytale API changes are caught on startup rather than at runtime.
 */
final class AssetFieldAccessor {

    // BlockGathering / BlockType
    final Field gatheringBreaking;
    final Field gatheringSoft;
    final Field blockTypeGathering;
    final Field gatheringUseDefaultDrop;

    // SoftBlockDropType
    final Field softItemId;
    final Field softDropListId;

    // HarvestingDropType
    final Field harvestItemId;
    final Field harvestDropListId;

    // PhysicsDropType
    final Field physicsItemId;
    final Field physicsDropListId;

    // ItemDrop
    final Field dropQuantityMin;
    final Field dropQuantityMax;

    // CraftingRecipe
    final Field recipeInput;

    // Item
    final Field itemMaxStack;

    AssetFieldAccessor() {
        try {
            gatheringBreaking     = resolve(BlockGathering.class, "breaking");
            gatheringSoft         = resolve(BlockGathering.class, "soft");
            blockTypeGathering    = resolve(BlockType.class, "gathering");
            gatheringUseDefaultDrop = resolve(BlockGathering.class, "useDefaultDropWhenPlaced");

            softItemId     = resolve(SoftBlockDropType.class, "itemId");
            softDropListId = resolve(SoftBlockDropType.class, "dropListId");

            harvestItemId     = resolve(HarvestingDropType.class, "itemId");
            harvestDropListId = resolve(HarvestingDropType.class, "dropListId");

            physicsItemId     = resolve(PhysicsDropType.class, "itemId");
            physicsDropListId = resolve(PhysicsDropType.class, "dropListId");

            dropQuantityMin = resolve(ItemDrop.class, "quantityMin");
            dropQuantityMax = resolve(ItemDrop.class, "quantityMax");

            recipeInput = resolve(CraftingRecipe.class, "input");
            itemMaxStack = resolve(Item.class, "maxStack");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(
                    "Asset field resolution failed — Hytale API may have changed: " + e.getMessage(), e);
        }
    }

    private static Field resolve(Class<?> clazz, String name) throws NoSuchFieldException {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
