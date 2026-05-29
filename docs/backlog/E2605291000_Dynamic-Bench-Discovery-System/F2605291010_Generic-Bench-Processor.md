---
id: F2605291010
type: feature
title: "Generic Bench Processor"
status: backlog
priority: high
epic: E2605291000
created: 2026-05-29
---

# Generic Bench Processor

## Description
Replace the three hardcoded processor classes (`BuildersProcessor`, `FurnitureProcessor`, `OverlapProcessor`) with a single parameterized `GenericBenchProcessor` that accepts a bench ID and its `preferNatural` flag. `DropScaler` dynamically creates one processor instance per discovered bench category (single-bench and overlap combinations).

## Acceptance Criteria

### Checklist
- [ ] A `GenericBenchProcessor` exists that takes bench configuration at construction time
- [ ] `DropScaler` creates processor instances dynamically from `BenchRegistry` data
- [ ] Blocks belonging to multiple benches get an overlap processor with configurable preference resolution
- [ ] `BuildersProcessor`, `FurnitureProcessor`, and `OverlapProcessor` are deleted
- [ ] Drop scaling results are identical for Builders and Furniture recipes (regression-safe)
- [ ] New benches get drop scaling applied automatically

### Scenarios
**Generic processor for a new bench**
- **Given** BenchRegistry has discovered bench "Workbench" with `preferNatural=false`
- **When** DropScaler creates processors
- **Then** a `GenericBenchProcessor("Workbench", preferNatural=false)` processes Workbench-only blocks

**Overlap handling for dynamic benches**
- **Given** a recipe declares BenchRequirements for both "Builders" and "Workbench"
- **When** BenchBlockClassifier classifies the block
- **Then** the block gets an overlap processor whose `preferNatural` is resolved from the bench configs

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605291045 | Create GenericBenchProcessor | backlog |
| S2605291050 | Refactor DropScaler for Dynamic Processor Dispatch | backlog |
| S2605291055 | Remove Hardcoded Processor Classes | backlog |

## Notes
- `AbstractBenchProcessor` already contains all the shared logic — the subclasses only return a `BenchCategory`. The generic processor can extend it directly with a constructor parameter.
- Overlap resolution: when a recipe belongs to multiple benches, the processor should use `preferNatural=true` if ANY of its benches has `preferNatural=true`. This preserves the current "furniture wins" behavior.
