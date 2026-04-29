---
area: "Blueprint Bench Filter System"
updated: 2026-04-29
---

# Blueprint Bench Filters — Behavioral Contract

## Player Experience Goal

The Blueprint Bench is the player's **building catalog**. When a player opens it, they should instantly orient: "What can I build right now with what I have?" The filter system must feel like **progressive narrowing** — each control reduces the visible set logically, and no filter interaction produces a confusing or empty result that contradicts what the player just saw.

## Filter Controls — What Each One Does

There are four independent filter dimensions. They compose via intersection (AND), not union.

### 1. Bench Tabs (All / Builders / Furniture)

| Aspect | Behavior |
|--------|----------|
| **Controls** | Which recipes are **eligible** for display |
| **Mechanism** | Recipes are classified into bench categories at registry time. Each recipe has a primary bench ID. |
| **"All" tab** | Union of all bench categories — no bench filtering applied |
| **Effect on sets** | Only sets containing recipes from the active bench tab appear in the sidebar |
| **Effect on dimming** | None — bench tabs do not affect affordability |

### 2. Craftable Dropdown (No Filter / Has Partial / Can Craft)

| Aspect | Behavior |
|--------|----------|
| **Controls** | Which **sets** appear in the sidebar + which items are **dimmed** in the grid |
| **"No Filter"** | All sets appear. All recipes appear. Nothing is dimmed. |
| **"Has Partial"** | A set appears if ≥1 recipe in that set has at least one affordable ingredient. Within visible sets, items without any affordable ingredient are **dimmed but visible**. |
| **"Can Craft"** | A set appears if ≥1 recipe in that set is **fully affordable**. Within visible sets, items that are not fully affordable are **dimmed but visible**. |
| **Set visibility rule** | A set qualifies if it passes the affordability threshold for **any** recipe within it (after bench tab filtering). Sets that fail this threshold are **hidden from the sidebar entirely**. |
| **Item visibility rule** | Within a visible set, **all** items in that set are shown. Unaffordable items are dimmed (`setItemUncraftable(true)`). Affordable items are bright. |
| **Null-set items** | When craftable filter is active (not "No Filter"), null-set items are **excluded**. See §Null-Set Items below. |

### 3. Set Sidebar (All / individual sets)

| Aspect | Behavior |
|--------|----------|
| **Controls** | Which sets' items appear in the recipe grid |
| **"All" (no selection)** | Shows the union of all items from all **visible** sets (as determined by bench tab + craftable filter) |
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

## Composition Rules

Filters compose as a pipeline. Each stage narrows the result of the previous stage:

```
All Recipes
  → Bench Tab filter         (which recipes are eligible)
  → Craftable filter          (which sets are visible + dimming state per item)
  → Set Sidebar filter        (which visible sets' items appear in grid)
  → Search filter             (text match within visible items)
  → Recipe Grid               (displayed items, with dimming applied)
```

**There is no precedence ambiguity.** Each filter stage operates on a different dimension:

| Stage | Dimension | Narrows | Dims |
|-------|-----------|---------|------|
| Bench Tab | Recipe eligibility | ✓ | — |
| Craftable | Set visibility + item affordability | ✓ (sets) | ✓ (items) |
| Set Sidebar | Set selection | ✓ | — |
| Search | Text match | ✓ | — |

## Affordability Definition

A recipe is "affordable" based on the active craftable filter level:

| Filter Level | Recipe qualifies if... |
|---|---|
| **No Filter** | Always (affordability not evaluated) |
| **Has Partial** | The player's inventory contains ≥1 unit of at least one ingredient |
| **Can Craft** | The player's inventory contains sufficient quantity of **all** ingredients |

### BlockGroup Interchangeability

If the recipe's output item belongs to a `BlockGroup` (e.g., `FullBlocks_Hardwood`), and the player has **any** member of that group in inventory, the recipe is considered affordable — because the engine allows free conversion between group members (block cycling). This is a **secondary affordability check** that runs after the primary ingredient check fails.

