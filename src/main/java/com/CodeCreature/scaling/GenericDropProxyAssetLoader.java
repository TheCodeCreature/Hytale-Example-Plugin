package com.CodeCreature.scaling;

/**
 * @node    GenericDropProxyAssetLoader
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Ensures proxy item assets exist for generic recipe inputs before synthetic recipe
 *          drop-list generation.
 * @wave    1 (asset ensure)
 * @status  Wave 1 - implemented with deterministic fallback when runtime item registration fails
 * @do-not  Mutate natural block scaling or placement cost logic.
 */

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.CodeCreature.util.DebugLogger;
import static com.CodeCreature.util.DebugLogger.Subsystem.SCALING;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemEntityConfig;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemStackContainerConfig;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTranslationProperties;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemUtility;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.InteractionConfiguration;

public final class GenericDropProxyAssetLoader {

    private static final String[] DEFAULT_PROXY_CATEGORIES = new String[]{"Plugin", "Plugin.GenericDropProxy"};

    private final GenericDropProxyCatalog proxyCatalog;
    private final AssetFieldAccessor fields;

    public GenericDropProxyAssetLoader(@Nonnull GenericDropProxyCatalog proxyCatalog,
                                       @Nonnull AssetFieldAccessor fields) {
        this.proxyCatalog = proxyCatalog;
        this.fields = fields;
    }

    /** @intent Ensure proxy assets for every generic input in the provided recipe.
     *  @wave   1 - implemented
     *  @status implemented
     *  @node   GenericDropProxyAssetLoader#ensureProxyAssetsForRecipe */
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

    /** @intent Ensure one proxy asset exists for the resource type and return the proxy item ID.
     *  @wave   1 - implemented
     *  @status implemented
     *  @node   GenericDropProxyAssetLoader#ensureProxyAsset */
    @Nonnull
    public String ensureProxyAsset(@Nonnull String resourceTypeId) {
        String normalizedResourceTypeId = normalizeRequired(resourceTypeId);
        String proxyItemId = proxyCatalog.buildProxyItemId(normalizedResourceTypeId);

        if (Item.getAssetMap().getAsset(proxyItemId) != null) {
            return proxyItemId;
        }

        Item proxyItem = buildProxyItem(normalizedResourceTypeId, proxyItemId);
        List<Item> toLoad = new ArrayList<>(1);
        toLoad.add(proxyItem);

        try {
            Item.getAssetStore().loadAssets("Plugin:GenericDropProxies", toLoad);
        } catch (Exception loadFailure) {
            DebugLogger.log(SCALING, Level.WARNING,
                    "[GenericDropProxyAssetLoader] Unable to register proxy asset via loadAssets for "
                            + normalizedResourceTypeId + " (" + proxyItemId + "): "
                            + loadFailure.getClass().getSimpleName() + ": " + loadFailure.getMessage());
        }

        if (Item.getAssetMap().getAsset(proxyItemId) == null) {
            DebugLogger.log(SCALING, Level.WARNING,
                    "[GenericDropProxyAssetLoader] Proxy asset still unavailable after ensure; "
                            + "drop projection will continue with deterministic proxy ID only: " + proxyItemId);
        }

        return proxyItemId;
    }

    /** @intent Build a runtime proxy item with generic icon metadata and non-placeable defaults.
     *  @wave   1 - implemented
     *  @status implemented
     *  @node   GenericDropProxyAssetLoader#buildProxyItem */
    @Nonnull
    public Item buildProxyItem(@Nonnull String resourceTypeId, @Nonnull String proxyItemId) {
        String iconPath = proxyCatalog.resolveProxyIconPath(resourceTypeId);
        String displayName = buildDisplayName(resourceTypeId);
        Item item = buildRepresentativeBackedItem(resourceTypeId, proxyItemId);

        if (item == null) {
            item = new Item(proxyItemId);
            setRequiredDefaults(item);
        }

        try {
            setFieldIfPresent(Item.class, item, "id", proxyItemId);
            fields.itemSet.set(item, proxyItemId);
            fields.itemMaxStack.setInt(item, 1200);

            if (iconPath != null && !iconPath.isEmpty()) {
                setFieldIfPresent(Item.class, item, "icon", iconPath);
            }

            setFieldIfPresent(Item.class, item, "translationProperties",
                    new ItemTranslationProperties(displayName, "Generic crafting resource"));
            setFieldIfPresent(Item.class, item, "name", displayName);
            setFieldIfPresent(Item.class, item, "displayName", displayName);
            setFieldIfPresent(Item.class, item, "categories", DEFAULT_PROXY_CATEGORIES);
            setFieldIfPresent(Item.class, item, "hasBlockType", false);
            setFieldIfPresent(Item.class, item, "blockId", null);
            setFieldIfPresent(Item.class, item, "resourceTypes",
                    new ItemResourceType[]{new ItemResourceType(resourceTypeId, 1)});
        } catch (Exception e) {
            DebugLogger.log(SCALING, Level.WARNING,
                    "[GenericDropProxyAssetLoader] Failed to populate optional proxy fields for "
                            + proxyItemId + ": " + e.getMessage());
        }

        return item;
    }

    @Nullable
    private static Item buildRepresentativeBackedItem(@Nonnull String resourceTypeId,
                                                      @Nonnull String proxyItemId) {
        Item fallbackRepresentative = null;

        for (String itemId : ResourceTypeResolver.getAllMatchingItemIds(resourceTypeId)) {
            Item representative = Item.getAssetMap().getAsset(itemId);
            if (representative == null) {
                continue;
            }

            if (fallbackRepresentative == null) {
                fallbackRepresentative = representative;
            }

            if (representative.getBlockId() != null) {
                continue;
            }

            Item proxy = new Item(representative);
            setFieldIfPresent(Item.class, proxy, "id", proxyItemId);
            return proxy;
        }

        if (fallbackRepresentative != null) {
            Item proxy = new Item(fallbackRepresentative);
            setFieldIfPresent(Item.class, proxy, "id", proxyItemId);
            return proxy;
        }

        return null;
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

    private static void setRequiredDefaults(@Nonnull Item item) {
        setFieldIfPresent(Item.class, item, "interactionConfig", InteractionConfiguration.DEFAULT);
        setFieldIfPresent(Item.class, item, "interactions", Map.of());
        setFieldIfPresent(Item.class, item, "interactionVars", Map.of());
        setFieldIfPresent(Item.class, item, "itemEntityConfig", ItemEntityConfig.DEFAULT);
        setFieldIfPresent(Item.class, item, "utility", ItemUtility.DEFAULT);
        setFieldIfPresent(Item.class, item, "itemStackContainerConfig", ItemStackContainerConfig.DEFAULT);
        setFieldIfPresent(Item.class, item, "playerAnimationsId", "Default");
        setFieldIfPresent(Item.class, item, "usePlayerAnimations", false);
        setFieldIfPresent(Item.class, item, "texture", "Items/Unknown.png");
        setFieldIfPresent(Item.class, item, "interactions", new EnumMap<InteractionType, String>(InteractionType.class));
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
        } catch (Exception ex) {
            throw new RuntimeException("Unable to set field " + fieldName, ex);
        }
    }
}
