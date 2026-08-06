package com.CodeCreature.scaling;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.CodeCreature.util.DebugLogger;
import static com.CodeCreature.util.DebugLogger.Subsystem.SCALING;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTranslationProperties;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

public final class GenericDropProxyAssetLoader {

    private static final String SAP_TEMPLATE_ITEM_ID = "Ingredient_Tree_Sap";
    private static final String PROXY_MODEL_PATH = "Items/GeneratedProxy/Temp_Quad_2D.blockymodel";

    private final GenericDropProxyCatalog proxyCatalog;

    public GenericDropProxyAssetLoader(@Nonnull GenericDropProxyCatalog proxyCatalog,
                                       @Nonnull AssetFieldAccessor fields) {
        this.proxyCatalog = proxyCatalog;
        // Kept for call-site compatibility; this loader no longer needs direct field accessor usage.
        Objects.requireNonNull(fields, "fields");
    }

    public void ensureProxyAssetsForRecipe(@Nonnull CraftingRecipe recipe) {
        MaterialQuantity[] inputs = recipe.getInput();
        if (inputs == null || inputs.length == 0) return;

        for (MaterialQuantity input : inputs) {
            if (input == null) continue;
            String resourceTypeId = normalize(input.getResourceTypeId());
            if (resourceTypeId == null) continue;
            ensureProxyAsset(resourceTypeId);
        }
    }

    @Nonnull
    public String ensureProxyAsset(@Nonnull String resourceTypeId) {
        String normalizedResourceTypeId = normalizeRequired(resourceTypeId);
        String genericTypeId = proxyCatalog.toGenericTypeId(normalizedResourceTypeId);
        String proxyItemId = proxyCatalog.buildProxyItemId(genericTypeId);

        if (Item.getAssetMap().getAsset(proxyItemId) != null) {
            return proxyItemId;
        }

        Item proxyItem = buildProxyItem(normalizedResourceTypeId, genericTypeId, proxyItemId);
        List<Item> toLoad = new ArrayList<>(1);
        toLoad.add(proxyItem);

        try {
            Item.getAssetStore().loadAssets("Plugin:GenericDropProxies", toLoad);
        } catch (Exception loadFailure) {
            DebugLogger.log(SCALING, Level.WARNING,
                    "[GenericDropProxyAssetLoader] Unable to register proxy asset via loadAssets for "
                            + genericTypeId + " (" + proxyItemId + "): "
                            + loadFailure.getClass().getSimpleName() + ": " + loadFailure.getMessage());
        }

        if (Item.getAssetMap().getAsset(proxyItemId) == null) {
            DebugLogger.log(SCALING, Level.WARNING,
                    "[GenericDropProxyAssetLoader] Proxy asset still unavailable after ensure; "
                            + "drop projection will continue with deterministic proxy ID only: " + proxyItemId);
        }

        return proxyItemId;
    }

    @Nonnull
    public Item buildProxyItem(@Nonnull String sourceResourceTypeId,
                               @Nonnull String genericTypeId,
                               @Nonnull String proxyItemId) {
        String iconPath = proxyCatalog.resolveProxyIconPath(genericTypeId);
        String texturePath = proxyCatalog.resolveProxyTexturePath(genericTypeId);
        String displayName = buildDisplayName(genericTypeId);
        Item item = cloneSapTemplate(proxyItemId);

        try {
            setFieldIfPresent(Item.class, item, "id", proxyItemId);

            if (iconPath != null && !iconPath.isEmpty()) {
                setFieldIfPresent(Item.class, item, "icon", iconPath);
            }

            if (texturePath != null && !texturePath.isEmpty()) {
                setFieldIfPresent(Item.class, item, "texture", texturePath);
            }

            // Use the dedicated generated-proxy quad model so icon/texture framing
            // does not inherit the sap model's presentation offsets.
            setFieldIfPresent(Item.class, item, "model", PROXY_MODEL_PATH);

            setFieldIfPresent(Item.class, item, "translationProperties",
                    new ItemTranslationProperties(displayName, "Generic crafting resource"));
            setFieldIfPresent(Item.class, item, "resourceTypes",
                    buildProxyResourceTypes(sourceResourceTypeId, genericTypeId));
        } catch (Exception e) {
            DebugLogger.log(SCALING, Level.WARNING,
                    "[GenericDropProxyAssetLoader] Failed to populate optional proxy fields for "
                            + proxyItemId + ": " + e.getMessage());
        }

        return item;
    }

