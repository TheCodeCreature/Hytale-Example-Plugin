package com.CodeCreature.ui.radial;

import com.CodeCreature.registry.BenchRecipeRegistries;
import com.CodeCreature.registry.FilteredRecipeEntry;
import com.CodeCreature.registry.RecipeFilterRegistry;
import com.CodeCreature.util.StencilMetadata;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import org.joml.Vector3i;

import java.util.Objects;


/**
 * Intercepts outbound SyncInteractionChains packets while the player is holding a stencil:
 * <ul>
 *   <li><b>Use</b> — opens the stencil radial menu</li>
 *   <li><b>Pick</b> — raycasts to the aimed block, resolves its recipe, and swaps the held stencil in-place</li>
 * </ul>
 *
 * Registration: PacketAdapters.registerOutbound(StencilRadialInputListener::onOutboundPacket)
 */
public final class StencilInputListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private StencilInputListener() {}

    /**
     * Intercepts outbound packets. When a SyncInteractionChains packet contains
     * an InteractionType.Use update while holding a stencil, opens the radial menu.
     * When it contains an InteractionType.Pick update while holding a stencil,
     * raycasts to the aimed block and swaps the stencil to match that block's recipe.
     */
    public static void onOutboundPacket(PlayerRef playerRef, Packet packet) {
        if (!(packet instanceof SyncInteractionChains syncPacket)) return;

        for (SyncInteractionChain chain : syncPacket.updates) {
            if (chain.interactionType != InteractionType.Use
                    && chain.interactionType != InteractionType.Pick) continue;

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) continue;

            Store<EntityStore> store = ref.getStore();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) continue;

            var itemInHand = player.getInventory().getActiveHotbarItem();
            if (!StencilMetadata.isStencil(itemInHand)) continue;

            var world = store.getExternalData().getWorld();

            if (chain.interactionType == InteractionType.Use) {
                // Stencil + Use — open radial menu on world thread
                LOGGER.atInfo().log("[Stencil] Use interaction for player %s — opening radial menu", playerRef.getUuid());
                world.execute(() -> openRadialMenu(playerRef, ref, store, player));
                break;
            }

            // Stencil + Pick — pick-to-switch on world thread
            world.execute(() -> pickToSwitch(playerRef, ref, store, player));
            break;
        }
    }

    /**
     * Opens the stencil radial menu for the given player.
     * Must be called on the world thread (via world.execute).
     */
    private static void openRadialMenu(PlayerRef playerRef, Ref<EntityStore> ref,
                                       Store<EntityStore> store, Player player) {
        if (playerRef == null || ref == null || store == null || player == null) {
            return;
        }

        var pageManager = player.getPageManager();
        pageManager.openCustomPage(
                Objects.requireNonNull(ref, "ref"),
                Objects.requireNonNull(store, "store"),
                new StencilRadialMenuPage(playerRef, player));
    }

    /**
     * Raycasts to the aimed block, resolves its recipe, and swaps the held stencil in-place.
     * Must be called on the world thread (via world.execute).
     */
    private static void pickToSwitch(PlayerRef playerRef, Ref<EntityStore> ref,
                                     Store<EntityStore> store, Player player) {
        if (playerRef == null || ref == null || store == null || player == null) {
            return;
        }

        Vector3i target = TargetUtil.getTargetBlock(
                Objects.requireNonNull(ref, "ref"),
                8.0,
                Objects.requireNonNull(store, "store"));
        if (target == null) return;

        var world = store.getExternalData().getWorld();
        BlockType blockType = world.getBlockType(target.x, target.y, target.z);
        if (blockType == null) return;

        String blockTypeId = blockType.getId();
        if (blockTypeId == null) return;

        CraftingRecipe recipe = BenchRecipeRegistries.getRecipeForBlock(blockTypeId);
        if (recipe == null) return;

        String recipeId = recipe.getId();
        if (recipeId == null) return;

        FilteredRecipeEntry entry = RecipeFilterRegistry.getEntry(recipeId);
        if (entry == null) return;

        var itemInHand = player.getInventory().getActiveHotbarItem();
        if (entry.recipeId().equals(StencilMetadata.getRecipeId(itemInHand))) return;

        ItemStack newStencil = StencilMetadata.createStencil(entry.outputItemId(), entry.recipeId());
        short activeSlot = (short) player.getInventory().getActiveHotbarSlot();
        var hotbar = player.getInventory().getHotbar();
        if (hotbar == null) return;

        hotbar.setItemStackForSlot(activeSlot, newStencil);

        LOGGER.atInfo().log("[Stencil] Pick-to-switch for player %s - swapped to recipe %s", playerRef.getUuid(), entry.recipeId());
    }
}
