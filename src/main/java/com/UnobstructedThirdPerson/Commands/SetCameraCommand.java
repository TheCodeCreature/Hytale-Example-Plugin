package com.UnobstructedThirdPerson.Commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class SetCameraCommand extends AbstractPlayerCommand {

    public SetCameraCommand() {
        super("SetCam", "Set Camera");
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Player player = store.getComponent(ref, Player.getComponentType()); // also a component

        UUIDComponent component = store.getComponent(ref, UUIDComponent.getComponentType());
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        player.sendMessage(Message.raw("Player: " + player.getDisplayName())); // returns UUID from UUIDComponent
        player.sendMessage(Message.raw("UUIDComponent : " + component.getUuid()));
        player.sendMessage(Message.raw("Transform : " + transform.getPosition()));

        SetCamera(playerRef, true);
    }

    private void SetCamera(@Nonnull PlayerRef playerRef, boolean enabled){

        if (!enabled) {
            playerRef.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.ThirdPerson, false, null));
            return;
        }

        ServerCameraSettings settings = new ServerCameraSettings();
        settings.isFirstPerson = false;
        settings.positionLerpSpeed = 1;
        settings.rotationLerpSpeed = 1;
        settings.distance = 10f;
        settings.eyeOffset = true;
        settings.displayReticle = true;
        settings.sendMouseMotion = true;  // Allow client to control camera rotation
        settings.mouseInputType = MouseInputType.LookAtTargetBlock;
        settings.mouseInputTargetType = MouseInputTargetType.Block;
        settings.applyLookType = ApplyLookType.LocalPlayerLookOrientation;
        settings.applyMovementType = ApplyMovementType.Position;
        settings.rotationType = RotationType.AttachedToPlusOffset;
        settings.positionType = PositionType.AttachedToPlusOffset;
//        settings.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffsetRaycast;

        playerRef.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, settings));
    }


    public static ServerCameraSettings buildFreecamSettings(double x, double y, double z, float yaw, float pitch, int speed) {
        ServerCameraSettings s = new ServerCameraSettings();
        s.positionLerpSpeed = 1.0F;
        s.rotationLerpSpeed = 1.0F;
        s.speedModifier = 1.0F;
        s.allowPitchControls = true;
        s.displayCursor = false;
        s.displayReticle = false;
        s.isFirstPerson = false;
        s.mouseInputType = MouseInputType.LookAtTarget;
        s.mouseInputTargetType = MouseInputTargetType.Any;
        s.sendMouseMotion = true;
        s.skipCharacterPhysics = true;
        s.movementForceRotationType = MovementForceRotationType.CameraRotation;
        s.movementForceRotation = new Direction(0.0F, 0.0F, 0.0F);
        s.canMoveType = CanMoveType.Always;
        s.applyMovementType = ApplyMovementType.Position;
        float h = Math.max(1.0F, (float)speed);
        float v = Math.max(0.5F, 0.4F + (float)speed * 0.12F);
        s.movementMultiplier = new Vector3f(h, v, h);
        s.attachedToType = AttachedToType.None;
        s.attachedToEntityId = 0;
        s.eyeOffset = true;
        s.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffset;
        s.positionOffset = new Position((double)0.0F, (double)0.0F, (double)0.0F);
        s.positionType = PositionType.Custom;
        s.position = new Position(x, y, z);
        s.rotationOffset = new Direction(0.0F, 0.0F, 0.0F);
        s.rotationType = RotationType.Custom;
        s.rotation = new Direction(yaw, pitch, 0.0F);
        s.applyLookType = ApplyLookType.Rotation;
        s.lookMultiplier = new Vector2f(1.0F, 1.0F);
        s.planeNormal = new Vector3f(0.0F, 1.0F, 0.0F);
        return s;
    }

    public static ServerCameraSettings buildFootCameraSettings() {
        ServerCameraSettings s = new ServerCameraSettings();
        s.positionLerpSpeed = 1;
        s.rotationLerpSpeed = 1;
        s.speedModifier = 1.0F;
        s.allowPitchControls = true;
        s.displayCursor = false;
        s.displayReticle = true;
        s.isFirstPerson = false;
        s.distance = 5;
        s.mouseInputType = MouseInputType.LookAtTarget;
        s.mouseInputTargetType = MouseInputTargetType.Any;
        s.sendMouseMotion = true;
        s.skipCharacterPhysics = false;
        s.movementForceRotationType = MovementForceRotationType.CameraRotation;
        s.movementForceRotation = new Direction(0.0F, 0.0F, 0.0F);
        s.canMoveType = CanMoveType.Always;
        s.applyMovementType = ApplyMovementType.CharacterController;
        s.movementMultiplier = new Vector3f(1.0F, 1.0F, 1.0F);
        s.attachedToType = AttachedToType.LocalPlayer;
        s.attachedToEntityId = 0;
        s.eyeOffset = false;
        s.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffset;
        s.positionOffset = new Position(1, 1, (double)0.0F);
        s.positionType = PositionType.AttachedToPlusOffset;
        s.position = new Position((double)0.0F, (double)0.0F, (double)0.0F);
        s.rotationOffset = new Direction(0.0F, 1, 0.0F);
        s.rotationType = RotationType.Custom;
        s.rotation = new Direction(0.0F, 0.0F, 0.0F);
        s.applyLookType = ApplyLookType.Rotation;
        s.lookMultiplier = new Vector2f(1.0F, 1.0F);
        s.planeNormal = new Vector3f(0.0F, 1.0F, 0.0F);
        return s;
    }
}
