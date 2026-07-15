---
area: "PlaceBlock Building Tool"
updated: 2026-04-26
---

# PlaceBlock Building Tool — Behavioral Contract

## Player Experience Goal

Building with crafted blocks should feel like **painting with a loaded brush**. The player selects what they want to build at the **Stencil Crafting** — a dedicated workbench block placed in the world — then walks into the world and places blocks fluidly — one click per block, with a live preview showing exactly where the block will go and whether they can afford it. The tool should eliminate the tedious craft→inventory→hotbar→place loop for structural building, replacing it with a streamlined select→place workflow.

## Behavioral Contracts

### Contract #10: Recipe Selection Does NOT Consume Resources

Selecting a recipe at the Stencil Crafting (via the Block_Placeholder input slot) transforms the placeholder's visual state and stores the selected recipe reference. No resources are deducted. The player can freely browse, change their mind, and re-select without cost.

**Why this matters:** The building tool's value proposition is deferred consumption. If resources were consumed at selection time, the tool would just be a regular crafting workflow with extra steps.

### Contract #11: Atomic Resource Consumption at Placement Time

When the player right-clicks to place a block:
1. The system identifies the armed recipe and its (already-scaled) input requirements
2. It checks the player's storage, backpack, and non-active hotbar slots for available resources. The active hotbar slot (holding the placeholder tool) is excluded from both availability checks and resource consumption to ensure visual continuity of the held tool.
3. If ALL required inputs are available: deduct atomically, place the block
4. If ANY required input is missing: deny placement, consume nothing, show red indicator

**Partial consumption must never occur.** If a recipe requires 48 stone and 12 fibre, and the player has 48 stone but only 6 fibre, NOTHING is consumed and NO block is placed.

**The active hotbar slot is always protected.** The placeholder tool consumes from the player's material stores — storage, backpack, and non-active hotbar slots — never from the slot the player is holding. This ensures the tool's icon, block preview, and rarity indicator remain visually stable during rapid placement.

### Contract #12: Mutual Exclusion with PlacementCostScaler

Each `PlaceBlockEvent` is handled by exactly ONE cost system:

| Placed Item | Cost System | Cost |
|-------------|-------------|------|
| Natural block item (stone, wood, dirt) | PlacementCostScaler | 12× of the item (11 extra consumed) |
| Armed Block_Placeholder (PlaceBlock tool) | PlaceBlock resource consumption | Recipe's scaled inputs from inventory |
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
- Inventory change events (item pickup, crafting)
- Successful placement (resources decreased — may transition green→red)
- Failed placement (indicator stays red)