    @Nonnull
    private ItemResourceType[] buildProxyResourceTypes(@Nonnull String sourceResourceTypeId,
                                                       @Nonnull String genericTypeId) {
        Set<String> ids = new LinkedHashSet<>();

        // Always include the specific authored type that triggered this proxy ensure.
        ids.add(sourceResourceTypeId);

        // Include every known subtype in the same generic family so this proxy can
        // satisfy recipes that require a specific ResourceTypeId variant.
        for (Item candidate : Item.getAssetMap().getAssetMap().values()) {
            if (candidate == null || candidate.getResourceTypes() == null) {
                continue;
            }
            for (ItemResourceType rt : candidate.getResourceTypes()) {
                String rtId = rt == null ? null : normalize(rt.id);
                if (rtId == null) {
                    continue;
                }
                if (genericTypeId.equals(proxyCatalog.toGenericTypeId(rtId))) {
                    ids.add(rtId);
                }
            }
        }

        // Keep generic id too for systems that key directly by top-level family.
        ids.add(genericTypeId);

        List<ItemResourceType> resourceTypes = new ArrayList<>(ids.size());
        for (String id : ids) {
            resourceTypes.add(new ItemResourceType(id, 1));
        }
        ItemResourceType[] result = new ItemResourceType[resourceTypes.size()];
        for (int i = 0; i < resourceTypes.size(); i++) {
            result[i] = resourceTypes.get(i);
        }
        return result;
    }

    @Nonnull
    private static Item cloneSapTemplate(@Nonnull String proxyItemId) {
        Item sapTemplate = Item.getAssetMap().getAsset(SAP_TEMPLATE_ITEM_ID);
        if (sapTemplate == null) {
            DebugLogger.log(SCALING, Level.WARNING,
                    "[GenericDropProxyAssetLoader] Sap template item is unavailable: " + SAP_TEMPLATE_ITEM_ID
                            + ". Falling back to direct item construction.");
            return new Item(proxyItemId);
        }

        Item clone = new Item(proxyItemId);
        copyAllFields(sapTemplate, clone);
        return clone;
    }

    @Nonnull
    private static String buildDisplayName(@Nonnull String resourceTypeId) {
        String normalized = resourceTypeId.trim();
        if (normalized.isEmpty()) {
            return "Generic Resource";
        }

        String pretty = normalized.replace('_', ' ');
        return "Generic " + pretty;
    }

    @Nullable
    private static String normalize(@Nullable String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    @Nonnull
    private static String normalizeRequired(@Nonnull String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException("resourceTypeId must not be blank");
        }
        return normalized;
    }

    private static void setFieldIfPresent(Class<?> type, Object target, String fieldName, Object value) {
        try {
            var field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException ignored) {
            // Runtime APIs can differ across versions; missing optional fields are tolerated.
        } catch (IllegalAccessException | IllegalArgumentException | SecurityException ex) {
            throw new RuntimeException("Unable to set field " + fieldName, ex);
        }
    }

    private static void copyAllFields(@Nonnull Object source, @Nonnull Object target) {
        Class<?> type = source.getClass();
        while (type != null) {
            for (var field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    field.set(target, field.get(source));
                } catch (IllegalAccessException | IllegalArgumentException | SecurityException ignored) {
                    // Best-effort copy: version-specific/runtime fields may be inaccessible.
                }
            }
            type = type.getSuperclass();
        }
    }
}
