package com.UnobstructedThirdPerson.placeblock;

import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;

/**
 * Adds a {@code Blueprint} {@link BenchRequirement} to every recipe that
 * targets the {@code Builders} or {@code Furniture_Bench} benches, so those
 * recipes appear in the Blueprint Bench's crafting window at runtime.
 *
 * <p>Must run during {@code LoadAssetEvent}, after {@code DropScaler.apply()}.
 */
public final class BlueprintBenchRecipeMutator {

    private static final String BLUEPRINT_ID = "Blueprint";
    private static final Set<String> SOURCE_BENCH_IDS = Set.of("Builders", "Furniture_Bench");

    private BlueprintBenchRecipeMutator() {}

    private static void log(String msg) {
        System.out.println("[BlueprintBenchMutator] " + msg);
    }

    public static void mutate() {
        Field benchReqField;
        try {
            benchReqField = CraftingRecipe.class.getDeclaredField("benchRequirement");
            benchReqField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            log("ERROR: Could not find benchRequirement field on CraftingRecipe: " + e.getMessage());
            return;
        }

        int mutated = 0;

        for (CraftingRecipe recipe : CraftingRecipe.getAssetMap().getAssetMap().values()) {
            if (recipe == null) continue;

            BenchRequirement[] reqs = recipe.getBenchRequirement();
            if (reqs == null || reqs.length == 0) continue;

            // Check if already has a Blueprint entry (idempotency)
            boolean alreadyHasBlueprint = false;
            BenchRequirement sourceReq = null;

            for (BenchRequirement req : reqs) {
                if (req == null) continue;
                if (BLUEPRINT_ID.equals(req.id)) {
                    alreadyHasBlueprint = true;
                    break;
                }
                if (sourceReq == null && SOURCE_BENCH_IDS.contains(req.id)) {
                    sourceReq = req;
                }
            }

            if (alreadyHasBlueprint || sourceReq == null) continue;

            // Create a new BenchRequirement for the Blueprint bench
            BenchRequirement blueprintReq = createBlueprintRequirement(sourceReq);
            if (blueprintReq == null) continue;

            // Append to the existing array
            BenchRequirement[] expanded = Arrays.copyOf(reqs, reqs.length + 1);
            expanded[reqs.length] = blueprintReq;

            try {
                benchReqField.set(recipe, expanded);
                mutated++;
            } catch (IllegalAccessException e) {
                log("ERROR: Could not set benchRequirement on recipe " + recipe.getId() + ": " + e.getMessage());
            }
        }

        log("Mutated " + mutated + " recipes to include Blueprint bench requirement.");
    }

    private static BenchRequirement createBlueprintRequirement(BenchRequirement source) {
        try {
            BenchRequirement req = BenchRequirement.class.getDeclaredConstructor().newInstance();
            req.id = BLUEPRINT_ID;

            // Set type to StructuralCrafting (matches Blueprint Bench's Bench.Type)
            Field typeField = BenchRequirement.class.getDeclaredField("type");
            typeField.setAccessible(true);
            typeField.set(req, BenchType.StructuralCrafting);

            // Copy categories from the source requirement
            Field catField = BenchRequirement.class.getDeclaredField("categories");
            catField.setAccessible(true);
            String[] srcCategories = (String[]) catField.get(source);
            if (srcCategories != null) {
                catField.set(req, Arrays.copyOf(srcCategories, srcCategories.length));
            }

            // Copy requiredTierLevel from the source requirement
            Field tierField = BenchRequirement.class.getDeclaredField("requiredTierLevel");
            tierField.setAccessible(true);
            tierField.set(req, tierField.get(source));

            return req;
        } catch (Exception e) {
            log("ERROR: Could not create BenchRequirement: " + e.getMessage());
            return null;
        }
    }
}
