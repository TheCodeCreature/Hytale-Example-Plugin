---
id: S2604221125
type: story
title: "Block Preview Integration with Armed Placeholder"
status: backlog
priority: high
feature: F2604221045
epic: E2604221030
created: 2026-04-22
---

# Block Preview Integration with Armed Placeholder

## User Story
As a **player**, I want to **see a ghost preview of the block I'm about to place** so that **I can position it accurately before committing resources**.

## Acceptance Criteria

### Checklist
- [ ] Holding an armed placeholder shows a ghost block at the aimed position
- [ ] The ghost block matches the selected recipe's output block type
- [ ] Preview respects the selected block's placement rules (surface, orientation, hitbox)
- [ ] Preview updates in real-time as the player looks around
- [ ] Player can adjust placement settings (rotation, flip, etc.) without breaking the preview
- [ ] No preview is shown for unarmed placeholders
- [ ] No preview is shown at invalid placement locations

### Scenarios
**Valid placement position**
- **Given** the player holds a placeholder armed with "Cobble Wall"
- **When** they aim at a valid wall placement location
- **Then** a ghost Cobble Wall appears at that position

**Invalid placement position**
- **Given** the player holds a placeholder armed with "Fence"
- **When** they aim at midair with no support
- **Then** no ghost is shown

**Unarmed placeholder**
- **Given** the player holds an unarmed placeholder
- **When** they aim at any location
- **Then** no ghost is shown

## Notes
- The engine has a built-in block preview system for PlaceBlock items. The goal is to hook into this.
- The existing `PreviewBlockManager` uses `ServerSetBlock` packets for server-controlled ghosts. The engine's native preview is client-side. Determine which approach to use.
- The Hytale Expert should clarify how the engine's preview system detects which block type to preview and whether this can be overridden per-item.
