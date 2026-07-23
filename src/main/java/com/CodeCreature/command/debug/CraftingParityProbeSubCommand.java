package com.CodeCreature.command.debug;

/**
 * @node    CraftingParityProbeSubCommand
 * @wiki    docs/The Fractonomical System/_knowledge/_sources/Hytale/04010000_Crafting-Input-Resolution/Overview.md
 * @intent  Provides a read-only runtime probe for generic crafting-input parity across engine direct checks,
 *          facade checks, planner outcomes, and raw-cost projection snapshots.
 * @wave    1 (runtime parity diagnostics)
 * @status  Wave 1 - implemented read-only parity probe command
 * @do-not  Mutate inventory or consume materials.
 *          Treat raw-cost representative projection as semantic proof.
 */

import com.CodeCreature.crafting.AutoCraftPlan;
import com.CodeCreature.crafting.AutoCraftPlanner;
import com.CodeCreature.crafting.CraftingAffordabilityFacade;
import com.CodeCreature.crafting.GenericIngredientResolution;
import com.CodeCreature.crafting.PlaceBlockCostUtil;
import com.CodeCreature.crafting.RawMaterialRequirement;
import com.CodeCreature.crafting.RecipeTreeResolver;
import com.CodeCreature.scaling.ResourceTypeResolver;
import com.CodeCreature.util.DebugLogger;
import com.CodeCreature.util.FeatureFlags;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.StringJoiner;
import java.util.logging.Level;

import static com.CodeCreature.util.DebugLogger.Subsystem.CRAFTING;

public class CraftingParityProbeSubCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> recipeIdArg;
    private final OptionalArg<String> preferNaturalArg;

    public CraftingParityProbeSubCommand() {
        super("craftprobe", "Probe crafting generic-input parity (read-only)");
        this.recipeIdArg = withRequiredArg("recipeId", "Crafting recipe ID", ArgTypes.STRING);
        this.preferNaturalArg = withOptionalArg("preferNatural", "true|false (defaults to false)", ArgTypes.STRING);
    }

    @Override
    protected void execute(@NonNull CommandContext context,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            playerRef.sendMessage(Message.raw("§c[CraftProbe] Could not resolve player."));
            return;
        }

        String recipeId = recipeIdArg.get(context);
        boolean preferNatural = parsePreferNatural(context);

        CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);
        if (recipe == null) {
            playerRef.sendMessage(Message.raw("§c[CraftProbe] Unknown recipeId: " + recipeId));
            return;
        }

        CombinedItemContainer container = player.getInventory().getCombinedBackpackStorageHotbar();
        if (container == null) {
            playerRef.sendMessage(Message.raw("§c[CraftProbe] Combined inventory container is unavailable."));
            return;
        }

        List<MaterialQuantity> perUnitMaterials = PlaceBlockCostUtil.getPerUnitCost(recipe);
        boolean detailedProbeLogging = FeatureFlags.get("diagnostics.craftingParityProbe");

        playerRef.sendMessage(Message.raw("§e[CraftProbe] recipe=" + recipe.getId() + " preferNatural=" + preferNatural));
        playerRef.sendMessage(Message.raw("§7[CraftProbe] perUnitInputs=" + perUnitMaterials.size()));

        for (int i = 0; i < perUnitMaterials.size(); i++) {
            MaterialQuantity input = perUnitMaterials.get(i);
            if (input == null) {
                continue;
            }
            GenericIngredientResolution resolution = ResourceTypeResolver.resolveGenericIngredient(input, preferNatural);

            String authored = "input[" + i + "] itemId=" + safe(input.getItemId())
                    + " resourceTypeId=" + safe(input.getResourceTypeId())
                    + " qty=" + input.getQuantity();
            String resolved = "  rep=" + safe(resolution.representativeItemId())
                    + " matches=" + joinList(resolution.orderedMatchingItemIds());

            playerRef.sendMessage(Message.raw("§7[CraftProbe] " + authored));
            playerRef.sendMessage(Message.raw("§7[CraftProbe] " + resolved));
            logDetailed(detailedProbeLogging, "[CraftProbe] " + authored + " | " + resolved);
        }

        boolean engineDirect = container.canRemoveMaterials(perUnitMaterials);
        boolean facadeDirect = CraftingAffordabilityFacade.isAffordable(recipe, preferNatural, container);
        AutoCraftPlan autoPlan = AutoCraftPlanner.plan(recipe, preferNatural, container);

        String parity = "engineDirect=" + engineDirect
                + " facadeDirect=" + facadeDirect
                + " autoPlan.affordable=" + autoPlan.affordable()
                + " autoPlan.requiresAutoCraft=" + autoPlan.requiresAutoCraft();
        playerRef.sendMessage(Message.raw("§b[CraftProbe] " + parity));
        logDetailed(detailedProbeLogging, "[CraftProbe] " + parity);

        if (engineDirect != facadeDirect) {
            String warn = "engineDirect != facadeDirect";
            playerRef.sendMessage(Message.raw("§6[CraftProbe][WARN] " + warn));
            logDetailed(detailedProbeLogging, "[CraftProbe][WARN] " + warn);
        }
        if (facadeDirect != autoPlan.affordable() && !autoPlan.requiresAutoCraft()) {
            String warn = "facadeDirect != autoPlan.affordable while requiresAutoCraft=false";
            playerRef.sendMessage(Message.raw("§6[CraftProbe][WARN] " + warn));
            logDetailed(detailedProbeLogging, "[CraftProbe][WARN] " + warn);
        }

        List<RawMaterialRequirement> rawProjection = RecipeTreeResolver.resolveRecipeToRaw(recipe, preferNatural);
        String rawLine = "rawProjection=" + formatRaw(rawProjection);
        playerRef.sendMessage(Message.raw("§7[CraftProbe] " + rawLine));
        logDetailed(detailedProbeLogging, "[CraftProbe] " + rawLine);
    }

    private boolean parsePreferNatural(@NonNull CommandContext context) {
        if (!preferNaturalArg.provided(context)) {
            return false;
        }
        return Boolean.parseBoolean(preferNaturalArg.get(context));
    }

    private static void logDetailed(boolean enabled, String line) {
        if (!enabled) {
            return;
        }
        DebugLogger.log(CRAFTING, Level.INFO, line);
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private static String joinList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (String value : values) {
            joiner.add(safe(value));
        }
        return joiner.toString();
    }

    private static String formatRaw(List<RawMaterialRequirement> rawProjection) {
        if (rawProjection == null || rawProjection.isEmpty()) {
            return "[]";
        }
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (RawMaterialRequirement req : rawProjection) {
            if (req == null) {
                continue;
            }
            joiner.add(req.itemId() + " x" + req.quantity());
        }
        return joiner.toString();
    }
}