---
id: S2605291055
type: story
title: "Remove Hardcoded Processor Classes"
status: backlog
priority: medium
feature: F2605291010
epic: E2605291000
created: 2026-05-29
---

# Remove Hardcoded Processor Classes

## User Story
As a **plugin developer**, I want **the old per-bench processor classes deleted** so that **there's no dead code or confusion about which processor path is used**.

## Acceptance Criteria

### Checklist
- [ ] `BuildersProcessor.java` is deleted
- [ ] `FurnitureProcessor.java` is deleted
- [ ] `OverlapProcessor.java` is deleted
- [ ] `BenchCategory.java` enum is deleted (or reduced if any non-bench-ID responsibilities remain)
- [ ] All references to deleted classes are removed
- [ ] Build compiles and all tests pass

### Scenarios
**Clean compilation**
- **Given** all three processor classes and BenchCategory are deleted
- **When** the project compiles
- **Then** zero compilation errors

## Notes
- This should be the last story in the feature — only after GenericBenchProcessor and DropScaler refactor are verified working.
- If `BenchCategory` has any remaining callers for `preferNatural` logic, those need to be migrated to `BenchRegistry` first.
