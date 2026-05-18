package com.CodeCreature.util;

import com.CodeCreature.util.StencilMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StencilMetadataTest {

    private static final String TEST_ITEM_ID = "Oak_Planks";
    private static final String TEST_RECIPE_ID = "crafting:oak_planks_x12";

    @Nested
    class IsStencil {

        @Test
        void nullStackReturnsFalse() {
            assertFalse(StencilMetadata.isStencil(null));
        }

        @Test
        void stackWithNoMetadataReturnsFalse() {
            ItemStack stack = new ItemStack(TEST_ITEM_ID, 1, null);
            assertFalse(StencilMetadata.isStencil(stack));
        }

        @Test
        void stackWithEmptyMetadataReturnsFalse() {
            ItemStack stack = new ItemStack(TEST_ITEM_ID, 1, new BsonDocument());
            assertFalse(StencilMetadata.isStencil(stack));
        }

        @Test
        void stackWithStencilTagTrueReturnsTrue() {
            BsonDocument metadata = new BsonDocument();
            metadata.put("BlueprintStencil", new BsonString("true"));
            ItemStack stack = new ItemStack(TEST_ITEM_ID, 1, metadata);

            assertTrue(StencilMetadata.isStencil(stack));
        }

        @Test
        void stackWithStencilTagFalseReturnsFalse() {
            BsonDocument metadata = new BsonDocument();
            metadata.put("BlueprintStencil", new BsonString("false"));
            ItemStack stack = new ItemStack(TEST_ITEM_ID, 1, metadata);

            assertFalse(StencilMetadata.isStencil(stack));
        }

        @Test
        void stackWithStencilTagNonStringTypeReturnsFalse() {
            BsonDocument metadata = new BsonDocument();
            metadata.put("BlueprintStencil", new BsonBoolean(true));
            ItemStack stack = new ItemStack(TEST_ITEM_ID, 1, metadata);

            assertFalse(StencilMetadata.isStencil(stack));
        }
    }

    @Nested
    class GetRecipeId {

        @Test
        void nullStackReturnsNull() {
            assertNull(StencilMetadata.getRecipeId(null));
        }

        @Test
        void stackWithNoMetadataReturnsNull() {
            ItemStack stack = new ItemStack(TEST_ITEM_ID, 1, null);
            assertNull(StencilMetadata.getRecipeId(stack));
        }

        @Test
        void stackWithMetadataButNoRecipeIdReturnsNull() {
            BsonDocument metadata = new BsonDocument();
            metadata.put("BlueprintStencil", new BsonString("true"));
            ItemStack stack = new ItemStack(TEST_ITEM_ID, 1, metadata);

            assertNull(StencilMetadata.getRecipeId(stack));
        }

        @Test
        void stackWithValidRecipeIdReturnsValue() {
            BsonDocument metadata = new BsonDocument();
            metadata.put("RecipeId", new BsonString(TEST_RECIPE_ID));
            ItemStack stack = new ItemStack(TEST_ITEM_ID, 1, metadata);

            assertEquals(TEST_RECIPE_ID, StencilMetadata.getRecipeId(stack));
        }

        @Test
        void stackWithRecipeIdNonStringTypeReturnsNull() {
            BsonDocument metadata = new BsonDocument();
            metadata.put("RecipeId", new BsonInt32(42));
            ItemStack stack = new ItemStack(TEST_ITEM_ID, 1, metadata);

            assertNull(StencilMetadata.getRecipeId(stack));
        }
    }

    @Nested
    class CreateStencil {

        @Test
        void createsItemWithCorrectItemId() {
            ItemStack stencil = StencilMetadata.createStencil(TEST_ITEM_ID, TEST_RECIPE_ID);
            assertEquals(TEST_ITEM_ID, stencil.getItemId());
        }

        @Test
        void createsItemWithQuantityOne() {
            ItemStack stencil = StencilMetadata.createStencil(TEST_ITEM_ID, TEST_RECIPE_ID);
            assertEquals(1, stencil.getQuantity());
        }

        @Test
        void createsItemWithStencilTagTrue() {
            ItemStack stencil = StencilMetadata.createStencil(TEST_ITEM_ID, TEST_RECIPE_ID);
            BsonDocument metadata = stencil.getMetadata();

            assertNotNull(metadata, "Stencil metadata should not be null");
            assertTrue(metadata.containsKey("BlueprintStencil"), "Metadata must contain BlueprintStencil key");
            assertEquals("true", metadata.getString("BlueprintStencil").getValue());
        }

        @Test
        void createsItemWithCorrectRecipeId() {
            ItemStack stencil = StencilMetadata.createStencil(TEST_ITEM_ID, TEST_RECIPE_ID);
            BsonDocument metadata = stencil.getMetadata();

            assertNotNull(metadata, "Stencil metadata should not be null");
            assertTrue(metadata.containsKey("RecipeId"), "Metadata must contain RecipeId key");
            assertEquals(TEST_RECIPE_ID, metadata.getString("RecipeId").getValue());
        }

        @Test
        void roundTripIsStencilReturnsTrue() {
            ItemStack stencil = StencilMetadata.createStencil(TEST_ITEM_ID, TEST_RECIPE_ID);
            assertTrue(StencilMetadata.isStencil(stencil),
                    "A stencil created via createStencil must be recognized by isStencil");
        }

        @Test
        void roundTripGetRecipeIdReturnsSameId() {
            ItemStack stencil = StencilMetadata.createStencil(TEST_ITEM_ID, TEST_RECIPE_ID);
            assertEquals(TEST_RECIPE_ID, StencilMetadata.getRecipeId(stencil),
                    "getRecipeId must return the same ID passed to createStencil");
        }
    }
}
