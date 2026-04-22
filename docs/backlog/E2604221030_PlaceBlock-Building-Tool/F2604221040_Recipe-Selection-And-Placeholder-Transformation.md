---
id: F2604221040
type: feature
title: "Recipe Selection & Placeholder Transformation"
status: backlog
priority: high
epic: E2604221030
created: 2026-04-22
updated: 2026-04-22
---

# Recipe Selection & Placeholder Transformation

## Description
When a `Block_Placeholder` is in the **Blueprint Bench** input slot and the player selects a recipe, the system intercepts the crafting event (`CraftRecipeEvent.Pre`), cancels standard crafting, and instead arms the placeholder with the selected recipe's metadata. The placeholder's quality swaps to indicate status. No resources are consumed during selection (Contract #10).

## Acceptance Criteria

### Checklist
- [ ] `CraftRecipeEvent.Pre` is intercepted when the input item is a `Block_Placeholder` at the Blueprint Bench
- [ ] Standard crafting is cancelled — no items consumed, no output produced
- [ ] The placeholder is armed with the recipe ID and target block type via BsonDocument metadata
- [ ] The armed placeholder's quality swaps to Green (Uncommon) if resources are available
- [ ] Recipe selection does NOT consume any resources (Contract #10)
- [ ] Player can select a different recipe — placeholder updates accordingly
- [ ] The armed placeholder retains its recipe reference when moved to the player's hotbar
- [ ] The armed placeholder retains its metadata across relog and server restart

### Scenarios
**Player selects a recipe at the Blueprint Bench**
- **Given** a `Block_Placeholder` is in the Blueprint Bench input slot
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
- **Given** a normal crafting ingredient (not a placeholder) is in the Blueprint Bench input slot
- **When** the player selects a recipe
- **Then** standard crafting occurs — resources consumed, output produced

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2604221215 | CraftRecipeEvent Interceptor for Placeholder | backlog |
| S2604221120 | Placeholder Arming via Metadata | backlog |
| S2604221110 | Inventory & Chest Resource Scanner | backlog |

## Notes
- The interception mechanism is `CraftRecipeEvent.Pre` — an ECS event that fires before crafting and is cancellable (confirmed by Hytale Expert).
- Icon transformation is NOT possible per-instance (R2 denied). The placeholder always shows the same generic icon regardless of armed recipe. Recipe identity is communicated via: BsonDocument metadata on the item, chat messages, and block preview during placement.
- Metadata persistence confirmed (R1): BsonDocument survives relog, death, drops, chest storage.
- The Architect's design introduces `PlaceBlockBenchInterceptor` — a new component replacing the old `PlaceBlockSelectorWindow`.
- The Portable Bench system is NOT involved in this feature.
