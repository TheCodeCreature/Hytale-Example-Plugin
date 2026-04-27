package com.UnobstructedThirdPerson.placeblock;

import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/**
 * Intercepts {@link CraftRecipeEvent.Pre} at the Blueprint Bench when a
 * Block_Placeholder is in the input slot. Instead of consuming the placeholder
 * and producing the output item, this system:
 * <ol>
 *   <li>Cancels the craft event</li>
 *   <li>Arms the placeholder with the selected recipe's ID and output block type</li>
 *   <li>Replaces the placeholder in the input slot with the armed version</li>
 * </ol>
 *
 * <p>Normal items at the Blueprint Bench still craft normally (this system
 * does not interfere). Crafting at other benches is completely unaffected.
 */
public class PlaceBlockBenchInterceptor extends EntityEventSystem<EntityStore, CraftRecipeEvent.Pre> {

    private static final String BLUEPRINT_ID = "Blueprint";

    // CraftingManager position fields — on the plugin compile classpath, safe in static init
    private static final Field CM_X;
    private static final Field CM_Y;
    private static final Field CM_Z;

    static {
        try {
            CM_X = CraftingManager.class.getDeclaredField("x");
            CM_X.setAccessible(true);
            CM_Y = CraftingManager.class.getDeclaredField("y");
            CM_Y.setAccessible(true);
            CM_Z = CraftingManager.class.getDeclaredField("z");
            CM_Z.setAccessible(true);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(
                    "PlaceBlockBenchInterceptor: failed to resolve CraftingManager fields: " + e.getMessage());
        }
    }

    // ---- All remaining reflective handles are lazily resolved at first event ----
    // These classes/methods live in packages not on the plugin compile classpath,
    // but ARE loadable at runtime once the server is fully booted.

    private static volatile boolean lazyResolved = false;
    private static Method worldGetStateMethod;
    private static Class<?> benchStateClass;
    private static Method benchStateGetWindows;
    private static Class<?> scwClass;
    private static Field scwInputContainer;

