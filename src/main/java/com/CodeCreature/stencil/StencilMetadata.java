package com.CodeCreature.stencil;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * BSON metadata utility for Blueprint Stencil items.
 *
 * <p>A stencil is any {@link ItemStack} whose BSON metadata contains
 * a {@code "BlueprintStencil"} tag set to {@code "true"} and a
 * {@code "RecipeId"} field identifying the crafting recipe to consume
 * resources from when the stencil is placed.
 *
 * <p>This class is the single source of truth for reading and writing
 * stencil metadata. All stencil detection and creation flows go through
 * these static methods.
 *
 * <p><b>Threading:</b> All methods are stateless and thread-safe.
 */
public final class StencilMetadata {

    /** BSON key whose presence marks an ItemStack as a stencil. */
    private static final String STENCIL_TAG_KEY = "BlueprintStencil";

    /** BSON key storing the recipe ID to consume resources from. */
    private static final String RECIPE_ID_KEY = "RecipeId";

    private StencilMetadata() {}

    /**
     * Checks whether the given ItemStack is a blueprint stencil.
     *
     * <p>An ItemStack is a stencil if and only if its BSON metadata
     * contains a {@value #STENCIL_TAG_KEY} key with value {@code "true"}.
     *
     * <p>This is the primary discriminator used by both
     * {@code StencilPlacementSystem} and {@code PlacementCostScaler}
     * to distinguish stencils from normal block items and PlaceBlock
     * placeholders.
     *
     * @param stack the ItemStack to check, may be null
     * @return true if the stack is a stencil, false otherwise
     */
    public static boolean isStencil(@Nullable ItemStack stack) {
        if (stack == null) return false;
        BsonDocument metadata = stack.getMetadata();
        if (metadata == null) return false;
        BsonValue val = metadata.get(STENCIL_TAG_KEY);
        if (val == null || !val.isString()) return false;
        return "true".equals(val.asString().getValue());
    }

    /**
     * Reads the recipe ID from a stencil's BSON metadata.
     *
     * <p>Returns the value of the {@value #RECIPE_ID_KEY} field if present
     * and is a string, or {@code null} if the stack is not a stencil or
     * the field is missing/wrong type.
     *
     * @param stack the stencil ItemStack, may be null
     * @return the recipe ID string, or null if not found
     */
    @Nullable
    public static String getRecipeId(@Nullable ItemStack stack) {
        if (stack == null) return null;
        BsonDocument metadata = stack.getMetadata();
        if (metadata == null) return null;
        BsonValue val = metadata.get(RECIPE_ID_KEY);
        if (val == null || !val.isString()) return null;
        return val.asString().getValue();
    }

    /**
     * Creates a new stencil ItemStack with the given item type and recipe ID.
     *
     * <p>Constructs an {@code ItemStack(itemTypeKey, 1, bsonDoc)} where
     * the BSON document contains:
     * <ul>
     *   <li>{@value #STENCIL_TAG_KEY} = {@code "true"}</li>
     *   <li>{@value #RECIPE_ID_KEY} = the provided recipeId</li>
     * </ul>
     *
     * <p>The returned item has quantity 1. The {@code itemTypeKey} should
     * be a valid block item ID (e.g., {@code "Oak_Planks"}) so that the
     * engine renders the correct block preview and fires PlaceBlockEvent.
     *
     * @param itemTypeKey the item type key (block item ID)
     * @param recipeId    the crafting recipe ID for resource consumption
     * @return a new ItemStack tagged as a stencil
     */
    @Nonnull
    public static ItemStack createStencil(@Nonnull String itemTypeKey, @Nonnull String recipeId) {
        BsonDocument metadata = new BsonDocument();
        metadata.put(STENCIL_TAG_KEY, new BsonString("true"));
        metadata.put(RECIPE_ID_KEY, new BsonString(recipeId));
        return new ItemStack(itemTypeKey, 2, metadata);
    }
}
