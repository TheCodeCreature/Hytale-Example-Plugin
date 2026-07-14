---
id: S2604230905
type: story
title: "Non-Block Recipe Feedback at Stencil Crafting"
status: backlog
priority: critical
feature: F2604221040
epic: E2604221030
created: 2026-04-23
---

# Non-Block Recipe Feedback at Stencil Crafting

## User Story
As a **player**, I want **clear feedback when I select a non-placeable recipe with my placeholder** so that **I understand why the tool didn't arm**.

## Acceptance Criteria

### Checklist
- [ ] When a player clicks a recipe whose output has no `blockId` while a `Block_Placeholder` is in the input slot, a chat message is sent
- [ ] The message clearly states the recipe cannot be used with the PlaceBlock tool
- [ ] The craft event is still cancelled (placeholder is not consumed)
- [ ] No metadata is written to the placeholder

### Scenarios
**Non-block recipe selected with placeholder**
- **Given** a `Block_Placeholder` is in the Stencil Crafting input slot
- **When** the player clicks a recipe for "Rope" (non-block output)
- **Then** the player sees: "§c[PlaceBlock] This recipe does not produce a placeable block."
- **And** the craft event is cancelled
- **And** the placeholder remains unarmed (Blue quality)

## Technical Notes
- Modify the `getOutputBlockTypeId() == null` path in `PlaceBlockBenchInterceptor.handle()` to send a message before returning
- The event should still be cancelled to prevent placeholder consumption
- This is a minor UX addition to the existing interceptor (S2604221215)
