---
id: S2605281015
type: story
title: "Migrate Debug Chat Messages to DebugLogger"
status: backlog
priority: high
feature: F2605281000
epic: E2605181400
created: 2026-05-28
---

# Migrate Debug Chat Messages to DebugLogger

## User Story
As a **developer**, I want **debug/diagnostic chat messages to route through DebugLogger** so that **I can silence debug chat spam while preserving essential player feedback**.

## Acceptance Criteria

### Checklist
- [ ] 7 debug chat message call sites replaced with `DebugLogger.chat()` calls
- [ ] 6 player feedback messages left as direct `sendMessage()` calls (NOT toggleable)
- [ ] Each debug call site uses the correct subsystem enum value
- [ ] Message text (including `§` color codes) preserved exactly
- [ ] Build compiles with zero errors after migration

### Message Classification

**Toggleable (Debug) — route through DebugLogger.chat():**
| # | File | Message |
|---|---|---|
| 1 | Plugin.java | `§a[Plugin] Resource scaling active.` (admin diagnostic on join) |
| 2-5 | StencilSubCommand.java | 4 error messages + 1 success (admin command output) |
| 6-7 | ParticleCommand.java | particle status messages (admin command output) |

**Always-on (Player feedback) — keep as direct sendMessage():**
| # | File | Message |
|---|---|---|
| 1-2 | StencilPlacementSystem.java | `§a[Stencil] Placed {block}` (placement confirmation) |
| 3 | StencilPlacementSystem.java | `§c[Stencil] {error}` via sendError() (placement denial) |
| 4 | StencilSelectionPage.java | `§c[StencilBook] No recipe selected.` (action denial) |
| 5 | StencilSelectionPage.java | `§a[StencilBook] Given stencil: {name}` (action confirmation) |

### Scenarios

**Debug chat message when enabled**
- **Given** `logging.plugin` flag is ON
- **When** a player joins and `DebugLogger.chat(playerRef, PLUGIN, "§a[Plugin] Resource scaling active.")` fires
- **Then** the player sees the message in chat

**Debug chat message when disabled**
- **Given** `logging.plugin` flag is OFF
- **When** a player joins
- **Then** no "Resource scaling active" message is sent

**Player feedback always shows**
- **Given** `logging.stencil` flag is OFF
- **When** a player places a stencil block
- **Then** they still see "§a[Stencil] Placed Oak Planks" because it's not routed through DebugLogger

## Notes
- Files to touch for debug migration: Plugin.java (1 site), StencilSubCommand.java (5 sites), ParticleCommand.java (2 sites)
- Files NOT touched (player feedback stays as-is): StencilPlacementSystem.java, StencilSelectionPage.java
