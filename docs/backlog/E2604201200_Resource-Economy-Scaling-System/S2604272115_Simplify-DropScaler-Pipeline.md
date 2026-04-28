---
id: S2604272115
type: story
title: "Simplify DropScaler Pipeline Phases"
status: backlog
priority: critical
feature: F2604272100
epic: E2604201200
created: 2026-04-27
---

# Simplify DropScaler Pipeline Phases

## User Story
As a **developer**, I want **the DropScaler pipeline simplified from 6 phases to 5** so that **all recipes are scaled uniformly, all natural drops scale 12x, and ingredient filtering is eliminated**.

## Acceptance Criteria

### Checklist
- [ ] Phase 1: Scale ALL recipes 12x (remove `isBaseBlockRecipe()` skip)
- [ ] Phase 2 (was 3): Classify blocks by bench category (no base-block tracking)
- [ ] Phase 3a (was 4a): Process ALL recipe blocks per category (use `getBlocksByCategory()` instead of `getNonBaseBlocksByCategory()`)
- [ ] Phase 3b (was 4b): Scale ALL natural block drops 12x (remove ingredient-only filtering)
- [ ] Phase 3b: Include Deco natural blocks in scaling (remove Deco skip TODO)
- [ ] Phase 4 (was 5): Register synthetic drop lists
- [ ] Phase 5 (was 6): Scale stack sizes
- [ ] Remove `collectIngredientItemIds()` method entirely
- [ ] Remove `processIngredientConfig()` method
- [ ] Remove `scaleDropListIngredients()` method
- [ ] Simplify `processNaturalBlock()` to scale ALL drops unconditionally
- [ ] Remove `ingredientItemIds` parameter from `BenchCategoryProcessor.process()` and `AbstractBenchProcessor.process()`
- [ ] Remove debug logging (`[RTR-DEBUG]` in ResourceTypeResolver)
- [ ] No compilation errors

### Scenarios
**Deco_Rope Drops Ingredients**
- **Given** Deco_Rope recipe: 1x Ingredient_Fibre (scaled to 12x)
- **When** Player breaks a placed Deco_Rope
- **Then** Player receives 12x Ingredient_Fibre (not the block itself)

**Leaf Drop List Fully Scaled**
- **Given** A leaf block with drop list containing Plant_Fiber (qty 1-2) and Berry (qty 1)
- **When** Player breaks the leaf block
- **Then** Both items have quantities scaled 12x (Plant_Fiber 12-24, Berry 12)

**All Natural Blocks Including Deco Scale**
- **Given** A Deco natural block that drops Plant_Fiber at quantity 1
- **When** Player breaks the Deco block
- **Then** Player receives 12 Plant_Fiber

## Notes
- Migration steps 6–7 in design doc §12
- This is the largest change — touches DropScaler, BenchCategoryProcessor, AbstractBenchProcessor
- Compile and test after completion
