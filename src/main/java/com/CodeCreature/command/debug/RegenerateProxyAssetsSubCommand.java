package com.CodeCreature.command.debug;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringJoiner;

import org.bson.BsonValue;
import org.jspecify.annotations.NonNull;

import com.CodeCreature.registry.BenchRecipeRegistries;
import com.CodeCreature.registry.BenchRecipeRegistry;
import com.CodeCreature.registry.BenchRegistry;
import com.CodeCreature.scaling.AssetFieldAccessor;
import com.CodeCreature.scaling.GenericDropProxyAssetLoader;
import com.CodeCreature.scaling.GenericDropProxyCatalog;
import com.CodeCreature.scaling.ResourceTypeResolver;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Rebuilds generic proxy item assets from current recipe inputs and dumps each generated
 * proxy Item asset JSON to disk for direct inspection.
 *
 * <p>Usage:</p>
 * <ul>
 *   <li>{@code /debug regenproxies}</li>
 *   <li>{@code /debug regenproxies all}</li>
 * </ul>
 */
public class RegenerateProxyAssetsSubCommand extends AbstractPlayerCommand {

    private final OptionalArg<String> modeArg;

    public RegenerateProxyAssetsSubCommand() {
        super("regenproxies", "Recreate generic proxy assets and dump JSON files");
        this.modeArg = withOptionalArg("mode", "Optional: 'all' to dump all proxies currently in asset map", ArgTypes.STRING);
    }

    @Override
    protected void execute(@NonNull CommandContext context,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        boolean dumpAll = modeArg.provided(context) && "all".equalsIgnoreCase(modeArg.get(context));
        runRegeneration(playerRef, dumpAll);
    }

    static void runRegeneration(@NonNull PlayerRef playerRef, boolean dumpAll) {
        Path dataDir = BenchRegistry.getDataDirectory();
        if (dataDir == null) {
            playerRef.sendMessage(Message.raw("§c[Debug] BenchRegistry data directory is unavailable; plugin may not be initialized."));
            return;
        }

        // Ensure resolver/index state reflects current loaded assets.
        ResourceTypeResolver.initialize();

        GenericDropProxyCatalog catalog = new GenericDropProxyCatalog();
        GenericDropProxyAssetLoader loader = new GenericDropProxyAssetLoader(catalog, AssetFieldAccessor.INSTANCE);

        Set<String> ensuredProxyIds = new LinkedHashSet<>();
        int recipesScanned = 0;

        for (BenchRecipeRegistry reg : BenchRecipeRegistries.getAllRegistries()) {
            for (CraftingRecipe recipe : reg.getAllRecipesById().values()) {
                if (recipe == null) {
                    continue;
                }
                recipesScanned++;
                if (recipe.getInput() == null) {
                    continue;
                }
                for (var input : recipe.getInput()) {
                    if (input == null || input.getResourceTypeId() == null || input.getResourceTypeId().isEmpty()) {
                        continue;
                    }
                    String proxyId = loader.ensureProxyAsset(input.getResourceTypeId());
                    ensuredProxyIds.add(proxyId);
                }
            }
        }

        Set<String> idsToDump = new LinkedHashSet<>();
        if (dumpAll) {
            for (String itemId : Item.getAssetMap().getAssetMap().keySet()) {
                if (catalog.isProxyItemId(itemId)) {
                    idsToDump.add(itemId);
                }
            }
        } else {
            idsToDump.addAll(ensuredProxyIds);
        }

        Path itemAssetDir = dataDir
            .resolve("Server")
            .resolve("Item")
            .resolve("Items")
            .resolve("Plugin")
            .resolve("GenericDropProxy");
        int dumped = 0;
        int missing = 0;
        int overflowFallback = 0;
        Set<String> usedFileNames = new LinkedHashSet<>();

        try {
            Files.createDirectories(itemAssetDir);

            for (String proxyId : idsToDump) {
                Item item = Item.getAssetMap().getAsset(proxyId);
                if (item == null) {
                    missing++;
                    continue;
                }

                String json;
                try {
                    BsonValue encoded = Item.CODEC.encode(item, new ExtraInfo());
                    json = encoded.toString();
                } catch (StackOverflowError overflow) {
                    overflowFallback++;
                    json = buildShallowProxyDebugJson(item, proxyId,
                            "Item.CODEC.encode caused StackOverflowError; wrote shallow debug JSON fallback");
                }

                // Canonical asset location so startup asset scanning auto-discovers proxy items.
                String readableFileName = buildReadableFileName(item, proxyId, catalog, usedFileNames);
                Path assetOut = itemAssetDir.resolve(readableFileName);
                Files.writeString(assetOut, json, StandardCharsets.UTF_8);
                dumped++;
            }
        } catch (Exception ex) {
            playerRef.sendMessage(Message.raw("§c[Debug] Failed to dump proxy assets: " + ex.getClass().getSimpleName() + ": " + ex.getMessage()));
            return;
        }

        playerRef.sendMessage(Message.raw("§a[Debug] Proxy regen complete. recipesScanned=" + recipesScanned
                + ", ensured=" + ensuredProxyIds.size()
                + ", dumped=" + dumped
                + ", missing=" + missing
                + ", overflowFallback=" + overflowFallback));
        if (overflowFallback > 0) {
            playerRef.sendMessage(Message.raw("§6[Debug] " + overflowFallback
                + " proxy dump(s) used shallow fallback JSON due to StackOverflowError in Item.CODEC.encode."));
        }
        playerRef.sendMessage(Message.raw("§7[Debug] Item asset directory: " + itemAssetDir));
        if (!dumpAll) {
            playerRef.sendMessage(Message.raw("§7[Debug] Tip: use /debug regenproxiesall (or /debug regenproxiesa) to dump every proxy currently in the runtime item asset map."));
        }
    }

