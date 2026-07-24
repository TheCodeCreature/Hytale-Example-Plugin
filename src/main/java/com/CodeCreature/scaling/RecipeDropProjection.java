package com.CodeCreature.scaling;

/**
 * @node    RecipeDropProjection
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Converts per-unit recipe inputs into break-drop entries while preserving authored
 *          generic ResourceTypeId inputs as proxy item IDs.
 * @wave    2 (projection integration)
 * @status  Wave 2 - implemented generic-preserving recipe drop projection
 * @do-not  Use RecipeTreeResolver raw projection helpers from this execution path.
 */

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.CodeCreature.crafting.PlaceBlockCostUtil;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

public final class RecipeDropProjection {

    private final GenericDropProxyCatalog proxyCatalog;

    public RecipeDropProjection(@Nonnull GenericDropProxyCatalog proxyCatalog) {
        this.proxyCatalog = proxyCatalog;
    }

    /** @intent Project a recipe into per-unit break-drops while keeping generic inputs generic.
     *  @wave   2 - implemented
     *  @status implemented
     *  @node   RecipeDropProjection#projectRecipeDrops */
    @Nonnull
    public List<ProjectedDrop> projectRecipeDrops(@Nonnull CraftingRecipe recipe, boolean preferNatural) {
        List<MaterialQuantity> perUnitInputs = PlaceBlockCostUtil.getPerUnitCost(recipe);
        if (perUnitInputs.isEmpty()) {
            return List.of();
        }

        List<ProjectedDrop> drops = new ArrayList<>(perUnitInputs.size());
        for (MaterialQuantity input : perUnitInputs) {
            ProjectedDrop projected = fromInput(input, preferNatural);
            if (projected != null) {
                drops.add(projected);
            }
        }
        return drops;
    }

    /** @intent Project one authored input to either a proxy drop or a concrete drop.
     *  @wave   2 - implemented
     *  @status implemented
     *  @node   RecipeDropProjection#fromInput */
    @Nullable
    public ProjectedDrop fromInput(@Nonnull MaterialQuantity input, boolean preferNatural) {
        String resourceTypeId = normalize(input.getResourceTypeId());
        if (resourceTypeId != null) {
            return new ProjectedDrop(
                    proxyCatalog.buildProxyItemId(resourceTypeId),
                    Math.max(1, input.getQuantity()),
                    true,
                    resourceTypeId
            );
        }

        String concreteItemId = ResourceTypeResolver.resolveInputItemId(input, preferNatural);
        if (concreteItemId == null || concreteItemId.isEmpty()) {
            return null;
        }

        return new ProjectedDrop(concreteItemId, Math.max(1, input.getQuantity()), false, null);
    }

    private static String normalize(@Nullable String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /** @intent Represent one projected recipe-drop output with generic trace metadata.
     *  @wave   2 - implemented
     *  @status implemented
     *  @node   RecipeDropProjection#ProjectedDrop */
    public record ProjectedDrop(String itemId, int quantity, boolean generic, String resourceTypeId) {}
}
