package com.CodeCreature.scaling;

import com.CodeCreature.registry.BenchRecipeRegistries;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.PhysicsDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Diagnostic ECS system that logs detailed drop configuration when a block
 * is broken. Toggle on/off via {@code /Debug BreakLog}.
 *
 * <p>When enabled, every {@link BreakBlockEvent} prints:
 * <ul>
 *   <li>Block type ID</li>
 *   <li>Whether it's classified as natural vs recipe block</li>
 *   <li>Full gathering config (breaking, soft, harvest, physics)</li>
 *   <li>Recipe inputs (if any)</li>
 *   <li>Resolved drop list contents</li>
 * </ul>
 */
public class BreakBlockDiagnostic extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final AtomicBoolean enabled = new AtomicBoolean(false);
    private static final Logger LOGGER = Logger.getLogger("BreakBlockDiagnostic");

    public BreakBlockDiagnostic() {
        super(BreakBlockEvent.class);
    }

    public static boolean isEnabled() {
        return enabled.get();
    }

    public static boolean toggle() {
        boolean prev, next;
        do {
            prev = enabled.get();
            next = !prev;
        } while (!enabled.compareAndSet(prev, next));
        return next;
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull BreakBlockEvent event) {
        if (!enabled.get()) return;

        BlockType bt = event.getBlockType();
        if (bt == null) return;

        String btId = bt.getId();
        StringBuilder sb = new StringBuilder();
        sb.append("\n══════════════════════════════════════════════\n");
        sb.append("[BreakLog] Block: ").append(btId).append("\n");

        // Classification
        boolean isNatural = NaturalResourceRegistry.isNaturalBlock(btId);
        CraftingRecipe recipe = BenchRecipeRegistries.getRecipeForBlock(btId);
        sb.append("  Natural: ").append(isNatural)
          .append("  |  Has recipe: ").append(recipe != null).append("\n");

        // Recipe details
        if (recipe != null) {
            sb.append("  Recipe inputs (post-scaling):\n");
            MaterialQuantity[] inputs = recipe.getInput();
            if (inputs != null) {
                for (MaterialQuantity mq : inputs) {
                    if (mq == null) continue;
                    String resolved = ResourceTypeResolver.resolveInputItemId(mq, null);
                    String itemId = mq.getItemId();
                    String resId = mq.getResourceTypeId();
                    sb.append("    - itemId=").append(itemId)
                      .append(", resourceTypeId=").append(resId)
                      .append(", qty=").append(mq.getQuantity())
                      .append("  → resolved: ").append(resolved).append("\n");
                }
            }
            MaterialQuantity primaryOut = recipe.getPrimaryOutput();
            if (primaryOut != null) {
                sb.append("  Recipe output: itemId=").append(primaryOut.getItemId())
                  .append(", qty=").append(primaryOut.getQuantity()).append("\n");
            }
        }

        // Gathering config
        BlockGathering gathering = bt.getGathering();
        if (gathering == null) {
            sb.append("  Gathering: NULL (engine fallback — drops 1x block item)\n");
        } else {
            sb.append("  Gathering config:\n");
            logBreaking(sb, gathering.getBreaking());
            logSoft(sb, gathering.getSoft());
            logHarvest(sb, gathering.getHarvest());
            logPhysics(sb, gathering.getPhysics());

            var toolData = gathering.getToolData();
            if (toolData != null && !toolData.isEmpty()) {
                sb.append("    toolData:\n");
                for (var entry : toolData.entrySet()) {
                    BlockGathering.BlockToolData td = entry.getValue();
                    if (td == null) continue;
                    sb.append("      [").append(entry.getKey()).append("] itemId=")
                      .append(td.getItemId()).append(", dropListId=")
                      .append(td.getDropListId()).append("\n");
                }
            }

            try {
                java.lang.reflect.Field udf = BlockGathering.class.getDeclaredField("useDefaultDropWhenPlaced");
                udf.setAccessible(true);
                sb.append("    useDefaultDropWhenPlaced: ").append(udf.get(gathering)).append("\n");
            } catch (Exception e) {
                sb.append("    useDefaultDropWhenPlaced: <unreadable>\n");
            }
        }

        // Block item
        var blockItem = bt.getItem();
        sb.append("  Block item: ").append(blockItem != null ? blockItem.getId() : "null").append("\n");

        // Try to resolve actual drops via BlockHarvestUtils
        if (gathering != null && gathering.getBreaking() != null) {
            BlockBreakingDropType breaking = gathering.getBreaking();
            try {
                List<ItemStack> drops = BlockHarvestUtils.getDrops(
                        bt, breaking.getQuantity(),
                        breaking.getItemId(), breaking.getDropListId());
                sb.append("  Resolved drops (BlockHarvestUtils):\n");
                for (ItemStack stack : drops) {
                    sb.append("    - ").append(stack.getItemId())
                      .append(" x").append(stack.getQuantity()).append("\n");
                }
            } catch (Exception e) {
                sb.append("  Resolved drops: ERROR — ").append(e.getMessage()).append("\n");
            }
        }

        // If breaking references a drop list, dump its contents
        if (gathering != null && gathering.getBreaking() != null) {
            String dlId = gathering.getBreaking().getDropListId();
            if (dlId != null) {
                sb.append("  Drop list '").append(dlId).append("' contents:\n");
                try {
                    ItemDropList list = ItemDropList.getAssetMap().getAsset(dlId);
                    if (list == null) {
                        sb.append("    NOT FOUND in asset store\n");
                    } else {
                        List<ItemDrop> allDrops = list.getContainer().getAllDrops(new ArrayList<>());
                        for (ItemDrop drop : allDrops) {
                            sb.append("    - ").append(drop.getItemId())
                              .append(" qty=").append(drop.getQuantityMin())
                              .append("-").append(drop.getQuantityMax()).append("\n");
                        }
                    }
                } catch (Exception e) {
                    sb.append("    ERROR reading drop list: ").append(e.getMessage()).append("\n");
                }
            }
        }

        sb.append("══════════════════════════════════════════════");
        LOGGER.info(sb.toString());
    }

    private static void logBreaking(StringBuilder sb, BlockBreakingDropType d) {
        if (d == null) {
            sb.append("    breaking: null\n");
            return;
        }
        sb.append("    breaking: gatherType=").append(d.getGatherType())
          .append(", quality=").append(d.getQuality())
          .append(", qty=").append(d.getQuantity())
          .append(", itemId=").append(d.getItemId())
          .append(", dropListId=").append(d.getDropListId()).append("\n");
    }

    private static void logSoft(StringBuilder sb, SoftBlockDropType d) {
        if (d == null) return;
        sb.append("    soft: itemId=").append(d.getItemId())
          .append(", dropListId=").append(d.getDropListId()).append("\n");
    }

    private static void logHarvest(StringBuilder sb, HarvestingDropType d) {
        if (d == null) return;
        sb.append("    harvest: itemId=").append(d.getItemId())
          .append(", dropListId=").append(d.getDropListId()).append("\n");
    }

    private static void logPhysics(StringBuilder sb, PhysicsDropType d) {
        if (d == null) return;
        sb.append("    physics: itemId=").append(d.getItemId())
          .append(", dropListId=").append(d.getDropListId()).append("\n");
    }
}
