package com.UnobstructedThirdPerson.portablebench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.builtin.crafting.window.CraftingWindow;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.window.CraftRecipeAction;
import com.hypixel.hytale.protocol.packets.window.WindowAction;
import com.hypixel.hytale.protocol.packets.window.WindowType;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TempAssetIdUtil;
import javax.annotation.Nonnull;
import java.util.Set;

/**
 * A blockless crafting window that opens a bench UI without requiring a placed bench block.
 * Follows the {@link com.hypixel.hytale.builtin.crafting.window.FieldCraftingWindow} pattern:
 * extends {@link Window} directly (not {@code BlockWindow}/{@code BenchWindow}) so there's
 * no block position validation, distance check, or inventory section requirement.
 *
 * <p>Uses {@link WindowType#PocketCrafting} — the client renders a flat recipe list per
 * category tab. Categories are defined by {@link PortableBenchConfig.CategoryDef} entries,
 * and recipes are populated at construction time from {@link CraftingPlugin}.</p>
 *
 * <p><strong>Why PocketCrafting instead of StructuralCrafting?</strong>
 * {@code StructuralCrafting} requires {@code ItemContainerWindow} (65-slot container)
 * and {@code MaterialContainerWindow} (nearby chests). Without a block, both are null
 * in the {@code OpenWindow} packet, causing a client crash.</p>
 */
public class PortableBenchWindow extends Window {

    @Nonnull
    private final JsonObject windowData = new JsonObject();

    /**
     * Constructs a PocketCrafting window populated with the given bench's categories and recipes.
     *
     * @param config the portable bench configuration defining categories and their recipe mappings
     */
    public PortableBenchWindow(@Nonnull PortableBenchConfig config) {
        super(WindowType.PocketCrafting);
        this.windowData.addProperty("type", BenchType.Crafting.ordinal());
        this.windowData.addProperty("id", config.benchId());
        this.windowData.addProperty("name", config.benchName());

        JsonArray categories = new JsonArray();
        for (PortableBenchConfig.CategoryDef categoryDef : config.categories()) {
            JsonObject category = new JsonObject();
            category.addProperty("id", categoryDef.id());
            category.addProperty("name", categoryDef.name());
            category.addProperty("icon", categoryDef.icon());

            JsonArray craftableRecipes = new JsonArray();
            for (String recipeCategoryId : categoryDef.recipeCategoryIds()) {
                Set<String> recipeIds = CraftingPlugin.getAvailableRecipesForCategory(
                        config.benchId(), recipeCategoryId);
                if (recipeIds != null) {
                    for (String recipeId : recipeIds) {
                        craftableRecipes.add(recipeId);
                    }
                }
            }
            category.add("craftableRecipes", craftableRecipes);
            categories.add(category);
        }
        this.windowData.add("categories", categories);
    }

    @Nonnull
    @Override
    public JsonObject getData() {
        return this.windowData;
    }

    @Override
    public boolean onOpen0(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        this.invalidate();
        return true;
    }

    @Override
    public void onClose0(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
    }

    @SuppressWarnings("removal")
    @Override
    public void handleAction(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull WindowAction action) {
        if (action instanceof CraftRecipeAction craftAction) {
            CraftingManager craftingManager = store.getComponent(ref, CraftingManager.getComponentType());
            if (CraftingWindow.craftSimpleItem(store, ref, craftingManager, craftAction)) {
                SoundUtil.playSoundEvent2d(
                        ref,
                        TempAssetIdUtil.getSoundEventIndex("SFX_Workbench_Craft"),
                        SoundCategory.UI,
                        store
                );
            }
        }
    }
}
