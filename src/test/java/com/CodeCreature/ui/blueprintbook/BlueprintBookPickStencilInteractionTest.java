package com.CodeCreature.ui.blueprintbook;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BlueprintBookPickStencilInteractionTest {

    @AfterEach
    void resetParticleEffect() {
        BlueprintBookPickStencilInteraction.setParticleEffect("GreenOrbImpact");
    }

    @Test
    void setParticleEffectUpdatesReturnedEffectName() {
        BlueprintBookPickStencilInteraction.setParticleEffect("SparkBurst");

        assertEquals("SparkBurst", BlueprintBookPickStencilInteraction.getParticleEffect());
    }

    @Test
    void setParticleEffectAllowsNull() {
        BlueprintBookPickStencilInteraction.setParticleEffect(null);

        assertNull(BlueprintBookPickStencilInteraction.getParticleEffect());
    }
}
