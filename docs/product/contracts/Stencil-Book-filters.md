---
area: "Stencil Crafting Filter System"
updated: 2026-05-05
---

# Stencil Crafting Filters — Behavioral Contract

## Player Experience Goal

The Stencil Crafting is the player's **building catalog**. When a player opens it, they should instantly orient: "What can I build right now with what I have?" The filter system must feel like **progressive narrowing** — each control reduces the visible set logically, and no filter interaction produces a confusing or empty result that contradicts what the player just saw.

## Filter Controls — What Each One Does

There are five independent filter dimensions. They compose via intersection (AND), not union.

### 1. Bench Tabs (All / Builders / Furniture)

| Aspect | Behavior |
|--------|----------|
| **Controls** | Which recipes are **eligible** for display |
| **Mechanism** | Recipes are classified into bench categories at registry time. Each recipe has a primary bench ID. |
| **"All" tab** | Union of all bench categories — no bench filtering applied |
| **Effect on sets** | Only sets containing recipes from the active bench tab appear in the sidebar |
| **Effect on dimming** | None — bench tabs do not affect affordability |

### 2. Affordability Mode Toggle (All / Inventory Driven / Resource Driven)

| Aspect | Behavior |
|--------|----------|
| **Controls** | Which **sets** appear in the sidebar + which items are **dimmed** in the grid |
| **"All"** | All sets appear. All recipes appear. Nothing is dimmed. Equivalent to no affordability filtering. |
| **"Inventory Driven"** | Filters based on the player's actual inventory. A set appears if ≥1 recipe in that set has at least one affordable ingredient. Within visible sets, items that are not affordable are **dimmed but visible**. |
| **"Resource Driven"** | Filters based on player-selected resource types from the Resource Type Grid. A set appears if ≥1 recipe in that set has an input ingredient whose `ResourceTypeId` matches ANY selected resource type. Within visible sets, items that don't match any selected resource type are **dimmed but visible**. |
| **Set visibility rule** | A set qualifies if it passes the active mode's threshold for **any** recipe within it (after bench tab filtering). Sets that fail this threshold are **hidden from the sidebar entirely**. |
| **Item visibility rule** | Within a visible set, **all** items in that set are shown. Non-matching items are dimmed (`setItemUncraftable(true)`). Matching items are bright. |
| **Null-set items** | When mode is not "All", null-set items appear in an "Uncategorized" set that can be filtered like any other set in the sidebar. See §Null-Set Items below. |

### 3. Set Sidebar (All / individual sets)

| Aspect | Behavior |
|--------|----------|
| **Controls** | Which sets' items appear in the recipe grid |
| **"All" (no selection)** | Shows the union of all items from all **visible** sets (as determined by bench tab + affordability mode) |
| **Single set selected** | Shows only items belonging to that set |
| **Multi-select** | Shows items belonging to any of the selected sets (union) |
| **Effect on dimming** | None — set selection filters which items appear, not their affordability state |

### 4. Search Bar

| Aspect | Behavior |
|--------|----------|
| **Controls** | Text-match filter across recipe ID, block type ID, and set name |
| **Mechanism** | Case-insensitive substring match. Applied **after** all other filters (tab, craftable, set). |
| **Effect on sets** | None — search does not hide sets from the sidebar |
| **Effect on dimming** | None — search does not change affordability state |
| **Empty search** | No text filtering applied |

### 5. Resource Type Grid

| Aspect | Behavior |
|--------|----------|
| **Controls** | Which recipes are visible when Resource Driven mode is active |
| **Mechanism** | Player toggles resource type icons (36x36 icon buttons, ~78 types). Recipes whose input `MaterialQuantity` entries include a `ResourceTypeId` matching ANY selected type pass the filter. Loose OR within selected types. |
| **No selection** | When no resource types are selected, all recipes pass (no filtering). |
| **Interaction with mode** | Resource type selections are IGNORED when mode is "All" or "Inventory Driven". Only active in "Resource Driven" mode. |

## Composition Rules

Filters compose as a pipeline. Each stage narrows the result of the previous stage:

```
All Recipes
  → Bench Tab filter         (which recipes are eligible)
  → Affordability mode        (three-state: All / Inventory Driven / Resource Driven)
     → If All: no filtering, no dimming
     → If Inventory Driven: set visibility + item dimming by inventory
     → If Resource Driven: set visibility + item dimming by resource type match
  → Set Sidebar filter        (which visible sets' items appear in grid)
  → Material Group filter     (which material categories are active)
  → Search filter             (text match within visible items)
  → Recipe Grid               (displayed items, with dimming applied)
```

**There is no precedence ambiguity.** Each filter stage operates on a different dimension:

| Stage | Dimension | Narrows | Dims |
|-------|-----------|---------|------|
| Bench Tab | Recipe eligibility | ✓ | — |
| Affordability Mode | Set visibility + item matching | ✓ (sets) | ✓ (items) |
| Set Sidebar | Set selection | ✓ | — |
| Material Group | Material category | ✓ | — |
| Search | Text match | ✓ | — |

## Affordability Definition

A recipe is "matching" based on the active affordability mode:

| Mode | Recipe qualifies if... |
|---|---|
| **All** | Always (no filtering applied) |
| **Inventory Driven** | The player's inventory contains ≥1 unit of at least one ingredient |
| **Resource Driven** | At least one of the recipe's input `MaterialQuantity` entries has a `ResourceTypeId` matching a player-selected resource type |

### BlockGroup Interchangeability

