---
topic: "CraftingManager.craftItem() — Full Analysis for Portable Bench Bypass"
category: "Crafting"
updated: 2026-04-17
sources: [".tmp_hytale_src/com/hypixel/hytale/builtin/crafting/component/CraftingManager.java"]
---

# CraftingManager.craftItem() — Full Source & Bypass Analysis

## 1. craftItem() — Complete Method

```java
public boolean craftItem(
    Ref<EntityStore> ref,
    ComponentAccessor<EntityStore> componentAccessor,
    CraftingRecipe recipe,
    int quantity,
    ItemContainer itemContainer
) {
    // 1. Check no upgrade in progress
    if (this.upgradingJob != null) {
        return false;
    }

    Objects.requireNonNull(recipe, "Recipe can't be null");

    // 2. Fire CraftRecipeEvent.Pre (cancellable ECS event)
    CraftRecipeEvent.Pre preEvent = new CraftRecipeEvent.Pre(recipe, quantity);
    componentAccessor.invoke(ref, preEvent);
    if (preEvent.isCancelled()) {
        return false;
    }

    // 3. *** THE BENCH VALIDATION — THIS IS THE BLOCKER ***
    if (!this.isValidBenchForRecipe(ref, componentAccessor, recipe)) {
        return false;
    }

    // 4. Get world and player
    World world = componentAccessor.getExternalData().getWorld();
    Player playerComponent = componentAccessor.getComponent(ref, Player.getComponentType());

    // 5. Remove input materials (skipped in Creative mode)
    if (playerComponent.getGameMode() != GameMode.Creative 
        && !removeInputFromInventory(itemContainer, recipe, quantity)) {
        // Send "missing ingredient" notification
        PlayerRef playerRefComponent = componentAccessor.getComponent(ref, PlayerRef.getComponentType());
        String translationKey = getRecipeOutputTranslationKey(recipe);
        if (translationKey != null) {
            NotificationUtil.sendNotification(
                playerRefComponent.getPacketHandler(),
                Message.translation("server.general.crafting.missingIngredient")
                    .param("item", Message.translation(translationKey)),
                NotificationStyle.Danger
            );
        }
        return false;
    }

    // 6. Fire CraftRecipeEvent.Post (cancellable ECS event)
    CraftRecipeEvent.Post postEvent = new CraftRecipeEvent.Post(recipe, quantity);
    componentAccessor.invoke(ref, postEvent);
    if (postEvent.isCancelled()) {
        return true;  // NOTE: returns true even though cancelled (items already consumed!)
    }

    // 7. Give output items to player
    giveOutput(ref, componentAccessor, recipe, quantity);

    // 8. Fire PlayerCraftEvent (global event bus, deprecated)
    IEventDispatcher<PlayerCraftEvent, PlayerCraftEvent> dispatcher = HytaleServer.get()
        .getEventBus()
        .dispatchFor(PlayerCraftEvent.class, world.getName());
    if (dispatcher.hasListener()) {
        dispatcher.dispatch(new PlayerCraftEvent(ref, playerComponent, recipe, quantity));
    }

    return true;
}
```

## 2. isValidBenchForRecipe() — The Blocker

