---
id: S2604221215
type: story
title: "CraftRecipeEvent Interceptor for Placeholder"
status: backlog
priority: high
feature: F2604221040
epic: E2604221030
created: 2026-04-22
---

# CraftRecipeEvent Interceptor for Placeholder

## User Story
As a **player**, I want **the Blueprint Bench to intercept crafting when my placeholder is in the input slot** so that **the placeholder gets armed with the recipe instead of being consumed**.

## Acceptance Criteria

### Checklist
- [ ] `PlaceBlockBenchInterceptor` listens for `CraftRecipeEvent.Pre`
- [ ] Interceptor only fires when the bench is a Blueprint Bench (`Bench.Id: "Blueprint"`)
- [ ] Interceptor only fires when a `Block_Placeholder` is in the input slot
- [ ] Interceptor cancels the crafting event — no resources consumed, no output produced
- [ ] Interceptor arms the placeholder via `PlaceBlockMetadata.setArmedRecipeId()`
- [ ] Interceptor swaps the placeholder's quality (Blue → Green/Red based on resource availability)
- [ ] Standard crafting at all other benches is completely unaffected

### Scenarios
**Placeholder at Blueprint Bench**
- **Given** a `Block_Placeholder` is in the Blueprint Bench input slot
- **When** the player clicks a recipe
- **Then** `CraftRecipeEvent.Pre` is cancelled, placeholder is armed

**Normal item at Blueprint Bench**
- **Given** a normal crafting ingredient is in the Blueprint Bench input slot
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
