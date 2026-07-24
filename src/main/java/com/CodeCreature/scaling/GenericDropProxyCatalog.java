package com.CodeCreature.scaling;

/**
 * @node    GenericDropProxyCatalog
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Deterministically maps ResourceTypeId values to proxy item IDs and icon paths for
 *          generic recipe break-drops without collapsing to concrete variants.
 * @wave    1 (proxy catalog)
 * @status  Wave 1 - implemented deterministic ID/icon mapping with reversible encoding
 * @do-not  Resolve runtime affordability or consumption semantics here.
 */

import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.CodeCreature.ui.bench.ResourceTypeRegistry;
import com.CodeCreature.ui.common.IconPathResolver;

public final class GenericDropProxyCatalog {

    private static final String PROXY_PREFIX = "Plugin_GenericDropProxy_RT_";
    private static final String RESOURCE_TYPE_ICON_ASSET_PREFIX = "Icons/ResourceTypes/";
    private static final String ITEM_ICON_ASSET_PREFIX = "Icons/ItemsGenerated/";
    private static final String ITEM_TEXTURE_ASSET_PREFIX = "Items/GeneratedProxyTextures/";

    /** @intent Build a stable proxy item ID for a ResourceTypeId using reversible hex encoding.
     *  @wave   1 - implemented
     *  @status implemented
     *  @node   GenericDropProxyCatalog#buildProxyItemId */
    @Nonnull
    public String buildProxyItemId(@Nonnull String resourceTypeId) {
        return PROXY_PREFIX + encodeHex(toGenericTypeId(resourceTypeId));
    }

    /** @intent Canonicalize variant resource IDs (e.g. Rock_Volcanic) to top-level generic IDs (e.g. Rock).
     *  @wave   4 - implemented
     *  @status implemented
     *  @node   GenericDropProxyCatalog#toGenericTypeId */
    @Nonnull
    public String toGenericTypeId(@Nonnull String resourceTypeId) {
        String normalized = normalizeResourceTypeId(resourceTypeId);
        int sep = normalized.indexOf('_');
        if (sep <= 0) {
            return normalized;
        }
        return normalized.substring(0, sep);
    }

    /** @intent Resolve a normalized generic icon path for a ResourceTypeId proxy.
     *  @wave   1 - implemented
     *  @status implemented
     *  @node   GenericDropProxyCatalog#resolveProxyIconPath */
    @Nullable
    public String resolveProxyIconPath(@Nonnull String resourceTypeId) {
        String normalized = toGenericTypeId(resourceTypeId);

        String resourceTypeIcon = resolveGenericResourceTypeIcon(normalized);
        if (resourceTypeIcon != null && !resourceTypeIcon.isEmpty()) {
            return adaptResourceTypeIconForItem(resourceTypeIcon);
        }

        var matches = ResourceTypeResolver.getAllMatchingItemIds(normalized);
        for (String itemId : matches) {
            var item = com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(itemId);
            if (item == null || item.getIcon() == null) continue;
            String normalizedItemIcon = toAssetIconPath(IconPathResolver.normalizeItemIcon(item.getIcon()));
            if (normalizedItemIcon != null && !normalizedItemIcon.isEmpty()) {
                return normalizedItemIcon;
            }
        }

        return null;
    }

    /** @intent Resolve an item-valid texture path for a ResourceTypeId proxy.
     *  @wave   5 - implemented mirrored texture path adaptation
     *  @status implemented
     *  @node   GenericDropProxyCatalog#resolveProxyTexturePath */
    @Nullable
    public String resolveProxyTexturePath(@Nonnull String resourceTypeId) {
        String iconPath = resolveProxyIconPath(resourceTypeId);
        if (iconPath == null || iconPath.isEmpty()) {
            return null;
        }
        return toTexturePath(iconPath);
    }

    @Nullable
    private static String resolveGenericResourceTypeIcon(@Nonnull String genericTypeId) {
        String iconToken = ResourceTypeRegistry.getIconPath(genericTypeId + "_Group");
        if (iconToken == null || iconToken.isBlank()) {
            iconToken = ResourceTypeRegistry.getIconPath(genericTypeId);
        }
        if (iconToken == null || iconToken.isBlank()) {
            return null;
        }
        return toAssetIconPath(IconPathResolver.normalizeResourceTypeIcon(iconToken));
    }

    @Nonnull
    private static String adaptResourceTypeIconForItem(@Nonnull String iconPath) {
        if (!iconPath.startsWith(RESOURCE_TYPE_ICON_ASSET_PREFIX)) {
            return iconPath;
        }
        return ITEM_ICON_ASSET_PREFIX + iconPath.substring(RESOURCE_TYPE_ICON_ASSET_PREFIX.length());
    }

    @Nullable
    private static String toTexturePath(@Nullable String iconPath) {
        if (iconPath == null || iconPath.isEmpty()) {
            return null;
        }
        if (iconPath.startsWith(ITEM_ICON_ASSET_PREFIX)) {
            return ITEM_TEXTURE_ASSET_PREFIX + iconPath.substring(ITEM_ICON_ASSET_PREFIX.length());
        }
        return null;
    }

    @Nullable
    private static String toAssetIconPath(@Nullable String iconPath) {
        if (iconPath == null) {
            return null;
        }
        String normalized = iconPath.replace('\\', '/').trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.startsWith("Common/")) {
            return normalized.substring("Common/".length());
        }
        return normalized;
    }

    /** @intent Check whether an item ID belongs to the generic proxy namespace.
     *  @wave   1 - implemented
     *  @status implemented
     *  @node   GenericDropProxyCatalog#isProxyItemId */
    public boolean isProxyItemId(@Nullable String itemId) {
        return itemId != null && itemId.startsWith(PROXY_PREFIX);
    }

    /** @intent Reverse a proxy item ID back to the authored ResourceTypeId.
     *  @wave   1 - implemented
     *  @status implemented
     *  @node   GenericDropProxyCatalog#extractResourceTypeId */
    @Nullable
    public String extractResourceTypeId(@Nullable String proxyItemId) {
        if (!isProxyItemId(proxyItemId)) {
            return null;
        }
        String encoded = proxyItemId.substring(PROXY_PREFIX.length());
        if (encoded.isEmpty()) {
            return null;
        }
        try {
            return decodeHex(encoded);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String normalizeResourceTypeId(@Nonnull String resourceTypeId) {
        String normalized = resourceTypeId.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("resourceTypeId must not be blank");
        }
        return normalized;
    }

    private static String encodeHex(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String decodeHex(String hex) {
        if ((hex.length() & 1) != 0) {
            throw new IllegalArgumentException("Hex length must be even");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Invalid hex encoding");
            }
            out[i / 2] = (byte) ((hi << 4) + lo);
        }
        return new String(out, StandardCharsets.UTF_8);
    }
}