```java
private boolean isValidBenchForRecipe(Ref<EntityStore> ref, ComponentAccessor<EntityStore> componentAccessor, CraftingRecipe recipe) {
    Player playerComponent = componentAccessor.getComponent(ref, Player.getComponentType());
    PlayerConfigData playerConfigData = playerComponent.getPlayerConfigData();
    String primaryOutputItemId = recipe.getPrimaryOutput() != null ? recipe.getPrimaryOutput().getItemId() : null;

    // Check 1: Knowledge requirement
    if (recipe.isKnowledgeRequired() && !(primaryOutputItemId != null && playerConfigData.getKnownRecipes().contains(primaryOutputItemId))) {
        return false;
    }

    // Check 2: Memories level requirement
    World world = componentAccessor.getExternalData().getWorld();
    if (recipe.getRequiredMemoriesLevel() > 1 && MemoriesPlugin.get().getMemoriesLevel(world.getGameplayConfig()) < recipe.getRequiredMemoriesLevel()) {
        return false;
    }

    // Check 3: Bench type/ID/tier matching — THIS IS THE PROBLEM
    BenchType benchType = this.blockType != null ? this.blockType.getBench().getType() : BenchType.Crafting;
    String benchName = this.blockType != null ? this.blockType.getBench().getId() : "Fieldcraft";
    boolean meetsRequirements = false;
    
    // Reads world block state at (x, y, z) for tier level
    BlockState state = world.getState(this.x, this.y, this.z, true);
    int benchTierLevel = state instanceof BenchState ? ((BenchState) state).getTierLevel() : 0;
    
    BenchRequirement[] requirements = recipe.getBenchRequirement();
    if (requirements != null) {
        for (BenchRequirement benchRequirement : requirements) {
            if (benchRequirement.type == benchType 
                && benchName.equals(benchRequirement.id) 
                && benchRequirement.requiredTierLevel <= benchTierLevel) {
                meetsRequirements = true;
                break;
            }
        }
    }

    if (!meetsRequirements) {
        return false;
    }

    // Check 4: For non-Fieldcraft Crafting benches, only one recipe type at a time
    if (benchType == BenchType.Crafting && !"Fieldcraft".equals(benchName)) {
        CraftingJob craftingJob = this.queuedCraftingJobs.peek();
        return craftingJob == null || craftingJob.recipe.getId().equals(recipe.getId());
    }
    return true;
}
```

### What isValidBenchForRecipe checks:

| Check | What it validates | Impact |
|-------|-------------------|--------|
| Knowledge | `isKnowledgeRequired()` → player's `knownRecipes` | Only for recipes that require discovery |
| Memories | `requiredMemoriesLevel > 1` → world memories level | Only for high-tier recipes |
| **Bench Type** | `benchRequirement.type == benchType` | **Must match BenchType enum** |
| **Bench ID** | `benchName.equals(benchRequirement.id)` | **Must match the Bench's `Id` field (e.g., "Builders")** |
| **Tier Level** | `benchRequirement.requiredTierLevel <= benchTierLevel` | **Reads BlockState at (x,y,z) for tier** |
| Queue exclusivity | Only one recipe type per non-Fieldcraft bench | Minor constraint |

## 3. removeInputFromInventory() — Simple Overload

```java
private static boolean removeInputFromInventory(ItemContainer itemContainer, CraftingRecipe craftingRecipe, int quantity) {
    List<MaterialQuantity> materialsToRemove = getInputMaterials(craftingRecipe, quantity);
    if (materialsToRemove.isEmpty()) {
        return true;
    }
    ListTransaction<MaterialTransaction> materialTransactions = itemContainer.removeMaterials(materialsToRemove, true, true, true);
    return materialTransactions.succeeded();
}
```

**Public API equivalent:**
- `CraftingManager.getInputMaterials(recipe, quantity)` — **public static**, returns `List<MaterialQuantity>`
- `ItemContainer.removeMaterials(materials, allOrNothing, exactAmount, filter)` — **public**, returns transaction
- `ItemContainer.canRemoveMaterials(materials)` — **public**, dry-run check

## 4. giveOutput() — Simple Overload

```java
private static void giveOutput(Ref<EntityStore> ref, ComponentAccessor<EntityStore> componentAccessor, CraftingRecipe craftingRecipe, int quantity) {
    Player playerComponent = componentAccessor.getComponent(ref, Player.getComponentType());
    if (playerComponent == null) return;
    
    List<ItemStack> itemStacks = getOutputItemStacks(craftingRecipe, quantity);
    Inventory inventory = playerComponent.getInventory();
    SimpleItemContainer.addOrDropItemStacks(componentAccessor, ref, inventory.getCombinedArmorHotbarStorage(), itemStacks);
}
```

**Public API equivalent:**
- `CraftingManager.getOutputItemStacks(recipe, quantity)` — **public static**
- `SimpleItemContainer.addOrDropItemStacks(accessor, ref, container, itemStacks)` — **public static**

