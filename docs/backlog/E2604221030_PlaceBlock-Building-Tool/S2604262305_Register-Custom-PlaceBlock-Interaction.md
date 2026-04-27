---
id: S2604262305
type: story
title: "Register Custom PlaceBlock Interaction Type"
status: backlog
priority: critical
feature: F2604262300
epic: E2604221030
created: 2026-04-26
---

# Register Custom PlaceBlock Interaction Type

## User Story
As a **plugin developer**, I want a custom interaction type registered with the
engine's codec so that armed placeholder states can use it in JSON without
relying on the built-in `PlaceBlock` interaction.

## Acceptance Criteria

### Checklist
- [ ] A class extending `SimpleBlockInteraction` is created
- [ ] The interaction is registered via `getCodecRegistry(Interaction.CODEC).register()` in `setup()`
- [ ] The type name (e.g. `"PlaceBlockTool"`) resolves from item JSON `"Type"` field
- [ ] The interaction compiles and loads without errors on server start
- [ ] `interactWithBlock()` is stubbed (logs target position, does not place)

### Scenarios
**Registration succeeds**
- **Given** the plugin starts
- **When** `setup()` runs
- **Then** `"PlaceBlockTool"` is available as an interaction type in item JSON

## Notes
- Follow the pattern used by `OpenBenchPageInteraction`, `PortableBenchInteraction`
- The codec must define any fields needed (none initially — metadata comes from ItemStack)
