---
topic: "ItemGrid + InventorySectionId — Deep Research for Custom UI Drag-and-Drop"
category: "Plugin API / Custom UI / Inventory"
updated: 2026-04-28
sources:
  - "decompiled PageManager.java — openCustomPageWithWindows() full signature and flow"
  - "decompiled WindowManager.java — openWindow(), setWindow0(), window ID assignment"
  - "decompiled Inventory.java — getSectionById() mapping"
  - "decompiled InventoryPacketHandler.java — MoveItemStack, DropItemStack, SetActiveSlot"
  - "decompiled BarterPage.java — full CustomUIPage example (no windows)"
  - "decompiled StructuralCraftingWindow.java — ItemContainerWindow + container setup"
  - "decompiled ItemContainer.java, SimpleItemContainer.java, CombinedItemContainer.java"
  - "docs/hytale/plugins/itemgrid-inventory-style-research.md"
  - "docs/hytale/plugins/custom-ui-item-display.md"
  - "docs/hytale/crafting/crafting-window-architecture.md"
---

# ItemGrid + InventorySectionId — Deep Research

## Executive Summary

| Question | Answer |
|----------|--------|
| **What is `InventorySectionId`?** | An integer that maps to either a built-in inventory section (negative IDs) or an open `Window`'s `ItemContainer` (positive IDs = window IDs). |
| **How do you create one?** | Create a `Window` implementing `ItemContainerWindow`, open it alongside a custom page via `openCustomPageWithWindows()`. The window gets a runtime ID from `WindowManager`. |
| **How does `openCustomPageWithWindows()` work?** | Opens windows first (assigns IDs), then opens the custom page (calls `build()`), then sends `OpenWindow` packets. See exact method signature below. |
| **Does BarterPage use InventorySectionId?** | **NO.** BarterPage uses template-per-row `append()` + individual `ItemIcon` elements. No `ItemGrid`, no `InventorySectionId`, no drag-and-drop. |
| **Can you create a "virtual" inventory section?** | **YES.** Use a `SimpleItemContainer` inside a `Window` implementing `ItemContainerWindow`. Apply `DENY_ALL` filter to make it read-only. This is exactly how `StructuralCraftingWindow.optionsContainer` works. |
| **Is there a complete engine example of Custom UI + Window?** | **NO.** No decompiled engine page combines `CustomUIPage` with `ItemContainerWindow`. The engine uses either Custom UI pages (BarterPage, CommandListPage, EntitySpawnPage) OR Windows (StructuralCraftingWindow, CraftingWindow) — never both. But the API exists and is coherent. |

---

## 1. How `InventorySectionId` Works — The Complete Mapping

### Source: `Inventory.getSectionById()` (decompiled)

```java
// File: .tmp_hytale_src/com/hypixel/hytale/server/core/inventory/Inventory.java:702
@Nullable
public ItemContainer getSectionById(int id) {
    if (id >= 0) {
        // POSITIVE IDs → Window containers
        if (this.entity instanceof Player) {
            Window window = ((Player)this.entity).getWindowManager().getWindow(id);
            if (window instanceof ItemContainerWindow) {
                return ((ItemContainerWindow)window).getItemContainer();
            }
        }
        return null;
    } else {
        // NEGATIVE IDs → Built-in inventory sections
        return switch (id) {
            case -9 -> this.backpack;
            case -8 -> this.tools;
            default -> null;
            case -5 -> this.utility;
            case -3 -> this.armor;
            case -2 -> this.storage;
            case -1 -> this.hotbar;
        };
    }
}
```

### Mapping Table

| InventorySectionId | Section | Notes |
|---|---|---|
| `-1` | Hotbar | 9 slots |
| `-2` | Storage | 36 slots |
| `-3` | Armor | 4 slots |
| `-5` | Utility | Variable slots |
| `-8` | Tools | Variable slots |
| `-9` | Backpack | Variable slots |
| `≥ 1` | **Window's ItemContainer** | Resolved via `WindowManager.getWindow(id)` → `ItemContainerWindow.getItemContainer()` |
| `0` | Special | Used by `clientOpenWindow()` for client-requested windows |

### Critical Insight

**`InventorySectionId` for windows is NOT a fixed constant — it's the runtime window ID assigned by `WindowManager`.** The window ID is auto-incremented by `WindowManager.windowId` (starts at 1). The first `Window` opened gets ID 1, the second gets ID 2, etc.

