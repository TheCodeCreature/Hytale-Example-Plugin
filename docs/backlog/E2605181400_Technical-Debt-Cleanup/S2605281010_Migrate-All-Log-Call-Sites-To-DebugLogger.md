---
id: S2605281010
type: story
title: "Migrate All Log Call Sites to DebugLogger"
status: backlog
priority: high
feature: F2605281000
epic: E2605181400
created: 2026-05-28
---

# Migrate All Log Call Sites to DebugLogger

## User Story
As a **developer**, I want **all existing log calls to route through DebugLogger** so that **I can toggle all debug output from a single place**.

## Acceptance Criteria

### Checklist
- [ ] All ~53 active log call sites replaced with `DebugLogger.log()` or `DebugLogger.logHytale()` calls
- [ ] Each call site uses the correct subsystem enum value
- [ ] Per-class `private static final Logger LOGGER` declarations removed from migrated files
- [ ] Per-class `private static final HytaleLogger LOGGER` declarations removed from migrated files
- [ ] Two unused logger declarations removed (`StencilRadialMenuPage`, `AffordabilityCoalescer`)
- [ ] Private `log()` helper methods in 7 classes removed (RecipeTierClassifier, NaturalResourceRegistry, DropScaler, RecipeFilterRegistry, BenchRecipeRegistry, BenchRecipeRegistries, RecipeTreeResolver, BlueprintBenchRecipeMutator)
- [ ] Log message content (tags, format strings) preserved exactly
- [ ] Build compiles with zero errors after migration

### Scenarios

**Migrated JUL call**
- **Given** `BlueprintBookParticleLoop` previously called `LOGGER.info("[BlueprintBookParticle] Created loop...")`
- **When** after migration
- **Then** the call reads `DebugLogger.log(BLUEPRINT_BOOK, Level.INFO, "[BlueprintBookParticle] Created loop...")`

**Migrated HytaleLogger call**
- **Given** `StencilInputListener` previously called `LOGGER.atInfo().log("[Stencil] Use interaction...", playerRef.getUuid())`
- **When** after migration
- **Then** the call reads `DebugLogger.logHytale(STENCIL, "[Stencil] Use interaction for player %s — opening radial menu", playerRef.getUuid())`

## Notes
- 18 files to touch — see audit report for complete inventory
- Subsystem mapping: Plugin→PLUGIN, StencilInputListener/StencilVisualManager/StencilPlacementSystem→STENCIL, BlueprintBookParticleLoop/BlueprintBookPickStencilInteraction→BLUEPRINT_BOOK, BlueprintSelectionPage/DetailPanelController/BlueprintBenchPrefsStore→BLUEPRINT_BENCH, RecipeTierClassifier/NaturalResourceRegistry/DropScaler/AbstractBenchProcessor/BreakBlockDiagnostic→SCALING, RecipeFilterRegistry/BenchRecipeRegistry/BenchRecipeRegistries→REGISTRY, RecipeTreeResolver/BlueprintBenchRecipeMutator→CRAFTING, IngredientTreeGridController/IngredientTreeBuilder→INGREDIENT_TREE
