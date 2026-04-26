package com.UnobstructedThirdPerson.placeblock;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlaceBlockMetadata {

    public static final String PLACEHOLDER_BLUE = "Block_Placeholder_Blue";
    public static final String PLACEHOLDER_GREEN = "Block_Placeholder_Green";
    public static final String PLACEHOLDER_RED = "Block_Placeholder_Red";
    public static final String GREEN_VARIANT_PREFIX = "Block_Placeholder_Green_";
    public static final int HOTBAR_SIZE = 9;

    private static final String RECIPE_ID_KEY = "RecipeId";
    private static final String TARGET_BLOCK_KEY = "TargetBlockId";

    private PlaceBlockMetadata() {}

    public static boolean isPlaceBlock(@Nullable ItemStack stack) {
        if (stack == null) return false;
        String id = stack.getItemId();
        return PLACEHOLDER_BLUE.equals(id) || PLACEHOLDER_GREEN.equals(id) || PLACEHOLDER_RED.equals(id)
                || id.startsWith(GREEN_VARIANT_PREFIX);
    }

    public static boolean isGreenVariant(@Nullable ItemStack stack) {
        if (stack == null) return false;
        String id = stack.getItemId();
        return PLACEHOLDER_GREEN.equals(id) || id.startsWith(GREEN_VARIANT_PREFIX);
    }

    @Nonnull
    public static String getVariantItemId(int hotbarSlot) {
        return GREEN_VARIANT_PREFIX + hotbarSlot;
    }

    @Nonnull
    public static ItemStack toVariant(@Nonnull ItemStack stack, int hotbarSlot) {
        String variantId = getVariantItemId(hotbarSlot);
        if (variantId.equals(stack.getItemId())) return stack;
        return new ItemStack(variantId, stack.getQuantity(), stack.getMetadata());
    }

    @Nonnull
    public static ItemStack toBaseGreen(@Nonnull ItemStack stack) {
        if (PLACEHOLDER_GREEN.equals(stack.getItemId())) return stack;
        return new ItemStack(PLACEHOLDER_GREEN, stack.getQuantity(), stack.getMetadata());
    }

    public static boolean isArmed(@Nullable ItemStack stack) {
        return getArmedRecipeId(stack) != null;
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
    public static ItemStack setArmedRecipeId(@Nonnull ItemStack stack, @Nonnull String recipeId, @Nonnull String blockTypeId) {
        BsonDocument metadata = stack.getMetadata();
        if (metadata == null) metadata = new BsonDocument();
        metadata.put(RECIPE_ID_KEY, new BsonString(recipeId));
        metadata.put(TARGET_BLOCK_KEY, new BsonString(blockTypeId));
        return new ItemStack(PLACEHOLDER_GREEN, stack.getQuantity(), metadata);
    }

    @Nonnull
    public static ItemStack clearArmedRecipe(@Nonnull ItemStack stack) {
        BsonDocument metadata = stack.getMetadata();
        if (metadata != null) {
            metadata.remove(RECIPE_ID_KEY);
            metadata.remove(TARGET_BLOCK_KEY);
        }
        return new ItemStack(PLACEHOLDER_BLUE, stack.getQuantity(), metadata);
    }
}
