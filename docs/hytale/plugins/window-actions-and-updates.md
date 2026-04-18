---
topic: "Window Actions, Updates, and Server-Side Toggle Mechanisms"
category: "Plugins / Windows"
updated: 2026-04-17
sources: ["WindowAction.java", "SendWindowAction.java", "UpdateWindow.java", "Window.java", "WindowManager.java", "BenchWindow.java", "CraftingWindow.java", "FieldCraftingWindow.java", "PageManager.java", "CustomUIPage.java"]
---

# Window Actions, Updates, and Server-Side Toggle Mechanisms

## Summary

The Hytale window system has a **fixed set of 9 WindowAction types** hardcoded in the protocol. There is no "custom action" type. However, the server CAN push updated window data to the client at any time via `invalidate()` + the `UpdateWindow` packet. This document covers all available mechanisms for implementing server-side window state changes in response to player input.

---

## 1. Complete WindowAction Subclass Inventory

All action types are defined in `com.hypixel.hytale.protocol.packets.window`. The `WindowAction.deserialize()` method uses a fixed switch on type IDs 0–8. **There is no extensibility point — you cannot add custom action types.**

| TypeId | Class | Fields | Purpose |
|--------|-------|--------|---------|
| 0 | `CraftRecipeAction` | `recipeId: String?`, `quantity: int` | Player clicks to craft a recipe |
| 1 | `TierUpgradeAction` | *(none)* | Player clicks bench tier upgrade button |
| 2 | `SelectSlotAction` | `slot: int` | Player selects a slot (structural crafting input) |
| 3 | `ChangeBlockAction` | `down: boolean` | Player cycles block variant up/down (structural) |
| 4 | `SetActiveAction` | `state: boolean` | Player toggles active state (processing bench on/off) |
| 5 | `CraftItemAction` | *(none)* | Player confirms crafting of placed item (structural) |
| 6 | `UpdateCategoryAction` | `category: String`, `itemCategory: String` | Player switches category/subcategory tab |
| 7 | `CancelCraftingAction` | *(none)* | Player cancels in-progress craft |
| 8 | `SortItemsAction` | `sortType: SortType` | Player sorts container items |

### Key Findings

- **No generic/custom action type exists.** The protocol is a closed enum.
- **`UpdateCategoryAction` (typeId=6)** is the most interesting for our purposes — the client sends this when the player switches tabs, and it carries two string fields. However, it's sent by the client's category tab UI, not by a toggle button.
- **`SetActiveAction` (typeId=4)** carries a boolean `state` — this is used by processing benches (on/off toggle). The client only sends it for `Processing` window types.

---

## 2. Window Invalidation — How `invalidate()` + `UpdateWindow` Works

### Mechanism

```
Server: window.invalidate()  →  sets isDirty = true
Server: WindowManager.updateWindows()  →  called each tick
  └─ for each window: if consumeIsDirty() == true:
       └─ WindowManager.updateWindow(window)
            └─ sends UpdateWindow packet to client
```

### UpdateWindow Packet (ID 201)

```java
public class UpdateWindow implements Packet {
    public int id;                          // window ID
    public String windowData;              // full JSON string (nullable)
    public InventorySection inventory;     // slot data (nullable)
    public ExtraResources extraResources;  // nearby chest data (nullable)
}
```

### What happens on the client

When the client receives `UpdateWindow`:
1. It looks up the open window by `id`
2. It **replaces** the window's JSON data with the new `windowData`
3. If `inventory` is non-null, it updates the slot grid
4. If the JSON contains `"needRebuild": true`, it **fully rebuilds** the UI (re-renders categories, recipe lists, etc.)

### Verified behavior

The vanilla `BenchWindow` uses this pattern extensively:
- `updateCraftingJob(percent)` — updates `"progress"` in JSON, calls `invalidate()`
- `updateBenchTierLevel(int)` — updates `"tierLevel"`, calls `setNeedRebuild()` + `invalidate()`
- `invalidateExtraResources()` — marks extra resources dirty, calls `invalidate()`

**Conclusion: Mutating `windowData` and calling `this.invalidate()` DOES cause the client to receive updated data.** The client re-reads the JSON and updates its display. For structural changes (new categories, different recipe lists), `setNeedRebuild()` should also be called to trigger a full UI rebuild.

---

## 3. Alternative Toggle Mechanisms — Analysis

### Option A: Chat Command (`/benchfilter`)

**Feasibility: HIGH — This is the most practical approach.**

