---
id: S2604272105
type: story
title: "Fix NaturalResourceRegistry Init and Simplify"
status: backlog
priority: critical
feature: F2604272100
epic: E2604201200
created: 2026-04-27
---

# Fix NaturalResourceRegistry Init and Simplify

## User Story
As a **server admin**, I want **natural resource detection to work correctly** so that **the 12x economy scales all natural drops properly**.

## Acceptance Criteria

### Checklist
- [ ] `init()` collects drops into the class-level `naturalItemIds` field (not an orphaned local variable)
- [ ] Single flat `naturalItemIds` set replaces the two-tier `coreNaturalItemIds` / `allNaturalItemIds`
- [ ] `isDecoBlock()` method removed (was unimplemented — threw UnsupportedOperationException)
- [ ] `isCoreNaturalItem()` method removed
- [ ] `getCoreNaturalItemIds()` method removed
- [ ] `isNaturalItem()` returns true for items dropped by ANY natural block (including Deco)
- [ ] Log line shows correct counts: `"Initialized: X natural block types, Y natural items"`
- [ ] No compilation errors in downstream consumers

### Scenarios
**Correct Initialization**
- **Given** Assets are loaded with natural blocks that have gathering configs
- **When** `NaturalResourceRegistry.init()` runs
- **Then** `getNaturalItemIds()` returns a non-empty set containing all items from all natural block drop paths

**Deco Blocks Included**
- **Given** A Deco natural block drops Plant_Fiber
- **When** `NaturalResourceRegistry.init()` runs
- **Then** Plant_Fiber is in `naturalItemIds` (no Deco exclusion)

## Notes
- Migration step 1 in design doc §12
- Must compile independently before proceeding to S2604272110
