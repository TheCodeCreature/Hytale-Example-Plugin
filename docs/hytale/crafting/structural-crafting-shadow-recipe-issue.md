---
topic: "StructuralCraftingWindow Shadow Recipe Craftability"
category: "Crafting / Recipe Sync"
updated: 2026-04-23
sources:
  - ".tmp_hytale_src/com/hypixel/hytale/builtin/crafting/window/StructuralCraftingWindow.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/asset/HytaleAssetStore.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/modules/item/CraftingRecipePacketGenerator.java"
  - ".tmp_hytale_src/com/hypixel/hytale/builtin/crafting/component/CraftingManager.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/CraftingRecipe.java"
  - ".tmp_hytale_src/com/hypixel/hytale/protocol/packets/assets/UpdateRecipes.java"
---

# Why Shadow Recipes Show But Are Uncraftable in StructuralCraftingWindow

## Executive Summary

**Root cause**: The client's StructuralCrafting window almost certainly validates recipe inputs using `itemId`-only matching. Shadow recipes use `resourceTypeId = "PlaceBlock"` (with `itemId = null`) as their input. The server's `CraftingManager.matches()` supports both `itemId` and `resourceTypeId` matching, but the client's StructuralCrafting code path likely only checks `itemId`. Since all vanilla StructuralCrafting recipes use `itemId`-based inputs, the client never needed `resourceTypeId` support in this window type.

**The client DOES have the shadow recipes** — confirmed via protocol analysis. The issue is client-side input validation, not missing data.

---

## 1. Recipe Sync to Client — CONFIRMED WORKING

### How `AssetStore.loadAssets()` triggers client sync

When `CraftingRecipe.getAssetStore().loadAssets("Hytale:Hytale", shadowRecipes)` is called:

1. `AssetStore.loadAssets()` → `loadAssets0()` → `handleRemoveOrUpdate()`
2. `HytaleAssetStore.handleRemoveOrUpdate()` checks if `packetGenerator != null` (it IS — `CraftingRecipePacketGenerator`)
3. If players are connected, generates an `UpdateRecipes` packet via `CraftingRecipePacketGenerator.generateUpdatePacket()`
4. Broadcasts via `Universe.get().broadcastPacketNoCache(packet)` to ALL connected players
5. Also invalidates `cachedInitPackets`, so new players connecting later get the full set via `generateInitPacket()`

**Key code** from `HytaleAssetStore.handleRemoveOrUpdate()`:
```java
if (this.packetGenerator != null) {
    this.cachedInitPackets = null;  // invalidate init cache
    Universe universe = Universe.get();
    if (universe.getPlayerCount() != 0 || !SETUP_PACKET_CONSUMERS.isEmpty()) {
        if (toBeUpdated != null && !toBeUpdated.isEmpty()) {
            Packet packet = this.packetGenerator.generateUpdatePacket(this.assetMap, toBeUpdated, query);
            universe.broadcastPacketNoCache(packet);
        }
    }
}
```

### Packet structure

`CraftingRecipePacketGenerator` creates `UpdateRecipes` (packet ID 60):
- `type = UpdateType.AddOrUpdate`
- `recipes = Map<String, protocol.CraftingRecipe>` — full recipe data per ID

Each `protocol.CraftingRecipe` includes:
- `id` — recipe ID string
- `inputs[]` — `MaterialQuantity[]` with `itemId`, `resourceTypeId`, `itemTag`, `quantity`
- `outputs[]` — `MaterialQuantity[]`
- `primaryOutput` — `MaterialQuantity`
- `benchRequirement[]` — `BenchRequirement[]` with `type`, `id`, `categories`, `requiredTierLevel`
- `knowledgeRequired` — boolean
- `timeSeconds` — float
- `requiredMemoriesLevel` — int

**The `resourceTypeId = "PlaceBlock"` IS serialized** in the recipe's `inputs[0]` via `MaterialQuantity.toPacket()`. The client receives this data.

### How new players get recipes

`HytaleAssetStore.sendAssets()` generates an `UpdateRecipes` packet with `UpdateType.Init` containing ALL recipes in the asset map (including shadow recipes loaded earlier).

### Conclusion: Client HAS the shadow recipe data

---

## 2. Client-Side Recipe Validation — THE PROBLEM

### What the server sends in StructuralCraftingWindow

