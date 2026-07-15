---
id: S2604221215
type: story
title: "CraftRecipeEvent Interceptor for Placeholder"
status: cancelled
priority: high
feature: F2604221040
epic: E2604221030
created: 2026-04-22
cancelled: 2026-04-24
cancellation-reason: "StructuralCraftingWindow client-side recipe dimming is unsolvable — shadow recipes with PlaceBlock input pass server-side but are dimmed client-side. Replaced by S2604240900 (command testing) and S2604240910 (custom UI)."
---

# CraftRecipeEvent Interceptor for Placeholder

## User Story
As a **player**, I want **the Stencil Crafting to intercept crafting when my placeholder is in the input slot** so that **the placeholder gets armed with the recipe instead of being consumed**.

## Acceptance Criteria

### Checklist
- [ ] `PlaceBlockBenchInterceptor` listens for `CraftRecipeEvent.Pre`
- [ ] Interceptor only fires when the bench is a Stencil Crafting (`Bench.Id: "Stencil"`)
- [ ] Interceptor only fires when a `Block_Placeholder` is in the input slot
- [ ] Interceptor cancels the crafting event — no resources consumed, no output produced
- [ ] Interceptor arms the placeholder via `PlaceBlockMetadata.setArmedRecipeId()`
- [ ] Interceptor swaps the placeholder's quality (Blue → Green/Red based on resource availability)
- [ ] Standard crafting at all other benches is completely unaffected

### Scenarios
**Placeholder at Stencil Crafting**
- **Given** a `Block_Placeholder` is in the Stencil Crafting input slot
- **When** the player clicks a recipe
- **Then** `CraftRecipeEvent.Pre` is cancelled, placeholder is armed

**Normal item at Stencil Crafting**
- **Given** a normal crafting ingredient is in the Stencil Crafting input slot
- **When** the player clicks a recipe
- **Then** standard crafting occurs normally

**Any item at Builders Bench**
- **Given** any item is in the Builders Bench input slot
- **When** the player clicks a recipe
- **Then** the interceptor does NOT fire — Builders Bench behavior is unchanged

## Notes
- `CraftRecipeEvent.Pre` is a cancellable ECS event confirmed by the Hytale Expert.
- The interceptor must return the armed placeholder to the input slot (or player inventory) after arming.
- Risk R10: Confirm that cancelling `CraftRecipeEvent.Pre` cleanly prevents all consumption.
- Risk R11: Determine how to return the placeholder to the player after intercepting.
