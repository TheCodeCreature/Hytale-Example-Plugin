package com.UnobstructedThirdPerson.portablebench;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable configuration for a portable bench tool item.
 * Loaded from {@code portable_benches.json} via {@link PortableBenchConfigLoader}.
 *
 * <p>Each config maps an item ID to a bench definition with grouped recipe categories.
 * The categories define how engine recipe category IDs are grouped into UI tabs.</p>
 */
public record PortableBenchConfig(
        String benchId,
        String benchName,
        CategoryDef[] categories
) {

    /**
     * Defines a single UI category tab in the PocketCrafting window.
     *
     * @param id               unique identifier for this UI category
     * @param name             display name or localization key shown on the tab
     * @param icon             asset path for the tab icon (e.g. {@code "Icons/CraftingCategories/..."})
     *                         or empty string if no icon
     * @param recipeCategoryIds engine recipe category IDs whose recipes are merged into this tab
     */
    public record CategoryDef(
            String id,
            String name,
            String icon,
            String[] recipeCategoryIds
    ) {
        public CategoryDef {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(recipeCategoryIds, "recipeCategoryIds");
            if (recipeCategoryIds.length == 0) {
                throw new IllegalArgumentException("recipeCategoryIds must not be empty");
            }
            recipeCategoryIds = recipeCategoryIds.clone();
        }

        @Override
        public String[] recipeCategoryIds() {
            return recipeCategoryIds.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CategoryDef that)) return false;
            return id.equals(that.id)
                    && name.equals(that.name)
                    && icon.equals(that.icon)
                    && Arrays.equals(recipeCategoryIds, that.recipeCategoryIds);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(id, name, icon);
            result = 31 * result + Arrays.hashCode(recipeCategoryIds);
            return result;
        }
    }

    public PortableBenchConfig {
        Objects.requireNonNull(benchId, "benchId");
        Objects.requireNonNull(benchName, "benchName");
        Objects.requireNonNull(categories, "categories");
        if (categories.length == 0) {
            throw new IllegalArgumentException("categories must not be empty");
        }
        categories = categories.clone();
    }

    @Override
    public CategoryDef[] categories() {
        return categories.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PortableBenchConfig that)) return false;
        return benchId.equals(that.benchId)
                && benchName.equals(that.benchName)
                && Arrays.equals(categories, that.categories);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(benchId, benchName);
        result = 31 * result + Arrays.hashCode(categories);
        return result;
    }
}
