package com.CodeCreature.stencil;

/**
 * @node    ProxyMorphCoordinator
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Performs one guarded morph pass over player inventory proxy-token stacks so generic
 *          drop proxies can become concrete variants when matching inventory is available.
 * @wave    1 (coalesced proxy morph slice)
 * @status  Wave 1 - implemented safe, fail-open per-slot morph pass for hotbar/backpack/storage
 * @do-not  Change planner math or drop generation semantics here.
 *          Allow per-slot failures to abort the overall refresh flow.
 */

import com.CodeCreature.crafting.GenericIngredientIdentity;
import com.CodeCreature.crafting.GenericIngredientResolution;
import com.CodeCreature.crafting.GenericTokenMorphPolicy;
import com.CodeCreature.crafting.GenericVariantMatcher;
import com.CodeCreature.scaling.GenericDropProxyCatalog;
import com.CodeCreature.scaling.AssetFieldAccessor;
import com.CodeCreature.scaling.ResourceTypeResolver;
import com.CodeCreature.util.DebugLogger;
import com.CodeCreature.util.StencilMetadata;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

import static com.CodeCreature.util.DebugLogger.Subsystem.STENCIL;

public final class ProxyMorphCoordinator {

    private final GenericDropProxyCatalog proxyCatalog;

    public ProxyMorphCoordinator() {
        this(new GenericDropProxyCatalog());
    }

    ProxyMorphCoordinator(@Nonnull GenericDropProxyCatalog proxyCatalog) {
        this.proxyCatalog = proxyCatalog;
    }

    /** @intent Perform one morph pass over all player inventory containers used by stencil refresh.
     *  @wave   1 - implemented
     *  @status implemented
     *  @node   ProxyMorphCoordinator#morphProxyStacks
     */
    public void morphProxyStacks(@Nullable Player player) {
        if (player == null || player.getInventory() == null) {
            return;
        }

        CombinedItemContainer combined = player.getInventory().getCombinedBackpackStorageHotbar();
        if (combined == null) {
            return;
        }

        ItemContainer hotbar = player.getInventory().getHotbar();
        ItemContainer backpack = player.getInventory().getBackpack();
        ItemContainer storage = player.getInventory().getStorage();
        List<ItemContainer> targetContainers = List.of(backpack, storage, hotbar);

        morphContainer(hotbar, combined, targetContainers);
        morphContainer(backpack, combined, targetContainers);
        morphContainer(storage, combined, targetContainers);
    }

