package com.CodeCreature.stencil;

/**
 * @node    ProxyMorphCoordinatorTest
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Verifies the minimal decision guardrails for proxy morph eligibility without requiring
 *          heavy runtime container bootstrapping.
 * @wave    1 (coalesced proxy morph slice)
 * @status  Wave 1 - implemented focused helper-level tests for proxy/no-op/morph decisions
 * @do-not  Treat these tests as full runtime inventory mutation coverage.
 *          Assert planner/drop-scaler semantics here.
 */

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import static com.CodeCreature.scaling.AssetTestHelper.cleanup;
import static com.CodeCreature.scaling.AssetTestHelper.installItems;
import static com.CodeCreature.scaling.AssetTestHelper.item;
import static com.CodeCreature.scaling.AssetTestHelper.resourceType;
import static com.CodeCreature.scaling.AssetTestHelper.setNaturalRegistry;
import com.CodeCreature.scaling.GenericDropProxyCatalog;
import com.CodeCreature.scaling.ResourceTypeResolver;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;

class ProxyMorphCoordinatorTest {

    private final GenericDropProxyCatalog catalog = new GenericDropProxyCatalog();

    @AfterEach
    void tearDown() {
        cleanup();
    }

    /** @intent Non-proxy current items should never be morph candidates.
     *  @wave   1 - implemented
     *  @status implemented
     *  @node   ProxyMorphCoordinatorTest#shouldMorphReturnsFalseForNonProxyCurrentItem
     */
    @Test
    void shouldMorphReturnsFalseForNonProxyCurrentItem() {
        boolean result = ProxyMorphCoordinator.shouldMorph("Wood_Hardwood_Planks", "Wood_Hardwood_Raw", catalog);
        assertFalse(result);
    }

    /** @intent Proxy items should no-op when selected target is missing/blank/proxy/self.
     *  @wave   1 - implemented
     *  @status implemented
     *  @node   ProxyMorphCoordinatorTest#shouldMorphReturnsFalseForNoOpTargets
     */
    @Test
    void shouldMorphReturnsFalseForNoOpTargets() {
        String proxy = catalog.buildProxyItemId("Hardwood");
        assertFalse(ProxyMorphCoordinator.shouldMorph(proxy, null, catalog));
        assertFalse(ProxyMorphCoordinator.shouldMorph(proxy, "", catalog));
        assertFalse(ProxyMorphCoordinator.shouldMorph(proxy, "   ", catalog));
        assertFalse(ProxyMorphCoordinator.shouldMorph(proxy, proxy, catalog));

        String anotherProxy = catalog.buildProxyItemId("Rock");
        assertFalse(ProxyMorphCoordinator.shouldMorph(proxy, anotherProxy, catalog));
    }

    /** @intent Proxy items should morph only when target is a concrete non-empty item ID different from current.
     *  @wave   1 - implemented
     *  @status implemented
     *  @node   ProxyMorphCoordinatorTest#shouldMorphReturnsTrueForConcreteTarget
     */
    @Test
    void shouldMorphReturnsTrueForConcreteTarget() {
        String proxy = catalog.buildProxyItemId("Hardwood");
        boolean result = ProxyMorphCoordinator.shouldMorph(proxy, "Wood_Hardwood_Planks", catalog);
        assertTrue(result);
    }

        /** @intent Wood proxy candidate expansion should include trunk family concrete IDs first.
         *  @wave   2 - implemented
         *  @status implemented
         *  @node   ProxyMorphCoordinatorTest#buildOrderedMorphCandidatesExpandsWoodToTrunkFamily
         */
        @Test
        void buildOrderedMorphCandidatesExpandsWoodToTrunkFamily() {
        Map<String, Item> items = new LinkedHashMap<>();
        items.put("Wood_Log_Oak", item(
            "Wood_Log_Oak", "Wood_Log_Oak", true, 100,
            "Wood_Oak",
            resourceType("Wood_Oak_Trunk")));
        items.put("Wood_Log_Birch", item(
            "Wood_Log_Birch", "Wood_Log_Birch", true, 100,
            "Wood_Birch",
            resourceType("Wood_Birch_Trunk")));
        items.put("Wood_Generic_Shaving", item(
            "Wood_Generic_Shaving", "Wood_Generic_Shaving", true, 100,
            "Wood_Generic_Shaving",
            resourceType("Wood")));

        installItems(items);
        setNaturalRegistry(Set.of(), Set.of());
        ResourceTypeResolver.initialize();

        List<String> candidates = ProxyMorphCoordinator.buildOrderedMorphCandidates("Wood");
        assertNotNull(candidates);
        assertTrue(candidates.contains("Wood_Log_Oak"));
        assertTrue(candidates.contains("Wood_Log_Birch"));
        assertTrue(candidates.contains("Wood_Generic_Shaving"));
        assertNotEquals("Wood_Generic_Shaving", candidates.get(0),
            "Wood trunk-family candidates should be ordered before exact Wood matches.");
        }

    /** @intent Exact-only condense should allow same itemId with equivalent metadata.
     *  @wave   2 - implemented
     *  @status implemented
     *  @node   ProxyMorphCoordinatorTest#canCondenseTogetherAllowsExactMatch
     */
    @Test
    void canCondenseTogetherAllowsExactMatch() {
        installItems(Map.of(
            "Rock_Stone", item("Rock_Stone", "Rock_Stone", true, 100)
        ));

        BsonDocument meta = new BsonDocument();
        meta.put("k", new BsonString("v"));
        ItemStack a = new ItemStack("Rock_Stone", 10, meta);
        ItemStack b = new ItemStack("Rock_Stone", 5, meta);

        assertTrue(ProxyMorphCoordinator.canCondenseTogether(a, b));
    }

    /** @intent Exact-only condense should reject stacks with differing metadata or differing item IDs.
     *  @wave   2 - implemented
     *  @status implemented
     *  @node   ProxyMorphCoordinatorTest#canCondenseTogetherRejectsDifferentStacks
     */
    @Test
    void canCondenseTogetherRejectsDifferentStacks() {
        installItems(Map.of(
            "Rock_Stone", item("Rock_Stone", "Rock_Stone", true, 100),
            "Rock_Shale_Brick", item("Rock_Shale_Brick", "Rock_Shale_Brick", true, 100)
        ));

        BsonDocument metaA = new BsonDocument();
        metaA.put("k", new BsonString("a"));
        BsonDocument metaB = new BsonDocument();
        metaB.put("k", new BsonString("b"));

        ItemStack a = new ItemStack("Rock_Stone", 10, metaA);
        ItemStack b = new ItemStack("Rock_Stone", 5, metaB);
        ItemStack c = new ItemStack("Rock_Shale_Brick", 5, metaA);

        assertFalse(ProxyMorphCoordinator.canCondenseTogether(a, b));
        assertFalse(ProxyMorphCoordinator.canCondenseTogether(a, c));
    }
}