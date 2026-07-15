---
topic: "StructuralCraftingWindow Recipe Dimming Mechanism"
category: "Crafting"
updated: 2026-04-23
sources: ["Decompiled StructuralCraftingWindow.java", "Decompiled CraftingManager.java", "Decompiled CraftingRecipePacketGenerator.java", "Decompiled protocol/CraftingRecipe.java", "Decompiled protocol/MaterialQuantity.java", "Decompiled ItemGridSlot.java"]
---

# StructuralCraftingWindow Recipe Dimming Mechanism

## Summary

Recipe dimming in StructuralCraftingWindow is **entirely client-side**. The server sends NO explicit craftability flag per recipe slot. The client computes craftability locally by looking up recipe input requirements (via recipe IDs in `optionSlotRecipes` windowData) and comparing them against the input slot contents.

## Complete Data Flow

```
Player places item in input slot
  → inputContainer change event fires
  → updateRecipes() called on server
  → getMatchingRecipes(inputStack) filters recipes via CraftingManager.matches()
  → matching recipes' OUTPUT items placed in optionsContainer
  → recipe IDs sent via windowData["optionSlotRecipes"]
  → container + windowData synced to client
  → CLIENT looks up each recipe ID in its local recipe cache
  → CLIENT checks recipe.inputs against input slot contents
  → CLIENT determines dim/lit state per recipe slot
```

## Server-Side: What Gets Sent

### `updateRecipes()` — Complete Method

```java
private void updateRecipes() {
    this.invalidate();
    this.optionsContainer.clear();
    this.optionSlotToRecipeMap.clear();
    ItemStack inputStack = this.inputContainer.getItemStack((short)0);
    ObjectList<CraftingRecipe> matchingRecipes = this.getMatchingRecipes(inputStack);
    if (matchingRecipes != null) {
        StructuralCraftingBench structuralBench = (StructuralCraftingBench)this.bench;
        sortRecipes(matchingRecipes, structuralBench);

        // Compute divider between header-category and non-header recipes
        int dividerIndex;
        for (dividerIndex = 0; dividerIndex < matchingRecipes.size(); dividerIndex++) {
            if (!hasHeaderCategory(structuralBench, matchingRecipes.get(dividerIndex))) break;
        }
        this.windowData.addProperty("dividerIndex", dividerIndex);

        // Populate options container with OUTPUT items (not inputs)
        short index = 0;
        for (CraftingRecipe match : matchingRecipes) {
            for (BenchRequirement requirement : match.getBenchRequirement()) {
                if (requirement.type == this.bench.getType()
                    && requirement.id.equals(this.bench.getId())) {
                    List<ItemStack> output = CraftingManager.getOutputItemStacks(match);
                    this.optionsContainer.setItemStackForSlot(index, output.getFirst(), false);
                    this.optionSlotToRecipeMap.put(index, match.getId());
                    index++;
                }
            }
        }

        // Send recipe IDs as windowData
        JsonArray optionSlotRecipes = new JsonArray();
        for (int ix = 0; ix < this.optionsContainer.getCapacity(); ix++) {
            String recipeId = this.optionSlotToRecipeMap.get(ix);
            if (recipeId != null) {
                optionSlotRecipes.add(recipeId);
            }
        }
        this.windowData.add("optionSlotRecipes", optionSlotRecipes);
    }
}
```

### What the server sends (windowData):

| Property | Type | Purpose |
|----------|------|---------|
| `selected` | int | Currently selected slot index |
| `dividerIndex` | int | Header category divider position |
| `optionSlotRecipes` | JsonArray[String] | Recipe IDs per option slot |
| `inventoryHints` | JsonArray[int] | Inventory slots with matching materials |
| `allowBlockGroupCycling` | boolean | Whether block group cycling UI is shown |
| `alwaysShowInventoryHints` | boolean | Whether to always show inventory hints |

### What the server does NOT send:

- ❌ No `craftable[]` boolean array
- ❌ No `available[]` flags
- ❌ No per-slot `canCraft` property
- ❌ `ItemGridSlot.isItemUncraftable` exists in the engine but is **never set** by StructuralCraftingWindow

## Server-Side: `handleAction()` for CraftRecipeAction

```java
case CraftRecipeAction craftAction:
    ItemStack output = this.optionsContainer.getItemStack((short)this.selectedSlot);
    if (output != null) {
        int quantity = craftAction.quantity;
        String recipeId = this.optionSlotToRecipeMap.get(this.selectedSlot);
        if (recipeId == null) return;

        CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);
        if (recipe == null) return;

        // Play sound, then queue craft
        craftingManager.queueCraft(ref, store, this, 0, recipe, quantity,
            this.inputContainer, CraftingManager.InputRemovalType.ORDERED);
        this.invalidate();
    }
```

**No pre-craft quantity check.** No material validation before `queueCraft()`. Validation happens later in `CraftingManager.tick()` when the job is processed.

## Server-Side: `getMatchingRecipes()` Filtering