The `StructuralCraftingWindow.updateRecipes()` method populates:
1. `optionsContainer` — ItemContainer with output items (synced via container protocol)
2. `windowData.optionSlotRecipes` — JsonArray of recipe ID strings
3. `windowData.dividerIndex` — int for category divider
4. `windowData.selected` — currently selected slot
5. `windowData.inventoryHints` — JsonArray of inventory slot indices with matching materials

**There is NO explicit "craftable" boolean** per recipe. The server does NOT tell the client which recipes are craftable — the client determines this independently.

### Server-side matching (works correctly)

`CraftingManager.matches()`:
```java
public static boolean matches(MaterialQuantity craftingMaterial, ItemStack itemStack) {
    String itemId = craftingMaterial.getItemId();
    if (itemId != null) {
        return itemId.equals(itemStack.getItemId());  // itemId match
    } else {
        String resourceTypeId = craftingMaterial.getResourceTypeId();
        if (resourceTypeId != null && itemStack.getItem().getResourceTypes() != null) {
            for (ItemResourceType irt : itemStack.getItem().getResourceTypes()) {
                if (resourceTypeId.equals(irt.id)) {
                    return true;  // resourceTypeId match
                }
            }
        }
        return false;
    }
}
```

This handles BOTH `itemId` and `resourceTypeId` inputs. Shadow recipes match because:
- Shadow input: `{itemId: null, resourceTypeId: "PlaceBlock", quantity: 1}`
- Placeholder item: has `ResourceTypes: [{"Id": "PlaceBlock"}]`
- Falls through `itemId` (null) → checks `resourceTypeId` → matches ✓

### Client-side matching (inferred — likely ONLY checks itemId)

**Evidence that the client does itemId-only matching for StructuralCrafting:**

1. **ALL 1457 shadow recipes are uncraftable** — not just some. The common factor across all shadow recipes is `itemId = null` in their inputs.

2. **Vanilla StructuralCrafting recipes universally use `itemId`-based inputs.** When you put "Rock_Stone" in a Stonecutter, recipes have `Input: [{ItemId: "Rock_Stone", Quantity: 1}]`. There's no reason for the client's StructuralCrafting code to implement `resourceTypeId` matching.

3. **The client CAN show the items** (server populates the container directly), **but CAN'T enable the craft button** (client validates independently). This is exactly the behavior of a failed input match.

4. **Items appear "dark/dimmed"** — consistent with the client marking recipes as "visible but not currently craftable with your input."

### Why the craft button is disabled

The client receives `optionSlotRecipes` (recipe IDs) and looks up each recipe in its local registry. For the selected recipe, it checks:

```
recipe.inputs[0].itemId == inputSlot.itemId
```

For shadow recipes: `null == "Block_Placeholder_Blue"` → **false** → craft button disabled.

The client does NOT fall through to `resourceTypeId` matching for StructuralCrafting windows (no vanilla recipe requires it).

---

## 3. How Vanilla Recipes Reach the Client

### Initial load (server startup)

1. `AssetRegistryLoader` registers the `CraftingRecipe` asset store with `CraftingRecipePacketGenerator`
2. Recipes are loaded from `Server/Item/Recipes/` JSON files
3. `Item.collectRecipesToGenerate()` creates auto-generated recipes from item definitions
4. These are loaded via `CraftingRecipe.getAssetStore().loadAssets()` inside the `LoadedAssetsEvent<Item>` handler
5. All recipes enter the asset map before any player connects

### Player connect

1. `HytaleAssetStore.sendAssets()` is called for each asset store
2. `generateInitPacket()` serializes ALL recipes from `assetMap.getAssetMap()` into an `UpdateRecipes` packet with `UpdateType.Init`
3. Player receives the full recipe set

### Knowledge/Known recipes (separate system)

`CraftingPlugin.sendKnownRecipes()` sends an `UpdateKnownRecipes` packet (packet ID 228) containing recipes the player has "learned" — used for `knowledgeRequired` gating in Crafting/DiagramCrafting windows. **StructuralCrafting does NOT use `knowledgeRequired` in vanilla** (enforced by the codec validator: "KnowledgeRequired in recipe can't be set for non crafting recipes").

---

## 4. Additional Findings

### `knowledgeRequired` inheritance is a secondary concern

The shadow recipe copy constructor inherits `knowledgeRequired` from the original recipe. If the original has `knowledgeRequired = true` (valid for Crafting/DiagramCrafting), the shadow recipe gets `knowledgeRequired = true` + `BenchType.StructuralCrafting` — an invalid combination per the codec validator:

