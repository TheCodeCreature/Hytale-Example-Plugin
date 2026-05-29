---
id: S2605291040
type: story
title: "Update BenchBlockClassifier for Dynamic Categories"
status: backlog
priority: high
feature: F2605291005
epic: E2605291000
created: 2026-05-29
---

# Update BenchBlockClassifier for Dynamic Categories

## User Story
As a **plugin developer**, I want **BenchBlockClassifier to work with dynamic bench data** so that **blocks from any bench are classified and partitioned for processing**.

## Acceptance Criteria

### Checklist
- [ ] `BenchBlockClassifier` no longer depends on `BenchCategory` enum
- [ ] Classification uses `BenchRegistry` to determine which benches a block's recipe belongs to
- [ ] `getBlocksByCategory()` is replaced with a method that partitions blocks by their bench ID set (e.g., blocks with `{"Builders"}`, blocks with `{"Workbench"}`, blocks with `{"Builders", "Workbench"}`)
- [ ] The classifier produces partitions suitable for parallel processor dispatch
- [ ] Each partition includes the `preferNatural` resolution for that bench combination

### Scenarios
**Single-bench partition**
- **Given** 50 blocks have recipes for only "Workbench"
- **When** classifier runs
- **Then** a partition `{"Workbench"}` contains those 50 block IDs with `preferNatural=false`

**Multi-bench overlap partition**
- **Given** 10 blocks have recipes for both "Builders" and "Furniture_Bench"
- **When** classifier runs
- **Then** a partition `{"Builders", "Furniture_Bench"}` contains those 10 blocks with `preferNatural=true` (Furniture wins)

## Notes
- The partition key could be a `Set<String>` of bench IDs, or a new lightweight record.
- `preferNatural` for overlaps: `true` if ANY bench in the set has `preferNatural=true`.
