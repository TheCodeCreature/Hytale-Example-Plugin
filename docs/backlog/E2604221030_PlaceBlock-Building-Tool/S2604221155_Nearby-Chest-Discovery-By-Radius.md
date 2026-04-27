---
id: S2604221155
type: story
title: "Nearby Chest Discovery by Radius"
status: cancelled
priority: high
feature: F2604221055
epic: E2604221030
created: 2026-04-22
---

# Nearby Chest Discovery by Radius

## User Story
As a **player**, I want the **system to find chests near the bench** so that **I can use resources stored in nearby chests for building**.

## Acceptance Criteria

### Checklist
- [ ] System discovers chest blocks within a configurable horizontal radius of the bench
- [ ] System discovers chest blocks within a configurable vertical radius of the bench
- [ ] Radius values are configurable per bench asset (or shared config)
- [ ] System identifies all chest types supported by Hytale (standard, large, etc.)
- [ ] System returns a list of discovered chests with their inventories
- [ ] Discovery runs when the bench window opens and can be re-triggered on demand

### Scenarios
**Chests within radius**
- **Given** 3 chests exist within 10 blocks of the bench
- **When** the chest scanner runs with radius 10
- **Then** all 3 chests are discovered

**Chests outside radius**
- **Given** a chest exists 15 blocks away
- **When** the scanner runs with radius 10
- **Then** that chest is not discovered

**No chests nearby**
- **Given** no chests are within radius
- **When** the scanner runs
- **Then** an empty list is returned

## Notes
- The Hytale Expert should clarify the API for: scanning blocks by type in a radius, accessing chest block entity inventories, and any performance implications of area scans.
- The `PortableStructuralWindow` has `chestHorizontalRadius` and `chestVerticalRadius` fields but they're set to 0 — this story needs to make them functional.