**Example:** Player has `Wood_Hardwood_Planks` but not `Wood_Hardwood_Trunk`. A recipe requiring `Wood_Hardwood_Trunk` as input would normally fail the affordability check. However, if the output is in the same `FullBlocks_Hardwood` group, the recipe is considered affordable because the player can cycle to the needed variant for free.

## Null-Set Items

Items with `Item.set == null` are recipes whose output item has no material set classification.

| Craftable Filter | Null-set behavior |
|---|---|
| **No Filter** | Null-set items appear in the grid (under "All" set view). They are never dimmed. |
| **Has Partial** | Null-set items are **excluded** from the grid entirely. |
| **Can Craft** | Null-set items are **excluded** from the grid entirely. |

**Rationale:** The craftable filter operates on sets. Items without a set cannot participate in set-level visibility gating. Showing them would create an inconsistency where the player sees items that aren't part of any material group while the UI is specifically filtering by material affordability.

## Scenario Answers

### Scenario 1: Player has `Wood_Hardwood_Trunk`. Craftable = "Can Craft".

| Aspect | Result |
|--------|--------|
| **Visible sets** | All sets where ≥1 recipe is fully craftable using `Wood_Hardwood_Trunk` (directly or via ingredient chain). This includes the Hardwood set and any set whose recipes can be fully paid with trunk-derived materials. |
| **Items in Hardwood set** | All Hardwood recipes shown. Those the player can fully craft are bright. Those requiring additional materials the player lacks are **dimmed**. |
| **Items in other visible sets** | All items in each visible set are shown. Affordable = bright, unaffordable = dimmed. |
| **Sets with zero affordable recipes** | Hidden from sidebar entirely. |
| **Null-set items** | Excluded. |

### Scenario 2: Player has `Wood_Hardwood_Planks` (not trunks). Craftable = "Can Craft".

| Aspect | Result |
|--------|--------|
| **BlockGroup effect** | If output items belong to `FullBlocks_Hardwood`, they are considered affordable via free block cycling — the player can convert Planks → any group member. |
| **Visible sets** | Sets containing recipes affordable via either (a) direct ingredient availability or (b) BlockGroup interchangeability. The Hardwood set qualifies because Planks → Trunk conversion is free within the group. |
| **Ornate/Decorative sets** | If Ornate or Decorative recipes' outputs are in the same FullBlocks group as Hardwood Planks, they are affordable via cycling. Those sets become visible. If not in the same group, they follow standard affordability. |
| **Dimming** | Within each visible set, items that are truly affordable (direct or via cycling) are bright; others are dimmed. |

### Scenario 3: "All" set filter + "Can Craft" — is this the union of all visible set items?

**Yes.** "All" (no set selection) shows exactly the union of all items from all sets that passed the craftable filter's visibility gate. It is algebraically equivalent to selecting every visible set simultaneously. No more, no less.

### Scenario 4: Items with null set when craftable filter is active.

**Excluded.** Null-set items do not appear when any craftable filter is active. They only appear under "No Filter". See §Null-Set Items above.

### Scenario 5: "No Filter" selected — everything shows, nothing dimmed?

**Yes.** "No Filter" disables all affordability logic:
- All sets appear in the sidebar (including those with zero affordable recipes)
- All recipes appear in the grid (including null-set items)
- No items are dimmed
- The grid is a complete, unfiltered catalog of all recipes for the active bench tab

## Anti-Patterns to Reject

- **Craftable filter hiding individual items.** The craftable filter gates **sets**, not individual items. Within a visible set, all items must be shown (dimmed if unaffordable). Hiding unaffordable items within a visible set removes the player's awareness of what they could build if they gathered more materials.
- **"All" set showing different items than the union of individual sets.** If a player clicks each set individually and notes the items, then clicks "All", they must see exactly the same items. Any discrepancy is a bug.
- **Null-set items appearing when craftable filter is active.** These items have no set context, so they can't participate in set-based visibility. Showing them alongside set-filtered results is incoherent.
- **Dimming affecting the set sidebar.** Sets are either visible or hidden. A set is never "dimmed" in the sidebar. Individual items within sets are dimmed in the grid.
- **Search changing set visibility.** Search only narrows the grid contents. It does not remove sets from the sidebar or change affordability state.
