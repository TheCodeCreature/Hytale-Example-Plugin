---
topic: "Crafting Window Architecture — BenchWindow, Containers, and the OpenWindow Packet"
category: "Crafting / Windows"
updated: 2026-04-17
sources: ["BenchWindow.java", "CraftingWindow.java", "StructuralCraftingWindow.java", "SimpleCraftingWindow.java", "DiagramCraftingWindow.java", "FieldCraftingWindow.java", "BlockWindow.java", "Window.java", "WindowManager.java", "OpenWindow.java", "ItemContainerWindow.java", "MaterialContainerWindow.java", "MaterialExtraResourcesSection.java", "InventorySection.java", "SimpleItemContainer.java", "CombinedItemContainer.java", "ItemContainer.java", "WindowType.java"]
---

# Crafting Window Architecture

## 1. Class Hierarchy

```
Window (abstract)
├── FieldCraftingWindow                              WindowType.PocketCrafting
│     (extends Window directly — no block, no containers)
│
└── BlockWindow (abstract, implements ValidatedWindow)
      └── BenchWindow (abstract, implements MaterialContainerWindow)
            └── CraftingWindow (abstract)
                  ├── SimpleCraftingWindow             WindowType.BasicCrafting
                  │     (implements MaterialContainerWindow — redundant, already on BenchWindow)
                  │
                  ├── DiagramCraftingWindow             WindowType.DiagramCrafting
                  │     (implements ItemContainerWindow)
                  │
                  └── StructuralCraftingWindow          WindowType.StructuralCrafting
                        (implements ItemContainerWindow)
```

### Key interfaces:

| Interface | Method | Purpose |
|-----------|--------|---------|
| `ItemContainerWindow` | `getItemContainer() → ItemContainer` | Provides the `InventorySection` in the OpenWindow/UpdateWindow packet |
| `MaterialContainerWindow` | `getExtraResourcesSection() → MaterialExtraResourcesSection` | Provides `ExtraResources` (nearby chests) in the packet |
| `ValidatedWindow` | `validate(ref, store) → boolean` | Distance/block-existence check; auto-closes window if invalid |

---

## 2. WindowType Enum

```java
public enum WindowType {
    Container(0),           // Generic item container (chests, etc.)
    PocketCrafting(1),      // Flat recipe list + category tabs (FieldCraftingWindow)
    BasicCrafting(2),       // Simple bench (Toolsmith, etc.) — SimpleCraftingWindow
    DiagramCrafting(3),     // Grid/diagram bench (e.g. Tailor) — DiagramCraftingWindow
    StructuralCrafting(4),  // Builder's Bench (1 input → N variants) — StructuralCraftingWindow
    Processing(5),          // Furnace/smelter
    Memories(6)             // Adventure memories UI
}
```

**Critical**: `WindowType` determines which **client-side renderer** is instantiated. Each renderer has hardcoded expectations about what data must be present.

---

## 3. ItemContainerWindow — The Key to Material Slots

### Interface Definition

```java
public interface ItemContainerWindow {
    @Nonnull
    ItemContainer getItemContainer();
}
```

### How it's used by WindowManager

When `WindowManager.openWindow()` builds the `OpenWindow` packet, it checks:

```java
InventorySection section = null;
if (window instanceof ItemContainerWindow itemContainerWindow) {
    section = itemContainerWindow.getItemContainer().toPacket();
}
```

`ItemContainer.toPacket()` serializes the container into an `InventorySection`:
```java
public InventorySection toPacket() {
    InventorySection packet = new InventorySection();
    packet.capacity = this.getCapacity();     // e.g. 65 for StructuralCrafting
    packet.items = this.toProtocolMap();       // Map<Integer, ItemWithAllMetadata>
    return packet;
}
```

### Who implements ItemContainerWindow?

| Class | Container Structure | Capacity |
|-------|-------------------|----------|
| `StructuralCraftingWindow` | `CombinedItemContainer(inputContainer[1], optionsContainer[64])` | 65 |
| `DiagramCraftingWindow` | `CombinedItemContainer(combinedInput, outputContainer)` | Varies by diagram |