## 5. CraftRecipeEvent — Both Pre and Post are Cancellable

```java
public abstract class CraftRecipeEvent extends CancellableEcsEvent {
    private final CraftingRecipe craftedRecipe;
    private final int quantity;
    
    // Inner classes:
    public static final class Pre extends CraftRecipeEvent { ... }
    public static final class Post extends CraftRecipeEvent { ... }
}
```

**WARNING:** If `Post` is cancelled, `craftItem()` returns `true` but items were already consumed and output is NOT given. This is a potential item-duplication or item-loss vector in the original code.

## 6. CraftingRecipe — Key Fields

```java
public class CraftingRecipe {
    protected String id;
    protected MaterialQuantity[] input;            // getInput()
    protected MaterialQuantity[] outputs;           // getOutputs() — all outputs including primary
    protected MaterialQuantity primaryOutput;       // getPrimaryOutput()
    protected int primaryOutputQuantity;            // multiplier
    protected BenchRequirement[] benchRequirement;  // getBenchRequirement()
    protected float timeSeconds;                    // getTimeSeconds()
    protected boolean knowledgeRequired;            // isKnowledgeRequired()
    protected int requiredMemoriesLevel;            // getRequiredMemoriesLevel() (default 1 = always available)
}
```

## 7. setBench() Analysis — Can We Fake It?

```java
public void setBench(int x, int y, int z, BlockType blockType) {
    Bench bench = blockType.getBench();
    Objects.requireNonNull(bench, "blockType isn't a bench!");  // MUST have a Bench config
    if (bench.getType() != BenchType.Crafting
        && bench.getType() != BenchType.DiagramCrafting
        && bench.getType() != BenchType.StructuralCrafting
        && bench.getType() != BenchType.Processing) {
        throw new IllegalArgumentException("blockType isn't a crafting bench!");
    }
    if (this.blockType != null) throw new IllegalArgumentException("Bench blockType is already set!");
    if (!this.queuedCraftingJobs.isEmpty()) throw new IllegalArgumentException("Queue already has jobs!");
    if (this.upgradingJob != null) throw new IllegalArgumentException("Upgrading job is already set!");
    
    this.x = x;
    this.y = y;
    this.z = z;
    this.blockType = blockType;
}
```

### What setBench requires:

1. `blockType.getBench()` must be non-null
2. Bench type must be Crafting, DiagramCrafting, StructuralCrafting, or Processing
3. No bench currently set (`this.blockType == null`)
4. No queued jobs
5. No upgrading job

### The tier level problem:

In `isValidBenchForRecipe`:
```java
BlockState state = world.getState(this.x, this.y, this.z, true);
int benchTierLevel = state instanceof BenchState ? ((BenchState) state).getTierLevel() : 0;
```

If we call `setBench(0, 0, 0, someBlockType)`, the engine reads the **actual world block state** at (0,0,0). If there's no `BenchState` there, `benchTierLevel = 0`.

**This means:**
- Recipes with `requiredTierLevel = 0` → **PASS** ✅
- Recipes with `requiredTierLevel > 0` → **FAIL** ❌

---

## 8. Bypass Approaches — Ranked

### Approach A: Replicate Logic Manually (RECOMMENDED)

Skip `CraftingManager.craftItem()` entirely. All the building blocks are **public static**:

