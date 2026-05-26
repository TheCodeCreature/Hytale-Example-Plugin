package com.CodeCreature.stencil;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Coalesces multiple synchronous inventory change events into a single deferred
 * {@link StencilVisualManager#refreshAffordability} call per world tick.
 *
 * <h3>Problem</h3>
 * A single {@code removeMaterials()} call fires change events on hotbar, backpack,
 * and storage containers synchronously. Each event previously called
 * {@code refreshAffordability()} inline, resulting in 5–6 redundant full scans
 * (40–48 {@code AutoCraftPlanner.plan()} calls) per block placement.
 *
 * <h3>Solution</h3>
 * Instead of calling {@code refreshAffordability()} directly, event handlers call
 * {@link #markDirty(World)}. The first call sets {@link #pending} to {@code true}
 * and schedules a single {@code world.execute()} runnable. Subsequent calls within
 * the same synchronous cascade see {@code pending=true} and no-op. The deferred
 * runnable clears the flag and executes one {@code refreshAffordability()} call
 * that sees the final inventory state.
 *
 * <h3>Re-entrancy guard</h3>
 * Also provides {@link #isRestoring()} / {@link #setRestoring(boolean)} to guard
 * {@code restoreStencils()} against re-entrant invocation. When
 * {@code setItemStackForSlot()} fires a re-entrant change event, the handler
 * checks {@code isRestoring()} and skips the redundant {@code restoreStencils()}
 * call.
 *
 * <h3>Threading</h3>
 * <ul>
 *   <li>{@code markDirty()} and {@code setRestoring()} are called from synchronous
 *       change event handlers on the world thread.</li>
 *   <li>{@code executeRefresh()} runs on the world thread via {@code world.execute()},
 *       which queues (never runs inline).</li>
 *   <li>{@code pending} is {@code AtomicBoolean} for correctness-by-default, though
 *       all access is on the world thread.</li>
 *   <li>{@code restoringStencils} is a plain {@code boolean} — only accessed from
 *       synchronous event handlers on the same thread.</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * One instance per registered player, created in {@link StencilSyncSystem#register}
 * and discarded in {@link StencilSyncSystem#unregister}. If the player disconnects
 * while a refresh is pending, the deferred {@code executeRefresh()} safely no-ops
 * because {@link StencilVisualManager#refreshAffordability} checks for tracked state.
 *
 * @see StencilSyncSystem
 * @see StencilVisualManager#refreshAffordability
 */
public final class AffordabilityCoalescer {

    private static final Logger LOGGER = Logger.getLogger("AffordabilityCoalescer");

    /** Player network reference for sending packets. */
    private final PlayerRef playerRef;

    /** Player entity for inventory access. */
    private final Player player;

    /**
     * Guards against scheduling multiple {@code world.execute()} calls.
     * Set to {@code true} by the first {@link #markDirty} call; cleared to
     * {@code false} inside {@link #executeRefresh} before running the actual
     * refresh. All subsequent {@code markDirty()} calls while {@code true}
     * are no-ops.
     */
    private final AtomicBoolean pending = new AtomicBoolean(false);

    /**
     * Re-entrancy guard for {@code restoreStencils()}.
     * Set to {@code true} before calling {@code restoreStencils()}, cleared
     * after it returns. When {@code true}, the hotbar change event handler
     * skips the {@code restoreStencils()} call to prevent the
     * {@code setItemStackForSlot()} → change event → {@code restoreStencils()}
     * re-entrant loop.
     *
     * <p>Plain {@code boolean} — only accessed from synchronous event handlers
     * on the world thread. No cross-thread access.
     */
    private boolean restoringStencils;

    /**
     * Creates a coalescer for the given player.
     *
     * @param playerRef the player's network reference, used to send packets
     *                  via {@link StencilVisualManager#refreshAffordability}
     * @param player    the player entity, used to access inventory
     */
    public AffordabilityCoalescer(@Nonnull PlayerRef playerRef, @Nonnull Player player) {
        this.playerRef = playerRef;
        this.player = player;
    }

    /**
     * Marks affordability as needing refresh and schedules a single deferred
     * {@code world.execute()} if one is not already pending.
     *
     * <p>Called from all three container change event handlers (hotbar, backpack,
     * storage). Multiple calls within the same synchronous event cascade result
     * in exactly one scheduled refresh.
     *
     * <p>If {@code world} is {@code null} (player mid-disconnect), this method
     * is a no-op.
     *
     * @param world the player's current world, used to call
     *              {@code world.execute(this::executeRefresh)}. May be null
     *              during disconnect.
     */
    public void markDirty(@Nullable World world) {
        if (world == null) return;
        if (pending.compareAndSet(false, true)) {
            world.execute(this::executeRefresh);
        }
    }

    /**
     * Returns whether {@code restoreStencils()} is currently executing.
     *
     * <p>Used by the hotbar change event handler to skip re-entrant
     * {@code restoreStencils()} calls triggered by {@code setItemStackForSlot()}.
     *
     * @return {@code true} if inside a {@code restoreStencils()} call
     */
    public boolean isRestoring() {
        return restoringStencils;
    }

    /**
     * Sets the re-entrancy guard for {@code restoreStencils()}.
     *
     * <p>Must be called with {@code true} before {@code restoreStencils()}
     * and {@code false} after it returns. Use try/finally to guarantee cleanup:
     * <pre>{@code
     * coalescer.setRestoring(true);
     * try {
     *     restoreStencils(hotbar);
     * } finally {
     *     coalescer.setRestoring(false);
     * }
     * }</pre>
     *
     * @param restoring {@code true} to set the guard, {@code false} to clear it
     */
    public void setRestoring(boolean restoring) {
        this.restoringStencils = restoring;
    }

    /**
     * Executes the deferred affordability refresh.
     *
     * <p>This method is the {@code Runnable} passed to {@code world.execute()}.
     * It runs on the world thread on the next tick after all synchronous change
     * events from the current mutation have completed.
     *
     * <p>Clears {@link #pending} BEFORE calling {@code refreshAffordability()},
     * so that if the refresh itself triggers inventory changes (it shouldn't,
     * but defensively), those changes can schedule a new refresh.
     *
     * <p>If the player disconnected between scheduling and execution,
     * {@link StencilVisualManager#refreshAffordability} will find no tracked
     * state and no-op safely.
     */
    private void executeRefresh() {
        pending.set(false);
        StencilVisualManager.refreshAffordability(playerRef, player);
    }
}