### Can you implement it without a block?

**Yes.** `ItemContainerWindow` is an interface — it has no dependency on blocks, `BlockWindow`, or `BenchWindow`. Any `Window` subclass can implement it. The containers (`SimpleItemContainer`, `CombinedItemContainer`) are fully in-memory.

```java
// Example: blockless window with material slots
public class MyWindow extends Window implements ItemContainerWindow {
    private final SimpleItemContainer inputContainer = new SimpleItemContainer((short)1);
    private final SimpleItemContainer optionsContainer = new SimpleItemContainer((short)64);
    private final CombinedItemContainer combined = new CombinedItemContainer(inputContainer, optionsContainer);

    @Override
    public ItemContainer getItemContainer() {
        return combined;
    }
}
```

The `WindowManager` will detect the `ItemContainerWindow` interface, call `toPacket()`, and include the `InventorySection` in the `OpenWindow` packet. It will also register a change event listener:

```java
// WindowManager.setWindow0()
if (window instanceof ItemContainerWindow itemContainerWindow) {
    ItemContainer itemContainer = itemContainerWindow.getItemContainer();
    this.windowChangeEvents.put(id, itemContainer.registerChangeEvent(
        EventPriority.LAST, e -> this.markWindowChanged(id)
    ));
}
```

This means any mutation to the container automatically marks the window dirty, triggering an `UpdateWindow` packet on the next tick.

---

## 4. MaterialContainerWindow — Nearby Chest Resources

### Interface Definition

```java
public interface MaterialContainerWindow {
    @Nonnull
    MaterialExtraResourcesSection getExtraResourcesSection();
    void invalidateExtraResources();
    boolean isValid();
}
```

### MaterialExtraResourcesSection

```java
public class MaterialExtraResourcesSection {
    private boolean valid;
    private ItemContainer itemContainer;        // Aggregated contents of nearby chests
    private ItemQuantity[] extraMaterials;       // Protocol-level material list

    public ExtraResources toPacket() {
        ExtraResources packet = new ExtraResources();
        packet.resources = this.extraMaterials;
        return packet;
    }
}
```

### How it's used by WindowManager

```java
ExtraResources extraResources = null;
if (window instanceof MaterialContainerWindow materialContainerWindow) {
    extraResources = materialContainerWindow.getExtraResourcesSection().toPacket();
}
```

### Can you skip it (send null ExtraResources)?

**Yes, conditionally.** The `OpenWindow` packet allows `null` for `extraResources` — it's a nullable field (bit 2 in the nullable bit field). The `WindowManager.openWindow()` only populates it if the window implements `MaterialContainerWindow`.

For the **PocketCrafting** renderer: `null` is fine — `FieldCraftingWindow` never sends it.

For the **StructuralCrafting** renderer: **Unknown.** The client may or may not dereference `ExtraResources`. Based on `BenchWindow` always implementing `MaterialContainerWindow`, the structural renderer likely expects it. However, you could provide an empty `MaterialExtraResourcesSection` with no materials.

For the **BasicCrafting** renderer: `SimpleCraftingWindow` implements `MaterialContainerWindow`, so the renderer likely expects it. However, crafting works even when `nearbyChestCount` is 0 (no nearby chests).

### Can you provide an empty one?

Yes. Create a `MaterialExtraResourcesSection`, set `extraMaterials` to an empty array, and mark it valid:

```java
MaterialExtraResourcesSection section = new MaterialExtraResourcesSection();
section.setExtraMaterials(new ItemQuantity[0]);
section.setValid(true);
```

---

## 5. The OpenWindow Packet

### Structure

```java
public class OpenWindow implements Packet {
    public static final int PACKET_ID = 200;
    public int id;                           // Window ID (auto-incremented by WindowManager)
    @Nonnull
    public WindowType windowType;            // Determines client renderer
    @Nullable
    public String windowData;                // JSON string with window configuration
    @Nullable
    public InventorySection inventory;       // Container slots (nullable, bit 1)
    @Nullable
    public ExtraResources extraResources;    // Nearby chest materials (nullable, bit 2)
}
```

