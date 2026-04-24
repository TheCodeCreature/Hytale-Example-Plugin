---
topic: "Custom Crafting/Selection UI Options for Plugins"
category: "Plugin API / UI"
updated: 2026-04-23
sources: ["decompiled source: CustomUIPage.java", "InteractiveCustomUIPage.java", "PageManager.java", "StructuralCraftingWindow.java", "WarpListPage.java", "PluginListPage.java", "RespawnPage.java", "PortalDeviceSummonPage.java", "CommandListPage.java", "ChoiceBasePage.java", "WindowType.java", "Page.java", "UICommandBuilder.java", "UIEventBuilder.java"]
---

# Custom Crafting/Selection UI Options in Hytale

## Summary

There are **five viable approaches** for creating a custom crafting/selection UI in Hytale. The most promising is the **Custom UI Page system** (`CustomUIPage` / `InteractiveCustomUIPage`), which is a fully server-driven, event-capable UI framework already used by multiple engine features. Below is the complete analysis of each option.

---

## Option 1: Custom UI Page System (CustomUIPage)

### Feasibility: HIGH ⭐ RECOMMENDED

### How It Works

Hytale has a complete server-to-client custom UI framework built around these classes:

```
CustomUIPage (abstract base)
└── InteractiveCustomUIPage<T> (typed event handling)
    └── Your page class
```

**Server** builds a UI declaratively via `UICommandBuilder` (layout) and `UIEventBuilder` (event bindings), sends it to the client as a `CustomPage` packet. **Client** renders UI from `.ui` template files, fires events back as `CustomPageEvent` packets. **Server** handles events via typed codec deserialization.

### Key Classes

| Class | Location | Purpose |
|-------|----------|---------|
| `CustomUIPage` | `server.core.entity.entities.player.pages` | Abstract base for custom pages |
| `InteractiveCustomUIPage<T>` | Same package | Adds typed event data handling via `BuilderCodec<T>` |
| `PageManager` | Same package | Opens/closes pages, handles events |
| `UICommandBuilder` | `server.core.ui.builder` | Builds UI layout commands |
| `UIEventBuilder` | Same package | Registers event bindings |
| `EventData` | Same package | Key-value data sent with events |

### Opening a Custom Page

```java
// From any context with access to Player component:
Player player = store.getComponent(ref, Player.getComponentType());
player.getPageManager().openCustomPage(ref, store, new MyBlueprintPage(playerRef));

// Or with associated windows (e.g. item containers):
player.getPageManager().openCustomPageWithWindows(ref, store, page, window1, window2);
```

### UICommandBuilder API

| Method | Purpose | Example |
|--------|---------|---------|
| `append(path)` | Load a `.ui` template file | `append("Pages/MyPage.ui")` |
| `append(selector, path)` | Append child from template | `append("#List", "Pages/ListItem.ui")` |
| `appendInline(selector, doc)` | Inline UI markup (no file needed) | `appendInline("#List", "Label { Text: Hello; }")` |
| `set(selector, value)` | Set property on element | `set("#Title.Text", "Blueprint Bench")` |
| `clear(selector)` | Clear children | `clear("#ItemList")` |
| `remove(selector)` | Remove element | `remove("#OldElement")` |
| `setNull(selector)` | Set null | — |

Supported value types: `String`, `boolean`, `float`, `int`, `double`, `Message`, `Value<T>` references, arrays, `ItemGridSlot`, `DropdownEntryInfo`, `ItemStack`, etc.

### UIEventBuilder API

Event binding types (`CustomUIEventBindingType`):
- `Activating` — button click
- `RightClicking` — right click
- `DoubleClicking` — double click
- `MouseEntered` / `MouseExited` — hover
- `ValueChanged` — input/checkbox change
- `ElementReordered` — drag reorder
- `SlotClicking` / `SlotDoubleClicking` — item slot interactions
- `SlotMouseEntered` / `SlotMouseExited` — slot hover
- `DragCancelled`, `Dropped` — drag and drop
- `SelectedTabChanged` — tab changes
- `KeyDown`, `FocusGained`, `FocusLost`, `Validating`, `Dismissing`

### CustomPageLifetime

| Value | Behavior |
|-------|----------|
| `CantClose` | Player cannot close (e.g., death screen) |
| `CanDismiss` | Player can press ESC to close |
| `CanDismissOrCloseThroughInteraction` | Close via ESC or interaction |