    /** @intent Iterate one container and morph eligible proxy stacks while isolating failures to each slot.
     *  @wave   1 - implemented
     *  @status implemented
     *  @node   ProxyMorphCoordinator#morphContainer
     */
    private void morphContainer(@Nullable ItemContainer container,
                                @Nonnull CombinedItemContainer combined,
                                @Nonnull List<ItemContainer> targetContainers) {
        if (container == null) {
            return;
        }

        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            try {
                ItemStack stack = container.getItemStack(slot);
                if (stack == null) {
                    continue;
                }

                String currentItemId = stack.getItemId();
                if (!proxyCatalog.isProxyItemId(currentItemId)) {
                    continue;
                }

                String resourceTypeId = proxyCatalog.extractResourceTypeId(currentItemId);
                if (resourceTypeId == null || resourceTypeId.isBlank()) {
                    continue;
                }

                GenericIngredientResolution resolution = buildResolution(resourceTypeId, stack.getQuantity());
                List<GenericVariantMatcher.VariantAvailability> availability =
                        GenericVariantMatcher.collectVariantAvailability(resolution, null, combined, true)
                                .stream()
                                .filter(v -> !proxyCatalog.isProxyItemId(v.itemId()))
                                .toList();

                GenericTokenMorphPolicy.MorphSelection selection =
                        GenericTokenMorphPolicy.selectFromAvailability(resolution, availability);
                String selectedItemId = selection.concreteItemId();

                if (!shouldMorph(currentItemId, selectedItemId, proxyCatalog)) {
                    continue;
                }

                if (selectedItemId == null || selectedItemId.isBlank()) {
                    continue;
                }

                ItemStack morphed = new ItemStack(selectedItemId, stack.getQuantity(), stack.getMetadata());
                container.setItemStackForSlot(slot, morphed);

                // Merge ONLY this newly converted stack into existing target
                // stacks with capacity. This avoids relocating unrelated stacks.
                mergeConvertedStackIntoExistingTargets(container, slot, targetContainers);
            } catch (Exception ex) {
                DebugLogger.log(STENCIL, Level.WARNING,
                        "[ProxyMorph] Failed to morph slot " + slot + "; continuing refresh pass. " + ex.getMessage());
            }
        }
    }

    /** @intent Build typed generic resolution payload for proxy matching using existing resolver ordering.
     *  @wave   2 - exact resource-type candidate resolution
     *  @status implemented
     *  @node   ProxyMorphCoordinator#buildResolution
     */
    @Nonnull
    private static GenericIngredientResolution buildResolution(@Nonnull String resourceTypeId, int quantity) {
        List<String> orderedMatches = buildOrderedMorphCandidates(resourceTypeId);
        String representative = orderedMatches.isEmpty() ? null : orderedMatches.get(0);
        GenericIngredientIdentity identity = new GenericIngredientIdentity(null, resourceTypeId, Math.max(0, quantity));
        return new GenericIngredientResolution(identity, orderedMatches, representative, true);
    }

    /** @intent Build deterministic, deduplicated morph candidates for the proxy resource type only.
     *  @wave   2 - implemented
     *  @status implemented
     *  @node   ProxyMorphCoordinator#buildOrderedMorphCandidates
     */
    @Nonnull
    static List<String> buildOrderedMorphCandidates(@Nonnull String resourceTypeId) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        ordered.addAll(ResourceTypeResolver.getAllMatchingItemIds(resourceTypeId));
        return new ArrayList<>(ordered);
    }

    /** @intent Merge only a converted source stack into existing exact stacks with capacity.
     *  @wave   2 - implemented source-only stacking to avoid stack relocation
     *  @status implemented
     *  @node   ProxyMorphCoordinator#mergeConvertedStackIntoExistingTargets
     */
    private static void mergeConvertedStackIntoExistingTargets(@Nonnull ItemContainer sourceContainer,
                                                               short sourceSlot,
                                                               @Nonnull List<ItemContainer> targetContainers) {
        ItemStack sourceStack = sourceContainer.getItemStack(sourceSlot);
        if (sourceStack == null || StencilMetadata.isStencil(sourceStack)) {
            return;
        }

        int maxStack = resolveMaxStack(sourceStack.getItemId());
        if (maxStack <= 1) {
            return;
        }

        int remaining = sourceStack.getQuantity();
        for (ItemContainer targetContainer : targetContainers) {
            if (targetContainer == null) {
                continue;
            }

            short capacity = targetContainer.getCapacity();
            for (short targetSlot = 0; targetSlot < capacity; targetSlot++) {
                if (targetContainer == sourceContainer && targetSlot == sourceSlot) {
                    continue;
                }

                ItemStack targetStack = targetContainer.getItemStack(targetSlot);
                if (!canCondenseTogether(sourceStack, targetStack)) {
                    continue;
                }

                int room = maxStack - targetStack.getQuantity();
                if (room <= 0) {
                    continue;
                }

                int move = Math.min(room, remaining);
                if (move <= 0) {
                    continue;
                }

                ItemStack mergedTarget = new ItemStack(
                        targetStack.getItemId(),
                        targetStack.getQuantity() + move,
                        targetStack.getMetadata());
                targetContainer.setItemStackForSlot(targetSlot, mergedTarget);

                remaining -= move;
                if (remaining <= 0) {
                    sourceContainer.removeItemStackFromSlot(sourceSlot);
                    return;
                }
            }
        }

        ItemStack reducedSource = new ItemStack(
                sourceStack.getItemId(),
                remaining,
                sourceStack.getMetadata());
        sourceContainer.setItemStackForSlot(sourceSlot, reducedSource);
    }

    /** @intent Check exact stack compatibility for condensing (same item and equivalent metadata, excluding stencils).
     *  @wave   2 - implemented
     *  @status implemented
     *  @node   ProxyMorphCoordinator#canCondenseTogether
     */
    static boolean canCondenseTogether(@Nullable ItemStack toStack,
                                       @Nullable ItemStack fromStack) {
        if (toStack == null || fromStack == null) {
            return false;
        }
        if (StencilMetadata.isStencil(toStack) || StencilMetadata.isStencil(fromStack)) {
            return false;
        }
        if (!toStack.getItemId().equals(fromStack.getItemId())) {
            return false;
        }
        return Objects.equals(toStack.getMetadata(), fromStack.getMetadata());
    }

    private static int resolveMaxStack(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return 1;
        }
        Item item = Item.getAssetMap().getAsset(itemId);
        if (item == null) {
            return 1;
        }
        try {
            int value = AssetFieldAccessor.INSTANCE.itemMaxStack.getInt(item);
            return Math.max(1, value);
        } catch (IllegalAccessException ignored) {
            return 1;
        }
    }

    /** @intent Determine whether a selected item ID is an eligible concrete morph target for the current proxy stack.
     *  @wave   1 - implemented
     *  @status implemented
     *  @node   ProxyMorphCoordinator#shouldMorph
     */
    static boolean shouldMorph(@Nullable String currentItemId,
                               @Nullable String selectedItemId,
                               @Nonnull GenericDropProxyCatalog proxyCatalog) {
        if (currentItemId == null || !proxyCatalog.isProxyItemId(currentItemId)) {
            return false;
        }
        if (selectedItemId == null || selectedItemId.isBlank()) {
            return false;
        }
        if (proxyCatalog.isProxyItemId(selectedItemId)) {
            return false;
        }
        return !selectedItemId.equals(currentItemId);
    }
}