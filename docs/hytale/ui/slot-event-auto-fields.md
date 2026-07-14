---
topic: "Slot Event Auto-Provided Fields"
category: "UI / Custom Pages / Events"
updated: 2026-04-28
sources: ["decompiled: EntitySpawnPage.java", "decompiled: CustomUIEventBinding.java", "decompiled: CustomPageEvent.java", "decompiled: ItemStack.java", "plugin: BlueprintSelectionPage.java"]
---

# Slot Event Auto-Provided Fields

## Summary

When certain `CustomUIEventBindingType` slot events fire on an `ItemGrid`, the **client automatically injects** the `ItemStackId` field into the event payload — even if the server-side `EventData` binding does not explicitly include it. No slot index is auto-provided by any event type.

`ItemStackId` contains the `ItemStack.Id` value (the item type identifier, e.g. `"Rock_Stone"`) of the slot that was interacted with.

---

## Auto-Provided Fields by Event Type

| Event Type | ID | Auto-provides `ItemStackId`? | Auto-provides Slot Index? | Confidence | Evidence |
|---|---|---|---|---|---|
| `Dropped` | 18 | **YES** | NO | **Confirmed** — decompiled engine code | EntitySpawnPage.java |
| `SlotClicking` | 13 | **YES** | NO | **High** — plugin code relies on it | BlueprintSelectionPage.java |
| `SlotMouseEntered` | 15 | **YES** | NO | **High** — plugin code relies on it | BlueprintSelectionPage.java |
| `SlotDoubleClicking` | 14 | **Likely YES** | NO | Inferred — same slot-event family | No direct evidence |
| `SlotMouseExited` | 16 | **Likely YES** | NO | Inferred — same slot-event family | No direct evidence |
| `DragCancelled` | 17 | Unknown | NO | No evidence | — |
| `SlotMouseDragCompleted` | 19 | Unknown | NO | No evidence | — |
| `SlotMouseDragExited` | 20 | Unknown | NO | No evidence | — |
| `SlotClickReleaseWhileDragging` | 21 | Unknown | NO | No evidence | — |
| `SlotClickPressWhileDragging` | 22 | Unknown | NO | No evidence | — |

**No slot-related event auto-provides a slot index.** The only way to identify a slot by index is to use the window-based `SelectSlotAction` system (which is a completely different mechanism via `WindowAction`, not `CustomUIEventBinding`).

---

## Evidence: `Dropped` (ID 18) — Confirmed via Decompiled Code

**Source**: `.tmp_hytale_src/com/hypixel/hytale/server/npc/pages/EntitySpawnPage.java`

The event binding specifies only `"Type"` — no `ItemStackId`:

```java
// Line 141 — binding does NOT include ItemStackId
eventBuilder.addEventBinding(
    CustomUIEventBindingType.Dropped, "#ItemMaterialSlot",
    new EventData().append("Type", "SetItemMaterial"), false);
```

But the handler reads `data.itemStackId` — proving the client auto-injects it:

```java
// Line 261 — handler reads auto-provided ItemStackId
private void handleSetItemMaterial(..., EntitySpawnPageEventData data) {
    if (data.itemStackId != null) {
        this.selectItem(ref, store, data.itemStackId, commandBuilder);
    }
}
```

The codec confirms `ItemStackId` is a recognized field:

```java
// Line 821
.append(new KeyedCodec<>("ItemStackId", Codec.STRING),
    (entry, s) -> entry.itemStackId = s, entry -> entry.itemStackId)
```

---

## Evidence: `SlotMouseEntered` (ID 15) — Plugin Code Relies On It

**Source**: `src/main/java/.../BlueprintSelectionPage.java`

Binding specifies only `"Action"`:

```java
// Line 435-436
evt.addEventBinding(CustomUIEventBindingType.SlotMouseEntered, "#RecipeGrid",
    EventData.of("Action", "RecipeHover"), false);
```

Handler reads `data.itemStackId` to identify the hovered recipe:

```java
// Line 294-301
} else if ("RecipeHover".equals(data.action)) {
    if (!isDragging && data.itemStackId != null) {
        for (RecipeEntry entry : displayedRecipes) {
            if (entry.outputItemId.equals(data.itemStackId)) {
                this.selectedRecipeId = entry.recipeId;
                break;
            }
        }
    }
}
```

