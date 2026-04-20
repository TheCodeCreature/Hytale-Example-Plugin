---
id: S2604201460
type: story
title: "Verify Placement Rotation Handling"
status: backlog
priority: medium
feature: F2604201410
epic: E2604201400
created: 2026-04-20
---

# Verify Placement Rotation Handling

## User Story
As a **player**, I want **blocks placed by PlaceBlock to respect the same rotation rules as normal placement** so that **stairs, slabs, and directional blocks orient correctly**.

## Acceptance Criteria

### Checklist
- [ ] Rotation from PlaceBlockEvent is passed through to WorldChunk.placeBlock()
- [ ] Directional blocks (stairs, slabs, doors) orient correctly based on player facing
- [ ] Rotation behavior matches normal block placement

### Scenarios
**Place a stair block facing the right direction**
- **Given** a PlaceBlock armed with a Stair recipe
- **When** the player places it facing north
- **Then** the stair block orients the same as if placed normally from inventory

## Notes
- May need runtime testing to verify RotationTuple decomposition into Rotation yaw/pitch/roll
