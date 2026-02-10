package com.UnobstructedThirdPerson.camera;

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
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ExtendedCameraSettings {

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

    @Nonnull
    public String toFullString() {
        StringBuilder sb = new StringBuilder("ExtendedCameraSettings{\n");
        sb.append("  positionLerpSpeed=").append(positionLerpSpeed).append("\n");
        sb.append("  rotationLerpSpeed=").append(rotationLerpSpeed).append("\n");
        sb.append("  distance=").append(distance).append("\n");
        sb.append("  speedModifier=").append(speedModifier).append("\n");
        sb.append("  allowPitchControls=").append(allowPitchControls).append("\n");
        sb.append("  displayCursor=").append(displayCursor).append("\n");
        sb.append("  displayReticle=").append(displayReticle).append("\n");
        sb.append("  mouseInputTargetType=").append(mouseInputTargetType).append("\n");
        sb.append("  sendMouseMotion=").append(sendMouseMotion).append("\n");
        sb.append("  skipCharacterPhysics=").append(skipCharacterPhysics).append("\n");
        sb.append("  isFirstPerson=").append(isFirstPerson).append("\n");
        sb.append("  movementForceRotationType=").append(movementForceRotationType).append("\n");
        sb.append("  movementForceRotation=").append(movementForceRotation).append("\n");
        sb.append("  attachedToType=").append(attachedToType).append("\n");
        sb.append("  attachedToEntityId=").append(attachedToEntityId).append("\n");
        sb.append("  eyeOffset=").append(eyeOffset).append("\n");
        sb.append("  positionDistanceOffsetType=").append(positionDistanceOffsetType).append("\n");
        sb.append("  positionOffset=").append(positionOffset).append("\n");
        sb.append("  rotationOffset=").append(rotationOffset).append("\n");
        sb.append("  positionType=").append(positionType).append("\n");
        sb.append("  position=").append(position).append("\n");
        sb.append("  rotationType=").append(rotationType).append("\n");
        sb.append("  rotation=").append(rotation).append("\n");
        sb.append("  canMoveType=").append(canMoveType).append("\n");
        sb.append("  applyMovementType=").append(applyMovementType).append("\n");
        sb.append("  movementMultiplier=").append(movementMultiplier).append("\n");
        sb.append("  applyLookType=").append(applyLookType).append("\n");
        sb.append("  lookMultiplier=").append(lookMultiplier).append("\n");
        sb.append("  mouseInputType=").append(mouseInputType).append("\n");
        sb.append("  planeNormal=").append(planeNormal).append("\n");
        sb.append("}");
        return sb.toString();
    }

    // Setters for test command
    public void setPositionLerpSpeed(float value) { this.positionLerpSpeed = value; }
    public void setRotationLerpSpeed(float value) { this.rotationLerpSpeed = value; }
    public void setDistance(float value) { this.distance = value; }
    public void setSpeedModifier(float value) { this.speedModifier = value; }
    public void setAllowPitchControls(boolean value) { this.allowPitchControls = value; }
    public void setDisplayCursor(boolean value) { this.displayCursor = value; }
    public void setDisplayReticle(boolean value) { this.displayReticle = value; }
    public void setSendMouseMotion(boolean value) { this.sendMouseMotion = value; }
    public void setSkipCharacterPhysics(boolean value) { this.skipCharacterPhysics = value; }
    public void setIsFirstPerson(boolean value) { this.isFirstPerson = value; }
    public void setEyeOffset(boolean value) { this.eyeOffset = value; }
    public void setAttachedToEntityId(int value) { this.attachedToEntityId = value; }
}
