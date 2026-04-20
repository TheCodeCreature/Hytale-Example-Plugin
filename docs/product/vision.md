---
area: "Resource Economy"
updated: 2026-04-18
---

# Resource Economy — Product Vision

## Player Experience Goal

Gathering resources should feel **rewarding and proportional**. When a player breaks a natural rock, they receive a generous yield (12× the vanilla amount), making exploration feel productive. When they craft at a bench, the recipe costs scale to match — a wall that normally costs 4 stone now costs 48, but that's exactly what 4 natural blocks gave them. The result is that **the craft-place-break loop feels fair and round-trips cleanly**: you gather materials, craft something, place it, and if you change your mind and break it, you get those materials back — not the crafted block itself.

The system should be **invisible to the player** once configured. No lag at startup, no unexplained recipe cost mismatches, no items that break the economy because they were missed. An admin configures it once, and it just works.

## Behavioral Contracts

1. **Natural blocks drop 12× their vanilla quantity.** A rock that drops 1 cobblestone now drops 12.
2. **Natural resource stack sizes scale to 12×** their vanilla maximum to accommodate the increased yield.
3. **Bench recipes cost 12× their vanilla input quantities.** If a fence costs 2 planks, it now costs 24. This applies to ALL recipes at registered benches — block outputs AND non-block outputs (Rope, Fibre, tools, ingredients).
4. **Breaking a crafted block drops its recipe ingredients**, not the block itself. The quantities match the scaled recipe cost divided by output quantity.
5. **Placing a natural block consumes 12× of the item** (enforced at runtime by PlacementCostScaler), so the place-break loop is symmetric.
6. **Base block recipes are excluded from cost scaling** — these are block recipes where every input is a raw natural resource (e.g., 1× Rock_Stone → Rock_Stone_Cobble). Their costs are implicitly scaled by Contract #1 (the natural drops are already 12×).
7. **Processed ingredients are NOT natural items.** Items like `Ingredient_Fibre` (crafted from `Plant_Fiber` at another bench) are processed materials. A recipe using `Ingredient_Fibre` is NOT a base recipe and MUST have its costs scaled.
8. **The economy configuration persists to disk.** Once calculated, modifications are saved so they do not need to be recomputed on every server boot.
9. **Admins can inspect, recalculate, and reset** the economy via in-game commands at any time.

## UX Flow

```mermaid
flowchart LR
    A["Player gathers 1 stone block"] --> B["Receives 12 cobblestone"]
    B --> C["Crafts 1 cobble wall\n(costs 48 cobblestone)"]
    C --> D["Places cobble wall"]
    D --> E["Breaks cobble wall"]
    E --> F["Receives 48 cobblestone back"]
    F --> C
```

```mermaid
flowchart LR
    G["Player gathers bush\n(receives 12 Plant_Fiber)"] --> H["Crafts Ingredient_Fibre\nat Workbench\n(costs 12 Plant_Fiber)"]
    H --> I["Crafts Deco_Rope\nat Builders bench\n(costs 12 Ingredient_Fibre)"]
    I --> J["Places Deco_Rope"]
    J --> K["Breaks Deco_Rope"]
    K --> L["Receives 12 Ingredient_Fibre"]
    L --> I
```

## Edge Cases & Decisions

| Scenario | Decision | Rationale |
|----------|----------|-----------|
| Recipe uses `Ingredient_Fibre` (a processed item, not a raw natural drop) | Scale the recipe cost ×12 | `Ingredient_Fibre` is NOT a natural item — it's crafted from `Plant_Fiber`. The recipe is not a "base" recipe. |
| Recipe output is a non-block item (e.g., Rope, Thread, Bolt_Wool) | Still scale its recipe cost ×12 | All bench recipes must participate in the economy, regardless of whether the output is placeable. |
| Recipe output is a block item classified as "base" (all inputs are raw natural resources) | Do NOT scale its recipe cost | The inputs already drop at 12× from natural blocks (Contract #1). Scaling would make them cost 144×. |
| Recipe at a bench not registered (e.g., Farmingbench, Stonecutter) | Do NOT scale | Only registered bench recipes participate. Expansion can add benches later. |
| Block destroyed by physics cascade | Same drops as manual breaking | Player expectation: blocks don't vanish into nothing. |
| Block placed by another player is broken | Same behavior — drops recipe ingredients | Consistency: all instances of a block type behave identically. |
| Server restarts after economy is configured | Economy persists — no recomputation | Startup speed and determinism. Recomputation only via explicit command. |
| Economy manifest is stale (game update changed recipes) | Admin runs recalculate command | The manifest includes a version/hash so staleness can be detected. |

## Anti-Patterns to Reject

- **Classifying processed ingredients as "natural items"** because they appear in a natural drop chain somewhere upstream. `Ingredient_Fibre` is not `Plant_Fiber`. The classification must look at the direct recipe inputs, not the transitive ingredient tree.
- **Recomputing asset modifications on every server boot.** This is slow, fragile, and prevents admins from verifying what was changed. Modifications must be persistable and inspectable.
- **Skipping non-block recipes** because they "don't have a block to modify." The recipe cost still needs scaling. Only the *drop modification* (Phase 4a) is block-specific.
- **Using the `NaturalResourceRegistry.isNaturalItem()` check to determine if a recipe input is a "base" input.** This check returns true for any item that ANY natural block can drop — but `Ingredient_Fibre` is not in that set. The correct check is whether the input resolves to an item that is exclusively a raw, unprocessed natural drop. Processed intermediaries must not be conflated with raw drops.