If the recipe's output item belongs to a `BlockGroup` (e.g., `FullBlocks_Hardwood`), and the player has **any** member of that group in inventory, the recipe is considered affordable — because the engine allows free conversion between group members (block cycling). This is a **secondary affordability check** that runs after the primary ingredient check fails.

**Example:** Player has `Wood_Hardwood_Planks` but not `Wood_Hardwood_Trunk`. A recipe requiring `Wood_Hardwood_Trunk` as input would normally fail the affordability check. However, if the output is in the same `FullBlocks_Hardwood` group, the recipe is considered affordable because the player can cycle to the needed variant for free.

## Null-Set Items

Items with `Item.set == null` are recipes whose output item has no material set classification. These items are treated as belonging to an **"Uncategorized" set** that appears in the sidebar and can be selected or deselected like any other set.

| Affordability Mode | Null-set behavior |
|---|---|
| **All** | Null-set items appear in the "Uncategorized" set. They are never dimmed. |
| **Inventory Driven** | Null-set items appear in the "Uncategorized" set. Items are dimmed or bright based on inventory affordability, same as any other set. |
| **Resource Driven** | Null-set items appear in the "Uncategorized" set if any null-set recipe's inputs match a selected resource type. Non-matching null-set items are dimmed. |

**Rationale:** Treating null-set items as an "Uncategorized" set provides consistent behavior across all modes. The player always has visibility into uncategorized recipes and can filter them in or out via the sidebar, maintaining the same interaction pattern as named sets.

## Scenario Answers

### Scenario 1: Player has `Wood_Hardwood_Trunk`. Mode = "Inventory Driven".

| Aspect | Result |
|--------|--------|
| **Visible sets** | All sets where ≥1 recipe is fully craftable using `Wood_Hardwood_Trunk` (directly or via ingredient chain). This includes the Hardwood set and any set whose recipes can be fully paid with trunk-derived materials. |
| **Items in Hardwood set** | All Hardwood recipes shown. Those the player can fully craft are bright. Those requiring additional materials the player lacks are **dimmed**. |
| **Items in other visible sets** | All items in each visible set are shown. Affordable = bright, unaffordable = dimmed. |
| **Sets with zero affordable recipes** | Hidden from sidebar entirely. |
| **Null-set items** | Shown in "Uncategorized" set. Items are dimmed or bright based on inventory affordability. |

### Scenario 2: Player has `Wood_Hardwood_Planks` (not trunks). Mode = "Inventory Driven".

| Aspect | Result |
|--------|--------|
| **BlockGroup effect** | If output items belong to `FullBlocks_Hardwood`, they are considered affordable via free block cycling — the player can convert Planks → any group member. |
| **Visible sets** | Sets containing recipes affordable via either (a) direct ingredient availability or (b) BlockGroup interchangeability. The Hardwood set qualifies because Planks → Trunk conversion is free within the group. |
| **Ornate/Decorative sets** | If Ornate or Decorative recipes' outputs are in the same FullBlocks group as Hardwood Planks, they are affordable via cycling. Those sets become visible. If not in the same group, they follow standard affordability. |
| **Dimming** | Within each visible set, items that are truly affordable (direct or via cycling) are bright; others are dimmed. |

### Scenario 3: "All" set filter + "Inventory Driven" — is this the union of all visible set items?

**Yes.** "All" (no set selection) shows exactly the union of all items from all sets that passed the affordability mode's visibility gate. It is algebraically equivalent to selecting every visible set simultaneously. No more, no less.

### Scenario 4: Items with null set when affordability mode is active.

**Shown in the "Uncategorized" set.** Null-set items appear in an "Uncategorized" set in the sidebar across all modes. They follow the same dimming rules as items in named sets. See §Null-Set Items above.

### Scenario 5: "All" mode selected — everything shows, nothing dimmed?

**Yes.** "All" mode disables all affordability logic:
- All sets appear in the sidebar (including the "Uncategorized" set)
- All recipes appear in the grid (including null-set items)
- No items are dimmed
- The grid is a complete, unfiltered catalog of all recipes for the active bench tab

### Scenario 6: Resource Driven mode — player selects Wood and Rock types

| Aspect | Result |
|--------|--------|
| **Visible sets** | Sets containing ≥1 recipe whose inputs include `ResourceTypeId` matching "Wood", "Rock", or any selected type |
| **Items in matching sets** | ALL items shown. Recipes using selected resource types are bright. Others are dimmed. |
| **Sets with zero matching recipes** | Hidden from sidebar. |
| **Null-set items** | Shown in "Uncategorized" set if any null-set recipe matches a selected resource type. |

## Anti-Patterns to Reject

- **Affordability mode hiding individual items.** The affordability mode gates **sets**, not individual items. Within a visible set, all items must be shown (dimmed if non-matching). Hiding non-matching items within a visible set removes the player's awareness of what they could build if they gathered more materials.
- **"All" set showing different items than the union of individual sets.** If a player clicks each set individually and notes the items, then clicks "All", they must see exactly the same items. Any discrepancy is a bug.
- **Resource type selections affecting non-Resource Driven modes.** Resource type grid selections must be completely ignored in "All" and "Inventory Driven" modes. Leaking resource type state into other modes creates unpredictable filtering.
- **Dimming affecting the set sidebar.** Sets are either visible or hidden. A set is never "dimmed" in the sidebar. Individual items within sets are dimmed in the grid.
- **Search changing set visibility.** Search only narrows the grid contents. It does not remove sets from the sidebar or change affordability state.
