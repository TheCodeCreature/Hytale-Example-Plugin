package com.CodeCreature.scaling;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;

class GenericDropProxyCatalogTest {

    /** @intent Ensure the same ResourceTypeId always maps to the same proxy ID and decodes cleanly.
     *  @wave   3 - implemented
     *  @status implemented
     *  @node   GenericDropProxyCatalogTest#proxyIdIsDeterministic */
    @Test
    void proxyIdIsDeterministic() {
        GenericDropProxyCatalog catalog = new GenericDropProxyCatalog();

        String idA = catalog.buildProxyItemId("Wood_All");
        String idB = catalog.buildProxyItemId("Wood_All");
        String idC = catalog.buildProxyItemId("Wood_Hardwood");
        String idD = catalog.buildProxyItemId("Rock_Shale");

        assertEquals(idA, idB, "Proxy ID mapping must be deterministic");
        assertEquals(idA, idC,
            "Variants in the same generic family should canonicalize to the same proxy ID");
        assertNotEquals(idA, idD, "Different generic resource families must map to different proxy IDs");
        assertTrue(catalog.isProxyItemId(idA), "ID must be recognized as proxy namespace");
        assertEquals("Wood", catalog.extractResourceTypeId(idA),
                "Proxy ID encoding should remain reversible");
    }

    /** @intent Ensure icon path resolution uses representative item-icon normalization and does not hardcode fallback icons.
     *  @wave   3 - implemented
     *  @status implemented
     *  @node   GenericDropProxyCatalogTest#iconPathResolutionUsesRepresentativeItemIcons */
    @Test
    void iconPathResolutionUsesRepresentativeItemIcons() {
        GenericDropProxyCatalog catalog = new GenericDropProxyCatalog();

        String icon = catalog.resolveProxyIconPath("Wood");

        assertNotNull(icon,
            "Catalog should resolve generic icon mapping when runtime resource-type icons are available");
        assertTrue(icon.startsWith("Icons/ItemsGenerated/"),
            "Resolved generic icon should remain in item-icon family for drop item assets");
    }

    /** @intent Ensure catalog resolves item-icon-family paths when a representative item icon exists.
     *  @wave   3 - implemented
     *  @status implemented
     *  @node   GenericDropProxyCatalogTest#iconPathResolutionNormalizesRepresentativeItemIcons */
    @Test
    void iconPathResolutionNormalizesRepresentativeItemIcons() {
        AssetTestHelper.installItems(Map.of(
            "ProxyIconRepresentative",
            AssetTestHelper.item(
                "ProxyIconRepresentative",
                null,
                false,
                64,
                AssetTestHelper.resourceType("Hardwood"))));

        GenericDropProxyCatalog catalog = new GenericDropProxyCatalog();

        String icon = catalog.resolveProxyIconPath("Hardwood");

        assertNotNull(icon,
            "Expected representative item icon to resolve when a matching runtime item exists");
        assertTrue(icon.startsWith("Icons/ItemsGenerated/"),
            "Resolved icon should remain in item-icon family for drop item assets");
    }

    /** @intent Ensure runtime-generated proxy items initialize required packet fields so login/item sync does not crash.
     *  @wave   3 - implemented proxy item packet-safety regression coverage
     *  @status implemented
     *  @node   GenericDropProxyCatalogTest#proxyItemToPacketDoesNotThrow */
    @Test
    void proxyItemToPacketDoesNotThrow() {
        AssetTestHelper.installItems(Map.of(
            "Ingredient_Tree_Sap",
            AssetTestHelper.item("Ingredient_Tree_Sap", null, false, 64)));

        GenericDropProxyCatalog catalog = new GenericDropProxyCatalog();
        GenericDropProxyAssetLoader loader = new GenericDropProxyAssetLoader(catalog, AssetFieldAccessor.INSTANCE);

        Item proxyItem = loader.buildProxyItem("Hardwood", "Hardwood", catalog.buildProxyItemId("Hardwood"));

        assertDoesNotThrow(proxyItem::toPacket,
                "Generated proxy items must initialize non-null packet fields required during login/item sync");
    }
}