```java
if (craftingRecipe.isKnowledgeRequired()
    && benchRequirement.type != BenchType.Crafting
    && benchRequirement.type != BenchType.DiagramCrafting) {
    results.fail("KnowledgeRequired in recipe can't be set for non crafting recipes");
}
```

This validator runs during codec decode (JSON parsing), NOT during `loadAssets(List<T>)` with pre-constructed objects. So the server accepts these recipes. But the client might also reject or ignore recipes with this invalid combination. **This is a secondary issue** — even if fixed, the `resourceTypeId` matching problem remains.

### `requiredMemoriesLevel` is inherited but defaults to 1

The copy constructor copies `requiredMemoriesLevel` from the original (default: 1). Level 1 means "always available." Only recipes explicitly set to level > 1 would be gated. This is unlikely to be the primary issue.

### The `CraftRecipeAction` path on the server IS reachable

If the client DID send a `CraftRecipeAction`, the server-side flow would work:
1. `StructuralCraftingWindow.handleAction()` looks up recipe from `optionSlotToRecipeMap`
2. `CraftingManager.queueCraft()` → `isValidBenchForRecipe()` validates bench type/id/tier
3. `removeInputFromInventory()` removes input items
4. Recipe crafts successfully

The bottleneck is the client never sends `CraftRecipeAction` because it believes the recipe isn't craftable.

---

## 5. Recommended Fix

### Primary fix: Use `itemId`-based inputs for shadow recipes

Change `StencilBookRecipeMutator.mutate()` to set the shadow recipe input as:
```java
inputField.set(shadow, new MaterialQuantity[]{
    new MaterialQuantity("Block_Placeholder_Blue", null, null, 1, null)
});
```

This makes the client's `itemId` matching work: `"Block_Placeholder_Blue" == "Block_Placeholder_Blue"` → craft button enabled.

**Trade-off**: Only one placeholder color would match per recipe set. Options:
- **Single placeholder**: Use only `Block_Placeholder_Blue` for the Stencil bench
- **Per-color recipes**: Create 3 shadow recipe sets (one per color) — 3× recipes
- **Consolidate to one placeholder item**: Replace the 3 color variants with a single `Block_Placeholder` item

### Secondary fix: Set `knowledgeRequired = false` on all shadow recipes

After the copy constructor, explicitly override:
```java
// After: CraftingRecipe shadow = new CraftingRecipe(recipe);
Field knowledgeField = CraftingRecipe.class.getDeclaredField("knowledgeRequired");
knowledgeField.setAccessible(true);
knowledgeField.set(shadow, false);
```

This prevents the invalid `knowledgeRequired + StructuralCrafting` combination and ensures no knowledge gating on shadow recipes.

### Secondary fix: Set `requiredMemoriesLevel = 1` on all shadow recipes

```java
Field memoriesField = CraftingRecipe.class.getDeclaredField("requiredMemoriesLevel");
memoriesField.setAccessible(true);
memoriesField.set(shadow, 1);
```

This ensures no memories-level gating.

---

## 6. Architecture Summary

```
Server                                          Client
──────                                          ──────
loadAssets(shadowRecipes)
  ├─ AssetStore.loadAssets0()
  │   ├─ assetMap.putAll()           ──────►    (recipes in asset map)
  │   ├─ handleRemoveOrUpdate()
  │   │   └─ CraftingRecipePacketGenerator
  │   │       .generateUpdatePacket()
  │   │       └─ UpdateRecipes packet  ──────►  Client recipe registry
  │   └─ LoadedAssetsEvent fired
  │       └─ CraftingPlugin.onRecipeLoad()
  │           └─ BenchRecipeRegistry updated
  │
Player opens Stencil bench
  ├─ StructuralCraftingWindow created
  │   └─ windowData sent              ──────►  Client opens StructuralCrafting UI
  │
Player puts placeholder in input
  ├─ inputContainer change event
  │   └─ updateRecipes()
  │       ├─ getMatchingRecipes()
  │       │   └─ CraftingManager.matches()
  │       │       └─ resourceTypeId match ✓
  │       ├─ optionsContainer populated
  │       │   └─ output items set      ──────►  Client shows items in grid ✓
  │       └─ optionSlotRecipes set     ──────►  Client receives recipe IDs ✓
  │                                             Client validates:
  │                                               recipe.input.itemId == input.itemId
  │                                               null == "Block_Placeholder_Blue"
  │                                               → MISMATCH → craft button DISABLED ✗
```