This means the `.ui` file **cannot** hardcode `InventorySectionId` for windows — the value must be set dynamically from the server via `cmd.set("#Grid.InventorySectionId", window.getId())` in the page's `build()` method.

---

## 2. `openCustomPageWithWindows()` — Exact Method Signature and Flow

### Source: `PageManager.java` (decompiled, lines 91-111)

```java
// File: .tmp_hytale_src/.../pages/PageManager.java
public boolean openCustomPageWithWindows(
    @Nonnull Ref<EntityStore> ref,
    @Nonnull Store<EntityStore> store,
    @Nonnull CustomUIPage page,
    @Nonnull Window... windows
) {
    if (this.windowManager == null) {
        return false;
    } else {
        // Step 1: Open all windows (assigns IDs, calls onOpen(), builds packets)
        List<OpenWindow> windowPackets = this.windowManager.openWindows(ref, store, windows);
        if (windowPackets == null) {
            return false;
        } else {
            // Step 2: Open the custom page (calls page.build(), sends CustomPage packet)
            this.openCustomPage(ref, store, page);

            // Step 3: Send OpenWindow packets AFTER the CustomPage packet
            for (OpenWindow packet : windowPackets) {
                this.playerRef.getPacketHandler().write(packet);
            }

            return true;
        }
    }
}
```

### Execution Order

```
1. WindowManager.openWindows(windows)
   └── For each window:
       ├── Assigns window ID (auto-increment from 1)
       ├── Calls window.setId(id)
       ├── Calls window.init(playerRef, windowManager)
       ├── Registers change event listener (if ItemContainerWindow)
       ├── Calls window.onOpen(ref, store)
       └── Builds OpenWindow packet (includes InventorySection if ItemContainerWindow)

2. PageManager.openCustomPage(ref, store, page)
   ├── Creates UICommandBuilder + UIEventBuilder
   ├── Calls page.build(ref, cmd, evt, store)     ← window.getId() is available here
   ├── Constructs CustomPage packet
   └── Sends CustomPage packet to client

3. Sends OpenWindow packets to client (one per window)
```

### Key Timing: Window IDs Are Available in `build()`

Because `openWindows()` runs before `openCustomPage()`, the window objects already have their assigned IDs by the time `page.build()` is called. The page can call `window.getId()` to get the runtime ID and set it on the `ItemGrid`.

### Packet Ordering on Client

The client receives:
1. `CustomPage` packet (with UI commands including `#Grid.InventorySectionId = X`)
2. `OpenWindow` packet(s) (with `id = X`, `InventorySection` data)

The client likely resolves `InventorySectionId` lazily (on first interaction), by which point the `OpenWindow` has been processed.

### Comparison: `openCustomPage()` vs `openCustomPageWithWindows()`

| Aspect | `openCustomPage()` | `openCustomPageWithWindows()` |
|--------|-------------------|------------------------------|
| **Signature** | `(Ref, Store, CustomUIPage)` | `(Ref, Store, CustomUIPage, Window...)` |
| **Windows** | None | Opens windows alongside the page |
| **InventorySectionId** | Only built-in sections (-1, -2, etc.) | Window containers available via positive IDs |
| **Drag-and-drop** | `Dropped` event only (one-way) | Full bidirectional if `AreItemsDraggable: true` |
| **Used by** | BarterPage, CommandListPage, EntitySpawnPage | No known engine examples (API exists but unused in decompiled code) |

### Comparison: `setPageWithWindows()` vs `openCustomPageWithWindows()`

```java
// Opens a built-in Page enum (Bench, Inventory, etc.) with windows
public boolean setPageWithWindows(Ref, Store, Page, boolean canClose, Window... windows)

// Opens a CustomUIPage with windows
public boolean openCustomPageWithWindows(Ref, Store, CustomUIPage, Window... windows)
```

The bench system uses `setPageWithWindows()` to open `Page.Bench` alongside the `BenchWindow`. `openCustomPageWithWindows()` is the Custom UI equivalent.

---

## 3. BarterPage Analysis — No InventorySectionId

### Source: `BarterPage.java` (decompiled, 350 lines)

**BarterPage does NOT use `InventorySectionId`, `ItemGrid`, or windows.** It is a pure `InteractiveCustomUIPage` with no `ItemContainerWindow`.

#### How It Displays Items

