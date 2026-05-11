package com.CodeCreature.resourcecollection;

import com.CodeCreature.resourcecollection.BenchRecipeRegistries;
import com.CodeCreature.resourcecollection.BenchRecipeRegistry;
import com.CodeCreature.resourcecollection.NaturalResourceRegistry;
import com.hypixel.hytale.assetstore.AssetMap;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.event.IEventBus;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.PhysicsDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.BlockGroup;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.asset.type.item.config.container.SingleItemDropContainer;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for setting up fake Hytale asset infrastructure in unit tests.
 * Uses reflection to:
 * - Create instances of Hytale classes with protected constructors
 * - Install fake AssetStore instances on static fields
 * - Set and read protected fields on Hytale objects
 * - Pre-populate NaturalResourceRegistry and BlockRecipeRegistry state
 */
public final class AssetTestHelper {

    private AssetTestHelper() {}

    // ──────────────────────────────────────────────────────────────
    //  Object creation
    // ──────────────────────────────────────────────────────────────

    /** Creates a BlockType with the given id and optional gathering config. */
    public static BlockType blockType(String id, BlockGathering gathering) {
        BlockType bt = new BlockType(id);
        if (gathering != null) {
            setField(BlockType.class, bt, "gathering", gathering);
        }
        return bt;
    }

    /** Creates an Item via reflection (protected constructor). */
    public static Item item(String id, String blockId, boolean hasBlockType, int maxStack) {
        Item it = construct(Item.class);
        setField(Item.class, it, "id", id);
        setField(Item.class, it, "blockId", blockId);
        setField(Item.class, it, "hasBlockType", hasBlockType);
        setField(Item.class, it, "maxStack", maxStack);
        return it;
    }

    /**
     * Creates an Item with {@link ItemResourceType} declarations.
     * Models the real game's {@code "ResourceTypes"} JSON array on items,
     * which is how the engine resolves {@code ResourceTypeId}-based recipe inputs.
     *
     * @param resourceTypes the resource types this item satisfies
     *                      (e.g. {@code "Wood_All"}, {@code "Rock"}, {@code "Fuel"})
     */
    public static Item item(String id, String blockId, boolean hasBlockType,
                            int maxStack, ItemResourceType... resourceTypes) {
        Item it = item(id, blockId, hasBlockType, maxStack);
        if (resourceTypes != null && resourceTypes.length > 0) {
            setField(Item.class, it, "resourceTypes", resourceTypes);
        }
        return it;
    }

    /**
     * Creates an Item with a {@code Set} value and {@link ItemResourceType}
     * declarations. Models the real game's item family grouping where the
     * base item's {@code Set} equals its own ID, and derivatives point their
     * {@code Set} to the base item's ID.
     *
     * @param setId         the item's Set value (e.g. the base item's ID)
     * @param resourceTypes the resource types this item satisfies
     */
    public static Item item(String id, String blockId, boolean hasBlockType,
                            int maxStack, String setId,
                            ItemResourceType... resourceTypes) {
        Item it = item(id, blockId, hasBlockType, maxStack, resourceTypes);
        if (setId != null) {
            setField(Item.class, it, "set", setId);
        }
        return it;
    }

    /**
     * Helper to create an {@link ItemResourceType} with the given ID and
     * quantity 1 (the common case for resource type declarations).
     */
    public static ItemResourceType resourceType(String id) {
        return new ItemResourceType(id, 1);
    }

    /** Creates a CraftingRecipe via reflection. */
    public static CraftingRecipe recipe(String id, MaterialQuantity[] inputs,
                                        MaterialQuantity primaryOutput,
                                        BenchType benchType) {
        return recipe(id, inputs, primaryOutput, benchType, null);
    }

    /** Creates a CraftingRecipe via reflection with a specific bench ID. */
    public static CraftingRecipe recipe(String id, MaterialQuantity[] inputs,
                                        MaterialQuantity primaryOutput,
                                        BenchType benchType, String benchId) {
        CraftingRecipe r = construct(CraftingRecipe.class);
        setField(CraftingRecipe.class, r, "id", id);
        setField(CraftingRecipe.class, r, "input", inputs);
        setField(CraftingRecipe.class, r, "primaryOutput", primaryOutput);
        if (benchType != null) {
            BenchRequirement req = new BenchRequirement(benchType, benchId, null, 0);
            setField(CraftingRecipe.class, r, "benchRequirement", new BenchRequirement[]{req});
        }
        return r;
    }

