package com.CodeCreature.scaling;

import com.CodeCreature.registry.BenchRecipeRegistries;
import com.CodeCreature.registry.BenchRegistry;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Classifies every block type by the set of bench IDs its crafting recipe
 * requires (filtered through {@link BenchRegistry#allBenchIds()}).
 *
 * <p>Built once after {@link BenchRecipeRegistries#init()} completes.
 * The classification is used by {@link DropScaler} to partition blocks into
 * independent sets that can be processed in parallel by
 * {@link GenericBenchProcessor} instances.
 *
 * <p>This class is immutable after {@link #classify()} — safe to read
 * from multiple threads.
 */
public final class BenchBlockClassifier {

    /** blockTypeId → set of bench IDs from the recipe's BenchRequirements */
    private Map<String, Set<String>> blockBenchSets = Collections.emptyMap();

    /**
     * Scans all block types and determines which bench(es) each recipe block
     * requires, filtering through {@link BenchRegistry#allBenchIds()}.
     *
     * <p>Must be called after {@link BenchRecipeRegistries#init()}.
     */
    public void classify() {
        Map<String, Set<String>> benchSets = new HashMap<>();
        Set<String> allBenchIds = BenchRegistry.allBenchIds();

        for (var entry : BlockType.getAssetMap().getAssetMap().entrySet()) {
            BlockType bt = entry.getValue();
            if (bt == null) continue;
            String btId = bt.getId();
            if ("Empty".equals(btId) || "Unknown".equals(btId)) continue;

            CraftingRecipe recipe = BenchRecipeRegistries.getRecipeForBlock(btId);
            if (recipe == null) continue;

            BenchRequirement[] reqs = recipe.getBenchRequirement();
            if (reqs == null) continue;

            Set<String> matched = new LinkedHashSet<>();
            for (BenchRequirement req : reqs) {
                if (req != null && req.id != null && allBenchIds.contains(req.id)) {
                    matched.add(req.id);
                }
            }
            if (!matched.isEmpty()) {
                benchSets.put(btId, Collections.unmodifiableSet(matched));
            }
        }

        this.blockBenchSets = Collections.unmodifiableMap(benchSets);
    }

    /**
     * Returns the set of bench IDs for a block type, or {@code null} if
     * the block has no recipe in any registered bench.
     */
    @Nonnull
    public Set<String> getBenchSet(@Nonnull String blockTypeId) {
        return blockBenchSets.getOrDefault(blockTypeId, Collections.emptySet());
    }

    /**
     * Returns {@code true} if the block type has a recipe in at least
     * one registered bench.
     */
    public boolean hasRecipe(@Nonnull String blockTypeId) {
        return blockBenchSets.containsKey(blockTypeId);
    }

    /**
     * Returns a map from each distinct bench-set to the set of block type
     * IDs that have exactly that bench-set.
     *
     * @return unmodifiable map of bench-set → block type IDs
     */
    @Nonnull
    public Map<Set<String>, Set<String>> getDistinctBenchSets() {
        Map<Set<String>, Set<String>> result = new LinkedHashMap<>();
        for (var entry : blockBenchSets.entrySet()) {
            result.computeIfAbsent(entry.getValue(), k -> new LinkedHashSet<>()).add(entry.getKey());
        }
        // Make inner sets unmodifiable
        Map<Set<String>, Set<String>> immutable = new LinkedHashMap<>();
        for (var entry : result.entrySet()) {
            immutable.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(immutable);
    }

}
