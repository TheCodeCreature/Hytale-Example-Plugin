---
id: S2604201485
type: story
title: "Implement AssignBenchInteraction"
status: backlog
priority: medium
feature: F2604201420
epic: E2604201400
created: 2026-04-20
---

# Implement AssignBenchInteraction

## User Story
As a **player**, I want **right-clicking the Assignment Bench to open a recipe assignment window** so that **I can arm my PlaceBlock at a physical station**.

## Acceptance Criteria

### Checklist
- [ ] Extends SimpleBlockInteraction (block-targeted, needs client position)
- [ ] Interaction type "Assign_Bench" is registered in the codec registry
- [ ] Looks up config from PortableBenchRegistry using "Bench_Assignment" key
- [ ] Creates and opens an AssignBenchWindow
- [ ] Fails gracefully if config is missing

## Notes
- Skeleton code exists at AssignBenchInteraction.java
- JSON interaction assets already scaffolded (Assign_Bench.json, Assign_Bench_Interaction.json)
