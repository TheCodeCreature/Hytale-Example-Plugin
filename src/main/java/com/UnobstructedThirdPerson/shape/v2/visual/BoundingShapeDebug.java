package com.UnobstructedThirdPerson.shape.v2.visual;

import com.hypixel.hytale.math.matrix.Matrix4d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.DebugShape;

import javax.annotation.Nonnull;

public record BoundingShapeDebug(
        @Nonnull Matrix4d transform,
        @Nonnull DebugShape shape,
        @Nonnull Vector3f color,
        float opacity
) {}
