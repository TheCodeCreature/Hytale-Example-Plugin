package com.CodeCreature.scaling;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

        assertEquals(idA, idB, "Proxy ID mapping must be deterministic");
        assertNotEquals(idA, idC, "Different resource types must map to different proxy IDs");
        assertTrue(catalog.isProxyItemId(idA), "ID must be recognized as proxy namespace");
        assertEquals("Wood_All", catalog.extractResourceTypeId(idA),
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

        assertNull(icon,
            "Catalog should not fabricate a hardcoded fallback icon when no representative runtime item is present in this unit scope");
    }

    /** @intent Ensure catalog resolves item-icon-family paths when a representative item icon exists.
     *  @wave   3 - implemented
     *  @status implemented
     *  @node   GenericDropProxyCatalogTest#iconPathResolutionNormalizesRepresentativeItemIcons */
    @Test
    void iconPathResolutionNormalizesRepresentativeItemIcons() {
        GenericDropProxyCatalog catalog = new GenericDropProxyCatalog();
        Item representative = new Item("ProxyIconRepresentative");
        representative.setResourceTypes(new com.hypixel.hytale.protocol.ItemResourceType[]{
                new com.hypixel.hytale.protocol.ItemResourceType("Hardwood", 1)
        });
        representative.setIcon("Icons/ItemsGenerated/Ingredient_Fibre.png");
        Item.getAssetMap().addAsset(representative);

        String icon = catalog.resolveProxyIconPath("Hardwood");

        org.junit.jupiter.api.Assertions.assertNotNull(icon,
            "Expected representative item icon to resolve when a matching runtime item exists");
        assertTrue(icon.startsWith("Common/Icons/ItemsGenerated/"),
            "Resolved icon should remain in item-icon family for drop item assets");
    }

    /** @intent Ensure runtime-generated proxy items initialize required packet fields so login/item sync does not crash.
     *  @wave   3 - implemented proxy item packet-safety regression coverage
     *  @status implemented
     *  @node   GenericDropProxyCatalogTest#proxyItemToPacketDoesNotThrow */
    @Test
    void proxyItemToPacketDoesNotThrow() {
        GenericDropProxyCatalog catalog = new GenericDropProxyCatalog();
        GenericDropProxyAssetLoader loader = new GenericDropProxyAssetLoader(catalog, AssetFieldAccessor.INSTANCE);

        Item proxyItem = loader.buildProxyItem("Hardwood", catalog.buildProxyItemId("Hardwood"));

        assertDoesNotThrow(proxyItem::toPacket,
                "Generated proxy items must initialize non-null packet fields required during login/item sync");

        var translation = proxyItem.getTranslationProperties();
        assertNotNull(translation, "Proxy item should expose explicit translation properties");
        assertEquals("Generic Hardwood", translation.getName(),
            "Proxy item should use a human-readable display name instead of fallback server.items.<id>.name");
    }
}