**The green indicator must never lie.** If a player sees green and right-clicks, placement should succeed unless another system (e.g., concurrent resource consumption) consumed the resources between the indicator update and the click. In that case, placement fails cleanly (Contract #11) and the indicator transitions to red.

### Contract #14: Placed Blocks Are Indistinguishable

Once a block is placed via the PlaceBlock tool, it is identical to the same block type placed from inventory or by any other means:
- Same block type ID in the world
- Same breaking behavior (Contract #4 from vision.md)
- Same drops on break (recipe ingredients at scaled quantity)
- No metadata distinguishing placement source
- Another player breaking this block gets the same experience as breaking any other instance

### Contract #15: The Stencil Crafting Is a Separate Block

The Stencil Crafting is a **new workbench block**, distinct from the existing Builders Bench (`Bench_Builders`). It does not modify, replace, or extend the Builders Bench's configuration or asset. The Builders Bench continues to function as a standard crafting station (craft → inventory). The Stencil Crafting does not produce crafted recipe outputs. It may dispense the Block_Placeholder tool itself as a utility acquisition (see Contract #17).

The Stencil Crafting block is cloned from the Builders Bench asset but has its own block type identity (`Bench_Stencil` or equivalent). The existing Builders Bench must remain unchanged.

**Why this matters:** Merging the PlaceBlock workflow into the Builders Bench would confuse two distinct player intents: "I want to craft items into my inventory" vs. "I want to arm a tool and go build directly." Separate benches keep these workflows clear.

### Contract #16: The Stencil Crafting Aggregates All Placeable Recipes

The Stencil Crafting displays placeable block and furniture recipes from **all registered benches** — not just the Builders Bench categories. Any recipe whose output is a placeable block or furniture item is available at the Stencil Crafting for the PlaceBlock arming workflow, regardless of which bench originally defines that recipe.

**Why this matters:** The Stencil Crafting is the universal "I want to build" station. Limiting it to Builders Bench categories would force players to arm different tools at different benches. A single point of access for all placeable recipes matches the tool's identity: "select what you want to build, then go build it."

### Contract #17: Placeholder Acquisition at the Stencil Crafting

The Stencil Crafting provides a "Get Placeholder" button that lets a player acquire a Block_Placeholder tool for 1× `Ingredient_Life_Essence`. This is a **flat-rate utility cost**, exempt from 12× recipe scaling — it is a tool access fee, not a crafting recipe.

| Aspect | Behavior |
|--------|----------|
| Cost | 1× `Ingredient_Life_Essence` per placeholder |
| Output | 1× `Block_Placeholder` (unarmed, Blue/Tool quality) |
| Repeatable | Yes — player can acquire as many as they want |
| Economy participation | None — exempt from 12× scaling |
| Inventory full | Placeholder drops at player's feet |

**Why this matters:** Without a survival acquisition path, the Block_Placeholder is only obtainable via commands. The Stencil Crafting is the natural discovery point — the player encounters the tool exactly where they'll use it. The cheap cost (1× Life Essence) gates initial access without creating an economic burden, since the placeholder is a durable tool that persists across sessions and survives death.

## Edge Cases & Decisions

| Scenario | Decision | Rationale |
|----------|----------|-----------|
| Player arms placeholder, logs out, logs back in | Placeholder retains armed recipe | Recipe selection is item state, not session state |
| Player dies while holding armed placeholder | Placeholder drops with armed recipe intact | Same as any tool — drops on death with its state |
| Two players deplete the same resource simultaneously | Each placement is an independent atomic transaction — first to place gets the resources, second sees red if insufficient | No reservation system; real-time availability only |
| Player tries to arm placeholder with a base block recipe | Allowed — cost is the unscaled recipe input quantity | Base recipes participate in the PlaceBlock flow; their inputs are already implicitly scaled by natural drop rates |
| Player tries to arm placeholder with a non-block recipe (e.g., Rope) | Denied — PlaceBlock tool only works with block-output recipes | Non-block items can't be "placed" — they go through the normal crafting flow |
| Player right-clicks in an invalid location (water, occupied block) | Standard engine placement validation applies — no resources consumed | The PlaceBlock tool defers to the engine's `PlaceBlockSettings` validation |
| PlaceBlock placement is cancelled by another plugin | Resources are NOT consumed if the block isn't actually placed | Consumption must be contingent on successful placement |
| Player interacts with Stencil Crafting without holding a Block_Placeholder | Standard bench UI opens (or interaction is denied) — no PlaceBlock flow | The PlaceBlock flow requires a placeholder item to arm. Without one, the bench either behaves as a normal bench or rejects the interaction |
| Player tries to arm placeholder at the Portable Bench (F-press) | Denied — the Portable Bench does not support PlaceBlock arming | The Portable Bench is a craft-to-inventory tool. PlaceBlock arming is exclusive to the Stencil Crafting |

## Anti-Patterns to Reject

- **Consuming resources at recipe selection time.** This destroys the tool's core value and creates a confusing refund flow.
- **Storing placement-source metadata on blocks.** "How it was placed" is an implementation detail, not a game mechanic. Blocks must be blocks.
- **Reserving resources when a recipe is selected.** Resource reservation across time introduces complexity (what if the player never places? what if they switch recipes?) with no player benefit. Real-time availability checks are simpler and sufficient.
- **Allowing PlaceBlock tool for non-block outputs.** The tool's identity is "place blocks into the world." Crafting Rope via a placeholder would be confusing — that's what the regular crafting flow (and Portable Bench) are for.
- **Using the Portable Bench (F-press handheld) for PlaceBlock arming.** The Portable Bench is a craft-anywhere convenience tool that produces inventory items. The Stencil Crafting is a physically-placed workbench block that arms placeholder tools for deferred placement. These are incompatible workflows.
- **Modifying the existing Builders Bench to support PlaceBlock arming.** The Builders Bench is a standard crafting station. The Stencil Crafting is a new, separate block (Contract #15). Merging them would create a confusing dual-purpose UI.
- **Limiting the Stencil Crafting to Builders Bench categories only.** The Stencil Crafting aggregates all placeable recipes from all registered benches (Contract #16). Restricting scope would force players to arm tools at multiple benches, defeating the tool's purpose.
- **Using a separate resource consumption calculation from the recipe's stored inputs.** The PlaceBlock tool must read the recipe's `MaterialQuantity[]` array directly — the same values that DropScaler already scaled. Any parallel calculation risks drift from the economy pipeline.
