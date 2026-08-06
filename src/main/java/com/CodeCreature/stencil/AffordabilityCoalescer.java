package com.CodeCreature.stencil;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

public final class AffordabilityCoalescer {

    /** Player network reference for sending packets. */
    private final PlayerRef playerRef;

    /** Player entity for inventory access. */
    private final Player player;

    /** Coordinator for one-pass proxy morphing during coalesced refresh. */
    private final ProxyMorphCoordinator proxyMorphCoordinator = new ProxyMorphCoordinator();

    private final AtomicBoolean pending = new AtomicBoolean(false);

    private boolean restoringStencils;

    /** Re-entrancy guard for mutation-triggered inventory events during proxy morph pass. */
    private boolean morphingProxyStacks;

    public AffordabilityCoalescer(@Nonnull PlayerRef playerRef, @Nonnull Player player) {
        this.playerRef = playerRef;
        this.player = player;
    }

    public void markDirty(@Nullable World world) {
        if (world == null) return;
        if (pending.compareAndSet(false, true)) {
            world.execute(this::executeRefresh);
        }
    }

    public boolean isRestoring() {
        return restoringStencils;
    }

    public void setRestoring(boolean restoring) {
        this.restoringStencils = restoring;
    }

    public boolean isMorphing() {
        return morphingProxyStacks;
    }

    private void executeRefresh() {
        try {
            runProxyMorphPass();
            StencilVisualManager.refreshAffordability(playerRef, player);
        } finally {
            pending.set(false);
        }
    }

    private void runProxyMorphPass() {
        if (morphingProxyStacks) {
            return;
        }
        morphingProxyStacks = true;
        try {
            proxyMorphCoordinator.morphProxyStacks(player);
        } catch (Exception ignored) {
            // Fail-open by design: never block affordability refresh if morphing fails.
        } finally {
            morphingProxyStacks = false;
        }
    }
}