```java
// In your PortableBenchWindow.handleAction():

// 1. Get recipe
CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);

// 2. Fire Pre event (optional — for compatibility with other plugins)
CraftRecipeEvent.Pre preEvent = new CraftRecipeEvent.Pre(recipe, quantity);
componentAccessor.invoke(ref, preEvent);
if (preEvent.isCancelled()) return;

// 3. Check knowledge/memories yourself (copy the 2 checks from isValidBenchForRecipe)
// ... skip bench type/ID/tier check entirely ...

// 4. Remove inputs
List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(recipe, quantity);
ItemContainer container = player.getInventory().getCombinedBackpackStorageHotbar();
if (player.getGameMode() != GameMode.Creative) {
    if (!container.canRemoveMaterials(inputs)) {
        // Send notification, return
        return;
    }
    ListTransaction<MaterialTransaction> txn = container.removeMaterials(inputs, true, true, true);
    if (!txn.succeeded()) return;
}

// 5. Fire Post event
CraftRecipeEvent.Post postEvent = new CraftRecipeEvent.Post(recipe, quantity);
componentAccessor.invoke(ref, postEvent);
if (postEvent.isCancelled()) return; // Note: items already consumed — same as engine behavior

// 6. Give output
List<ItemStack> outputs = CraftingManager.getOutputItemStacks(recipe, quantity);
SimpleItemContainer.addOrDropItemStacks(componentAccessor, ref, 
    player.getInventory().getCombinedArmorHotbarStorage(), outputs);

// 7. Fire PlayerCraftEvent (deprecated but for compat)
IEventDispatcher<PlayerCraftEvent, PlayerCraftEvent> dispatcher = HytaleServer.get()
    .getEventBus().dispatchFor(PlayerCraftEvent.class, world.getName());
if (dispatcher.hasListener()) {
    dispatcher.dispatch(new PlayerCraftEvent(ref, player, recipe, quantity));
}
```

**Pros:**
- No bench block required
- No coordinate spoofing
- No tier level issues
- Full control over which checks to run
- Uses all public APIs

**Cons:**
- Must maintain if engine crafting logic changes
- Must manually replicate knowledge/memories checks if needed

### Approach B: setBench() + craftItem() + clearBench() Sandwich

```java
// Find the real bench BlockType for "Builders"
BlockType benchBlockType = /* find a BlockType where getBench().getId() == "Builders" */;

craftingManager.setBench(0, 0, 0, benchBlockType);
craftingManager.craftItem(ref, componentAccessor, recipe, quantity, container);
craftingManager.clearBench(ref, componentAccessor);
```

**Problems:**
1. **Tier level reads world state at (0,0,0)** — no BenchState there → tier = 0
2. Recipes requiring `requiredTierLevel > 0` fail
3. Block at (0,0,0) might not exist / might be in an unloaded chunk
4. `clearBench()` calls `cancelAllCrafting()` which tries to refund items
5. Fragile — depends on world state at arbitrary coordinates

**Verdict: NOT recommended.**

### Approach C: Use craftSimpleItem() from CraftingWindow

`CraftingWindow.craftSimpleItem()` is a **public static** convenience method:

```java
public static boolean craftSimpleItem(Store<EntityStore> store, Ref<EntityStore> ref, 
    CraftingManager craftingManager, CraftRecipeAction action) {
    CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(action.recipeId);
    craftingManager.craftItem(ref, store, recipe, action.quantity, 
        player.getInventory().getCombinedBackpackStorageHotbar());
    return true;
}
```

**Same problem** — still calls `craftingManager.craftItem()` which calls `isValidBenchForRecipe()`.

### Approach D: No Alternative Signatures Exist

There is:
- **No** `canCraft()` method separate from `craftItem()`
- **No** overload that skips bench validation
- **No** admin/command craft method
- **No** `forceCraft()` variant
- Only **one** `craftItem()` signature

---

## 9. Recommendation

**Use Approach A (manual replication)**. The engine provides all the building blocks as public static methods:

| Step | Public API |
|------|-----------|
| Get input materials | `CraftingManager.getInputMaterials(recipe, quantity)` |
| Check if player has materials | `ItemContainer.canRemoveMaterials(materials)` |
| Remove materials | `ItemContainer.removeMaterials(materials, true, true, true)` |
| Get output items | `CraftingManager.getOutputItemStacks(recipe, quantity)` |
| Give items to player | `SimpleItemContainer.addOrDropItemStacks(accessor, ref, container, items)` |
| Fire ECS events | `componentAccessor.invoke(ref, event)` |
| Fire global events | `HytaleServer.get().getEventBus().dispatchFor(...)` |

The bench check is the **only** thing you need to skip. Everything else can be replicated 1:1 using public APIs, matching the exact engine behavior.
