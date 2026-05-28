package com.CodeCreature.ui.blueprintbook;

import com.CodeCreature.registry.BenchRecipeRegistries;
import com.CodeCreature.registry.FilteredRecipeEntry;
import com.CodeCreature.registry.RecipeFilterRegistry;
import com.CodeCreature.util.StencilMetadata;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

/**
 * Interaction that raycasts to the aimed block, resolves its crafting recipe,
 * and adds the corresponding stencil to the player's inventory (hotbar first).
 */
public class BlueprintBookPickStencilInteraction extends SimpleInstantInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static volatile String particleEffect = "GreenOrbImpact";

    public static String getParticleEffect() { return particleEffect; }
    public static void setParticleEffect(String name) { particleEffect = name; }

    public static final BuilderCodec<BlueprintBookPickStencilInteraction> CODEC =
            BuilderCodec.builder(BlueprintBookPickStencilInteraction.class,
                    BlueprintBookPickStencilInteraction::new, SimpleInstantInteraction.CODEC)
                    .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        Ref<EntityStore> ref = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        // Main hand wins — if the player is holding a stencil, let StencilInputListener handle Pick
        var activeItem = player.getInventory().getActiveHotbarItem();
        if (StencilMetadata.isStencil(activeItem)) return;

        Store<EntityStore> store = commandBuffer.getStore();

        Vector3i target = TargetUtil.getTargetBlock(ref, 8.0, store);
        if (target == null) return;

        var world = store.getExternalData().getWorld();
        BlockType blockType = world.getBlockType(target.x, target.y, target.z);
        if (blockType == null) return;

        String blockTypeId = blockType.getId();
        CraftingRecipe recipe = BenchRecipeRegistries.getRecipeForBlock(blockTypeId);
        if (recipe == null) return;

        FilteredRecipeEntry entry = RecipeFilterRegistry.getEntry(recipe.getId());
        if (entry == null) return;

        if (hasStencilForRecipe(player.getInventory().getHotbar(), entry.recipeId())
                || hasStencilForRecipe(player.getInventory().getBackpack(), entry.recipeId())) {
            return;
        }

        ItemStack stencil = StencilMetadata.createStencil(entry.outputItemId(), entry.recipeId());
        player.getInventory().getCombinedHotbarFirst().addItemStack(stencil);

        LOGGER.atInfo().log("[BlueprintBook] Gave stencil for %s to player %s", entry.recipeId(), playerRef.getUuid());
    }

    private boolean hasStencilForRecipe(ItemContainer container, String recipeId) {
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (StencilMetadata.isStencil(stack) && recipeId.equals(StencilMetadata.getRecipeId(stack))) {
                return true;
            }
        }
        return false;
    }
}