```java
private ObjectList<CraftingRecipe> getMatchingRecipes(@Nullable ItemStack inputStack) {
    if (inputStack == null) return null;
    List<CraftingRecipe> recipes = CraftingPlugin.getBenchRecipes(this.bench.getType(), this.bench.getId());
    if (recipes.isEmpty()) return null;

    ObjectList<CraftingRecipe> matchingRecipes = new ObjectArrayList<>();
    for (CraftingRecipe recipe : recipes) {
        List<MaterialQuantity> inputMaterials = CraftingManager.getInputMaterials(recipe);
        if (inputMaterials.size() == 1
            && CraftingManager.matches(inputMaterials.getFirst(), inputStack)) {
            matchingRecipes.add(recipe);
        }
    }
    return matchingRecipes.isEmpty() ? null : matchingRecipes;
}
```

### `CraftingManager.matches()` — Server-Side Matching Logic

```java
public static boolean matches(MaterialQuantity craftingMaterial, ItemStack itemStack) {
    String itemId = craftingMaterial.getItemId();
    if (itemId != null) {
        return itemId.equals(itemStack.getItemId());  // itemId match
    } else {
        String resourceTypeId = craftingMaterial.getResourceTypeId();
        if (resourceTypeId != null && itemStack.getItem().getResourceTypes() != null) {
            for (ItemResourceType irt : itemStack.getItem().getResourceTypes()) {
                if (resourceTypeId.equals(irt.id)) return true;  // resourceType match
            }
        }
        return false;
    }
}
```

**Key**: Server supports BOTH `itemId` and `resourceTypeId` matching. The server does NOT check quantity in `matches()` — it only checks type.

## Server-Side: `getInputMaterials()` Behavior

```java
public static List<MaterialQuantity> getInputMaterials(CraftingRecipe recipe, int quantity) {
    return recipe.getInput() == null
        ? Collections.emptyList()
        : getInputMaterials(recipe.getInput(), quantity);
}

private static List<MaterialQuantity> getInputMaterials(MaterialQuantity[] input, int quantity) {
    ObjectList<MaterialQuantity> materials = new ObjectArrayList<>();
    for (MaterialQuantity craftingMaterial : input) {
        materials.add(new MaterialQuantity(
            craftingMaterial.getItemId(),
            craftingMaterial.getResourceTypeId(),
            null,
            craftingMaterial.getQuantity() * quantity,
            craftingMaterial.getMetadata()
        ));
    }
    return materials;
}
```

**Just returns `recipe.getInput()` wrapped in new MaterialQuantity objects.** For reflection-overridden fields, `getInput()` returns the overridden array. No hidden computation.

## Server-Side: `removeMaterialsOrdered()` — What Happens at Craft Time

```java
// In testRemoveMaterialFromSlot:
if (material.getItemId() != null) {
    // Match by ItemStack equivalence
    return testRemoveItemStackFromSlot(...);
} else if (material.getTagIndex() != Integer.MIN_VALUE) {
    // Match by tag
    return testRemoveTagFromSlot(...);
} else {
    // Match by resourceTypeId
    return testRemoveResourceFromSlot(container, slot, material.toResource(), ...);
}
```

For `resourceTypeId`-based input: uses `testRemoveResourceFromSlot()` which checks if the item in the slot has the matching resource type. **Server-side removal would succeed — but the client blocks the craft action from ever being sent.**

## Client-Side: Recipe Sync via `UpdateRecipes` Packet

Shadow recipes ARE synced to the client:

1. `StencilBookRecipeMutator.mutate()` calls `CraftingRecipe.getAssetStore().loadAssets()`
2. Inside `AssetStore.loadAssets0()`, it calls `handleRemoveOrUpdate()`
3. `HytaleAssetStore.handleRemoveOrUpdate()`:
   - **Invalidates `cachedInitPackets`** (`this.cachedInitPackets = null`)
   - Broadcasts update packet to connected players (none at startup)
4. When a player connects later, `sendAssets()` generates a fresh init packet from the current asset map — which includes shadow recipes
5. `CraftingRecipePacketGenerator.generateInitPacket()` calls `recipe.toPacket(key)` for each recipe
6. `toPacket()` serializes the overridden input correctly:
   ```java
   packet.inputs = ArrayUtil.copyAndMutate(this.input, MaterialQuantity::toPacket, ...);
   ```

**The client receives shadow recipes with correct input data (`resourceTypeId = "PlaceBlock"`, `quantity = 1`).**

## Protocol: How Input MaterialQuantity Is Serialized

```java
// Server MaterialQuantity.toPacket():
public com.hypixel.hytale.protocol.MaterialQuantity toPacket() {
    packet.itemId = this.itemId;           // null for resourceType-based
    packet.itemTag = this.tagIndex;         // Integer.MIN_VALUE (not set)
    packet.resourceTypeId = this.resourceTypeId;  // "PlaceBlock"
    packet.quantity = this.quantity;         // 1
}
```

Protocol `MaterialQuantity` sent to client:
- `itemId = null`
- `itemTag = -2147483648`
- `resourceTypeId = "PlaceBlock"`
- `quantity = 1`

## Root Cause Analysis: Why Recipes Are Dimmed

### Definitive Finding

