---
id: S2604272120
type: story
title: "Update Tests for Uniform Scaling"
status: backlog
priority: high
feature: F2604272100
epic: E2604201200
created: 2026-04-27
---

# Update Tests for Uniform Scaling

## User Story
As a **developer**, I want **tests updated to verify uniform scaling behavior** so that **the simplified pipeline is validated and regressions are caught**.

## Acceptance Criteria

### Checklist
- [ ] Remove base-block test cases from BenchRecipeRegistryTest (`baseBlockRecipeIdentified()`, `nonBaseBlockRecipeIdentified()`, `isBaseBlockTypeWorks()`)
- [ ] Remove `IsResourceTypeExclusivelyNatural` nested test class from ResourceTypeResolverTest
- [ ] Remove `baseBlockRecipeIds` from TestDataSet and AssetTestHelper
- [ ] Add assertions that previously-base recipes ARE now scaled 12x
- [ ] Add assertions that ALL natural drop quantities are scaled (not just ingredients)
- [ ] All tests pass

### Scenarios
**Previously-Base Recipe Now Scaled**
- **Given** A recipe with all-natural inputs (e.g., Trunk → Planks)
- **When** Pipeline runs
- **Then** Recipe input quantities are 12x vanilla values

## Notes
- Migration step 8 in design doc §12
- Run after all code changes compile
