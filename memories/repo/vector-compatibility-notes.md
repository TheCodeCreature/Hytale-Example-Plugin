# Vector compatibility notes

- Current server API expects position vectors from org.joml (e.g., Vector3d, Vector3i) in plugin code.
- Entity rotation contracts use com.hypixel.hytale.math.vector.Rotation3fc, typically instantiated as Rotation3f.
- TransformComponent constructor signature: TransformComponent(org.joml.Vector3dc, com.hypixel.hytale.math.vector.Rotation3fc).
- HeadRotation constructor signature: HeadRotation(com.hypixel.hytale.math.vector.Rotation3fc).
