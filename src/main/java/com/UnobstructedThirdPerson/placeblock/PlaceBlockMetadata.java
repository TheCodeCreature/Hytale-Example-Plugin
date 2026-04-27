package com.UnobstructedThirdPerson.placeblock;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlaceBlockMetadata {

    public static final String PLACEHOLDER_ID = "Block_Placeholder";
    public static final String ARMED_GREEN_PREFIX = "*Block_Placeholder_State_Armed_Green_";
    public static final String ARMED_RED_ID = "*Block_Placeholder_State_Armed_Red";
    public static final int HOTBAR_SIZE = 9;

    private static final String RECIPE_ID_KEY = "RecipeId";
    private static final String TARGET_BLOCK_KEY = "TargetBlockId";
    private static final String SLOT_INDEX_KEY = "SlotIndex";

    private PlaceBlockMetadata() {}

    public static boolean isPlaceBlock(@Nullable ItemStack stack) {
        if (stack == null) return false;
        String id = stack.getItemId();
        return PLACEHOLDER_ID.equals(id)
                || id.startsWith(ARMED_GREEN_PREFIX)
                || ARMED_RED_ID.equals(id);
    }

    public static boolean isArmed(@Nullable ItemStack stack) {
        if (stack == null) return false;
        String id = stack.getItemId();
        return id.startsWith(ARMED_GREEN_PREFIX) || ARMED_RED_ID.equals(id);
    }

    public static boolean isGreenVariant(@Nullable ItemStack stack) {
        if (stack == null) return false;
        return stack.getItemId().startsWith(ARMED_GREEN_PREFIX);
    }

    @Nonnull
    public static String getGreenStateItemId(int slot) {
        return ARMED_GREEN_PREFIX + slot;
    }

    public static int getSlotIndex(@Nullable ItemStack stack) {
        if (stack == null) return -1;
        String id = stack.getItemId();
        if (id.startsWith(ARMED_GREEN_PREFIX)) {
            String suffix = id.substring(ARMED_GREEN_PREFIX.length());
            try {
                return Integer.parseInt(suffix);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        if (ARMED_RED_ID.equals(id)) {
            BsonDocument metadata = stack.getMetadata();
            if (metadata == null) return -1;
            BsonValue val = metadata.get(SLOT_INDEX_KEY);
            if (val == null || !val.isInt32()) return -1;
            return val.asInt32().getValue();
        }
        return -1;
    }

    @Nullable
    public static String getArmedRecipeId(@Nullable ItemStack stack) {
        if (stack == null) return null;
        BsonDocument metadata = stack.getMetadata();
        if (metadata == null) return null;
        BsonValue val = metadata.get(RECIPE_ID_KEY);
        if (val == null || !val.isString()) return null;
        return val.asString().getValue();
    }

    @Nullable
    public static String getOutputBlockTypeId(@Nullable ItemStack stack) {
        if (stack == null) return null;
        BsonDocument metadata = stack.getMetadata();
        if (metadata == null) return null;
        BsonValue val = metadata.get(TARGET_BLOCK_KEY);
        if (val == null || !val.isString()) return null;
        return val.asString().getValue();
    }

    @Nonnull
    public static ItemStack arm(@Nonnull ItemStack stack, @Nonnull String recipeId,
                                @Nonnull String blockTypeId, int hotbarSlot) {
        BsonDocument metadata = stack.getMetadata() != null ? stack.getMetadata() : new BsonDocument();
        metadata.put(RECIPE_ID_KEY, new BsonString(recipeId));
        metadata.put(TARGET_BLOCK_KEY, new BsonString(blockTypeId));
        metadata.put(SLOT_INDEX_KEY, new BsonInt32(hotbarSlot));
        ItemStack withMetadata = new ItemStack(stack.getItemId(), stack.getQuantity(), metadata);
        return withMetadata.withState("Armed_Green_" + hotbarSlot);
    }

    @Nonnull
    public static ItemStack disarm(@Nonnull ItemStack stack) {
        BsonDocument metadata = stack.getMetadata();
        if (metadata != null) {
            metadata.remove(RECIPE_ID_KEY);
            metadata.remove(TARGET_BLOCK_KEY);
            metadata.remove(SLOT_INDEX_KEY);
        }
        return new ItemStack(PLACEHOLDER_ID, stack.getQuantity(), metadata);
    }

    @Nonnull
    public static ItemStack toArmedGreen(@Nonnull ItemStack stack, int slot) {
        BsonDocument metadata = stack.getMetadata() != null ? stack.getMetadata() : new BsonDocument();
        metadata.put(SLOT_INDEX_KEY, new BsonInt32(slot));
        ItemStack withMetadata = new ItemStack(stack.getItemId(), stack.getQuantity(), metadata);
        return withMetadata.withState("Armed_Green_" + slot);
    }

    @Nonnull
    public static ItemStack toArmedRed(@Nonnull ItemStack stack) {
        BsonDocument metadata = stack.getMetadata() != null ? stack.getMetadata() : new BsonDocument();
        ItemStack withMetadata = new ItemStack(stack.getItemId(), stack.getQuantity(), metadata);
        return withMetadata.withState("Armed_Red");
    }

}