    @NonNull
    private static String buildShallowProxyDebugJson(@NonNull Item item,
                                                     @NonNull String proxyId,
                                                     @NonNull String warning) {
        StringJoiner out = new StringJoiner(",\n", "{\n", "\n}\n");

        out.add("  \"id\": " + quote(proxyId));
        out.add("  \"icon\": " + quoteOrNull(readStringField(item, "icon")));
        out.add("  \"set\": " + quoteOrNull(readStringField(item, "set")));
        out.add("  \"blockId\": " + quoteOrNull(readStringField(item, "blockId")));
        out.add("  \"hasBlockType\": " + bool(readBooleanField(item, "hasBlockType")));
        out.add("  \"maxStack\": " + intNum(readIntField(item, "maxStack")));
        out.add("  \"categories\": " + stringArray(readStringArrayField(item, "categories")));
        out.add("  \"resourceTypeIds\": " + stringArray(readResourceTypeIds(item)));

        Object translationProps = readField(item, "translationProperties");
        out.add("  \"translationName\": " + quoteOrNull(readStringField(translationProps, "name")));
        out.add("  \"translationDescription\": " + quoteOrNull(readStringField(translationProps, "description")));

        out.add("  \"dumpWarning\": " + quote(warning));
        return out.toString();
    }

    private static String[] readResourceTypeIds(@NonNull Item item) {
        Object value = readField(item, "resourceTypes");
        if (!(value instanceof Object[] arr) || arr.length == 0) {
            return new String[0];
        }
        String[] out = new String[arr.length];
        for (int i = 0; i < arr.length; i++) {
            String id = readStringField(arr[i], "id");
            out[i] = id == null ? "" : id;
        }
        return out;
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) return null;
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                var f = type.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static String readStringField(Object target, String fieldName) {
        Object value = readField(target, fieldName);
        return value instanceof String s ? s : null;
    }

    private static Boolean readBooleanField(Object target, String fieldName) {
        Object value = readField(target, fieldName);
        return value instanceof Boolean b ? b : null;
    }

    private static Integer readIntField(Object target, String fieldName) {
        Object value = readField(target, fieldName);
        return value instanceof Integer i ? i : null;
    }

    private static String[] readStringArrayField(Object target, String fieldName) {
        Object value = readField(target, fieldName);
        if (!(value instanceof String[] arr)) {
            return new String[0];
        }
        return arr;
    }

    private static String quoteOrNull(String value) {
        return value == null ? "null" : quote(value);
    }

    private static String quote(String value) {
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    private static String bool(Boolean value) {
        return value == null ? "null" : Boolean.toString(value);
    }

    private static String intNum(Integer value) {
        return value == null ? "null" : Integer.toString(value);
    }

    private static String stringArray(String[] values) {
        if (values == null || values.length == 0) {
            return "[]";
        }
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (String value : values) {
            joiner.add(quote(value == null ? "" : value));
        }
        return joiner.toString();
    }

    @NonNull
    private static String buildReadableFileName(@NonNull Item item,
                                                @NonNull String proxyId,
                                                @NonNull GenericDropProxyCatalog catalog,
                                                @NonNull Set<String> usedFileNames) {
        String resourceTypeId = catalog.extractResourceTypeId(proxyId);
        String displayName = readStringField(readField(item, "translationProperties"), "name");

        String preferred = displayName;
        if (preferred == null || preferred.isBlank() || preferred.contains(".")) {
            preferred = resourceTypeId == null || resourceTypeId.isBlank()
                    ? proxyId
                    : "Generic_" + resourceTypeId;
        }

        String base = sanitizeFileToken(preferred);
        if (base.isBlank()) {
            base = sanitizeFileToken(proxyId);
        }

        String candidate = base + ".json";
        if (usedFileNames.add(candidate)) {
            return candidate;
        }

        String disambiguated = base + "__" + sanitizeFileToken(proxyId) + ".json";
        usedFileNames.add(disambiguated);
        return disambiguated;
    }

    @NonNull
    private static String sanitizeFileToken(@NonNull String raw) {
        String normalized = raw.trim().replace(' ', '_');
        normalized = normalized.replaceAll("[^A-Za-z0-9_-]", "_");
        normalized = normalized.replaceAll("_+", "_");
        while (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
