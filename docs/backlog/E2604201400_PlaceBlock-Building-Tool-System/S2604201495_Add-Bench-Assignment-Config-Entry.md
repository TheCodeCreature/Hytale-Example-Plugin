---
id: S2604201495
type: story
title: "Add Bench_Assignment Config Entry"
status: backlog
priority: medium
feature: F2604201420
epic: E2604201400
created: 2026-04-20
---

# Add Bench_Assignment Config Entry

## User Story
As a **developer**, I want **the Assignment Bench to have a config entry in portable_benches.json** so that **it knows which recipe categories to display**.

## Acceptance Criteria

### Checklist
- [ ] portable_benches.json includes "Bench_Assignment" entry
- [ ] Categories aggregate both Builders and Furniture bench recipe categories
- [ ] Config loads successfully via PortableBenchConfigLoader
- [ ] AssignBenchInteraction and PlaceBlockMenuInteraction can both look up this config

## Notes
- Config entry already added to portable_benches.json — verify it loads correctly
