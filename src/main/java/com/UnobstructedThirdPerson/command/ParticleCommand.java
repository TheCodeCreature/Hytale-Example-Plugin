package com.UnobstructedThirdPerson.command;

import com.UnobstructedThirdPerson.stencil.BlueprintBookPickStencilInteraction;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

public class ParticleCommand extends AbstractPlayerCommand {

    public ParticleCommand() {
        super("bookParticle", "Get or set the Blueprint Book particle effect");
        this.addUsageVariant(new SetParticleVariant());
    }

    // /bookParticle → show current
    @Override
    protected void execute(@NonNull CommandContext context,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref,
                           @NonNull PlayerRef playerRef,
                           @NonNull World world) {
        String current = BlueprintBookPickStencilInteraction.getParticleEffect();
        playerRef.sendMessage(Message.raw("Current particle effect: " + current));
    }

    // /bookParticle set Dust_Sparkles_Fine → set
    private static class SetParticleVariant extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        SetParticleVariant() {
            super("Set the Blueprint Book particle effect");
            this.nameArg = withRequiredArg("name", "Particle system name (e.g. Dust_Sparkles_Fine)", ArgTypes.STRING);
        }

        @Override
        protected void execute(@NonNull CommandContext context,
                               @NonNull Store<EntityStore> store,
                               @NonNull Ref<EntityStore> ref,
                               @NonNull PlayerRef playerRef,
                               @NonNull World world) {
            String name = nameArg.get(context);
            BlueprintBookPickStencilInteraction.setParticleEffect(name);
            playerRef.sendMessage(Message.raw("Particle effect set to: " + name));
        }
    }
}
