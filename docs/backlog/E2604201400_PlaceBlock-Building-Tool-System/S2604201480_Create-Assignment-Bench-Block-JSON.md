---
id: S2604201480
type: story
title: "Create Assignment Bench Block JSON Asset"
status: backlog
priority: medium
feature: F2604201420
epic: E2604201400
created: 2026-04-20
---

# Create Assignment Bench Block JSON Asset

## User Story
As a **player**, I want **to place an Assignment Bench in the world** so that **I can use it to assign recipes to my PlaceBlock items**.

## Acceptance Criteria

### Checklist
- [ ] Bench_Assignment.json block type asset exists
- [ ] Uses an existing bench model (e.g., Bench_Builders model)
- [ ] Defines "Interactions": { "Use": "Assign_Bench" }
- [ ] No BlockEntity required (window is stateless)
- [ ] Block can be placed and broken without errors
- [ ] An item asset exists to make the block obtainable

## Notes
- Block type JSON format needs research — check existing bench block JSON examples in vanilla assets
- May need to create both a BlockType JSON and an Item JSON for the block item