A chat command can:
1. Access the player's `WindowManager` via `Player.getPageManager().windowManager` (or by tracking the open window instance)
2. Locate the open `PortableBenchWindow` instance
3. Toggle the filter state
4. Rebuild the categories with the new filter
5. Call `setNeedRebuild()` + `invalidate()` to push the update

```
Player types /benchfilter while window is open
  → Command handler finds the player's open PortableBenchWindow
  → Toggles filterCraftable flag
  → Calls buildCategories(container) with new filter
  → Calls setNeedRebuild() + invalidate()
  → WindowManager.updateWindows() sends UpdateWindow on next tick
  → Client re-renders with filtered/unfiltered recipes
```

**Challenge:** The command handler needs a reference to the open window. Options:
- Store the window instance in a `Map<UUID, PortableBenchWindow>` when opened, remove on close
- Iterate `WindowManager.getWindows()` to find the `PortableBenchWindow` instance

### Option B: Repurpose an Existing WindowAction

**Feasibility: MEDIUM — Fragile but possible.**

The `UpdateCategoryAction` (typeId=6) is sent when the player clicks a category tab. Your `handleAction()` already receives all actions. You could detect when a specific "magic" category is selected and interpret it as a toggle:

```java
@Override
public void handleAction(..., WindowAction action) {
    if (action instanceof UpdateCategoryAction catAction) {
        if ("__toggle_filter__".equals(catAction.category)) {
            // Toggle filter, rebuild, invalidate
            return;
        }
    }
    // ... normal CraftRecipeAction handling
}
```

**Problem:** The client only sends `UpdateCategoryAction` when the user clicks a real category tab. You'd need to include a fake "__toggle_filter__" category in your categories JSON. The client would render it as a real tab, which is a UX hack but would work.

### Option C: Custom Key Binding → Server Packet

**Feasibility: NONE with current API.**

There is no plugin API to register custom key bindings on the client. The client doesn't expose a mechanism to send arbitrary packets in response to key presses. Key handling is entirely client-side.

### Option D: `Window.handleRawAction()` or Similar Override

**Feasibility: NONE.**

No such method exists. `Window.handleAction()` is the only action handler, and it receives typed `WindowAction` instances (the fixed 9 types above).

### Option E: Close + Reopen Window with Different Config

**Feasibility: HIGH — Simple and reliable.**

```
Player types /benchfilter
  → Command closes current window
  → Command opens new PortableBenchWindow(config, !filterCraftable, container)
  → Player sees the same bench UI with different recipe list
```

**Pros:** No need for mutable state, no `invalidate()` complexity, guaranteed clean UI state.
**Cons:** Brief visual flicker as window closes/reopens. Player loses scroll position and selected category.

### Option F: Reuse `SetActiveAction` (typeId=4) as a Toggle

**Feasibility: LOW.**

`SetActiveAction` carries a `boolean state` field. However, the client only sends this for `Processing` window types. A `PocketCrafting` window's UI has no on/off toggle button, so the client will never generate this action.

### Option G: Custom UI Page with Event Callback

**Feasibility: EXPERIMENTAL — Most flexible but complex.**

Hytale supports `CustomUIPage` with HTML/CSS/JS-based custom UI. A `CustomUIPage` can:
- Receive `handleDataEvent(ref, store, rawData)` callbacks from client-side JS
- Send `UICommandBuilder` updates back to the client

You could potentially overlay a custom UI toggle button on top of the bench window. When clicked, the client sends a `CustomPageEvent` with `type=Data`, which triggers `handleDataEvent()` on the server. The server then updates the window data and invalidates.

**This requires the Custom UI system**, which is documented at the official Hytale modding docs. It's the most "correct" approach for a proper toggle button but adds significant complexity.

---

## 4. How Vanilla Handles "Hide Unknown Recipes"

Based on decompiled code analysis:

### The Client Toggle is 100% Client-Side

The "hide unknown recipes" toggle in the vanilla crafting UI:
1. Compares each recipe's `requiredMemoriesLevel` against the `worldMemoriesLevel` from the window JSON
2. Filters the recipe list **in the client renderer** — no server communication
3. **Does NOT send any WindowAction** to the server
4. The toggle state persists in client-side settings, not on the server

### No Server Callback for the Toggle

Neither `BenchWindow`, `CraftingWindow`, nor `FieldCraftingWindow` have any handler for a "hide/show" toggle. The `handleAction()` methods in these classes only handle:
- `CraftRecipeAction` — craft an item
- `TierUpgradeAction` — bench upgrade (in `SimpleCraftingWindow`)
- `UpdateCategoryAction` — category tab change (logged but no filter logic)