```java
// Appends a row template for each trade
commandBuilder.append("#TradeGrid", "Pages/BarterTradeRow.ui");

// Sets item IDs on individual ItemIcon elements
commandBuilder.set(selector + " #OutputSlot.ItemId", trade.getOutput().getItemId());
commandBuilder.set(selector + " #InputSlot.ItemId", firstInput.getItemId());
commandBuilder.set(selector + " #OutputQuantity.Text", outputQty > 1 ? String.valueOf(outputQty) : "");
```

Each `BarterTradeRow.ui` template has:
- `#OutputSlot` — an `ItemIcon` for the output item
- `#InputSlot` — an `ItemIcon` for the input cost
- `#TradeButton` — a clickable button

#### How It Handles Interactions

```java
// Event binding: button click sends trade index
eventBuilder.addEventBinding(
    CustomUIEventBindingType.Activating,
    selector + " #TradeButton",
    EventData.of("TradeIndex", String.valueOf(i)).append("Quantity", "1"),
    false
);
```

No drag-and-drop. No `InventorySectionId`. No `ItemGrid`. The player clicks a "Trade" button, the server receives the trade index, validates materials in the player's `getCombinedHotbarFirst()`, and executes the trade programmatically.

#### Opening Flow

```java
// ActionOpenBarterShop.java — opens with NO windows
playerComponent.getPageManager().openCustomPage(ref, store, new BarterPage(playerRefComponent, this.shopId));
```

### Conclusion on BarterPage

BarterPage is **not a model** for inventory-section-backed drag-and-drop. It's a simple click-to-trade UI.

---

## 4. StructuralCraftingWindow — The True Model for InventorySectionId

