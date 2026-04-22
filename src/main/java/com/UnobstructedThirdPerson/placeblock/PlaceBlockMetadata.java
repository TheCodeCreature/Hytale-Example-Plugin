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

    private static final String RECIPE_ID_KEY = "RecipeId";
    private static final String TARGET_BLOCK_KEY = "TargetBlockId";

    private PlaceBlockMetadata() {}

    public static boolean isPlaceBlock(@Nullable ItemStack stack) {
        if (stack == null) return false;
        String id = stack.getItemId();
        return PLACEHOLDER_BLUE.equals(id) || PLACEHOLDER_GREEN.equals(id) || PLACEHOLDER_RED.equals(id);
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