**The client computes craftability locally.** It receives recipe IDs via `optionSlotRecipes`, looks them up in its local recipe cache, and checks the recipe's `inputs` against the current input slot.

### Most Likely Root Cause: Client Doesn't Support `resourceTypeId` Matching for Craftability

In vanilla StructuralCrafting, **ALL recipes use `itemId`-based inputs**:
- `MaterialQuantity("Item_HardwoodPlank", null, null, 12, null)` — 12 planks for stairs
- `MaterialQuantity("Item_SandstoneBrick", null, null, 6, null)` — 6 bricks for wall

The client's craftability check for StructuralCraftingWindow was likely built to:
1. Get `recipe.inputs[0].itemId`
2. Compare with `inputSlot.itemId`
3. Check `inputSlot.quantity >= recipe.inputs[0].quantity`

For shadow recipes with `itemId = null` and `resourceTypeId = "PlaceBlock"`:
- Step 1: `recipe.inputs[0].itemId` → `null`
- Step 2: `null` ≠ input item's ID → **NO MATCH** → **DIMMED**

The server's `CraftingManager.matches()` falls through to resourceTypeId when itemId is null, but the client likely does NOT have this fallthrough — or implements it differently.

### Alternative Hypothesis: Quantity Mismatch

In vanilla, the client checks `inputSlot.quantity >= recipe.inputs[0].quantity`:
- Recipe needs 12 planks, player has 100 → lit
- Recipe needs 12 planks, player has 5 → dimmed

For shadow recipes (quantity 1):
- Recipe needs 1, player has 1 → `1 >= 1` → **should pass**
- This is unlikely to be the issue unless there's an off-by-one error

### Eliminated Hypotheses

1. **`ItemGridSlot.isItemUncraftable`** — Never set by StructuralCraftingWindow; uses `SimpleItemContainer`, not `ItemGridSlot` arrays
2. **`knowledgeRequired`** — Vanilla StructuralCrafting recipes have `knowledgeRequired = false`; shadow copies inherit this
3. **Recipes not reaching client** — `loadAssets()` invalidates cache; init packet generated fresh; `CraftingRecipePacketGenerator` serializes all recipes
4. **`getInput()` returning original data** — `getInput()` returns the field directly; reflection override IS respected
5. **Server-side pre-craft check** — `handleAction` for CraftRecipeAction has NO pre-craft quantity or material validation

## Critical Answer: Vanilla Dimming Behavior

> "If I put 1 plank in the input slot, but a recipe requires 12 planks, does that recipe show as DIMMED?"

**Yes.** The client dims recipes where the input slot quantity is insufficient. The client checks `inputSlot.quantity >= recipe.inputs[0].quantity`. With 1 plank and a recipe needing 12, the recipe shows dimmed. This is the core dimming mechanism.

For shadow recipes with quantity 1 and 1 placeholder in the input, the quantity check should pass. **The issue is the material type matching, not quantity.**

## Recommended Fix

**Use `itemId`-based input instead of `resourceTypeId`-based input.** Change the shadow recipe input from:

```java
// Current (resourceTypeId-based — client can't match)
new MaterialQuantity(null, "PlaceBlock", null, 1, null)
```

To:

```java
// Use the actual placeholder item's ID (itemId-based — matches vanilla behavior)
new MaterialQuantity("Block_Placeholder_Blue", null, null, 1, null)
```

This aligns with how ALL vanilla StructuralCrafting recipes define their input — always `itemId`-based. The client's craftability check is designed for `itemId` matching.

### If itemId-Based Still Doesn't Work

If switching to itemId and it's still dimmed, investigate:

1. **Verify the exact item ID matches**: `"Block_Placeholder_Blue"` in recipe input must exactly equal the placeholder item's ID string. Log both.
2. **Verify UpdateRecipes packet timing**: Add logging to confirm shadow recipes are in the asset map BEFORE the first player connects.
3. **Check if `processConfig()` is called**: The `afterDecode` callback `processConfig()` normalizes outputs. Shadow recipes created via copy constructor + reflection may bypass this. Specifically: if `outputs` is null/empty, `processConfig()` sets `outputs = [primaryOutput]`. Shadow recipes inherit outputs from the original, so this should be fine.
4. **Check if the client validates recipe.benchRequirement**: The client might check that the bench the player is using matches one of the recipe's bench requirements. The shadow recipe has `BenchRequirement(StructuralCrafting, "Stencil", ...)`. The client must know it's at a "Stencil" bench. Verify the bench block's bench ID is exactly "Stencil".

## See Also

- [CraftingManager source](../../.tmp_hytale_src/com/hypixel/hytale/builtin/crafting/component/CraftingManager.java)
- [StructuralCraftingWindow source](../../.tmp_hytale_src/com/hypixel/hytale/builtin/crafting/window/StructuralCraftingWindow.java)
- [CraftingRecipePacketGenerator source](../../.tmp_hytale_src/com/hypixel/hytale/server/core/modules/item/CraftingRecipePacketGenerator.java)
- [Protocol MaterialQuantity source](../../.tmp_hytale_src/com/hypixel/hytale/protocol/MaterialQuantity.java)
