package com.CodeCreature.crafting;

import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates shadow recipes with {@code PlaceBlock} ResourceTypeId input and
 * {@code Blueprint} {@link BenchRequirement} for every placeable-output recipe.
 * Original recipes are NOT modified.
 *
 * <p>Must run during {@code LoadAssetEvent}, after {@code DropScaler.apply()}.
 */
public final class BlueprintBenchRecipeMutator {

    private static final String BLUEPRINT_ID = "Blueprint";
    private static final Map<String, String> SHADOW_TO_ORIGINAL = new HashMap<>();

    private BlueprintBenchRecipeMutator() {}

    public static String getOriginalRecipeId(String shadowRecipeId) {
        return SHADOW_TO_ORIGINAL.get(shadowRecipeId);
    }

    private static void log(String msg) {
        System.out.println("[BlueprintBenchMutator] " + msg);
    }

    public static void mutate() {
        Field idField, inputField, benchReqField, knowledgeField, memoriesField;
        try {
            idField = CraftingRecipe.class.getDeclaredField("id");
            idField.setAccessible(true);
            inputField = CraftingRecipe.class.getDeclaredField("input");
            inputField.setAccessible(true);
            benchReqField = CraftingRecipe.class.getDeclaredField("benchRequirement");
            benchReqField.setAccessible(true);
            knowledgeField = CraftingRecipe.class.getDeclaredField("knowledgeRequired");
            knowledgeField.setAccessible(true);
            memoriesField = CraftingRecipe.class.getDeclaredField("requiredMemoriesLevel");
            memoriesField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            log("ERROR: Could not find required fields on CraftingRecipe: " + e.getMessage());
            return;
        }

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
                String shadowId = "Blueprint_" + originalId;

                // Override id
                idField.set(shadow, shadowId);

                // Override input to use PlaceBlock ResourceType (matches all 3 placeholder colors)
                inputField.set(shadow, new MaterialQuantity[]{
                        new MaterialQuantity(null, "PlaceBlock", null, 1, null)
                });

                // Override benchRequirement to Blueprint only
                benchReqField.set(shadow, new BenchRequirement[]{
                        new BenchRequirement(BenchType.StructuralCrafting, BLUEPRINT_ID, sourceReq.categories, 0)
                });

                // Clear knowledge requirements — StructuralCrafting doesn't support them,
                // and inherited values from the original recipe would keep recipes locked
                knowledgeField.set(shadow, false);
                memoriesField.set(shadow, 1);

                shadowRecipes.add(shadow);
                SHADOW_TO_ORIGINAL.put(shadowId, originalId);
            } catch (Exception e) {
                log("ERROR creating shadow for recipe " + recipe.getId() + ": " + e.getMessage());
            }
        }

        if (!shadowRecipes.isEmpty()) {
            try {
                CraftingRecipe.getAssetStore().loadAssets("Hytale:Hytale", shadowRecipes);
                log("Registered " + shadowRecipes.size() + " shadow Blueprint recipes.");

                // Diagnostic: verify shadow recipes are in the asset map
                int found = 0, missing = 0;
                for (CraftingRecipe shadow : shadowRecipes) {
                    CraftingRecipe lookup = CraftingRecipe.getAssetMap().getAsset(shadow.getId());
                    if (lookup != null) {
                        found++;
                    } else {
                        missing++;
                        log("MISSING from asset map: " + shadow.getId());
                    }
                }
                log("Asset map verification: " + found + " found, " + missing + " missing.");

                // Also log a sample shadow recipe's details for debugging
                if (!shadowRecipes.isEmpty()) {
                    CraftingRecipe sample = shadowRecipes.get(0);
                    MaterialQuantity[] sampleInput = sample.getInput();
                    MaterialQuantity sampleOutput = sample.getPrimaryOutput();
                    BenchRequirement[] sampleReqs = sample.getBenchRequirement();
                    log("Sample shadow: id=" + sample.getId()
                            + " input=" + (sampleInput != null ? sampleInput.length + " entries, rtId=" + (sampleInput.length > 0 ? sampleInput[0].getResourceTypeId() : "none") : "null")
                            + " output=" + (sampleOutput != null ? sampleOutput.getItemId() : "null")
                            + " benchReq=" + (sampleReqs != null ? sampleReqs.length + " entries, id=" + (sampleReqs.length > 0 ? sampleReqs[0].id : "none") : "null"));
                }
            } catch (Exception e) {
                log("ERROR registering shadow recipes: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            log("WARNING: No shadow recipes created.");
        }
    }

    static boolean hasBlueprintRequirement(CraftingRecipe recipe) {
        BenchRequirement[] reqs = recipe.getBenchRequirement();
        if (reqs == null) return false;
        for (BenchRequirement req : reqs) {
            if (req != null && BLUEPRINT_ID.equals(req.id)) return true;
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
