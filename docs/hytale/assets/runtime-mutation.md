---
topic: "Runtime Asset Mutation"
category: "Assets"
updated: 2026-04-16
sources: ["codebase analysis"]
---

# Runtime Asset Mutation

## Summary

Hytale's asset objects (BlockType, BlockGathering, CraftingRecipe, Item, etc.) don't expose public setters for most fields. Plugins use Java reflection to modify assets at runtime during the `LoadAssetEvent` phase. This is powerful but fragile.

## Reflection Pattern

### Field Resolution

Resolve fields once at construction time and reuse:

```java
final class AssetFieldAccessor {
    final Field gatheringBreaking;
    final Field blockTypeGathering;
    final Field recipeInput;
    final Field itemMaxStack;

    AssetFieldAccessor() {
        try {
            gatheringBreaking  = resolve(BlockGathering.class, "breaking");
            blockTypeGathering = resolve(BlockType.class, "gathering");
            recipeInput        = resolve(CraftingRecipe.class, "input");
            itemMaxStack       = resolve(Item.class, "maxStack");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(
                "Asset field resolution failed — API may have changed", e);
        }
    }

    private static Field resolve(Class<?> clazz, String name) throws NoSuchFieldException {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
```

### Applying Mutations

```java
// Scale recipe inputs
MaterialQuantity[] scaled = new MaterialQuantity[inputs.length];
for (int i = 0; i < inputs.length; i++) {
    scaled[i] = inputs[i].clone(inputs[i].getQuantity() * 12);
}
recipeInput.set(recipe, scaled);

// Modify item max stack
itemMaxStack.set(item, 1200);

// Replace gathering config
blockTypeGathering.set(blockType, newGathering);
```

## Known Reflectable Fields

| Class | Field | Type | Purpose |
|-------|-------|------|---------|
| `BlockType` | `gathering` | `BlockGathering` | Block's gathering configuration |
| `BlockGathering` | `breaking` | `BlockBreakingDropType` | Breaking drop config |
| `BlockGathering` | `useDefaultDropWhenPlaced` | `boolean` | Player-placed drop behavior |
| `SoftBlockDropType` | `itemId`, `dropListId` | `String` | Soft block drops |
| `HarvestingDropType` | `itemId`, `dropListId` | `String` | Harvest drops |
| `PhysicsDropType` | `itemId`, `dropListId` | `String` | Physics drops |
| `ItemDrop` | `quantityMin`, `quantityMax` | `int` | Drop quantity range |
| `CraftingRecipe` | `input` | `MaterialQuantity[]` | Recipe inputs |
| `Item` | `maxStack` | `int` | Maximum stack size |

## Critical Risks

### 1. Shared Instance Contamination

**The #1 risk of runtime mutation.** Multiple `BlockType` objects can reference the same `BlockGathering` Java instance due to JSON inheritance (`Parent` field). Mutating a shared gathering affects all blocks sharing it.

**Safe pattern**: Always clone before mutating:

```java
BlockGathering original = blockType.getGathering();
BlockGathering clone = cloneGathering(original);  // Deep copy all fields
gatheringField.set(blockType, clone);               // Assign clone to this block only
// Now safe to mutate clone
```

### 2. API Breakage

Reflection depends on exact field names. Hytale updates can rename or remove fields. Use early resolution (at construction time) to fail fast on startup rather than at runtime.

### 3. Thread Safety

Asset mutation should only happen during `LoadAssetEvent`, before gameplay begins. Mutating assets during gameplay is unsafe — the engine may be reading them concurrently.

### 4. Identity-Based Tracking

When cloning shared instances, use `IdentityHashMap` or `Collections.newSetFromMap(new IdentityHashMap<>())` to track which Java objects have already been processed:

```java
Set<Object> processedConfigs = Collections.newSetFromMap(new IdentityHashMap<>());

for (BlockType bt : allBlocks) {
    BlockGathering g = bt.getGathering();
    if (processedConfigs.contains(g)) continue;  // Already handled this shared instance
    processedConfigs.add(g);
    // ... process
}
```

## What Can Be Mutated

| Asset | Mutatable? | Notes |
|-------|-----------|-------|
| Block gathering config | Yes | Clone first to avoid shared-instance issues |
| Crafting recipe inputs | Yes | Clone MaterialQuantity arrays |
| Item max stack size | Yes | Direct field set |
| Drop list quantities | Yes | Modify quantityMin/quantityMax on ItemDrop |
| Block textures | No | Loaded into client; server can't change rendering |
| Block models | No | Client-side only |
| Sound effects | No | Client-side only |

## What Cannot Be Mutated (Safely)

- Adding completely new block types at runtime (requires client-side awareness)
- Changing block rendering properties (DrawType, Opacity, textures)
- Modifying client UI elements
- Changing NPC models or animations

## See Also

- [Asset Pipeline](./asset-pipeline.md)
- [Block Gathering](../blocks/gathering.md)
- [Plugin Lifecycle](../plugins/lifecycle.md)
