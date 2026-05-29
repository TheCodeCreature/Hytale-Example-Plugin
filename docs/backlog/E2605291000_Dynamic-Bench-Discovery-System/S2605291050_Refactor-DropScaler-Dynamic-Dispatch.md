---
id: S2605291050
type: story
title: "Refactor DropScaler for Dynamic Processor Dispatch"
status: backlog
priority: high
feature: F2605291010
epic: E2605291000
created: 2026-05-29
---

# Refactor DropScaler for Dynamic Processor Dispatch

## User Story
As a **plugin developer**, I want **DropScaler to create processors dynamically from BenchRegistry** so that **new benches get drop scaling without code changes**.

## Acceptance Criteria

### Checklist
- [ ] `DropScaler.applyModifications()` no longer creates hardcoded `List.of(new BuildersProcessor(), ...)`
- [ ] Processors are created from `BenchBlockClassifier`'s dynamic partitions
- [ ] One `GenericBenchProcessor` is created per partition (bench ID set + preferNatural)
- [ ] Parallel dispatch still works — each processor runs in a virtual thread
- [ ] Total modified/skipped counts are still reported correctly

### Scenarios
**Dynamic dispatch with 4 benches**
- **Given** BenchRegistry has Builders, Furniture_Bench, Workbench, and Loom
- **When** BenchBlockClassifier finds partitions: {Builders}, {Furniture_Bench}, {Workbench}, {Loom}, {Builders,Workbench}
- **Then** DropScaler creates 5 GenericBenchProcessor instances and runs them in parallel

## Notes
- The `BenchRegistry.init()` call needs to be added to `DropScaler.apply()` before `RecipeFilterRegistry.init()`.
- Init order: NaturalResourceRegistry → RecipeTierClassifier → BenchRegistry → RecipeFilterRegistry → BenchRecipeRegistries → applyModifications
