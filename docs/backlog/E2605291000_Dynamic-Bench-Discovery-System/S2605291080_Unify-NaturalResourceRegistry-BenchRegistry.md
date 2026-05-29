---
id: S2605291080
type: story
title: "Unify NaturalResourceRegistry with BenchRegistry"
status: backlog
priority: high
feature: F2605291005
epic: E2605291000
created: 2026-05-29
---

# Unify NaturalResourceRegistry with BenchRegistry

## User Story
As a **plugin developer**, I want **NaturalResourceRegistry to derive its bench IDs from BenchRegistry** so that **there is no silent divergence between hardcoded sets**.

## Acceptance Criteria

### Checklist
- [ ] `NaturalResourceRegistry.CRAFTING_BENCH_IDS` is removed or derived from `BenchRegistry.getAllBenchIds()`
- [ ] `NaturalResourceRegistry.isCraftingBench()` uses the dynamic registry
- [ ] No hardcoded bench ID strings remain in `NaturalResourceRegistry`
- [ ] Natural/crafted classification remains correct for all known benches

### Scenarios
**Dynamic bench added**
- **Given** BenchRegistry discovers bench "Workbench"
- **When** NaturalResourceRegistry checks if a block's recipe is from a crafting bench
- **Then** "Workbench" recipes are recognized as crafting-bench recipes

## Notes
- Currently `NaturalResourceRegistry.CRAFTING_BENCH_IDS = Set.of("Builders", "Furniture_Bench", "Workbench", "Fieldcraft")` — a separate hardcoded set.
- This MUST be unified with `BenchRegistry` to prevent silent divergence when new benches are discovered.
- Init order: `BenchRegistry.init()` must run before `NaturalResourceRegistry.init()`.
