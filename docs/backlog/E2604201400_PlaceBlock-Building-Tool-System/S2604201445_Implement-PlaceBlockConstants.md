---
id: S2604201445
type: story
title: "Implement PlaceBlockConstants"
status: backlog
priority: high
feature: F2604201405
epic: E2604201400
created: 2026-04-20
---

# Implement PlaceBlockConstants

## User Story
As a **developer**, I want **centralized constants for PlaceBlock item IDs and metadata keys** so that **all PlaceBlock code references the same values without magic strings**.

## Acceptance Criteria

### Checklist
- [ ] ITEM_DEFAULT, ITEM_ARMED, ITEM_NO_RESOURCES constants match JSON asset file names
- [ ] META_RECIPE_ID and META_RECIPE_NAME constants define the BSON metadata keys
- [ ] ALL_PLACEBLOCK_IDS contains all three variant IDs for quick identity checks
- [ ] Class is final with private constructor (utility class pattern)

## Notes
- Skeleton code exists at `PlaceBlockConstants.java` — implementation is essentially complete, just verify values match JSON file names
