---
id: S2604262315
type: story
title: "Update Block_Placeholder JSON to Custom Interaction"
status: backlog
priority: critical
feature: F2604262300
epic: E2604221030
created: 2026-04-26
---

# Update Block_Placeholder JSON to Custom Interaction

## User Story
As a **plugin developer**, I want the armed placeholder states to reference the
custom interaction type so that right-click uses our handler instead of the
engine's `PlaceBlock`.

## Acceptance Criteria

### Checklist
- [ ] All Armed_Green_0–8 states' Secondary interaction changed from `"Type": "PlaceBlock"` to `"Type": "PlaceBlockTool"`
- [ ] Armed_Red state's Secondary interaction changed similarly
- [ ] `RemoveItemInHand` field removed (no longer relevant — custom interaction doesn't consume)
- [ ] `BlockType` retained on all states (ghost preview must still render)
- [ ] `PlacementSettings.BlockPreviewVisibility: "Default"` retained
- [ ] Server starts without errors; items load correctly

### Scenarios
**JSON loads correctly**
- **Given** the updated Block_Placeholder.json
- **When** the server starts
- **Then** all 11 states register with the custom interaction type and log no errors

## Notes
- The base (Blue) state keeps `"Use": "PlaceBlock_Menu"` — no change
- `BlockType` stays on all states for ghost preview rendering