    /** Creates a BlockGathering via reflection. */
    public static BlockGathering gathering(BlockBreakingDropType breaking,
                                           SoftBlockDropType soft,
                                           HarvestingDropType harvest,
                                           PhysicsDropType physics) {
        BlockGathering g = construct(BlockGathering.class);
        setField(BlockGathering.class, g, "breaking", breaking);
        setField(BlockGathering.class, g, "soft", soft);
        setField(BlockGathering.class, g, "harvest", harvest);
        setField(BlockGathering.class, g, "physics", physics);
        return g;
    }

    /** Creates a MaterialQuantity with a direct itemId. */
    public static MaterialQuantity materialQty(String itemId, int quantity) {
        return new MaterialQuantity(itemId, null, null, quantity, null);
    }

    /** Creates a MaterialQuantity with a resourceTypeId. */
    public static MaterialQuantity materialQtyResource(String resourceTypeId, int quantity) {
        return new MaterialQuantity(null, resourceTypeId, null, quantity, null);
    }

    // ──────────────────────────────────────────────────────────────
    //  Asset store installation
    // ──────────────────────────────────────────────────────────────

    /**
     * Installs a fake BlockType asset store backed by the given map.
     * Uses a real BlockTypeAssetMap populated via reflection.
     */
    @SuppressWarnings("unchecked")
    public static void installBlockTypes(Map<String, BlockType> blockTypes) {
        try {
            BlockTypeAssetMap<String, BlockType> btMap =
                    new BlockTypeAssetMap<>(BlockType[]::new, bt -> null);
            // Populate the internal map via reflection
            Field assetMapField = DefaultAssetMap.class.getDeclaredField("assetMap");
            assetMapField.setAccessible(true);
            Map<String, BlockType> internalMap = (Map<String, BlockType>) assetMapField.get(btMap);
            internalMap.putAll(blockTypes);

            AssetStore<String, BlockType, BlockTypeAssetMap<String, BlockType>> store =
                    TestAssetStore.create(String.class, BlockType.class, btMap, k -> new BlockType(k));
            setStaticField(BlockType.class, "ASSET_STORE", store);
        } catch (Exception e) {
            throw new RuntimeException("Failed to install BlockType store", e);
        }
    }

    /**
     * Installs a fake Item asset store backed by the given map.
     */
    public static void installItems(Map<String, Item> items) {
        try {
            DefaultAssetMap<String, Item> map = new DefaultAssetMap<>(new HashMap<>(items));
            AssetStore<String, Item, DefaultAssetMap<String, Item>> store =
                    createStore(String.class, Item.class, map, null);
            setStaticField(Item.class, "ASSET_STORE", store);
        } catch (Exception e) {
            throw new RuntimeException("Failed to install Item store", e);
        }
    }

    /**
     * Installs a fake CraftingRecipe asset store backed by the given map.
     */
    public static void installRecipes(Map<String, CraftingRecipe> recipes) {
        try {
            DefaultAssetMap<String, CraftingRecipe> map = new DefaultAssetMap<>(new HashMap<>(recipes));
            AssetStore<String, CraftingRecipe, DefaultAssetMap<String, CraftingRecipe>> store =
                    createStore(String.class, CraftingRecipe.class, map, null);
            setStaticField(CraftingRecipe.class, "ASSET_STORE", store);
        } catch (Exception e) {
            throw new RuntimeException("Failed to install CraftingRecipe store", e);
        }
    }

    /**
     * Installs a fake ItemDropList asset store backed by the given map.
     */
    public static void installDropLists(Map<String, ItemDropList> lists) {
        try {
            DefaultAssetMap<String, ItemDropList> map = new DefaultAssetMap<>(new HashMap<>(lists));
            AssetStore<String, ItemDropList, DefaultAssetMap<String, ItemDropList>> store =
                    createStore(String.class, ItemDropList.class, map, null);
            setStaticField(ItemDropList.class, "ASSET_STORE", store);
        } catch (Exception e) {
            throw new RuntimeException("Failed to install ItemDropList store", e);
        }
    }

