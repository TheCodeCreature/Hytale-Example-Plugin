---
id: S2605020305
type: story
title: "Pipeline Material Group Extraction and Filtering"
status: backlog
priority: high
feature: F2605020300
epic: E2604221030
created: 2026-05-02
---

# Pipeline Material Group Extraction and Filtering

## User Story
As a **player**, I want the bench to group sets by material type so that I can quickly narrow down to the category of blocks I'm interested in.

## Acceptance Criteria

### Checklist
- [ ] New pipeline stage extracts material groups from set prefixes (text before first `_`)
- [ ] Groups are derived from the currently visible sets (respects tab + search filters)
- [ ] A representative item ID is selected per group (first alphabetical item from any set in that group)
- [ ] New pipeline stage filters recipes by active material groups before set filtering
- [ ] PipelineResult includes `currentGroups` (list of group names) and `groupRepresentativeItems` (map of group → itemId)
- [ ] When material groups are active, only sets belonging to those groups appear in `currentSets`
- [ ] Existing pipeline stages continue to work unchanged

### Scenarios
**Group extraction from sets**
- **Given** visible sets include "Wood_Hardwood", "Wood_Mahogany", "Rock_Shale", "Rock_Granite"
- **When** the pipeline extracts groups
- **Then** groups are ["Rock", "Wood"] (sorted alphabetically)

**Group filtering restricts sets**
- **Given** active groups = {"Wood"}
- **When** the pipeline runs
- **Then** `currentSets` only contains sets starting with "Wood_"

## Notes
- Pipeline remains a pure function — groups passed in as input, not stored on pipeline
