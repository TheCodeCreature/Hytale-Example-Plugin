package com.CodeCreature.ui.stencilbook;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StencilBookPickStencilInteractionTest {

    @AfterEach
    void resetParticleEffect() {
        StencilBookPickStencilInteraction.setParticleEffect("GreenOrbImpact");
    }

    @Test
    void setParticleEffectUpdatesReturnedEffectName() {
        StencilBookPickStencilInteraction.setParticleEffect("SparkBurst");

        assertEquals("SparkBurst", StencilBookPickStencilInteraction.getParticleEffect());
    }

    @Test
    void setParticleEffectAllowsNull() {
        StencilBookPickStencilInteraction.setParticleEffect(null);

        assertNull(StencilBookPickStencilInteraction.getParticleEffect());
    }
}