### How WindowManager constructs it

```java
public OpenWindow openWindow(Ref<EntityStore> ref, Window window, Store<EntityStore> store) {
    int id = this.windowId.getAndUpdate(/* auto-increment */);
    this.setWindow(id, window);

    if (!window.onOpen(ref, store)) {
        this.closeWindow(ref, id, store);
        return null;
    }

    InventorySection section = null;
    if (window instanceof ItemContainerWindow icw) {
        section = icw.getItemContainer().toPacket();
    }

    ExtraResources extraResources = null;
    if (window instanceof MaterialContainerWindow mcw) {
        extraResources = mcw.getExtraResourcesSection().toPacket();
    }

    return new OpenWindow(id, window.getType(), window.getData().toString(), section, extraResources);
}
```

### Per-WindowType packet contents

| WindowType | InventorySection | ExtraResources | Source |
|------------|-----------------|----------------|--------|
| `PocketCrafting(1)` | `null` | `null` | `FieldCraftingWindow` |
| `BasicCrafting(2)` | `null` | `ExtraResources` | `SimpleCraftingWindow` (MaterialContainerWindow only) |
| `DiagramCrafting(3)` | `InventorySection` | `ExtraResources` | `DiagramCraftingWindow` (ItemContainerWindow + MaterialContainerWindow via BenchWindow) |
| `StructuralCrafting(4)` | `InventorySection` | `ExtraResources` | `StructuralCraftingWindow` (ItemContainerWindow + MaterialContainerWindow via BenchWindow) |

---

## 6. How StructuralCraftingWindow Works (Builder's Bench)

### Container Setup

```java
public StructuralCraftingWindow(BenchState benchState) {
    super(WindowType.StructuralCrafting, benchState);

    // 1 input slot — player places a block here
    this.inputContainer = new SimpleItemContainer((short)1);
    this.inputContainer.registerChangeEvent(e -> this.updateRecipes());
    this.inputContainer.setSlotFilter(FilterActionType.ADD, (short)0, this::isValidInput);

    // 64 output option slots — variants that can be crafted from the input
    this.optionsContainer = new SimpleItemContainer((short)64);
    this.optionsContainer.setGlobalFilter(FilterType.DENY_ALL);  // read-only for client

    // Combined = input + options as one container for the protocol
    this.combinedItemContainer = new CombinedItemContainer(this.inputContainer, this.optionsContainer);
}
```

### Input Validation

Only items that match at least one recipe are accepted into the input slot:

```java
private boolean isValidInput(FilterActionType type, ItemContainer container, short slot, ItemStack stack) {
    if (type != FilterActionType.ADD) return true;
    ObjectList<CraftingRecipe> matching = this.getMatchingRecipes(stack);
    return matching != null && !matching.isEmpty();
}
```

### Recipe Resolution (updateRecipes)

When the input slot changes, `updateRecipes()` fires:

```java
private void updateRecipes() {
    this.invalidate();
    this.optionsContainer.clear();
    this.optionSlotToRecipeMap.clear();

    ItemStack inputStack = this.inputContainer.getItemStack((short)0);
    ObjectList<CraftingRecipe> matchingRecipes = this.getMatchingRecipes(inputStack);

    if (matchingRecipes != null) {
        sortRecipes(matchingRecipes, (StructuralCraftingBench)this.bench);

        // Populate option slots with output items
        short index = 0;
        for (CraftingRecipe match : matchingRecipes) {
            for (BenchRequirement req : match.getBenchRequirement()) {
                if (req.type == bench.getType() && req.id.equals(bench.getId())) {
                    List<ItemStack> output = CraftingManager.getOutputItemStacks(match);
                    this.optionsContainer.setItemStackForSlot(index, output.getFirst(), false);
                    this.optionSlotToRecipeMap.put(index, match.getId());
                    index++;
                }
            }
        }

        // Also send recipe IDs array in JSON
        JsonArray optionSlotRecipes = new JsonArray();
        for (int i = 0; i < optionsContainer.getCapacity(); i++) {
            String recipeId = optionSlotToRecipeMap.get(i);
            if (recipeId != null) optionSlotRecipes.add(recipeId);
        }
        this.windowData.add("optionSlotRecipes", optionSlotRecipes);
    }
}
```