    /**
     * Installs a fake BlockGroup asset store on the AssetRegistry.
     * BlockGroup uses AssetRegistry.getAssetStore(BlockGroup.class) rather than a static field.
     */
    @SuppressWarnings("unchecked")
    public static void installBlockGroups(Map<String, BlockGroup> groups) {
        try {
            DefaultAssetMap<String, BlockGroup> map = new DefaultAssetMap<>(new HashMap<>(groups));
            AssetStore<String, BlockGroup, DefaultAssetMap<String, BlockGroup>> store =
                    createStore(String.class, BlockGroup.class, map, null);
            // BlockGroup is looked up via AssetRegistry.getAssetStore(BlockGroup.class),
            // which reads from the private storeMap. Inject directly.
            Field storeMapField = com.hypixel.hytale.assetstore.AssetRegistry.class.getDeclaredField("storeMap");
            storeMapField.setAccessible(true);
            Map<Class<?>, Object> storeMap = (Map<Class<?>, Object>) storeMapField.get(null);
            storeMap.put(BlockGroup.class, store);
        } catch (Exception e) {
            throw new RuntimeException("Failed to install BlockGroup store", e);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Registry pre-population (bypasses init() for isolated tests)
    // ──────────────────────────────────────────────────────────────

    public static void setNaturalRegistry(Set<String> naturalBlockTypes, Set<String> naturalItemIds) {
        setStaticField(NaturalResourceRegistry.class, "naturalBlockTypes",
                Collections.unmodifiableSet(naturalBlockTypes));
        setStaticField(NaturalResourceRegistry.class, "naturalItemIds",
                Collections.unmodifiableSet(naturalItemIds));
    }

    /**
     * Pre-populates the BenchRecipeRegistries static state by creating
     * BenchRecipeRegistry instances with the given data.
     * The benchId is applied to all entries in the single registry.
     */
    public static void setBenchRecipeRegistries(String benchId,
                                                Map<String, CraftingRecipe> byBlock,
                                                Map<String, CraftingRecipe> byId,
                                                Set<String> baseBlockRecipeIds) {
        setBenchRecipeRegistries(
                Map.of(benchId, byBlock),
                Map.of(benchId, byId),
                Map.of(benchId, baseBlockRecipeIds));
    }

    /**
     * Pre-populates the BenchRecipeRegistries static state with multiple benches.
     */
    public static void setBenchRecipeRegistries(
            Map<String, Map<String, CraftingRecipe>> byBlockPerBench,
            Map<String, Map<String, CraftingRecipe>> byIdPerBench,
            Map<String, Set<String>> baseIdsPerBench) {
        java.util.LinkedHashMap<String, BenchRecipeRegistry> regs = new java.util.LinkedHashMap<>();
        for (String benchId : byBlockPerBench.keySet()) {
            BenchRecipeRegistry reg = new BenchRecipeRegistry(benchId);
            setField(BenchRecipeRegistry.class, reg, "recipesByBlockType",
                    Collections.unmodifiableMap(byBlockPerBench.getOrDefault(benchId, Collections.emptyMap())));
            setField(BenchRecipeRegistry.class, reg, "recipesById",
                    Collections.unmodifiableMap(byIdPerBench.getOrDefault(benchId, Collections.emptyMap())));
            setField(BenchRecipeRegistry.class, reg, "baseBlockRecipeIds",
                    Collections.unmodifiableSet(baseIdsPerBench.getOrDefault(benchId, Collections.emptySet())));
            regs.put(benchId, reg);
        }
        setStaticField(BenchRecipeRegistries.class, "registries",
                Collections.unmodifiableMap(regs));
    }

    // ──────────────────────────────────────────────────────────────
    //  Field readers (for verifying modifications)
    // ──────────────────────────────────────────────────────────────

    public static int readBreakingQuantity(BlockBreakingDropType breaking) {
        return readIntField(BlockBreakingDropType.class, breaking, "quantity");
    }

    public static String readBreakingItemId(BlockBreakingDropType breaking) {
        return readField(BlockBreakingDropType.class, breaking, "itemId");
    }

    public static String readBreakingDropListId(BlockBreakingDropType breaking) {
        return readField(BlockBreakingDropType.class, breaking, "dropListId");
    }

    public static int readItemMaxStack(Item item) {
        return readIntField(Item.class, item, "maxStack");
    }

    public static int readItemDropQuantityMin(ItemDrop drop) {
        return readIntField(ItemDrop.class, drop, "quantityMin");
    }

    public static int readItemDropQuantityMax(ItemDrop drop) {
        return readIntField(ItemDrop.class, drop, "quantityMax");
    }

    public static MaterialQuantity[] readRecipeInputs(CraftingRecipe recipe) {
        return readField(CraftingRecipe.class, recipe, "input");
    }

    public static BlockBreakingDropType readGatheringBreaking(BlockGathering g) {
        return readField(BlockGathering.class, g, "breaking");
    }

    public static String readSoftItemId(SoftBlockDropType soft) {
        return readField(SoftBlockDropType.class, soft, "itemId");
    }

    public static String readSoftDropListId(SoftBlockDropType soft) {
        return readField(SoftBlockDropType.class, soft, "dropListId");
    }

    public static String readHarvestItemId(HarvestingDropType harvest) {
        return readField(HarvestingDropType.class, harvest, "itemId");
    }

    public static String readHarvestDropListId(HarvestingDropType harvest) {
        return readField(HarvestingDropType.class, harvest, "dropListId");
    }

    public static boolean readUseDefaultDropWhenPlaced(BlockGathering g) {
        try {
            Field f = BlockGathering.class.getDeclaredField("useDefaultDropWhenPlaced");
            f.setAccessible(true);
            return f.getBoolean(g);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read useDefaultDropWhenPlaced", e);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Cleanup
    // ──────────────────────────────────────────────────────────────

    /** Nulls out all installed static ASSET_STORE fields and resets registries. */
    public static void cleanup() {
        setStaticField(BlockType.class, "ASSET_STORE", null);
        setStaticField(Item.class, "ASSET_STORE", null);
        setStaticField(CraftingRecipe.class, "ASSET_STORE", null);
        setStaticField(ItemDropList.class, "ASSET_STORE", null);

        setNaturalRegistry(Collections.emptySet(), Collections.emptySet());
        setStaticField(BenchRecipeRegistries.class, "registries", Collections.emptyMap());
    }

    // ──────────────────────────────────────────────────────────────
    //  Internal reflection utilities
    // ──────────────────────────────────────────────────────────────

    // ──────────────────────────────────────────────────────────────
    //  Internal: Concrete AssetStore subclass for testing
    // ──────────────────────────────────────────────────────────────

    /**
     * Minimal concrete AssetStore that only implements enough to serve
     * getAssetMap() calls. Avoids HytaleAssetStore which requires
     * server networking classes that aren't available in tests.
     */
    private static class TestAssetStore<K, T extends JsonAssetWithMap<K, M>, M extends AssetMap<K, T>>
            extends AssetStore<K, T, M> {

        private static class TestBuilder<K, T extends JsonAssetWithMap<K, M>, M extends AssetMap<K, T>>
                extends AssetStore.Builder<K, T, M, TestBuilder<K, T, M>> {
            TestBuilder(Class<K> kClass, Class<T> tClass, M assetMap) {
                super(kClass, tClass, assetMap);
            }

            @Override
            public AssetStore<K, T, M> build() {
                return new TestAssetStore<>(this);
            }
        }

        TestAssetStore(TestBuilder<K, T, M> builder) {
            super(builder);
        }

        @Override protected IEventBus getEventBus() { return null; }
        @Override public void addFileMonitor(String s, Path p) {}
        @Override public void removeFileMonitor(Path p) {}
        @Override protected void handleRemoveOrUpdate(Set<K> s, Map<K, T> m, AssetUpdateQuery q) {}

        static <K, T extends JsonAssetWithMap<K, M>, M extends AssetMap<K, T>>
        TestAssetStore<K, T, M> create(Class<K> kClass, Class<T> tClass, M assetMap) {
            return create(kClass, tClass, assetMap, null);
        }

        static <K, T extends JsonAssetWithMap<K, M>, M extends AssetMap<K, T>>
        TestAssetStore<K, T, M> create(Class<K> kClass, Class<T> tClass, M assetMap,
                                        java.util.function.Function<K, T> replaceOnRemove) {
            TestBuilder<K, T, M> builder = new TestBuilder<>(kClass, tClass, assetMap);
            if (replaceOnRemove != null) {
                builder.setReplaceOnRemove(replaceOnRemove);
            }
            return new TestAssetStore<>(builder);
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, T extends JsonAssetWithMap<K, M>, M extends AssetMap<K, T>>
    AssetStore<K, T, M> createStore(Class<K> kClass, Class<T> tClass, M assetMap,
                                     java.util.function.Function<K, T> replaceOnRemove) {
        try {
            return TestAssetStore.create(kClass, tClass, assetMap);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test AssetStore for " + tClass.getSimpleName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T construct(Class<T> clazz) {
        try {
            Constructor<T> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to construct " + clazz.getSimpleName(), e);
        }
    }

    static void setField(Class<?> clazz, Object instance, String fieldName, Object value) {
        try {
            Field f = clazz.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(instance, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set " + fieldName + " on " + clazz.getSimpleName(), e);
        }
    }

    static void setStaticField(Class<?> clazz, String fieldName, Object value) {
        try {
            Field f = clazz.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(null, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set static " + fieldName + " on " + clazz.getSimpleName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T readField(Class<?> clazz, Object instance, String fieldName) {
        try {
            Field f = clazz.getDeclaredField(fieldName);
            f.setAccessible(true);
            return (T) f.get(instance);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read " + fieldName + " on " + clazz.getSimpleName(), e);
        }
    }

    static int readIntField(Class<?> clazz, Object instance, String fieldName) {
        try {
            Field f = clazz.getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.getInt(instance);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read int " + fieldName + " on " + clazz.getSimpleName(), e);
        }
    }
}