### Real Engine Examples

1. **WarpListPage** — List with search, button click events, dynamic updates
2. **PluginListPage** — List with checkboxes, selection highlighting, detail panel
3. **RespawnPage** — Non-dismissable page with action button, item display
4. **PortalDeviceSummonPage** — Rich page with artwork, text, confirm button, hover effects
5. **CommandListPage** — Multi-panel page with search, breadcrumb navigation, subcommand details
6. **ItemRepairPage** (via ChoiceBasePage) — Choice list with item icons, requirements checking

### Critical Constraint: `.ui` Template Files

The `append(path)` commands reference client-side `.ui` files (e.g., `"Pages/WarpListPage.ui"`). These files must exist on the client. Options:

1. **Use `appendInline()`**: Some UI can be built entirely inline without `.ui` files. The inline syntax is Hytale's UI markup language (e.g., `"Label { Text: Hello; Style: (Alignment: Center); }"`). Observed in engine code but limited examples.

2. **Reuse existing `.ui` templates**: Reference templates that ship with the game (e.g., `"Pages/WarpEntryButton.ui"`, `"Pages/PluginListButton.ui"`, `"Pages/DroppedItemSlot.ui"`). These have `#ItemIcon`, `#Label`, `#Button` elements we can reuse.

3. **Ship `.ui` files with the mod**: If Hytale supports modded asset loading for UI files, we could ship custom templates. Needs testing.

### Blueprint Bench Application

```java
public class BlueprintSelectionPage extends InteractiveCustomUIPage<BlueprintSelectionPage.EventData> {
    // build() → show list of matching recipes
    // handleDataEvent() → receive "Assign" action with selected recipeId
    // No crafting, no resource consumption — just assignment
}
```

### Risks
- `.ui` template availability — we need either inline-only UI or reusable existing templates
- UI styling may be limited without custom templates
- No known examples of plugins shipping `.ui` files (untested path)
- The inline markup syntax (`"Label { Text: ...; Style: (...); }"`) is sparsely documented

### Verdict
**This fully solves the "Assign not Craft" problem.** The server receives custom events (not `CraftRecipeAction`), so there's zero coupling to the crafting system. We control the label, the behavior, everything.

---

## Option 2: Hijack StructuralCraftingWindow (Server-Side Interception)

### Feasibility: MEDIUM-HIGH

### How It Works

Continue using the existing `StructuralCraftingWindow` but intercept at the server level. The client sends `CraftRecipeAction` packets which the server's `handleAction()` method processes. We can subclass or wrap the window to reinterpret craft actions as assignments.

### Flow Analysis

```
Client clicks recipe → SelectSlotAction packet (selects slot)
Client clicks "Craft" → CraftRecipeAction packet (recipeId, quantity)
Server handleAction() receives CraftRecipeAction
  → Reads optionSlotToRecipeMap.get(selectedSlot) → gets recipeId
  → Gets CraftingRecipe from asset map
  → Calls craftingManager.queueCraft(...)
```

### Key Question: Do Dimmed Recipes Send Packets?

Looking at `StructuralCraftingWindow.handleAction()`:

```java
case CraftRecipeAction craftAction:
    ItemStack output = this.optionsContainer.getItemStack((short)this.selectedSlot);
    if (output != null) {
        // No material check here — just proceeds
        String recipeId = this.optionSlotToRecipeMap.get(this.selectedSlot);
        CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);
        craftingManager.queueCraft(...);
    }
```

The server-side code does NOT check material availability in `handleAction` itself — that check happens deeper in `queueCraft()`. The `inventoryHints` sent to the client are purely cosmetic (they tell the client which recipes the player has materials for, so the client can dim them). **Whether dimmed recipes are clickable is a client-side UI decision that we cannot determine from server code alone.**

### Implementation Approach

Create a custom `Window` subclass that:
1. Uses `WindowType.StructuralCrafting` (so the client renders the familiar structural crafting UI)
2. Overrides `handleAction()` to intercept `CraftRecipeAction`
3. Instead of calling `craftingManager.queueCraft()`, performs the block assignment

### Advantages
- Uses existing UI — no need for `.ui` files
- Players get a familiar interface
- Input slot filtering already works
- Recipe list population already works

