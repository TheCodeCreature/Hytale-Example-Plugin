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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.CodeCreature.ui.bench.ResourceTypeRegistry;
import com.CodeCreature.ui.common.IconPathResolver;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemCategory;

public final class GenericDropProxyCatalog {

    private static final String PROXY_PREFIX = "Plugin_GenericDropProxy_RT_";
    private static final String COMMON_UI_CUSTOM_PREFIX = "Common/UI/Custom/";
    private static final String RESOURCE_TYPE_ICON_ASSET_PREFIX = "Icons/ResourceTypes/";
    private static final String GROUP_ICON_ASSET_PREFIX = "GroupIcons/";
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
        String trunkFamily = canonicalizeTrunkFamily(normalized);
        if (trunkFamily != null) {
            return trunkFamily;
        }
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

        String semanticIcon = resolveSemanticIconPath(normalized);
        if (semanticIcon != null && !semanticIcon.isEmpty()) {
            return toProxyItemIconPath(semanticIcon);
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

    @Nonnull
    private static String toProxyItemIconPath(@Nonnull String iconPath) {
        String normalized = iconPath.replace('\\', '/').trim();

        // Proxy items should use generated item-icon family so their item-scale
        // icon and model texture paths stay aligned.
        if (normalized.startsWith(RESOURCE_TYPE_ICON_ASSET_PREFIX)) {
            return ITEM_ICON_ASSET_PREFIX + normalized.substring(RESOURCE_TYPE_ICON_ASSET_PREFIX.length());
        }
        if (normalized.startsWith(GROUP_ICON_ASSET_PREFIX)) {
            return ITEM_ICON_ASSET_PREFIX + normalized.substring(GROUP_ICON_ASSET_PREFIX.length());
        }
        return normalized;
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
    private static String resolveSemanticIconPath(@Nonnull String resourceTypeId) {
        List<String> queryTokens = splitTokens(resourceTypeId);
        if (queryTokens.isEmpty()) {
            return null;
        }

        String exactStem = String.join("_", queryTokens);
        String lastToken = queryTokens.get(queryTokens.size() - 1);

        LinkedHashSet<String> preferredStems = new LinkedHashSet<>();
        preferredStems.add("Any_" + exactStem);
        preferredStems.add("Any_" + lastToken);
        preferredStems.add(exactStem);
        preferredStems.add(lastToken);

        List<IconCandidate> candidates = new ArrayList<>();
        Map<String, String> resourceTypeIcons = collectResourceTypeIcons();
        Map<String, String> groupIcons = collectGroupIcons();

        for (String stem : preferredStems) {
            String resourceTypeAssetPath = RESOURCE_TYPE_ICON_ASSET_PREFIX + stem + ".png";
            if (resourceExistsForAssetPath(resourceTypeAssetPath)) {
                candidates.add(new IconCandidate(resourceTypeAssetPath, splitTokens(stem), FolderRank.RESOURCE_TYPES));
            }

            String groupAssetPath = GROUP_ICON_ASSET_PREFIX + stem + ".png";
            if (resourceExistsForAssetPath(groupAssetPath)) {
                candidates.add(new IconCandidate(groupAssetPath, splitTokens(stem), FolderRank.GROUP_ICONS));
            }
        }

        addKnownCandidates(candidates, resourceTypeIcons, FolderRank.RESOURCE_TYPES);
        addKnownCandidates(candidates, groupIcons, FolderRank.GROUP_ICONS);

        return candidates.stream()
                .map(candidate -> ScoredIconCandidate.score(candidate, queryTokens, exactStem, lastToken))
                .filter(ScoredIconCandidate::matched)
                .sorted(Comparator
                        .comparingInt(ScoredIconCandidate::priority)
                        .thenComparingInt(ScoredIconCandidate::folderRank)
                        .thenComparingInt(ScoredIconCandidate::tokenCount)
                        .thenComparing(ScoredIconCandidate::assetPath))
                .map(ScoredIconCandidate::assetPath)
                .findFirst()
                .orElse(null);
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

    private static void addKnownCandidates(@Nonnull List<IconCandidate> candidates,
                                           @Nonnull Map<String, String> assetPathsByStem,
                                           @Nonnull FolderRank folderRank) {
        for (Map.Entry<String, String> entry : assetPathsByStem.entrySet()) {
            String assetPath = entry.getValue();
            if (!resourceExistsForAssetPath(assetPath)) {
                continue;
            }
            candidates.add(new IconCandidate(assetPath, splitTokens(entry.getKey()), folderRank));
        }
    }

    @Nonnull
    private static Map<String, String> collectResourceTypeIcons() {
        Map<String, String> icons = new LinkedHashMap<>();
        for (ResourceTypeRegistry.ResourceTypeEntry entry : ResourceTypeRegistry.getAll()) {
            registerIconStem(icons, IconPathResolver.normalizeResourceTypeIcon(entry.iconFilename()));
        }
        return icons;
    }

    @Nonnull
    private static Map<String, String> collectGroupIcons() {
        Map<String, String> icons = new LinkedHashMap<>();
        try {
            if (ItemCategory.getAssetStore() == null || ItemCategory.getAssetMap() == null) {
                return icons;
            }
            for (ItemCategory category : ItemCategory.getAssetMap().getAssetMap().values()) {
                if (category == null || category.getIcon() == null) {
                    continue;
                }
                registerIconStem(icons, IconPathResolver.normalizeCategoryIcon(category.getIcon()));
            }
        } catch (Exception ignored) {
            // Some runtime/test boot paths do not initialize ItemCategory assets.
            // Fail-open so resource-type and item-based icon resolution can continue.
        }
        return icons;
    }

    private static void registerIconStem(@Nonnull Map<String, String> icons, @Nullable String normalizedPath) {
        String assetPath = toAssetIconPath(normalizedPath);
        if (assetPath == null || assetPath.isEmpty()) {
            return;
        }

        String stem = stemFromAssetPath(assetPath);
        if (stem == null || stem.isEmpty()) {
            return;
        }
        icons.putIfAbsent(stem, assetPath);
    }

    @Nullable
    private static String stemFromAssetPath(@Nullable String assetPath) {
        if (assetPath == null || assetPath.isEmpty()) {
            return null;
        }

        int slash = assetPath.lastIndexOf('/');
        String filename = slash >= 0 ? assetPath.substring(slash + 1) : assetPath;
        if (!filename.endsWith(".png")) {
            return null;
        }
        return filename.substring(0, filename.length() - 4);
    }

    @Nonnull
    private static List<String> splitTokens(@Nonnull String raw) {
        String[] parts = raw.split("[_-]+");
        List<String> tokens = new ArrayList<>(parts.length);
        for (String part : parts) {
            String normalized = part == null ? "" : part.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                tokens.add(normalized);
            }
        }
        return List.copyOf(tokens);
    }

    private static boolean resourceExistsForAssetPath(@Nonnull String assetPath) {
        return GenericDropProxyCatalog.class.getClassLoader()
                .getResource(COMMON_UI_CUSTOM_PREFIX + "Common/" + assetPath) != null;
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

    @Nullable
    private static String canonicalizeTrunkFamily(@Nonnull String resourceTypeId) {
        // Keep trunk semantics stable: Wood_Oak_Trunk/Birch/... should resolve to Wood_Trunk,
        // not collapse to generic Wood.
        if (resourceTypeId.startsWith("Wood_") && resourceTypeId.endsWith("_Trunk")) {
            return "Wood_Trunk";
        }
        return null;
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

    private record IconCandidate(@Nonnull String assetPath,
                                 @Nonnull List<String> tokens,
                                 @Nonnull FolderRank folderRank) {}

    private record ScoredIconCandidate(@Nonnull String assetPath,
                                       int priority,
                                       int folderRank,
                                       int tokenCount,
                                       boolean matched) {
        @Nonnull
        private static ScoredIconCandidate score(@Nonnull IconCandidate candidate,
                                                 @Nonnull List<String> queryTokens,
                                                 @Nonnull String exactStem,
                                                 @Nonnull String lastToken) {
            List<String> candidateTokens = candidate.tokens();
            String candidateStem = String.join("_", candidateTokens);
            String candidateLastToken = candidateTokens.isEmpty() ? "" : candidateTokens.get(candidateTokens.size() - 1);
            boolean anyPrefix = !candidateTokens.isEmpty() && "any".equals(candidateTokens.get(0));
            List<String> payloadTokens = anyPrefix ? candidateTokens.subList(1, candidateTokens.size()) : candidateTokens;
            String payloadStem = String.join("_", payloadTokens);

            int priority = Integer.MAX_VALUE;
            boolean matched = false;
            if (anyPrefix && payloadStem.equals(exactStem)) {
                priority = 1;
                matched = true;
            } else if (anyPrefix && payloadTokens.size() == 1 && lastToken.equals(payloadTokens.get(0))) {
                priority = 2;
                matched = true;
            } else if (candidateStem.equals(exactStem)) {
                priority = 3;
                matched = true;
            } else if (candidateTokens.size() == 1 && candidateStem.equals(lastToken)) {
                priority = 4;
                matched = true;
            } else if (isSimpleTokenFallback(candidateTokens, queryTokens)) {
                priority = 5;
                matched = true;
            }

            return new ScoredIconCandidate(
                    candidate.assetPath(),
                    priority,
                    candidate.folderRank().order,
                    candidateTokens.size(),
                    matched);
        }
    }

    private static boolean isSimpleTokenFallback(@Nonnull List<String> candidateTokens,
                                                 @Nonnull List<String> queryTokens) {
        if (candidateTokens.isEmpty()) {
            return false;
        }

        if (candidateTokens.size() == 1) {
            return queryTokens.contains(candidateTokens.get(0));
        }

        return candidateTokens.size() == 2
                && "any".equals(candidateTokens.get(0))
                && queryTokens.contains(candidateTokens.get(1));
    }

    private enum FolderRank {
        RESOURCE_TYPES(0),
        GROUP_ICONS(1);

        private final int order;

        FolderRank(int order) {
            this.order = order;
        }
    }
}
