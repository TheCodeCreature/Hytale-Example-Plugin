---
id: S2604240910
type: story
title: "Custom Stencil Selection UI"
status: backlog
priority: high
feature: F2604221040
epic: E2604221030
created: 2026-04-24
---

# Custom Stencil Selection UI

## User Story
As a **player**, I want **a dedicated Stencil Crafting UI with a recipe browser and "Select" button** so that **I can visually browse available recipes and arm my placeholder without using commands**.

## Acceptance Criteria

### Checklist
- [ ] Stencil Crafting opens a custom `InteractiveCustomUIPage` (not StructuralCraftingWindow)
- [ ] Left panel: input slot accepting only Block_Placeholder items
- [ ] Center panel: scrollable/filterable recipe list showing placeable block recipes
- [ ] Recipes filtered by player inventory (show all, distinguish affordable vs. not)
- [ ] Right panel: recipe details (name, inputs, output preview)
- [ ] "Select" button arms the placeholder (Contract #10 — no resource consumption)
- [ ] All three placeholder colors (Blue/Green/Red) work in the input
- [ ] `PlaceBlockBenchInterceptor` is removed — no longer needed

### Scenarios
**Open Stencil Crafting**
- **Given** the Stencil Crafting block is placed in the world
- **When** the player interacts with it
- **Then** the custom selection UI opens (NOT the standard crafting window)

**Select a recipe**
- **Given** the UI is open with a placeholder in the input
- **When** the player browses recipes and clicks "Select" on "Cobble Wall"
- **Then** the placeholder is armed, quality swaps to Green

## Notes
- Depends on Phase A.5 spike: test `appendInline()` expressiveness, `.ui` file shipping, bench interaction override
- Three open risks: R1 (inline UI limits), R2 (plugin .ui files), R3 (preventing StructuralCraftingWindow auto-open)
- This replaces S2604221215 (CraftRecipeEvent Interceptor) as the arming mechanism