### Disadvantages
- Button still says "Craft" on the client — cannot change to "Assign"
- If the client blocks clicks on dimmed recipes, players MUST have materials to click (defeating the purpose)
- Tied to the structural crafting UI layout — no flexibility
- May have side effects from BenchWindow/CraftingWindow base class behavior (bench state management, sounds, etc.)

### Verdict
**Partially solves the problem.** The server can intercept, but the client UI still says "Craft" and may enforce material requirements client-side. Good as a **quick prototype** but not a long-term solution.

---

## Option 3: Command-Based Approach (/placeblock assign)

### Feasibility: HIGH (for prototyping)

### How It Works

Register a command via `this.getCommandRegistry().registerCommand(...)` that lets players assign recipes by typing.

### Available Command API

| Class | Purpose |
|-------|---------|
| `AbstractPlayerCommand` | Base for player-executed commands |
| `AbstractCommandCollection` | Groups subcommands |
| `RequiredArg<T>` | Required typed argument |
| `OptionalArg<T>` | Optional typed argument |
| `FlagArg` | Boolean flag |
| `SingleArgumentType<T>` | Custom argument type with parsing |

The plugin already registers commands via `this.getCommandRegistry().registerCommand(...)`.

### Example

```java
public class PlaceBlockCommand extends AbstractCommandCollection {
    public PlaceBlockCommand() {
        super("placeblock", "placeblock.desc");
        addSubCommand(new AssignCommand());
        addSubCommand(new ListCommand());
    }

    private static class AssignCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> recipeArg = ...;
        
        @Override
        protected void execute(CommandContext ctx, Store store, Ref ref, PlayerRef playerRef, World world) {
            // Look up held Block_Placeholder, assign the recipe
        }
    }
}
```

### Advantages
- Zero UI complexity — works immediately
- Full server control over behavior
- No resource consumption by design
- Good for testing the underlying assignment logic

### Disadvantages
- Terrible UX — players must type recipe IDs
- No visual feedback of available recipes
- Not viable for production

### Verdict
**Does not solve the UI problem but is the fastest path to testing the assignment logic.** Use as a development tool alongside a proper UI solution.

---

## Option 4: Custom Page + Window Hybrid (openCustomPageWithWindows)

### Feasibility: MEDIUM

### How It Works

`PageManager.openCustomPageWithWindows()` opens a `CustomUIPage` AND attaches `Window` objects. This is exactly what the bench system does (via `setPageWithWindows`), but the custom page would give us full control over the UI while the window provides item container support.

### Flow

```java
// Create a custom page for the selection UI
BlueprintSelectionPage page = new BlueprintSelectionPage(playerRef, recipes);

// Create a simple window for the input slot
BlueprintInputWindow window = new BlueprintInputWindow(); // extends Window, implements ItemContainerWindow

// Open both together
player.getPageManager().openCustomPageWithWindows(ref, store, page, window);
```

### Advantages
- Custom page provides full UI control (labels, buttons, events)
- Window provides proper item container handling (drag/drop, server-synced inventory)
- Closest to the "proper" way to build complex interactive UIs

