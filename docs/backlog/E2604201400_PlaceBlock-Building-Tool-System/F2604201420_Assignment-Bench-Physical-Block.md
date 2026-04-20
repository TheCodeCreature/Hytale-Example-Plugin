---
id: F2604201420
type: feature
title: "Assignment Bench (Physical Block)"
status: backlog
priority: medium
epic: E2604201400
created: 2026-04-20
---

# Assignment Bench (Physical Block)

## Description
A placeable bench block that opens a StructuralCrafting window when interacted with. The player inserts a PlaceBlock item into the input slot, browses recipe categories from Builders and Furniture benches as a guide, and assigns a recipe to the PlaceBlock via an "Assign" action. No materials are consumed — the bench only writes metadata.

## Acceptance Criteria

### Checklist
- [ ] Assignment Bench block can be placed and broken in the world
- [ ] Right-clicking the bench opens a StructuralCrafting window
- [ ] Input slot only accepts PlaceBlock items (all three variants)
- [ ] Recipe categories from both Builders and Furniture benches are shown
- [ ] Selecting a recipe and clicking "Assign" writes recipe ID to the PlaceBlock
- [ ] PlaceBlock quality variant is swapped after assignment
- [ ] On window close, PlaceBlock is returned to the player's inventory
- [ ] No block entity required — window is stateless

### Scenarios
**Assign via physical bench**
- **Given** a player places an Assignment Bench and opens it
- **When** they insert a PlaceBlock_Default, select "Cobble Wall", and click Assign
- **Then** the PlaceBlock in the input slot becomes PlaceBlock_Armed or PlaceBlock_NoResources with recipe metadata

**Reject non-PlaceBlock items**
- **Given** a player opens the Assignment Bench
- **When** they try to insert a regular block item (e.g., Oak Planks)
- **Then** the item is rejected — only PlaceBlock variants are accepted

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2604201480 | Create Assignment Bench Block JSON Asset | backlog |
| S2604201485 | Implement AssignBenchInteraction | backlog |
| S2604201490 | Implement AssignBenchWindow | backlog |
| S2604201495 | Add Bench_Assignment Config Entry | backlog |

## Notes
- Reuses an existing bench model (e.g., Builders Bench) — no new 3D model needed
- Window follows PortableStructuralWindow pattern (implements ItemContainerWindow + MaterialContainerWindow)
- Skeleton code exists at `assignbench/AssignBenchInteraction.java` and `AssignBenchWindow.java`
- Config entry already added to `portable_benches.json`
