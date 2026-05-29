---
id: S2605291065
type: story
title: "Wire Deny List into Discovery Pipeline"
status: backlog
priority: medium
feature: F2605291015
epic: E2605291000
created: 2026-05-29
---

# Wire Deny List into Discovery Pipeline

## User Story
As a **server operator**, I want **the deny list to actually prevent benches from being registered** so that **excluded benches have zero impact on the plugin**.

## Acceptance Criteria

### Checklist
- [ ] `Plugin.onAssetsLoaded()` reads the deny list config and passes it to `BenchRegistry.init(denyList)`
- [ ] Denied bench IDs are excluded before any downstream system sees them
- [ ] `RecipeFilterRegistry` never indexes recipes for denied benches
- [ ] No UI tabs appear for denied benches
- [ ] No drop scaling runs for denied bench blocks

### Scenarios
**End-to-end exclusion**
- **Given** deny list contains `["Processing"]`
- **When** the full init pipeline runs
- **Then** no Processing recipes in RecipeFilterRegistry, no Processing tab in UI, no Processing blocks processed by DropScaler

## Notes
- The deny list is passed as `Set<String>` to `BenchRegistry.init()` — simple and testable.