### Recipe Matching Logic

```java
private ObjectList<CraftingRecipe> getMatchingRecipes(ItemStack inputStack) {
    if (inputStack == null) return null;

    List<CraftingRecipe> recipes = CraftingPlugin.getBenchRecipes(bench.getType(), bench.getId());
    ObjectList<CraftingRecipe> matching = new ObjectArrayList<>();

    for (CraftingRecipe recipe : recipes) {
        List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(recipe);
        // Only matches recipes with exactly 1 input material
        if (inputs.size() == 1 && CraftingManager.matches(inputs.getFirst(), inputStack)) {
            matching.add(recipe);
        }
    }

    return matching.isEmpty() ? null : matching;
}
```

### Action Handling

```java
switch (action) {
    case SelectSlotAction selectAction:
        // Player clicks an option slot → update selected index
        this.selectedSlot = MathUtil.clamp(selectAction.slot, 0, optionsContainer.getCapacity());
        this.windowData.addProperty("selected", this.selectedSlot);
        this.invalidate();
        break;

    case CraftRecipeAction craftAction:
        // Player clicks craft → queue the recipe from the input container
        String recipeId = this.optionSlotToRecipeMap.get(this.selectedSlot);
        CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);
        craftingManager.queueCraft(ref, store, this, 0, recipe, quantity,
            this.inputContainer, CraftingManager.InputRemovalType.ORDERED);
        break;

    case ChangeBlockAction changeBlockAction:
        // Block group cycling (e.g., Oak Planks → Birch Planks)
        this.changeBlockType(ref, changeBlockAction.down, store);
        break;
}
```

### OnClose — Item Return

```java
public void onClose0(Ref<EntityStore> ref, ComponentAccessor<EntityStore> store) {
    super.onClose0(ref, store);
    // Return items from input slot to player's inventory
    List<ItemStack> items = this.inputContainer.dropAllItemStacks();
    SimpleItemContainer.addOrDropItemStacks(store, ref,
        player.getInventory().getCombinedHotbarFirst(), items);
    craftingManager.cancelAllCrafting(ref, store);
}
```

---

## 7. BenchWindow's Block Dependencies

`BenchWindow` extends `BlockWindow`, which requires:

```java
public BlockWindow(WindowType type, int x, int y, int z, int rotationIndex, BlockType blockType) {
    super(type);
    this.x = x; this.y = y; this.z = z;
    this.rotationIndex = rotationIndex;
    this.blockType = blockType;
}
```

And `BenchWindow` itself requires:

```java
public BenchWindow(WindowType type, BenchState benchState) {
    super(type, benchState.getBlockX(), benchState.getBlockY(), benchState.getBlockZ(),
          benchState.getRotationIndex(), benchState.getBlockType());
    this.bench = this.blockType.getBench();      // Bench config from block type
    this.benchState = benchState;
    Item item = this.blockType.getItem();
    // ... builds windowData from bench/item properties
}
```

**The block dependencies are:**
1. `BlockWindow.validate()` — checks distance to block position AND that the block still exists in the world
2. `BenchWindow.onOpen0()` — feeds `MaterialExtraResourcesSection` from nearby chests around the block
3. `BenchWindow` constructor — reads bench config, item name, tier from the block type
4. `CraftingWindow.onClose0()` — sets block interaction state (visual state on the block)

---

## 8. FieldCraftingWindow — The Blockless Precedent

`FieldCraftingWindow` is the vanilla example of a blockless crafting window:

```java
public class FieldCraftingWindow extends Window {
    public FieldCraftingWindow() {
        super(WindowType.PocketCrafting);
        windowData.addProperty("type", BenchType.Crafting.ordinal());   // 0
        windowData.addProperty("id", "Fieldcraft");
        windowData.addProperty("name", "server.ui.inventory.fieldcraft.title");
        // Build categories with flat recipe lists
    }
}
```

