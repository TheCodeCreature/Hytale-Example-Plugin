package com.CodeCreature.crafting;

import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import com.CodeCreature.scaling.AssetFieldAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.CodeCreature.util.DebugLogger;
import static com.CodeCreature.util.DebugLogger.Subsystem.*;

/**
 * Creates shadow recipes with {@code PlaceBlock} ResourceTypeId input and
 * {@code Stencil} {@link BenchRequirement} for every placeable-output recipe.
 * Original recipes are NOT modified.
 *
 * <p>Must run during {@code LoadAssetEvent}, after {@code DropScaler.apply()}.
 */
public final class StencilBookRecipeMutator {

    private static final String STENCIL_ID = "Stencil";
    private static final Map<String, String> SHADOW_TO_ORIGINAL = new HashMap<>();

    private StencilBookRecipeMutator() {}

    public static String getOriginalRecipeId(String shadowRecipeId) {
        return SHADOW_TO_ORIGINAL.get(shadowRecipeId);
    }

    public static void mutate() {
        AssetFieldAccessor f = AssetFieldAccessor.INSTANCE;

        List<CraftingRecipe> shadowRecipes = new ArrayList<>();

        for (CraftingRecipe recipe : CraftingRecipe.getAssetMap().getAssetMap().values()) {
            if (recipe == null) continue;

            BenchRequirement[] reqs = recipe.getBenchRequirement();
            if (reqs == null || reqs.length == 0) continue;

            if (!hasPlaceableOutput(recipe)) continue;

            // Find the first non-null source requirement for categories
            BenchRequirement sourceReq = null;
            for (BenchRequirement req : reqs) {
                if (req != null) {
                    sourceReq = req;
                    break;
                }
            }
            if (sourceReq == null) continue;

            try {
                // Create shadow via copy constructor
                CraftingRecipe shadow = new CraftingRecipe(recipe);

                String originalId = recipe.getId();
                String shadowId = "Stencil_" + originalId;

                // Override id
                f.recipeId.set(shadow, shadowId);

                // Override input to use PlaceBlock ResourceType (matches all 3 placeholder colors)
                f.recipeInput.set(shadow, new MaterialQuantity[]{
                        new MaterialQuantity(null, "PlaceBlock", null, 1, null)
                });

                // Override benchRequirement to Stencil only
                f.recipeBenchRequirement.set(shadow, new BenchRequirement[]{
                        new BenchRequirement(BenchType.StructuralCrafting, STENCIL_ID, sourceReq.categories, 0)
                });

                // Clear knowledge requirements — StructuralCrafting doesn't support them,
                // and inherited values from the original recipe would keep recipes locked
                f.recipeKnowledgeRequired.set(shadow, false);
                f.recipeMemoriesLevel.set(shadow, 1);

                shadowRecipes.add(shadow);
                SHADOW_TO_ORIGINAL.put(shadowId, originalId);
            } catch (Exception e) {
                DebugLogger.log(CRAFTING, Level.INFO, "[StencilBookMutator] ERROR creating shadow for recipe " + recipe.getId() + ": " + e.getMessage());
            }
        }

        if (!shadowRecipes.isEmpty()) {
            try {
                CraftingRecipe.getAssetStore().loadAssets("Hytale:Hytale", shadowRecipes);
                DebugLogger.log(CRAFTING, Level.INFO, "[StencilBookMutator] Registered " + shadowRecipes.size() + " shadow Stencil recipes.");

                // Diagnostic: verify shadow recipes are in the asset map
                int found = 0, missing = 0;
                for (CraftingRecipe shadow : shadowRecipes) {
                    CraftingRecipe lookup = CraftingRecipe.getAssetMap().getAsset(shadow.getId());
                    if (lookup != null) {
                        found++;
                    } else {
                        missing++;
                        DebugLogger.log(CRAFTING, Level.INFO, "[StencilBookMutator] MISSING from asset map: " + shadow.getId());
                    }
                }
                DebugLogger.log(CRAFTING, Level.INFO, "[StencilBookMutator] Asset map verification: " + found + " found, " + missing + " missing.");

                // Also log a sample shadow recipe's details for debugging
                if (!shadowRecipes.isEmpty()) {
                    CraftingRecipe sample = shadowRecipes.get(0);
                    MaterialQuantity[] sampleInput = sample.getInput();
                    MaterialQuantity sampleOutput = sample.getPrimaryOutput();
                    BenchRequirement[] sampleReqs = sample.getBenchRequirement();
                    DebugLogger.log(CRAFTING, Level.INFO, "[StencilBookMutator] Sample shadow: id=" + sample.getId()
                            + " input=" + (sampleInput != null ? sampleInput.length + " entries, rtId=" + (sampleInput.length > 0 ? sampleInput[0].getResourceTypeId() : "none") : "null")
                            + " output=" + (sampleOutput != null ? sampleOutput.getItemId() : "null")
                            + " benchReq=" + (sampleReqs != null ? sampleReqs.length + " entries, id=" + (sampleReqs.length > 0 ? sampleReqs[0].id : "none") : "null"));
                }
            } catch (Exception e) {
                DebugLogger.log(CRAFTING, Level.INFO, "[StencilBookMutator] ERROR registering shadow recipes: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            DebugLogger.log(CRAFTING, Level.INFO, "[StencilBookMutator] WARNING: No shadow recipes created.");
        }
    }

    static boolean hasStencilRequirement(CraftingRecipe recipe) {
        BenchRequirement[] reqs = recipe.getBenchRequirement();
        if (reqs == null) return false;
        for (BenchRequirement req : reqs) {
            if (req != null && STENCIL_ID.equals(req.id)) return true;
        }
        return false;
    }

    private static boolean hasPlaceableOutput(CraftingRecipe recipe) {
        MaterialQuantity primaryOutput = recipe.getPrimaryOutput();
        if (primaryOutput == null) return false;
        String outputItemId = primaryOutput.getItemId();
        if (outputItemId == null) return false;
        Item outputItem = Item.getAssetMap().getAsset(outputItemId);
        if (outputItem == null) return false;
        return outputItem.getBlockId() != null;
    }
}