---

## Evidence: `SlotClicking` (ID 13) — Plugin Code Relies On It

**Source**: `src/main/java/.../BlueprintSelectionPage.java`

Binding specifies only `"Action"`:

```java
// Line 439-440
evt.addEventBinding(CustomUIEventBindingType.SlotClicking, "#RecipeGrid",
    EventData.of("Action", "RecipePickup"), false);
```

Handler reads `data.itemStackId`:

```java
// Line 307-315
} else if ("RecipePickup".equals(data.action)) {
    isDragging = true;
    if (data.itemStackId != null) {
        for (RecipeEntry entry : displayedRecipes) {
            if (entry.outputItemId.equals(data.itemStackId)) {
                this.selectedRecipeId = entry.recipeId;
                break;
            }
        }
    }
}
```

---

## What `ItemStackId` Contains

From `ItemStack.CODEC` (decompiled):

```java
.append(new KeyedCodec<>("Id", Codec.STRING),
    (itemStack, id) -> itemStack.itemId = id, itemStack -> itemStack.itemId)
```

`ItemStackId` is the `Id` field of the `ItemStack` in the interacted slot — the **item type identifier** (e.g. `"Rock_Stone"`, `"Wood_Oak_Trunk"`). It is NOT a unique stack instance ID.

---

## Slot Index: Not Available via Custom UI Events

The custom UI event system (`CustomUIEventBinding` → `CustomPageEvent`) does **not** provide a slot index. The packet structure is:

```java
// CustomPageEvent.java (decompiled)
public class CustomPageEvent implements Packet {
    public CustomPageEventType type;  // Acknowledge, Data, Dismiss
    public String data;               // JSON string with merged EventData
}
```

The `data` field is a flat JSON string containing the merged `EventData` keys plus any auto-injected fields. There is no structured slot index field.

### Window-Based Alternative

For slot index tracking, the engine uses the `WindowAction` system:

```java
// StructuralCraftingWindow.java (decompiled)
case SelectSlotAction selectAction:
    int newSlot = MathUtil.clamp(selectAction.slot, 0, capacity);
```

This requires `ItemGrid` with `InventorySectionId` + `openCustomPageWithWindows()` — a different architecture than custom page events.

---

## `@` Value References: No Known ItemGrid Slot Properties

The `@` prefix in `EventData` captures live UI element values at event time:

```java
EventData.of("@SearchQuery", "#SearchInput.Value")  // works
EventData.of("@Tab", "#BenchTabs.SelectedTab")      // works
```

**There is no evidence** that ItemGrid exposes slot-specific properties accessible via `@` value references. Paths like `#RecipeGrid.SelectedSlot.ItemId` or `#RecipeGrid.HoveredSlot.Index` are NOT documented and likely do not exist. The only settable ItemGrid property found in the codebase is `.Slots` (accepts `ItemGridSlot[]`).

---

## Practical Implications

### Identifying which slot was interacted with

Since only `ItemStackId` (item type id) is provided and no slot index:

1. **If all slots have unique item types** → match `ItemStackId` against your slot data to identify the slot (this is what `BlueprintSelectionPage` does).

2. **If multiple slots share the same item type** → `ItemStackId` alone is ambiguous. You cannot distinguish which slot was clicked/hovered. Workarounds:
   - Ensure unique items per slot (append metadata or use distinct item ids)
   - Use the window-based `InventorySectionId` approach for true slot-level tracking
   - Track state server-side and infer from context

### Codec Pattern

Always include `ItemStackId` in your event data codec if binding slot events:

```java
public static final BuilderCodec<MyEventData> CODEC = BuilderCodec.builder(
        MyEventData.class, MyEventData::new)
    .append(new KeyedCodec<>("Action", Codec.STRING),
        (e, v) -> e.action = v, e -> e.action).add()
    .append(new KeyedCodec<>("ItemStackId", Codec.STRING),
        (e, v) -> e.itemStackId = v, e -> e.itemStackId).add()
    .build();
```

---

## See Also

- [UI Data Binding](./ui-data-binding.md) — `@` prefix, event patterns, codec structure
- [Custom UI for Stencil Crafting](../plugins/custom-ui-for-Blueprint-Book.md) — event binding overview