### Disadvantages
- Same `.ui` template dependency as Option 1
- More complex to implement — two systems to coordinate
- Unclear if custom pages can interact with window item containers (e.g., can the custom page listen to changes in the window's item container?)

### Verdict
**Promising for a polished final solution** but has the same `.ui` template challenge as Option 1 with added complexity.

---

## Option 5: ChoiceBasePage Pattern

### Feasibility: MEDIUM-HIGH

### How It Works

`ChoiceBasePage` is an existing engine pattern for presenting a list of choices to the player. Each `ChoiceElement` has a display name, description, requirements, and interactions. Clicking an element runs its interactions.

The `ItemRepairPage` uses this pattern — it shows a list of repairable items and clicking one runs the repair interaction.

### Key Classes

| Class | Purpose |
|-------|---------|
| `ChoiceBasePage` | Base page that renders a list of `ChoiceElement` items |
| `ChoiceElement` | Abstract element with display name, description, interactions, requirements |
| `ChoiceInteraction` | Action to run when element is selected |
| `ChoiceRequirement` | Prerequisite check |

### How It Renders

```java
// For each element:
element.addButton(commandBuilder, eventBuilder, selector, playerRef);
eventBuilder.addEventBinding(Activating, selector, EventData.of("Index", i));
```

### Advantages
- Built-in engine pattern — well-tested
- Handles selection logic, requirements checking
- `.ui` templates already exist on the client (ItemRepairPage.ui pattern)

### Disadvantages
- Still requires a `.ui` template for the page layout
- Each `ChoiceElement` subclass must implement `addButton()` which references `.ui` templates
- May not support the specific layout we need (input slot + filtered recipe grid)
- Designed for simple choice lists, not the input→filter→select flow we need

### Verdict
**Could work for a simplified version** (static list of assignable recipes without input filtering). Not ideal for the full Blueprint Bench flow that needs dynamic filtering based on input item.

---

## Comparison Matrix

| Criterion | Custom Page | Hijack Window | Command | Hybrid | ChoicePage |
|-----------|:-----------:|:-------------:|:-------:|:------:|:----------:|
| **"Assign" button label** | ✅ Full control | ❌ Says "Craft" | N/A | ✅ | ✅ |
| **No resource consumption** | ✅ By design | ✅ Server can skip | ✅ | ✅ | ✅ |
| **Recipe filtering** | ✅ Server-driven | ✅ Built-in | ❌ Manual | ✅ | ❌ Limited |
| **Input slot** | ⚠️ Via Hybrid | ✅ Built-in | ❌ | ✅ | ❌ |
| **Item grid display** | ✅ #ItemIcon | ✅ Native | ❌ | ✅ | ⚠️ |
| **No `.ui` files needed** | ⚠️ Inline only | ✅ | ✅ | ⚠️ | ❌ |
| **Development speed** | Medium | Fast | Fastest | Slow | Medium |
| **Production quality** | High | Low | None | Highest | Medium |
| **Plugin-only (no client mod)** | ⚠️ Unknown | ✅ | ✅ | ⚠️ | ⚠️ |

---

## Recommended Strategy

### Phase 1: Command-Based Testing (Now)
Build `/placeblock assign <recipe>` to validate the assignment logic works correctly without any UI complexity.

### Phase 2: StructuralCraftingWindow Hijack (Quick Prototype)
Subclass/wrap `StructuralCraftingWindow` to intercept `CraftRecipeAction` and perform assignment instead. Test whether dimmed recipes are clickable. This tells us if the approach is viable before investing in custom UI.

### Phase 3: Custom UI Page (Production)
Build a `InteractiveCustomUIPage` using either:
- **Inline markup** (`appendInline()`) for a simple but functional UI
- **Existing `.ui` templates** (reuse `Pages/WarpEntryButton.ui`, `Pages/DroppedItemSlot.ui`, etc.)
- **Custom `.ui` files** shipped with the mod (if the engine supports it)

### Key Unknown to Resolve First
**Can a plugin ship `.ui` files that the client will load?** This determines whether we can build rich custom UI templates or must rely on inline markup and reusing built-in templates. Check:
- Does the mod asset loading system include UI files?
- Are `.ui` files loaded from the mod's resource path?
- The `append("Pages/...")` path — where does it resolve relative to?

---

## Appendix: Protocol Details

### Window System
- `WindowType` is a **fixed protocol enum**: `Container(0)`, `PocketCrafting(1)`, `BasicCrafting(2)`, `DiagramCrafting(3)`, `StructuralCrafting(4)`, `Processing(5)`, `Memories(6)`
- Cannot add new window types — the client must understand the type to render it
- `WindowAction` is also fixed: `CraftRecipeAction(0)`, `TierUpgradeAction(1)`, `SelectSlotAction(2)`, `ChangeBlockAction(3)`, `SetActiveAction(4)`, `CraftItemAction(5)`, `UpdateCategoryAction(6)`, `CancelCraftingAction(7)`, `SortItemsAction(8)`

### Page System
- `Page` enum: `None(0)`, `Bench(1)`, `Inventory(2)`, `ToolsSettings(3)`, `Map(4)`, `MachinimaEditor(5)`, `ContentCreation(6)`, `Custom(7)`
- Custom pages use `Page.Custom` implicitly via `openCustomPage()`
- Pages and windows are independent — a page can exist without windows, and windows can exist with standard pages

### Custom Page Protocol
- Server → Client: `CustomPage` packet (ID 218) with commands and event bindings
- Client → Server: `CustomPageEvent` packet with type (`Acknowledge`, `Data`, `Dismiss`) and data string
- Data is JSON-encoded, deserialized via `BuilderCodec<T>` on the server