- Extends `Window` directly — no `BlockWindow`, no `BenchWindow`
- Does NOT implement `ItemContainerWindow` → no `InventorySection` sent
- Does NOT implement `MaterialContainerWindow` → no `ExtraResources` sent
- Uses `WindowType.PocketCrafting` → client renders flat recipe list with tabs
- Crafting deducts from player inventory via `CraftingWindow.craftSimpleItem()`

---

## 9. Feasibility Analysis: Blockless Builder's Bench

### Option A: PocketCrafting + ItemContainerWindow (Hybrid)

Create a `Window` subclass that implements `ItemContainerWindow` but uses `WindowType.PocketCrafting`:

```java
public class PortableStructuralWindow extends Window implements ItemContainerWindow {
    private final SimpleItemContainer inputContainer = new SimpleItemContainer((short)1);
    private final SimpleItemContainer optionsContainer = new SimpleItemContainer((short)64);
    private final CombinedItemContainer combined = new CombinedItemContainer(input, options);

    public PortableStructuralWindow() {
        super(WindowType.PocketCrafting);
    }

    @Override
    public ItemContainer getItemContainer() { return combined; }
}
```

**Outcome**: WindowManager will send the `InventorySection` in the packet. But the PocketCrafting client renderer likely **ignores** the InventorySection — it doesn't know how to render material slots. The recipe list still works, but you don't get the "place item → see variants" interaction.

**Risk**: Low (no crash), but the container data is wasted — the UI won't render input/output slots.

### Option B: StructuralCrafting + Full Container Implementation

Create a `Window` subclass with `WindowType.StructuralCrafting` that implements both `ItemContainerWindow` and `MaterialContainerWindow`:

```java
public class PortableStructuralWindow extends Window
    implements ItemContainerWindow, MaterialContainerWindow {

    private final SimpleItemContainer inputContainer = new SimpleItemContainer((short)1);
    private final SimpleItemContainer optionsContainer = new SimpleItemContainer((short)64);
    private final CombinedItemContainer combined = new CombinedItemContainer(input, options);
    private final MaterialExtraResourcesSection extraResources = new MaterialExtraResourcesSection();

    public PortableStructuralWindow() {
        super(WindowType.StructuralCrafting);
        extraResources.setExtraMaterials(new ItemQuantity[0]);
        extraResources.setValid(true);
        // Populate windowData with all required JSON fields...
    }

    @Override
    public ItemContainer getItemContainer() { return combined; }

    @Override
    public MaterialExtraResourcesSection getExtraResourcesSection() { return extraResources; }

    @Override
    public void invalidateExtraResources() { /* no-op for portable */ }

    @Override
    public boolean isValid() { return true; }
}
```

**Outcome**: The client receives `WindowType.StructuralCrafting` with valid `InventorySection` (65 slots) and `ExtraResources` (empty). The structural renderer should instantiate with the slot grid.

**Risks**:
1. The `windowData` JSON must include ALL fields the client expects (see Section 6 JSON shape). Missing fields may cause client errors.
2. The client's structural renderer may read `blockItemId` and try to look up a block texture — if this ID is wrong or missing, the UI may render incorrectly.
3. There may be client-side validation that checks for block position data in the JSON (e.g., `x`, `y`, `z` from `BlockWindow`). Unknown if the structural renderer uses this.
4. `BlockWindow.validate()` won't run (we don't extend BlockWindow), so there's no auto-close on distance. This is actually desirable for a portable bench.

**Required JSON shape** (all fields from the inheritance chain):

```json
{
    "type": 3,
    "id": "Builders",
    "name": "server.items.Bench_Builders.name",
    "blockItemId": "hytale:Bench_Builders",
    "tierLevel": 1,
    "worldMemoriesLevel": 0,
    "nearbyChestCount": 0,
    "maxChestCount": 0,
    "chestHorizontalRadius": 0,
    "chestVerticalRadius": 0,
    "categories": [ /* ... from CraftingBench config ... */ ],
    "memoriesPerLevel": [],
    "selected": 0,
    "allowBlockGroupCycling": true,
    "alwaysShowInventoryHints": false,
    "inventoryHints": [],
    "optionSlotRecipes": []
}
```

