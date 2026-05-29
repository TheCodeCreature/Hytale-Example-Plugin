---
id: S2605291045
type: story
title: "Create GenericBenchProcessor"
status: backlog
priority: high
feature: F2605291010
epic: E2605291000
created: 2026-05-29
---

# Create GenericBenchProcessor

## User Story
As a **plugin developer**, I want **a single parameterized processor class** so that **any bench's blocks get drop scaling without dedicated processor classes**.

## Acceptance Criteria

### Checklist
- [ ] `GenericBenchProcessor` extends `AbstractBenchProcessor`
- [ ] Constructor accepts bench ID(s) and `preferNatural` flag
- [ ] Processing logic is identical to existing processors (all logic is in `AbstractBenchProcessor`)
- [ ] Can be constructed for single-bench or multi-bench (overlap) scenarios
- [ ] Drop output is bit-identical to current `BuildersProcessor`/`FurnitureProcessor`/`OverlapProcessor` for existing benches

### Scenarios
**Single bench construction**
- **Given** `new GenericBenchProcessor(Set.of("Workbench"), false)`
- **When** `process()` is called with Workbench-only blocks
- **Then** drops are resolved with `preferNatural=false`

**Overlap construction matching current behavior**
- **Given** `new GenericBenchProcessor(Set.of("Builders", "Furniture_Bench"), true)`
- **When** processing overlap blocks
- **Then** output is identical to current `OverlapProcessor`

## Notes
- Since `AbstractBenchProcessor` already contains all the logic, and current subclasses only override `category()`, the generic processor essentially just needs to provide the configuration through a different mechanism than an enum constant.
- The `BenchCategoryProcessor` interface currently returns `BenchCategory` from `category()` — this interface needs updating to return dynamic config instead.
