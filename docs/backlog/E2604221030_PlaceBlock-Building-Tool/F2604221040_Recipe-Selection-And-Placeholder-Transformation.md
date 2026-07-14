---
id: F2604221040
type: feature
title: "Recipe Selection & Placeholder Transformation"
status: in-progress
priority: high
epic: E2604221030
created: 2026-04-22
updated: 2026-04-24
---

# Recipe Selection & Placeholder Transformation

## Description
Arms a `Block_Placeholder` with a target recipe so the player can place blocks in-world. The arming mechanism is delivered in phases:

- **Phase 2a (active):** `/placeblock` command for testing — `assign`, `clear`, `list`, `info` subcommands
- **Phase 2b (backlog):** Custom `InteractiveCustomUIPage` with recipe browser + "Select" button

**UI Pivot (2026-04-24):** The original approach (CraftRecipeEvent.Pre interceptor at StructuralCraftingWindow) was abandoned because the client dims all shadow recipes — the placeholder item doesn't match the recipe's expected input material client-side. The command approach bypasses this entirely, and the custom UI will replace StructuralCraftingWindow altogether.

## Acceptance Criteria

### Checklist
- [ ] `CraftRecipeEvent.Pre` is intercepted when the input item is a `Block_Placeholder` at the Stencil Crafting
- [ ] Standard crafting is cancelled — no items consumed, no output produced
- [ ] The placeholder is armed with the recipe ID and target block type via BsonDocument metadata
- [ ] The armed placeholder's quality swaps to Green (Uncommon) if resources are available
- [ ] Recipe selection does NOT consume any resources (Contract #10)
- [ ] Player can select a different recipe — placeholder updates accordingly
- [ ] The armed placeholder retains its recipe reference when moved to the player's hotbar
- [ ] The armed placeholder retains its metadata across relog and server restart

### Scenarios
**Player selects a recipe at the Stencil Crafting**
- **Given** a `Block_Placeholder` is in the Stencil Crafting input slot
- **When** the player clicks a recipe (e.g., "Cobble Wall")
- **Then** crafting is cancelled, the placeholder is armed with "Cobble Wall" recipe metadata

**Placeholder swaps quality on arming**
- **Given** the placeholder was Blue (unarmed)
- **When** the player arms it with a recipe and has sufficient resources
- **Then** the placeholder swaps to Green (Uncommon)

**Player changes recipe selection**
- **Given** the placeholder is armed with "Cobble Wall"
- **When** the player selects "Stone Fence" instead
- **Then** the placeholder's metadata updates to "Stone Fence", no resources consumed

**Metadata persists after bench close**
- **Given** the player has armed the placeholder at the bench
- **When** they close the bench
- **Then** the placeholder retains the armed recipe in their inventory

**Normal crafting unaffected**
- **Given** a normal crafting ingredient (not a placeholder) is in the Stencil Crafting input slot
- **When** the player selects a recipe
- **Then** standard crafting occurs — resources consumed, output produced

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2604221215 | CraftRecipeEvent Interceptor for Placeholder | cancelled — StructuralCraftingWindow approach abandoned |
| S2604221120 | Placeholder Arming via Metadata | done |
| S2604240900 | PlaceBlock Command for Testing | in-progress |
| S2604240920 | Custom UI Feasibility Spike | backlog |
| S2604240910 | Custom Blueprint Selection UI | backlog |

## Notes
- **Pivot History:** The original CraftRecipeEvent.Pre + StructuralCraftingWindow approach was implemented and tested (S2604221215). Server-side interception worked, but the client dims all shadow recipes because the placeholder doesn't match the recipe's expected crafting input. This is a fundamental StructuralCraftingWindow limitation, not a bug.
- **What Survives:** `PlaceBlockMetadata` (unchanged), `BlueprintBookRecipeMutator` (shadow recipes still useful as a recipe catalog), `PlaceBlockPlacementSystem` (unchanged, Phase 3).
- **What's Removed:** `PlaceBlockBenchInterceptor` will be removed once the command (Phase 2a) validates the pipeline. No longer needed.
- Icon transformation is NOT possible per-instance (R2 denied). The placeholder always shows a generic icon regardless of armed recipe.
- Metadata persistence confirmed (R1): BsonDocument survives relog, death, drops, chest storage.
