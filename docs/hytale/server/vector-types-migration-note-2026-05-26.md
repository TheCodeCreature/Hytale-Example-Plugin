---
topic: "Vector Type Migration (Decompile Update)"
category: "server"
updated: 2026-05-26
sources:
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/util/TargetUtil.java"
  - ".tmp_hytale_src/com/hypixel/hytale/math/vector/Transform.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/modules/entity/component/TransformComponent.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/modules/entity/component/HeadRotation.java"
  - ".tmp_hytale_src/com/hypixel/hytale/protocol/Vector3d.java"
  - ".tmp_hytale_src/com/hypixel/hytale/protocol/Vector3f.java"
  - ".tmp_hytale_src/com/hypixel/hytale/protocol/Vector3i.java"
---

# Vector Type Migration (Decompile Update)

## Summary
Current decompiled server sources show two vector families existing side-by-side:
- `com.hypixel.hytale.math.vector.*` for world/entity math APIs.
- `com.hypixel.hytale.protocol.*` for packet/protocol payload DTOs.

For gameplay/plugin code that interacts with world transforms, targeting, and entity components, keep using math vectors.

## Evidence From Decompiled Signatures

`TargetUtil` (world targeting utility) uses math vectors:
- `Vector3i getTargetBlock(Ref<EntityStore>, double, ComponentAccessor<EntityStore>)`
- `Vector3d getTargetLocation(...)`
- `Transform getLook(...)` then `Vector3d pos = transform.getPosition()` and `Vector3d dir = transform.getDirection()`

`Transform` uses math vectors:
- `Vector3d getPosition()`
- `void setPosition(Vector3d)`
- `Vector3f getRotation()`
- `void setRotation(Vector3f)`
- `Vector3d getDirection()`

`TransformComponent` uses math vectors:
- constructor `TransformComponent(Vector3d position, Vector3f rotation)`

`HeadRotation` uses math vectors:
- constructor `HeadRotation(Vector3f rotation)`
- returns `Vector3d` and `Vector3i` for direction helpers

Protocol vectors are separate DTO classes under `com.hypixel.hytale.protocol` with packet serialization methods (`serialize`, `deserialize`, `computeSize`), and are not replacements for world math APIs.

## Practical Migration Rule
Use this rule to resolve compile breaks after decompile updates:
- If the API is in world/entity/interaction utilities (`TargetUtil`, `Transform`, `TransformComponent`, `HeadRotation`, `World`), use `com.hypixel.hytale.math.vector.Vector3d/Vector3f/Vector3i`.
- If the API is in packet or protocol models (`com.hypixel.hytale.protocol.packets.*` fields), use `com.hypixel.hytale.protocol.Vector3d/Vector3f/Vector3i`.

## Known Adjacent Signature Shift
A common adjacent change is accessor typing:
- Newer signatures expect `ComponentAccessor<EntityStore>` (for example in `TargetUtil.getTargetBlock(...)`).
- Passing `Store<EntityStore>` still works where `Store` implements `ComponentAccessor`, but nullness annotations may now produce stricter warnings.
