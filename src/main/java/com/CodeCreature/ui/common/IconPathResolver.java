package com.CodeCreature.ui.common;

import com.CodeCreature.ui.bench.ResourceTypeRegistry;
import com.hypixel.hytale.server.core.asset.type.item.config.ResourceType;

import javax.annotation.Nullable;

/**
 * Single source of truth for UI icon path normalization and resource-type icon resolution.
 *
 * <p>All public methods are pure functions: they accept a raw path or resource type ID and
 * return either a fully-qualified UI-relative path (rooted under
 * {@code Common/UI/Custom/}) or {@code null} when resolution or normalization is impossible.
 *
 * <p>This class owns the normalization rules for three icon families:
 * <ul>
 *   <li><b>Item icons</b> — resolved from {@code Common/Icons/ItemsGenerated/}</li>
 *   <li><b>Resource-type icons</b> — resolved from {@code Common/Icons/ResourceTypes/}</li>
 *   <li><b>Category icons</b> — resolved from {@code Common/GroupIcons/}</li>
 * </ul>
 *
 * <p>Callers must decide what to do when {@code null} is returned (suppress rendering,
 * choose a fallback, or surface telemetry). This class never fabricates a path.
 *
 * <p>This class is stateless and thread-safe.
 */
public final class IconPathResolver {

    private static final String ICONS_ROOT           = "Common/Icons/";
    private static final String ITEMS_GENERATED_PATH = "Common/Icons/ItemsGenerated/";
    private static final String RESOURCE_TYPES_PATH  = "Common/Icons/ResourceTypes/";
    private static final String GROUP_ICONS_PATH     = "Common/GroupIcons/";

    private IconPathResolver() {}

    // ─── Public API ─────────────────────────────────────────────

    /**
     * Normalizes a raw item icon path toward {@code Common/Icons/ItemsGenerated/}.
     *
     * <p>Accepted input forms:
     * <ul>
     *   <li>{@code Common/Icons/ItemsGenerated/Bench_X.png} — preserved as-is</li>
     *   <li>{@code Icons/ItemsGenerated/Bench_X.png} → {@code Common/Icons/ItemsGenerated/Bench_X.png}</li>
     *   <li>{@code ItemsGenerated/Bench_X.png} → {@code Common/Icons/ItemsGenerated/Bench_X.png}</li>
    *   <li>{@code Any_Item.png} → {@code Common/Icons/ItemsGenerated/Any_Item.png}</li>
     *   <li>Any other path with {@code /} → {@code Common/Icons/<path>}</li>
     * </ul>
     *
     * <p>Returns {@code null} for {@code null}, empty, path-traversal, or bare filenames
    * values.
     *
     * @param raw raw icon value from an asset or config entry
     * @return normalized UI-relative path, or {@code null} if not resolvable
     */
    @Nullable
    public static String normalizeItemIcon(@Nullable String raw) {
        String n = sanitize(raw);
        if (n == null) return null;

        if (n.startsWith("Common/Icons/")) return n;
        if (n.startsWith("Icons/"))         return "Common/" + n;
        if (n.startsWith("ItemsGenerated/")) return ICONS_ROOT + n;
        if (n.contains("/"))                 return ICONS_ROOT + n;
        // Bare filename — treat as an ItemsGenerated icon filename.
        return ITEMS_GENERATED_PATH + n;
    }

    /**
     * Normalizes a raw resource-type icon path toward {@code Common/Icons/ResourceTypes/}.
     *
     * <p>Accepted input forms:
     * <ul>
     *   <li>{@code Common/Icons/ResourceTypes/Hardwood.png} — preserved</li>
     *   <li>{@code Icons/ResourceTypes/Hardwood.png} → {@code Common/Icons/ResourceTypes/Hardwood.png}</li>
     *   <li>{@code ResourceTypes/Hardwood.png} → {@code Common/Icons/ResourceTypes/Hardwood.png}</li>
     *   <li>{@code Hardwood.png} → {@code Common/Icons/ResourceTypes/Hardwood.png}</li>
     *   <li>Any other path with {@code /} → {@code Common/Icons/<path>}</li>
     * </ul>
     *
     * <p>Returns {@code null} for {@code null}, empty, or path-traversal values.
     *
     * @param raw raw icon value from a registry entry or asset
     * @return normalized UI-relative path, or {@code null} if not resolvable
     */
    @Nullable
    public static String normalizeResourceTypeIcon(@Nullable String raw) {
        String n = sanitize(raw);
        if (n == null) return null;

        if (n.startsWith("Common/Icons/")) return n;
        if (n.startsWith("Icons/"))         return "Common/" + n;
        if (n.startsWith("ResourceTypes/")) return ICONS_ROOT + n;
        if (n.contains("/"))                return ICONS_ROOT + n;
        // Bare filename — treat as a ResourceTypes icon filename
        return RESOURCE_TYPES_PATH + n;
    }

    /**
     * Normalizes a raw category icon path toward {@code Common/GroupIcons/}.
     *
     * <p>Extracts the last path segment from engine-provided values (which may include
     * directory components) and prepends the group-icon base path. Rejects blank values
     * and path-traversal attempts.
     *
     * @param raw raw icon value from an {@code ItemCategory} asset
     * @return normalized UI-relative path, or {@code null} if not resolvable
     */
    @Nullable
    public static String normalizeCategoryIcon(@Nullable String raw) {
        String n = sanitize(raw);
        if (n == null) return null;

        // Take only the last segment (engine values may be directory-relative)
        int lastSlash = n.lastIndexOf('/');
        String filename = lastSlash >= 0 ? n.substring(lastSlash + 1) : n;
        if (filename.isEmpty()) return null;

        return GROUP_ICONS_PATH + filename;
    }

    /**
     * Resolves the UI icon path for a resource type by ID, checking the plugin registry
     * first and then the engine asset map.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>{@link ResourceTypeRegistry#getIconPath(String)} (plugin registry)</li>
     *   <li>{@link ResourceType#getAssetMap()} icon field (engine asset)</li>
     * </ol>
     *
     * <p>Both candidates are normalized via {@link #normalizeResourceTypeIcon(String)}.
     * Returns {@code null} if neither source provides a usable icon — callers must decide
     * whether to suppress rendering or log a telemetry event.
     *
     * @param resourceTypeId the engine resource type ID (e.g. {@code "Hardwood"})
     * @return fully-qualified UI-relative icon path, or {@code null} if unresolvable
     */
    @Nullable
    public static String resolveResourceTypeIcon(@Nullable String resourceTypeId) {
        if (resourceTypeId == null || resourceTypeId.isEmpty()) return null;

        // 1. Plugin registry (highest priority)
        String registryIcon = ResourceTypeRegistry.getIconPath(resourceTypeId);
        if (registryIcon != null) {
            return normalizeResourceTypeIcon(registryIcon);
        }

        // 2. Engine asset map
        ResourceType rtAsset = ResourceType.getAssetMap().getAsset(resourceTypeId);
        if (rtAsset != null && rtAsset.getIcon() != null) {
            return normalizeResourceTypeIcon(rtAsset.getIcon());
        }

        return null;
    }

    // ─── Internal helpers ────────────────────────────────────────

    /**
     * Normalizes separators, trims whitespace, and rejects unsafe values.
     *
     * @return sanitized forward-slash path, or {@code null} if invalid
     */
    @Nullable
    private static String sanitize(@Nullable String raw) {
        if (raw == null) return null;
        String n = raw.replace('\\', '/').trim();
        if (n.isEmpty() || n.contains("..")) return null;
        return n;
    }
}
