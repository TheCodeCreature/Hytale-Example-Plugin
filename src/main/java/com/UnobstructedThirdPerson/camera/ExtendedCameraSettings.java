package com.UnobstructedThirdPerson.camera;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.protocol.ApplyLookType;
import com.hypixel.hytale.protocol.ApplyMovementType;
import com.hypixel.hytale.protocol.AttachedToType;
import com.hypixel.hytale.protocol.CanMoveType;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.MouseInputTargetType;
import com.hypixel.hytale.protocol.MouseInputType;
import com.hypixel.hytale.protocol.MovementForceRotationType;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.PositionDistanceOffsetType;
import com.hypixel.hytale.protocol.PositionType;
import com.hypixel.hytale.protocol.RotationType;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.Vector2f;
import com.hypixel.hytale.protocol.Vector3f;
import com.hypixel.hytale.server.core.codec.ProtocolCodecs;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ExtendedCameraSettings {

    // Position codec for Position type (uses doubles)
    public static final BuilderCodec<Position> POSITION_CODEC = BuilderCodec.builder(Position.class, Position::new)
        .addField(new KeyedCodec<>("X", Codec.DOUBLE), (p, v) -> p.x = v, p -> p.x)
        .addField(new KeyedCodec<>("Y", Codec.DOUBLE), (p, v) -> p.y = v, p -> p.y)
        .addField(new KeyedCodec<>("Z", Codec.DOUBLE), (p, v) -> p.z = v, p -> p.z)
        .build();

    // All 30 fields from ServerCameraSettings with defaults
    protected float positionLerpSpeed = 1.0f;
    protected float rotationLerpSpeed = 1.0f;
    protected float distance = 0.0f;
    protected float speedModifier = 1.0f;
    protected boolean allowPitchControls = false;
    protected boolean displayCursor = false;
    protected boolean displayReticle = false;
    @Nonnull
    protected MouseInputTargetType mouseInputTargetType = MouseInputTargetType.Any;
    protected boolean sendMouseMotion = false;
    protected boolean skipCharacterPhysics = false;
    protected boolean isFirstPerson = true;
    @Nonnull
    protected MovementForceRotationType movementForceRotationType = MovementForceRotationType.AttachedToHead;
    @Nullable
    protected Direction movementForceRotation = null;
    @Nonnull
    protected AttachedToType attachedToType = AttachedToType.LocalPlayer;
    protected int attachedToEntityId = 0;
    protected boolean eyeOffset = false;
    @Nonnull
    protected PositionDistanceOffsetType positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffset;
    @Nullable
    protected Position positionOffset = null;
    @Nullable
    protected Direction rotationOffset = null;
    @Nonnull
    protected PositionType positionType = PositionType.AttachedToPlusOffset;
    @Nullable
    protected Position position = null;
    @Nonnull
    protected RotationType rotationType = RotationType.AttachedToPlusOffset;
    @Nullable
    protected Direction rotation = null;
    @Nonnull
    protected CanMoveType canMoveType = CanMoveType.AttachedToLocalPlayer;
    @Nonnull
    protected ApplyMovementType applyMovementType = ApplyMovementType.CharacterController;
    @Nullable
    protected Vector3f movementMultiplier = null;
    @Nonnull
    protected ApplyLookType applyLookType = ApplyLookType.LocalPlayerLookOrientation;
    @Nullable
    protected Vector2f lookMultiplier = null;
    @Nonnull
    protected MouseInputType mouseInputType = MouseInputType.LookAtTarget;
    @Nullable
    protected Vector3f planeNormal = null;

    public static final BuilderCodec<ExtendedCameraSettings> CODEC = BuilderCodec.builder(ExtendedCameraSettings.class, ExtendedCameraSettings::new)
        // Float fields
        .addField(new KeyedCodec<>("PositionLerpSpeed", Codec.FLOAT, false, true),
            (s, v) -> s.positionLerpSpeed = v, s -> s.positionLerpSpeed)
        .addField(new KeyedCodec<>("RotationLerpSpeed", Codec.FLOAT, false, true),
            (s, v) -> s.rotationLerpSpeed = v, s -> s.rotationLerpSpeed)
        .addField(new KeyedCodec<>("Distance", Codec.FLOAT, false, true),
            (s, v) -> s.distance = v, s -> s.distance)
        .addField(new KeyedCodec<>("SpeedModifier", Codec.FLOAT, false, true),
            (s, v) -> s.speedModifier = v, s -> s.speedModifier)

        // Boolean fields
        .addField(new KeyedCodec<>("AllowPitchControls", Codec.BOOLEAN, false, true),
            (s, v) -> s.allowPitchControls = v, s -> s.allowPitchControls)
        .addField(new KeyedCodec<>("DisplayCursor", Codec.BOOLEAN, false, true),
            (s, v) -> s.displayCursor = v, s -> s.displayCursor)
        .addField(new KeyedCodec<>("DisplayReticle", Codec.BOOLEAN, false, true),
            (s, v) -> s.displayReticle = v, s -> s.displayReticle)
        .addField(new KeyedCodec<>("SendMouseMotion", Codec.BOOLEAN, false, true),
            (s, v) -> s.sendMouseMotion = v, s -> s.sendMouseMotion)
        .addField(new KeyedCodec<>("SkipCharacterPhysics", Codec.BOOLEAN, false, true),
            (s, v) -> s.skipCharacterPhysics = v, s -> s.skipCharacterPhysics)
        .addField(new KeyedCodec<>("IsFirstPerson", Codec.BOOLEAN, false, true),
            (s, v) -> s.isFirstPerson = v, s -> s.isFirstPerson)
        .addField(new KeyedCodec<>("EyeOffset", Codec.BOOLEAN, false, true),
            (s, v) -> s.eyeOffset = v, s -> s.eyeOffset)

        // Integer field
        .addField(new KeyedCodec<>("AttachedToEntityId", Codec.INTEGER, false, true),
            (s, v) -> s.attachedToEntityId = v, s -> s.attachedToEntityId)

        // Enum fields
        .addField(new KeyedCodec<>("MouseInputTargetType", new EnumCodec<>(MouseInputTargetType.class), false, true),
            (s, v) -> s.mouseInputTargetType = v, s -> s.mouseInputTargetType)
        .addField(new KeyedCodec<>("MovementForceRotationType", new EnumCodec<>(MovementForceRotationType.class), false, true),
            (s, v) -> s.movementForceRotationType = v, s -> s.movementForceRotationType)
        .addField(new KeyedCodec<>("AttachedToType", new EnumCodec<>(AttachedToType.class), false, true),
            (s, v) -> s.attachedToType = v, s -> s.attachedToType)
        .addField(new KeyedCodec<>("PositionDistanceOffsetType", new EnumCodec<>(PositionDistanceOffsetType.class), false, true),
            (s, v) -> s.positionDistanceOffsetType = v, s -> s.positionDistanceOffsetType)
        .addField(new KeyedCodec<>("PositionType", new EnumCodec<>(PositionType.class), false, true),
            (s, v) -> s.positionType = v, s -> s.positionType)
        .addField(new KeyedCodec<>("RotationType", new EnumCodec<>(RotationType.class), false, true),
            (s, v) -> s.rotationType = v, s -> s.rotationType)
        .addField(new KeyedCodec<>("CanMoveType", new EnumCodec<>(CanMoveType.class), false, true),
            (s, v) -> s.canMoveType = v, s -> s.canMoveType)
        .addField(new KeyedCodec<>("ApplyMovementType", new EnumCodec<>(ApplyMovementType.class), false, true),
            (s, v) -> s.applyMovementType = v, s -> s.applyMovementType)
        .addField(new KeyedCodec<>("ApplyLookType", new EnumCodec<>(ApplyLookType.class), false, true),
            (s, v) -> s.applyLookType = v, s -> s.applyLookType)
        .addField(new KeyedCodec<>("MouseInputType", new EnumCodec<>(MouseInputType.class), false, true),
            (s, v) -> s.mouseInputType = v, s -> s.mouseInputType)

        // Direction fields (yaw, pitch, roll)
        .addField(new KeyedCodec<>("MovementForceRotation", ProtocolCodecs.DIRECTION, false, true),
            (s, v) -> s.movementForceRotation = v, s -> s.movementForceRotation)
        .addField(new KeyedCodec<>("RotationOffset", ProtocolCodecs.DIRECTION, false, true),
            (s, v) -> s.rotationOffset = v, s -> s.rotationOffset)
        .addField(new KeyedCodec<>("Rotation", ProtocolCodecs.DIRECTION, false, true),
            (s, v) -> s.rotation = v, s -> s.rotation)

        // Position fields (x, y, z doubles)
        .addField(new KeyedCodec<>("PositionOffset", POSITION_CODEC, false, true),
            (s, v) -> s.positionOffset = v, s -> s.positionOffset)
        .addField(new KeyedCodec<>("Position", POSITION_CODEC, false, true),
            (s, v) -> s.position = v, s -> s.position)

        // Vector3f fields
        .addField(new KeyedCodec<>("MovementMultiplier", ProtocolCodecs.VECTOR3F, false, true),
            (s, v) -> s.movementMultiplier = v, s -> s.movementMultiplier)
        .addField(new KeyedCodec<>("PlaneNormal", ProtocolCodecs.VECTOR3F, false, true),
            (s, v) -> s.planeNormal = v, s -> s.planeNormal)

        // Vector2f field
        .addField(new KeyedCodec<>("LookMultiplier", ProtocolCodecs.VECTOR2F, false, true),
            (s, v) -> s.lookMultiplier = v, s -> s.lookMultiplier)

        .build();

    public ExtendedCameraSettings() {
    }

    public ExtendedCameraSettings(@Nonnull ExtendedCameraSettings other) {
        this.positionLerpSpeed = other.positionLerpSpeed;
        this.rotationLerpSpeed = other.rotationLerpSpeed;
        this.distance = other.distance;
        this.speedModifier = other.speedModifier;
        this.allowPitchControls = other.allowPitchControls;
        this.displayCursor = other.displayCursor;
        this.displayReticle = other.displayReticle;
        this.mouseInputTargetType = other.mouseInputTargetType;
        this.sendMouseMotion = other.sendMouseMotion;
        this.skipCharacterPhysics = other.skipCharacterPhysics;
        this.isFirstPerson = other.isFirstPerson;
        this.movementForceRotationType = other.movementForceRotationType;
        this.movementForceRotation = other.movementForceRotation;
        this.attachedToType = other.attachedToType;
        this.attachedToEntityId = other.attachedToEntityId;
        this.eyeOffset = other.eyeOffset;
        this.positionDistanceOffsetType = other.positionDistanceOffsetType;
        this.positionOffset = other.positionOffset;
        this.rotationOffset = other.rotationOffset;
        this.positionType = other.positionType;
        this.position = other.position;
        this.rotationType = other.rotationType;
        this.rotation = other.rotation;
        this.canMoveType = other.canMoveType;
        this.applyMovementType = other.applyMovementType;
        this.movementMultiplier = other.movementMultiplier;
        this.applyLookType = other.applyLookType;
        this.lookMultiplier = other.lookMultiplier;
        this.mouseInputType = other.mouseInputType;
        this.planeNormal = other.planeNormal;
    }

    @Nonnull
    public ServerCameraSettings toPacket() {
        ServerCameraSettings packet = new ServerCameraSettings();
        packet.positionLerpSpeed = this.positionLerpSpeed;
        packet.rotationLerpSpeed = this.rotationLerpSpeed;
        packet.distance = this.distance;
        packet.speedModifier = this.speedModifier;
        packet.allowPitchControls = this.allowPitchControls;
        packet.displayCursor = this.displayCursor;
        packet.displayReticle = this.displayReticle;
        packet.mouseInputTargetType = this.mouseInputTargetType;
        packet.sendMouseMotion = this.sendMouseMotion;
        packet.skipCharacterPhysics = this.skipCharacterPhysics;
        packet.isFirstPerson = this.isFirstPerson;
        packet.movementForceRotationType = this.movementForceRotationType;
        packet.movementForceRotation = this.movementForceRotation;
        packet.attachedToType = this.attachedToType;
        packet.attachedToEntityId = this.attachedToEntityId;
        packet.eyeOffset = this.eyeOffset;
        packet.positionDistanceOffsetType = this.positionDistanceOffsetType;
        packet.positionOffset = this.positionOffset;
        packet.rotationOffset = this.rotationOffset;
        packet.positionType = this.positionType;
        packet.position = this.position;
        packet.rotationType = this.rotationType;
        packet.rotation = this.rotation;
        packet.canMoveType = this.canMoveType;
        packet.applyMovementType = this.applyMovementType;
        packet.movementMultiplier = this.movementMultiplier;
        packet.applyLookType = this.applyLookType;
        packet.lookMultiplier = this.lookMultiplier;
        packet.mouseInputType = this.mouseInputType;
        packet.planeNormal = this.planeNormal;
        return packet;
    }

    // Getters for all fields
    public float getPositionLerpSpeed() { return positionLerpSpeed; }
    public float getRotationLerpSpeed() { return rotationLerpSpeed; }
    public float getDistance() { return distance; }
    public float getSpeedModifier() { return speedModifier; }
    public boolean isAllowPitchControls() { return allowPitchControls; }
    public boolean isDisplayCursor() { return displayCursor; }
    public boolean isDisplayReticle() { return displayReticle; }
    @Nonnull public MouseInputTargetType getMouseInputTargetType() { return mouseInputTargetType; }
    public boolean isSendMouseMotion() { return sendMouseMotion; }
    public boolean isSkipCharacterPhysics() { return skipCharacterPhysics; }
    public boolean isFirstPerson() { return isFirstPerson; }
    @Nonnull public MovementForceRotationType getMovementForceRotationType() { return movementForceRotationType; }
    @Nullable public Direction getMovementForceRotation() { return movementForceRotation; }
    @Nonnull public AttachedToType getAttachedToType() { return attachedToType; }
    public int getAttachedToEntityId() { return attachedToEntityId; }
    public boolean isEyeOffset() { return eyeOffset; }
    @Nonnull public PositionDistanceOffsetType getPositionDistanceOffsetType() { return positionDistanceOffsetType; }
    @Nullable public Position getPositionOffset() { return positionOffset; }
    @Nullable public Direction getRotationOffset() { return rotationOffset; }
    @Nonnull public PositionType getPositionType() { return positionType; }
    @Nullable public Position getPosition() { return position; }
    @Nonnull public RotationType getRotationType() { return rotationType; }
    @Nullable public Direction getRotation() { return rotation; }
    @Nonnull public CanMoveType getCanMoveType() { return canMoveType; }
    @Nonnull public ApplyMovementType getApplyMovementType() { return applyMovementType; }
    @Nullable public Vector3f getMovementMultiplier() { return movementMultiplier; }
    @Nonnull public ApplyLookType getApplyLookType() { return applyLookType; }
    @Nullable public Vector2f getLookMultiplier() { return lookMultiplier; }
    @Nonnull public MouseInputType getMouseInputType() { return mouseInputType; }
    @Nullable public Vector3f getPlaneNormal() { return planeNormal; }

    @Override
    public String toString() {
        return "ExtendedCameraSettings{" +
            "positionLerpSpeed=" + positionLerpSpeed +
            ", rotationLerpSpeed=" + rotationLerpSpeed +
            ", distance=" + distance +
            ", isFirstPerson=" + isFirstPerson +
            ", eyeOffset=" + eyeOffset +
            ", displayReticle=" + displayReticle +
            "}";
    }
}
