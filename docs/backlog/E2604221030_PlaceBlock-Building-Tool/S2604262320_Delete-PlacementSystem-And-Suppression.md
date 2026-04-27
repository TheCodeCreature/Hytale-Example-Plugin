---
id: S2604262320
type: story
title: "Delete PlaceBlockPlacementSystem and Suppress/Resume"
status: backlog
priority: high
feature: F2604262300
epic: E2604221030
created: 2026-04-26
---

# Delete PlaceBlockPlacementSystem and Suppress/Resume

## User Story
As a **developer**, I want dead code from the old `PlaceBlock` interception
approach removed so that the codebase is clean and no legacy paths can
accidentally fire.

## Acceptance Criteria

### Checklist
- [ ] `PlaceBlockPlacementSystem.java` deleted
- [ ] Its registration in the plugin's `setup()` removed
- [ ] `suppressSync()` and `resumeSync()` removed from `PlaceholderSyncSystem`
- [ ] `suppressed` map removed from `PlaceholderSyncSystem`
- [ ] Suppression guard removed from change event lambdas in `PlaceholderSyncSystem`
- [ ] All diagnostic `DIAG:` logging removed from `PlaceholderSyncSystem` and `BlockPreviewReskinManager`
- [ ] Contract #12 (mutual exclusion with PlacementCostScaler) javadoc removed or updated
- [ ] Project compiles cleanly with no stale references

### Scenarios
**Clean build**
- **Given** all deletions are complete
- **When** `./gradlew build` runs
- **Then** build succeeds with no errors or warnings related to deleted types

## Notes
- `PlacementCostScaler` continues to handle its own PlaceBlockEvents independently — no change needed there
- `BlockPreviewReskinManager` DIAG logging can be removed now that the reskin cascade is gone