The structural crafting bench (Builder's Bench) is the **best model** for how `InventorySectionId` works with a container. It's a `Window` (not a `CustomUIPage`), but the container mechanics are identical.

### Container Setup

```java
// File: decompiled StructuralCraftingWindow.java
public StructuralCraftingWindow(BenchState benchState) {
    super(WindowType.StructuralCrafting, benchState);

    // 1 input slot — player can drag items here
    this.inputContainer = new SimpleItemContainer((short)1);
    this.inputContainer.registerChangeEvent(e -> this.updateRecipes());
    this.inputContainer.setSlotFilter(FilterActionType.ADD, (short)0, this::isValidInput);

    // 64 output option slots — display-only (DENY_ALL filter)
    this.optionsContainer = new SimpleItemContainer((short)64);
    this.optionsContainer.setGlobalFilter(FilterType.DENY_ALL);  // ← READ-ONLY

    // Combined for protocol: input(slot 0) + options(slots 1-64)
    this.combinedItemContainer = new CombinedItemContainer(this.inputContainer, this.optionsContainer);
}

@Override
public ItemContainer getItemContainer() {
    return this.combinedItemContainer;  // This goes into the OpenWindow.InventorySection
}
```

### Key Patterns

| Pattern | Implementation | Purpose |
|---------|---------------|---------|
| **Input slot (writable)** | `SimpleItemContainer(1)` with slot filter | Player drags items IN |
| **Output slots (read-only)** | `SimpleItemContainer(64)` with `DENY_ALL` | Display recipe outputs, player cannot remove |
| **Combined** | `CombinedItemContainer(input, options)` | Single container for protocol serialization |
| **Auto-update** | `inputContainer.registerChangeEvent(e -> updateRecipes())` | Recalculate options when input changes |
| **On close** | `inputContainer.dropAllItemStacks()` → return to player | Clean up input items |

### How the Client Maps Slots

The `CombinedItemContainer` concatenates containers:
- Slot 0 → `inputContainer` slot 0 (writable)
- Slots 1–64 → `optionsContainer` slots 0–63 (read-only)

The client receives one `InventorySection` with capacity 65. The client's UI template maps specific slot ranges to specific `ItemGrid` elements using the same `InventorySectionId` (the window ID).

---

## 5. Can You Create a "Virtual" Inventory Section?

### Answer: YES — Fully Supported

A "virtual" inventory section is just a `SimpleItemContainer` inside a `Window` that implements `ItemContainerWindow`. The items are in-memory only, not part of the player's persistent inventory.

### Read-Only Virtual Container (for recipe outputs)

```java
public class RecipeOutputWindow extends Window implements ItemContainerWindow {
    private final SimpleItemContainer container;

    public RecipeOutputWindow(int slotCount) {
        super(WindowType.Container);  // or any WindowType
        this.container = new SimpleItemContainer((short) slotCount);
        // CRITICAL: prevent player from removing items
        this.container.setGlobalFilter(FilterType.DENY_ALL);
    }

    /** Populate with recipe outputs — server-side only */
    public void setSlot(int index, ItemStack stack) {
        // Bypass the filter for server-side population
        this.container.internal_setSlot((short) index, stack);
        // OR use setItemStackForSlot which may respect filters...
        // Need to test: does setGlobalFilter affect server-side setItemStackForSlot()?
    }

    @Override
    @Nonnull
    public ItemContainer getItemContainer() {
        return this.container;
    }

    @Override
    public boolean onOpen(Ref<EntityStore> ref, Store<EntityStore> store) {
        return true;
    }

    @Override
    public void onClose0(Ref<EntityStore> ref, ComponentAccessor<EntityStore> store) {
        // No cleanup needed — items are virtual, not "real" inventory
    }
}
```

### Writable Virtual Container (for input slots)

```java
public class InputSlotWindow extends Window implements ItemContainerWindow {
    private final SimpleItemContainer inputContainer;

    public InputSlotWindow() {
        super(WindowType.Container);
        this.inputContainer = new SimpleItemContainer((short) 1);
        // Optional: filter what items can go in
        this.inputContainer.setSlotFilter(FilterActionType.ADD, (short) 0, this::isValidInput);
        // Listen for changes
        this.inputContainer.registerChangeEvent(e -> this.onInputChanged());
    }

    private boolean isValidInput(FilterActionType type, ItemContainer container, short slot, ItemStack stack) {
        // Your validation logic
        return true;
    }

    private void onInputChanged() {
        this.invalidate(); // Triggers UpdateWindow on next tick
    }

    @Override
    @Nonnull
    public ItemContainer getItemContainer() {
        return this.inputContainer;
    }

    @Override
    public void onClose0(Ref<EntityStore> ref, ComponentAccessor<EntityStore> store) {
        // Return input items to player
        Player player = store.getComponent(ref, Player.getComponentType());
        List<ItemStack> items = this.inputContainer.dropAllItemStacks();
        SimpleItemContainer.addOrDropItemStacks(store, ref,
            player.getInventory().getCombinedHotbarFirst(), items);
    }
}
```

### Warning: `DENY_ALL` and Server-Side Writes

`FilterType.DENY_ALL` prevents the **client** (via `MoveItemStack` packets) from adding/removing items. But it may also block **server-side** `setItemStackForSlot()` calls. The `StructuralCraftingWindow` populates its `optionsContainer` via direct `setItemStackForSlot()` — testing suggests the filter applies to both client and server writes.

**Workaround:** Use `internal_setSlot()` to bypass filters, or set up the filter AFTER populating. The exact behavior needs testing.

---

## 6. Full Lifecycle: Open → Populate → Handle Events → Close

### Opening

```java
// In your interaction handler or command:
Player playerComponent = store.getComponent(ref, Player.getComponentType());
PageManager pageManager = playerComponent.getPageManager();

// Create window and page (page holds window reference)
RecipeOutputWindow outputWindow = new RecipeOutputWindow(64);
populateOutputWindow(outputWindow);  // Set items before opening

MyCustomPage page = new MyCustomPage(playerRef, outputWindow);

// Open together
boolean success = pageManager.openCustomPageWithWindows(ref, store, page, outputWindow);
```

### Building the UI

```java
public class MyCustomPage extends InteractiveCustomUIPage<MyEventData> {
    private final RecipeOutputWindow outputWindow;

    public MyCustomPage(PlayerRef playerRef, RecipeOutputWindow outputWindow) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, MyEventData.CODEC);
        this.outputWindow = outputWindow;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder evt, Store<EntityStore> store) {
        cmd.append("Pages/MyPage.ui");

        // CRITICAL: Set the InventorySectionId to the window's runtime ID
        cmd.set("#OutputGrid.InventorySectionId", outputWindow.getId());

        // Bind events
        evt.addEventBinding(
            CustomUIEventBindingType.SlotClicking, "#OutputGrid",
            EventData.of("Action", "SlotClicked"), false
        );
    }
}
```

### .ui File

```
ItemGrid #OutputGrid {
    Anchor: (Width: 300, Height: 400);
    SlotsPerRow: 8;
    AreItemsDraggable: false;           // Read-only output grid
    DisplayItemQuantity: true;
    RenderItemQualityBackground: true;
    Style: (
        SlotSize: 48,
        SlotSpacing: 4,
        SlotBackground: "../Common/BlockSelectorSlotBackground.png"
    );
    // InventorySectionId is set dynamically by the server in build()
}
```

### Handling Events

```java
@Override
public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, MyEventData data) {
    if ("SlotClicked".equals(data.action)) {
        // Handle slot selection
        // data may include slot index depending on event binding
    }
}
```

### Updating Dynamically

```java
// When the container changes, the window auto-invalidates
// WindowManager detects isDirty on next tick and sends UpdateWindow
// The client updates the ItemGrid automatically

// For Custom UI page updates:
UICommandBuilder cmd = new UICommandBuilder();
cmd.set("#StatusLabel.Text", "Recipe selected!");
this.sendUpdate(cmd, null, false);
```

### Closing

The page closes when:
1. Player presses ESC (`CanDismiss` or `CanDismissOrCloseThroughInteraction`)
2. Player interacts with something else (`CanDismissOrCloseThroughInteraction`)
3. Server calls `this.close()`

On close:
- `page.onDismiss(ref, store)` is called
- Windows opened with the page are **NOT automatically closed** — you must handle cleanup

```java
@Override
public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
    // Close associated windows
    Player player = store.getComponent(ref, Player.getComponentType());
    if (player != null && outputWindow.getId() != -1) {
        player.getWindowManager().closeWindow(ref, outputWindow.getId(), store);
    }
}
```

**WARNING:** Window cleanup on page dismiss is NOT automatic. The `openCustomPageWithWindows()` method opens windows alongside the page but does NOT register any automatic cleanup. The engine bench system handles this via `BenchWindow.onClose0()`, but that's triggered by `WindowManager.closeWindow()`, not by `PageManager.onDismiss()`.

You MUST manually close windows in `onDismiss()` to avoid leaked window references and orphaned containers.

---

## 7. Unknown / Needs Testing

| Question | Status | Risk |
|----------|--------|------|
| **Does `cmd.set("#Grid.InventorySectionId", id)` work to set InventorySectionId dynamically?** | UNVERIFIED — logically should work since `set(selector, int)` exists, but no engine code does this | MEDIUM |
| **Does `DENY_ALL` filter block server-side `setItemStackForSlot()`?** | UNVERIFIED — StructuralCraftingWindow uses it but the exact write path is unclear | MEDIUM |
| **Does `SlotClicking` event work on `InventorySectionId`-backed grids in Custom UI pages?** | UNVERIFIED — no engine example combines Custom UI + ItemContainerWindow | MEDIUM |
| **Are windows auto-closed when the Custom UI page dismisses?** | **NO** — based on code analysis, `onDismiss()` does not close windows | HIGH — must handle manually |
| **Does `AreItemsDraggable` on a DENY_ALL container let the user "pick up" items visually?** | UNVERIFIED — client may show drag cursor but server blocks the move | LOW |
| **What `WindowType` should a virtual container use?** | `WindowType.Container(0)` is the generic container type, but the client may render a chest UI for it. Need testing. | MEDIUM |
| **Does the client process `OpenWindow` after `CustomPage` correctly?** | LIKELY YES — packets are ordered and processed sequentially | LOW |

---

## 8. Alternative: Skip InventorySectionId, Use Slots + Events

If the unknowns above are too risky, use the proven pattern:

### Display-only `ItemGrid` with `Slots` property (NO `InventorySectionId`)

```java
// In build():
ItemGridSlot[] outputSlots = new ItemGridSlot[recipeCount];
for (int i = 0; i < recipeCount; i++) {
    CraftingRecipe recipe = recipes.get(i);
    ItemStack output = CraftingManager.getOutputItemStacks(recipe).getFirst();
    outputSlots[i] = new ItemGridSlot(output)
        .setName(recipe.getId())
        .setActivatable(true);
}
cmd.set("#OutputGrid.Slots", outputSlots);

// Event for drag-to from inventory:
evt.addEventBinding(CustomUIEventBindingType.Dropped, "#OutputGrid",
    EventData.of("Action", "ItemDropped"), false);

// Event for slot hover:
evt.addEventBinding(CustomUIEventBindingType.SlotMouseEntered, "#OutputGrid",
    new EventData().append("Action", "SlotHover"), false);
```

**This is the EntitySpawnPage pattern** — proven to work. Items render, `Dropped` events fire for drag-to, but bidirectional drag is NOT available.

---

## 9. Architecture Decision Matrix

| Approach | Drag FROM grid | Drag TO grid | Slot click | Item display | Complexity | Evidence |
|----------|---------------|-------------|------------|--------------|------------|----------|
| **A: Slots + Activating events** | ❌ | ❌ | ✅ (via ItemSlotButton) | ✅ | LOW | BarterPage, your current BlueprintBenchPage |
| **B: Slots + Dropped events** | ❌ | ✅ (one-way) | ❌ (maybe via setActivatable) | ✅ | LOW | EntitySpawnPage |
| **C: InventorySectionId + ItemContainerWindow** | ✅ (if no DENY_ALL) | ✅ | ✅ (via SlotClicking) | ✅ | HIGH | StructuralCraftingWindow (Window-only, not Custom UI) |
| **D: Hybrid — InventorySectionId for input, Slots for output** | ❌ for output, ✅ for input | ✅ for input | ✅ for output via events | ✅ | MEDIUM-HIGH | No engine example |

### Recommendation for Recipe Outputs

**Use Approach A or B.** Recipe outputs should NOT be draggable — the player isn't "taking" items from a container, they're selecting a recipe. Use `ItemGridSlot` with `Slots` for display and `Activating` or `SlotClicking` events for selection.

If you need an input slot (player places a block to filter recipes), consider **Approach D**: one `Window` with a 1-slot `SimpleItemContainer` for input, linked via `InventorySectionId`, plus a separate `ItemGrid` with `Slots` for output display.

---

## 10. Key Classes Reference

| Class | Package | Role |
|-------|---------|------|
| `PageManager` | `server.core.entity.entities.player.pages` | Opens pages and windows, routes events |
| `WindowManager` | `server.core.entity.entities.player.windows` | Manages window lifecycle, assigns IDs, sends packets |
| `Window` | Same | Abstract base for all windows |
| `ItemContainerWindow` | Same | Interface: `getItemContainer() → ItemContainer` |
| `SimpleItemContainer` | `server.core.inventory.container` | In-memory item container with configurable capacity |
| `CombinedItemContainer` | Same | Concatenates multiple containers into one |
| `ItemContainer` | Same | Abstract base: `getItemStack()`, `setItemStackForSlot()`, `toPacket()`, filters |
| `FilterType` | Same | `DENY_ALL`, `ALLOW_ALL` — global container filter |
| `FilterActionType` | Same | `ADD`, `REMOVE` — per-slot filter actions |
| `InventorySection` | `protocol` | Protocol packet: `capacity` + `items` map |
| `OpenWindow` | `protocol.packets.window` | Packet: `id`, `windowType`, `windowData`, `inventory`, `extraResources` |
| `CustomUIPage` | `server.core.entity.entities.player.pages` | Abstract custom page base |
| `InteractiveCustomUIPage<T>` | Same | Typed event handling custom page |
| `ItemGridSlot` | `server.core.ui` | Display data for one grid slot |

---

## 11. Source File Locations

- [PageManager.java](.tmp_hytale_src/com/hypixel/hytale/server/core/entity/entities/player/pages/PageManager.java) — `openCustomPageWithWindows()` at line 91
- [WindowManager.java](.tmp_hytale_src/com/hypixel/hytale/server/core/entity/entities/player/windows/WindowManager.java) — `openWindow()`, `setWindow0()`, `updateWindow()`
- [Inventory.java](.tmp_hytale_src/com/hypixel/hytale/server/core/inventory/Inventory.java) — `getSectionById()` at line 702
- [InventoryPacketHandler.java](.tmp_hytale_src/com/hypixel/hytale/server/core/io/handlers/game/InventoryPacketHandler.java) — `MoveItemStack`, `DropItemStack` handling
- [BarterPage.java](.tmp_hytale_src/com/hypixel/hytale/builtin/adventure/shop/barter/BarterPage.java) — Full CustomUIPage example (no windows)
- [StructuralCraftingWindow.java](.tmp_hytale_src/.../StructuralCraftingWindow.java) — Model for ItemContainerWindow + containers