### `updateData()` Does Not Exist

There is no `updateData()` method on `Window`. The pattern for pushing updates is:
1. Mutate `windowData` (the `JsonObject`)
2. Call `invalidate()` (sets `isDirty`)
3. Optionally call `setNeedRebuild()` (adds `"needRebuild": true` to JSON)
4. `WindowManager.updateWindows()` picks it up on the next tick

---

## 5. Recommended Approach: Chat Command + In-Place Update

The most practical implementation combines **Option A** (chat command) with **in-place window data mutation**:

### Architecture

```
┌──────────────┐     /benchfilter      ┌───────────────────────┐
│   Player     │ ──────────────────►   │  BenchFilterCommand   │
│ (in-game)    │                       │                       │
└──────────────┘                       │ 1. Find open window   │
       ▲                               │ 2. Toggle filter flag │
       │                               │ 3. Rebuild categories │
       │  UpdateWindow packet          │ 4. setNeedRebuild()   │
       │  (next tick)                  │ 5. invalidate()       │
       └───────────────────────────────┘                       │
                                       └───────────────────────┘
```

### Key Implementation Details

1. **Track open windows:** Store `Map<UUID, PortableBenchWindow>` — add on open, remove on close via `registerCloseEvent()`
2. **Make `filterCraftable` mutable:** Change from `final` to non-final, add a setter or toggle method
3. **Expose `buildCategories()` for re-invocation:** Already exists, just needs access to the player's container
4. **Call `setNeedRebuild()` before `invalidate()`:** This adds `"needRebuild": true` to the JSON, telling the client to fully rebuild the UI rather than just updating progress bars
5. **Thread safety:** The command executes on the network thread; use `world.execute()` if accessing world state, but `windowData` mutation + `invalidate()` should be safe since `isDirty` is `AtomicBoolean`

### Why Not Close+Reopen?

Close+reopen works but has worse UX:
- Visual flicker (window close animation → open animation)
- Player loses their selected category tab
- Player loses scroll position in the recipe list
- Two packets instead of one

### Alternative: Fake Category Tab

If you want a toggle that works without typing a chat command, add a fake category to the categories array:

```json
{
  "id": "__filter_toggle__",
  "name": "🔍 Filter: All",
  "icon": "filter_icon",
  "craftableRecipes": []
}
```

When the player clicks this "tab", the client sends `UpdateCategoryAction` with `category="__filter_toggle__"`. Your `handleAction()` catches this, toggles the filter, rebuilds real categories, and invalidates. The tab would appear as a normal category tab in the UI — not ideal UX but functional without any client-side changes.

---

## 6. API Reference

### Window (base class)

| Method | Purpose |
|--------|---------|
| `invalidate()` | Sets `isDirty` flag; `WindowManager` sends `UpdateWindow` on next tick |
| `setNeedRebuild()` | Adds `"needRebuild": true` to JSON data; signals full UI rebuild |
| `getData()` | Returns the `JsonObject` that becomes the `UpdateWindow.windowData` string |
| `handleAction(ref, store, action)` | Called when client sends `SendWindowAction` packet |
| `close(ref, componentAccessor)` | Closes this window via `WindowManager` |
| `registerCloseEvent(consumer)` | Register callback for when window is closed |
| `getPlayerRef()` | Get the `PlayerRef` for the player who has this window open |

### WindowManager

| Method | Purpose |
|--------|---------|
| `openWindow(ref, window, store)` | Opens a window; sends `OpenWindow` packet |
| `updateWindow(window)` | Sends `UpdateWindow` packet immediately (usually called by `updateWindows()`) |
| `updateWindows()` | Called each tick; sends `UpdateWindow` for all dirty windows |
| `getWindow(id)` | Get window by ID |
| `getWindows()` | Get all open windows |
| `closeWindow(ref, id, componentAccessor)` | Close and remove window |

### PageManager

| Method | Purpose |
|--------|---------|
| `setPageWithWindows(ref, store, page, canClose, windows...)` | Opens windows and sets the UI page |
| `openCustomPage(ref, store, page)` | Opens a Custom UI page (HTML/CSS/JS) |

---

## See Also

- [Plugin Capabilities](./capabilities.md)
- [Networking & Packets](./networking.md)
- [StructuralCrafting Window Crash Analysis](../crafting/structural-window-crash-analysis.md)
- [Server vs Client Boundary](../server-client-boundary.md)