    /**
     * Lazily resolves all reflection handles that depend on restricted packages.
     * Called once on the first {@code CraftRecipeEvent.Pre} using the live
     * runtime World instance whose concrete class IS fully accessible.
     *
     * @return true if all handles resolved successfully
     */
    private static synchronized boolean ensureLazyResolved(World world) {
        if (lazyResolved) return true;
        try {
            // World.getState — resolve on the concrete runtime class, not abstract World.class
            worldGetStateMethod = findMethod(world.getClass(), "getState",
                    int.class, int.class, int.class, boolean.class);
            if (worldGetStateMethod == null) {
                log("ERROR: getState(int,int,int,boolean) not found on " + world.getClass().getName());
                return false;
            }
            worldGetStateMethod.setAccessible(true);
            log("Resolved getState on " + world.getClass().getName());

            // BenchState — load via the server's classloader (Thread context CL)
            ClassLoader serverCL = Thread.currentThread().getContextClassLoader();
            benchStateClass = Class.forName(
                    "com.hypixel.hytale.builtin.crafting.state.BenchState", true, serverCL);
            benchStateGetWindows = findMethod(benchStateClass, "getWindows");
            if (benchStateGetWindows == null) {
                log("ERROR: getWindows() not found on BenchState");
                return false;
            }
            benchStateGetWindows.setAccessible(true);

            // StructuralCraftingWindow
            scwClass = Class.forName(
                    "com.hypixel.hytale.builtin.crafting.window.StructuralCraftingWindow", true, serverCL);
            scwInputContainer = scwClass.getDeclaredField("inputContainer");
            scwInputContainer.setAccessible(true);

            lazyResolved = true;
            log("All lazy reflection handles resolved successfully.");
            return true;
        } catch (Exception e) {
            log("ERROR during lazy resolution: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Searches a class and all its superclasses and interfaces for a method
     * by name and parameter types. Works around module system restrictions.
     */
    @Nullable
    private static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        // Try declared methods on the class itself first
        try {
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(name) && paramsMatch(m.getParameterTypes(), paramTypes)) {
                    return m;
                }
            }
        } catch (Exception ignored) {
            // getDeclaredMethods may fail on restricted classes
        }
        // Search all interfaces (including inherited)
        for (Class<?> iface : clazz.getInterfaces()) {
            Method found = findMethod(iface, name, paramTypes);
            if (found != null) return found;
        }
        // Search superclass
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null) {
            return findMethod(superClass, name, paramTypes);
        }
        return null;
    }

    private static boolean paramsMatch(Class<?>[] actual, Class<?>[] expected) {
        if (actual.length != expected.length) return false;
        for (int i = 0; i < actual.length; i++) {
            if (actual[i] != expected[i]) return false;
        }
        return true;
    }

    public PlaceBlockBenchInterceptor() {
        super(CraftRecipeEvent.Pre.class);
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull CraftRecipeEvent.Pre event) {

        CraftingRecipe recipe = event.getCraftedRecipe();

        // 1. Only intercept Blueprint Bench recipes
        if (!hasBlueprintRequirement(recipe)) return;

        // 2. Get CraftingManager to find bench position
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        CraftingManager craftingManager = store.getComponent(ref, CraftingManager.getComponentType());
        if (craftingManager == null) return;

        int x, y, z;
        try {
            x = CM_X.getInt(craftingManager);
            y = CM_Y.getInt(craftingManager);
            z = CM_Z.getInt(craftingManager);
        } catch (IllegalAccessException e) {
            log("ERROR reading CraftingManager position: " + e.getMessage());
            return;
        }

        // 3. Get BenchState from the world via lazy-resolved reflection
        World world = store.getExternalData().getWorld();
        if (!ensureLazyResolved(world)) return;

        Object benchState;
        try {
            benchState = worldGetStateMethod.invoke(world, x, y, z, true);
        } catch (Exception e) {
            log("ERROR calling world.getState: " + e.getMessage());
            return;
        }
        if (benchState == null || !benchStateClass.isInstance(benchState)) return;

        // 4. Get the player's UUID to look up their window
        UUIDComponent uuidComp = archetypeChunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComp == null) return;
        UUID uuid = uuidComp.getUuid();

        // 5. Get the BenchWindow for this player via reflection
        Object benchWindow;
        try {
            @SuppressWarnings("unchecked")
            Map<UUID, ?> windows = (Map<UUID, ?>) benchStateGetWindows.invoke(benchState);
            benchWindow = windows.get(uuid);
        } catch (Exception e) {
            log("ERROR accessing BenchState.getWindows: " + e.getMessage());
            return;
        }
        if (benchWindow == null) return;

        // 6. Get the input container from the StructuralCraftingWindow
        if (!scwClass.isInstance(benchWindow)) return;

        SimpleItemContainer inputContainer;
        try {
            inputContainer = (SimpleItemContainer) scwInputContainer.get(benchWindow);
        } catch (IllegalAccessException e) {
            log("ERROR accessing inputContainer: " + e.getMessage());
            return;
        }
        if (inputContainer == null) return;

        ItemStack inputItem = inputContainer.getItemStack((short) 0);
        if (inputItem == null) return;

        // 7. Check if the input item is a Block_Placeholder
        if (!PlaceBlockMetadata.isPlaceBlock(inputItem)) {
            // Normal item — let crafting proceed normally
            return;
        }

        // 8. Cancel the craft event — don't consume the placeholder
        event.setCancelled(true);

        // 9. Arm the placeholder with recipe info — resolve original recipe ID from shadow
        String shadowRecipeId = recipe.getId();
        String originalRecipeId = BlueprintBenchRecipeMutator.getOriginalRecipeId(shadowRecipeId);
        if (originalRecipeId == null) {
            // Fallback: not a shadow recipe, use as-is
            originalRecipeId = shadowRecipeId;
        }
        String outputBlockTypeId = getOutputBlockTypeId(recipe);
        if (outputBlockTypeId == null) {
            log("WARNING: Recipe " + originalRecipeId + " has no output block type ID, cannot arm.");
            // Send feedback to the player
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef != null) {
                playerRef.sendMessage(Message.raw("§c[PlaceBlock] This recipe does not produce a placeable block."));
            }
            return;
        }

        // TODO: resolve actual hotbar slot when this interceptor is re-enabled
        ItemStack armed = PlaceBlockMetadata.arm(inputItem, originalRecipeId, outputBlockTypeId, 0);

        // 10. Replace the item in the input slot
        inputContainer.replaceItemStackInSlot((short) 0, inputItem, armed);

        log("Armed placeholder with recipe '" + originalRecipeId + "' (shadow: " + shadowRecipeId + "') → block '" + outputBlockTypeId + "'.");

        // 11. Send feedback to the player
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null) {
            playerRef.sendMessage(Message.raw("§a[PlaceBlock] Armed with: " + originalRecipeId));
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    private static boolean hasBlueprintRequirement(CraftingRecipe recipe) {
        BenchRequirement[] reqs = recipe.getBenchRequirement();
        if (reqs == null) return false;
        for (BenchRequirement req : reqs) {
            if (req != null && BLUEPRINT_ID.equals(req.id)) return true;
        }
        return false;
    }

    @Nullable
    private static String getOutputBlockTypeId(CraftingRecipe recipe) {
        MaterialQuantity primaryOutput = recipe.getPrimaryOutput();
        if (primaryOutput == null) return null;
        String outputItemId = primaryOutput.getItemId();
        if (outputItemId == null) return null;
        Item outputItem = Item.getAssetMap().getAsset(outputItemId);
        if (outputItem == null) return null;
        return outputItem.getBlockId();
    }

    private static void log(String msg) {
        System.out.println("[PlaceBlockBenchInterceptor] " + msg);
    }
}
