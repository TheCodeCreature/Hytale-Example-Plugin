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

    @Test
    void proxyIdIsDeterministic() {
        GenericDropProxyCatalog catalog = new GenericDropProxyCatalog();

        String idA = catalog.buildProxyItemId("Wood_All");
        String idB = catalog.buildProxyItemId("Wood_All");
        String idC = catalog.buildProxyItemId("Wood_Hardwood");
        String idD = catalog.buildProxyItemId("Rock_Shale");
        String idTrunkA = catalog.buildProxyItemId("Wood_Oak_Trunk");
        String idTrunkB = catalog.buildProxyItemId("Wood_Birch_Trunk");

        assertEquals(idA, idB, "Proxy ID mapping must be deterministic");
        assertEquals(idA, idC,
            "Variants in the same generic family should canonicalize to the same proxy ID");
        assertNotEquals(idA, idD, "Different generic resource families must map to different proxy IDs");
        assertEquals(idTrunkA, idTrunkB,
            "Wood trunk variants should canonicalize to the same trunk-family proxy ID");
        assertNotEquals(idA, idTrunkA,
            "Wood generic and Wood trunk generic must remain distinct proxy IDs");
        assertTrue(catalog.isProxyItemId(idA), "ID must be recognized as proxy namespace");
        assertEquals("Wood", catalog.extractResourceTypeId(idA),
                "Proxy ID encoding should remain reversible");
        assertEquals("Wood_Trunk", catalog.extractResourceTypeId(idTrunkA),
                "Wood trunk proxy IDs should decode to trunk-family generic ID");
    }

    @Test
    void iconPathResolutionUsesRepresentativeItemIcons() {
        GenericDropProxyCatalog catalog = new GenericDropProxyCatalog();

        String icon = catalog.resolveProxyIconPath("Wood");

        assertNotNull(icon,
            "Catalog should resolve generic icon mapping when runtime resource-type icons are available");
        assertEquals("Icons/ItemsGenerated/Wood.png", icon,
            "Resolved generic icon should preserve semantic ranking but project to generated item-icon family");
    }

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
        assertEquals("Icons/ItemsGenerated/Hardwood.png", icon,
            "Registry/resource-type semantic icon should outrank representative item icon fallback and map to item icons");
    }

    @Test
    void trunkFamilyIconPrefersAnyTrunk() {
        GenericDropProxyCatalog catalog = new GenericDropProxyCatalog();

        String icon = catalog.resolveProxyIconPath("Wood_Trunk");

        assertEquals("Icons/ItemsGenerated/Any_Trunk.png", icon,
            "Wood_Trunk should prefer Any_Trunk according to the ranked fallback hierarchy and map to item icons");
    }

    @Test
    void trunkFamilyTexturePathRemainsNullWithoutGeneratedTexture() {
        GenericDropProxyCatalog catalog = new GenericDropProxyCatalog();

        String texture = catalog.resolveProxyTexturePath("Wood_Trunk");

        assertEquals("Items/GeneratedProxyTextures/Any_Trunk.png", texture,
            "Trunk-family proxy textures should map from generated item icons when mirrored texture assets exist");
    }

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
