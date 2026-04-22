---
area: "PlaceBlock Building Tool"
updated: 2026-04-22
---

# PlaceBlock Building Tool — Behavioral Contract

## Player Experience Goal

Building with crafted blocks should feel like **painting with a loaded brush**. The player selects what they want to build at the bench, then walks into the world and places blocks fluidly — one click per block, with a live preview showing exactly where the block will go and whether they can afford it. The tool should eliminate the tedious craft→inventory→hotbar→place loop for structural building, replacing it with a streamlined select→place workflow.

## Behavioral Contracts

### Contract #10: Recipe Selection Does NOT Consume Resources

Selecting a recipe at the Builders Bench (via the Block_Placeholder input slot) transforms the placeholder's visual state and stores the selected recipe reference. No resources are deducted. The player can freely browse, change their mind, and re-select without cost.

**Why this matters:** The building tool's value proposition is deferred consumption. If resources were consumed at selection time, the tool would just be a regular crafting workflow with extra steps.

### Contract #11: Atomic Resource Consumption at Placement Time

When the player right-clicks to place a block:
1. The system identifies the armed recipe and its (already-scaled) input requirements
2. It checks the player's inventory AND nearby chests (within the configurable bench radius)
3. If ALL required inputs are available: deduct atomically, place the block
4. If ANY required input is missing: deny placement, consume nothing, show red indicator

**Partial consumption must never occur.** If a recipe requires 48 stone and 12 fibre, and the player has 48 stone but only 6 fibre, NOTHING is consumed and NO block is placed.

### Contract #12: Mutual Exclusion with PlacementCostScaler

Each `PlaceBlockEvent` is handled by exactly ONE cost system:

| Placed Item | Cost System | Cost |
|-------------|-------------|------|
| Natural block item (stone, wood, dirt) | PlacementCostScaler | 12× of the item (11 extra consumed) |
| Armed Block_Placeholder (PlaceBlock tool) | PlaceBlock resource consumption | Recipe's scaled inputs from inventory/chests |
| Unarmed Block_Placeholder | Neither — placement denied | N/A |
| Crafted block item from inventory | Neither — standard 1:1 placement | 1× of the item (vanilla behavior) |

Both systems subscribe to `PlaceBlockEvent`. They must check the placed item and early-exit when the event is not theirs. Under no circumstances should both systems deduct resources for the same placement.

### Contract #13: Real-Time Resource Availability Indicators

The placeholder's rarity/quality indicator is a **promise to the player**:

| Indicator | Meaning | Trigger |
|-----------|---------|---------|
| Blue (Tool quality) | No recipe selected | Default state, or recipe cleared |
| Green (Uncommon quality) | Recipe selected, resources sufficient | Inventory check passes |
| Red (Developer quality) | Recipe selected, resources insufficient | Inventory check fails |

The indicator updates on:
- Recipe selection/change at the bench
- Inventory change events (item pickup, chest interaction, crafting)
- Successful placement (resources decreased — may transition green→red)
- Failed placement (indicator stays red)

**The green indicator must never lie.** If a player sees green and right-clicks, placement should succeed unless another system (e.g., concurrent chest access) consumed the resources between the indicator update and the click. In that case, placement fails cleanly (Contract #11) and the indicator transitions to red.

### Contract #14: Placed Blocks Are Indistinguishable

Once a block is placed via the PlaceBlock tool, it is identical to the same block type placed from inventory or by any other means:
- Same block type ID in the world
- Same breaking behavior (Contract #4 from vision.md)
- Same drops on break (recipe ingredients at scaled quantity)
- No metadata distinguishing placement source
- Another player breaking this block gets the same experience as breaking any other instance

## Edge Cases & Decisions

| Scenario | Decision | Rationale |
|----------|----------|-----------|
| Player selects recipe, walks far from chests, tries to place | Check only inventory (no chests in range) | Chest radius is relative to player position at placement time, not bench position |
| Player arms placeholder, logs out, logs back in | Placeholder retains armed recipe | Recipe selection is item state, not session state |
| Player dies while holding armed placeholder | Placeholder drops with armed recipe intact | Same as any tool — drops on death with its state |
| Two players arm placeholders with same recipe, share a chest | Each placement is an independent atomic transaction — first to place gets the resources, second sees red if insufficient | No reservation system; real-time availability only |
| Player tries to arm placeholder with a base block recipe | Allowed — cost is the unscaled recipe input quantity | Base recipes participate in the PlaceBlock flow; their inputs are already implicitly scaled by natural drop rates |
| Player tries to arm placeholder with a non-block recipe (e.g., Rope) | Denied — PlaceBlock tool only works with block-output recipes | Non-block items can't be "placed" — they go through the normal crafting flow |
| Player right-clicks in an invalid location (water, occupied block) | Standard engine placement validation applies — no resources consumed | The PlaceBlock tool defers to the engine's `PlaceBlockSettings` validation |
| PlaceBlock placement is cancelled by another plugin | Resources are NOT consumed if the block isn't actually placed | Consumption must be contingent on successful placement |

## Anti-Patterns to Reject

- **Consuming resources at recipe selection time.** This destroys the tool's core value and creates a confusing refund flow.
- **Storing placement-source metadata on blocks.** "How it was placed" is an implementation detail, not a game mechanic. Blocks must be blocks.
- **Reserving resources when a recipe is selected.** Resource reservation across time introduces complexity (what if the player never places? what if they switch recipes?) with no player benefit. Real-time availability checks are simpler and sufficient.
- **Allowing PlaceBlock tool for non-block outputs.** The tool's identity is "place blocks into the world." Crafting Rope via a placeholder would be confusing — that's what the regular crafting flow (and Portable Bench) are for.
- **Using a separate resource consumption calculation from the recipe's stored inputs.** The PlaceBlock tool must read the recipe's `MaterialQuantity[]` array directly — the same values that DropScaler already scaled. Any parallel calculation risks drift from the economy pipeline.
