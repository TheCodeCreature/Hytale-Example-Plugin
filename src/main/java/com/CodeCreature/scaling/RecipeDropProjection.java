package com.CodeCreature.scaling;

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

    @Nullable
    public ProjectedDrop fromInput(@Nonnull MaterialQuantity input, boolean preferNatural) {
        String resourceTypeId = normalize(input.getResourceTypeId());
        if (resourceTypeId != null) {
            String genericTypeId = proxyCatalog.toGenericTypeId(resourceTypeId);
            return new ProjectedDrop(
                    proxyCatalog.buildProxyItemId(genericTypeId),
                    Math.max(1, input.getQuantity()),
                    true,
                    genericTypeId
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

    public record ProjectedDrop(String itemId, int quantity, boolean generic, String resourceTypeId) {}
}
