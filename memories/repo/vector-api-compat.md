# Vector API compatibility

- In this repo's current compile classpath, gameplay-facing vector usage in these interaction/raycast paths resolves to `org.joml.Vector3i` and `org.joml.Vector3d`.
- `com.hypixel.hytale.math.vector.Vector3*` imports are unresolved in plugin code at compile time.