### Option C: PocketCrafting (Current Approach — No Material Slots)

Stay with `WindowType.PocketCrafting`, which shows a flat recipe list. No input slots, no variant selection.

**This is what the current `PortableBenchWindow` does.** It works but doesn't replicate the Builder's Bench interaction.

---

## 10. UpdateWindow — How Containers Stay Synced

When a container's contents change (e.g., player places item in input slot), the `WindowManager` sends an `UpdateWindow` packet:

```java
public void updateWindow(Window window) {
    InventorySection section = null;
    if (window instanceof ItemContainerWindow icw) {
        section = icw.getItemContainer().toPacket();
    }

    ExtraResources extraResources = null;
    if (window instanceof MaterialContainerWindow mcw && !mcw.isValid()) {
        extraResources = mcw.getExtraResourcesSection().toPacket();
    }

    playerRef.getPacketHandler().writeNoCache(
        new UpdateWindow(window.getId(), window.getData().toString(), section, extraResources)
    );
}
```

The `WindowManager.setWindow0()` auto-registers a change listener on `ItemContainerWindow` containers:

```java
if (window instanceof ItemContainerWindow icw) {
    ItemContainer container = icw.getItemContainer();
    this.windowChangeEvents.put(id, container.registerChangeEvent(
        EventPriority.LAST, e -> this.markWindowChanged(id)
    ));
}
```

This means `SimpleItemContainer` changes automatically propagate to the client without manual packet sending.

---

## 11. SimpleItemContainer — In-Memory Container

```java
public class SimpleItemContainer extends ItemContainer {
    protected short capacity;
    protected Short2ObjectMap<ItemStack> items;

    public SimpleItemContainer(short capacity) {
        this.capacity = capacity;
        this.items = new Short2ObjectOpenHashMap<>(capacity);
    }
}
```

Key features for portable use:
- Fully in-memory — no block or world dependency
- Supports slot filters: `setSlotFilter(FilterActionType.ADD, slot, filter)`
- Supports global filters: `setGlobalFilter(FilterType.DENY_ALL)` (makes container read-only)
- Auto-fires change events when items are added/removed
- Can be combined: `new CombinedItemContainer(container1, container2)` creates a unified view

---

## 12. Summary: What's Needed for a "Place Items, See Craftable" Window

To replicate the Builder's Bench behavior without a block:

| Requirement | How to satisfy |
|------------|----------------|
| Material input slot | `SimpleItemContainer(1)` with filter + change listener |
| Output variant display | `SimpleItemContainer(64)` with DENY_ALL filter |
| Combined container for protocol | `CombinedItemContainer(input, options)` |
| InventorySection in packet | Implement `ItemContainerWindow`, return combined container |
| ExtraResources in packet | Implement `MaterialContainerWindow` with empty resources, OR test if null is accepted |
| Client renderer | `WindowType.StructuralCrafting` for slot-based UI, `WindowType.PocketCrafting` for flat list |
| Recipe resolution | Reimplement `StructuralCraftingWindow.updateRecipes()` logic |
| Action handling | Handle `SelectSlotAction`, `CraftRecipeAction`, `ChangeBlockAction` |
| Item return on close | `inputContainer.dropAllItemStacks()` → return to player inventory |
| Window data JSON | Must include all fields the client renderer expects |

The `WindowManager` handles all packet serialization automatically if you implement the interfaces correctly. No manual packet construction needed.

## See Also
- [structural-window-crash-analysis.md](./structural-window-crash-analysis.md) — Why the original StructuralCrafting attempt crashed
- [portable-bench-feasibility.md](./portable-bench-feasibility.md) — Broader feasibility analysis
- [recipes.md](./recipes.md) — CraftingRecipe format and MaterialQuantity
