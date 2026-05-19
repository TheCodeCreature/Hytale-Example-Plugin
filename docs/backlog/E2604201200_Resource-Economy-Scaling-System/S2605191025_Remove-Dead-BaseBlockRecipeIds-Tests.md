---
id: S2605191025
type: story
title: "Remove Dead baseBlockRecipeIds from Test Infrastructure"
status: done
priority: critical
feature: F2605191000
epic: E2604201200
created: 2026-05-19
---

# Remove Dead baseBlockRecipeIds from Test Infrastructure

## User Story
As a **developer**, I want **test infrastructure cleaned up to remove references to the deleted baseBlockRecipeIds field** so that **tests compile and pass instead of failing with reflection errors**.

## Acceptance Criteria

### Checklist
- [ ] `AssetTestHelper.setBenchRecipeRegistries()` — remove `baseBlockRecipeIds` parameter from both overloads
- [ ] `AssetTestHelper.setBenchRecipeRegistries()` — remove `setField(BenchRecipeRegistry.class, reg, "baseBlockRecipeIds", ...)` call
- [ ] `TestDataSet.baseBlockRecipeIds` field removed
- [ ] `TestDataSet.install()` — `setBenchRecipeRegistries()` call updated (no baseBlockRecipeIds map)
- [ ] `ResourceScalingIntegrationTest.BaseBlockBehavior` nested test class — update to test leaf-only scaling behavior instead of base-block exclusion
  - `planksKeep1xCost` → planks recipe uses raw input (Wood_Log_Oak), so it IS scaled ×12. Test should assert `inputs[0].getQuantity() == 12`
  - `planksDropSelf` → planks is a recipe block, so Phase 3a creates a synthetic drop list. Test should verify the drop list exists (not that breaking returns Wood_Planks_Oak directly)
- [ ] All 42 previously failing tests now pass
- [ ] No compilation errors

### Scenarios
**Test Helper Without baseBlockRecipeIds**
- **Given** `AssetTestHelper.setBenchRecipeRegistries()` is called without a baseBlockRecipeIds parameter
- **When** Tests run
- **Then** No reflection error on BenchRecipeRegistry

**Planks Recipe Scaled as Raw Input**
- **Given** TestDataSet with Wood_Log_Oak as natural item, Planks recipe input is Wood_Log_Oak
- **When** `applyFullPipeline()` runs with RecipeTierClassifier
- **Then** Planks recipe input quantity is 12 (raw input scaled)

## Notes
- Root cause: `BenchRecipeRegistry.baseBlockRecipeIds` field was removed from production code but test infrastructure still references it via reflection
- The `BaseBlockBehavior` tests asserted old behavior (base blocks keep 1× cost). Under leaf-only scaling, planks use a raw input, so the cost IS scaled ×12. The test assertions must change.
- Must also ensure `RecipeTierClassifier` is initialized in the test pipeline
