---
id: F2605181415
type: feature
title: "Plugin Package Alignment"
status: backlog
priority: medium
epic: E2605181400
created: 2026-05-18
---

# Plugin Package Alignment

## Description
Move `Plugin.java` from the root `com` package to `com.CodeCreature`, aligning the entry point with the rest of the codebase's package hierarchy. The only external reference is the `plugin_main_entrypoint` property in `gradle.properties` which drives the manifest `"Main"` field.

## Acceptance Criteria

### Checklist
- [ ] `src/main/java/com/Plugin.java` deleted
- [ ] `src/main/java/com/CodeCreature/Plugin.java` created with `package com.CodeCreature;`
- [ ] `gradle.properties` updated: `plugin_main_entrypoint=com.CodeCreature.Plugin`
- [ ] `gradle build` passes
- [ ] Plugin loads on server start (confirmed by player-join message)

### Scenarios
**Server startup after move**
- **Given** the package move is complete
- **When** the Hytale server starts and a player joins
- **Then** the message "§a[Plugin] Resource scaling active." appears in chat

**Build verification**
- **Given** the move is complete
- **When** `./gradlew clean build` runs
- **Then** `build/resources/main/manifest.json` contains `"Main": "com.CodeCreature.Plugin"`

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605181416 | Move Plugin.java to CodeCreature Package | backlog |

## Notes
- Design doc: docs/design-deferred-fixes.md (Item #11)
- Blast radius: docs/review-refactor-blast-radius.md (Finding #11)
- Zero Java source files import `com.Plugin` — no cascading changes
- Binary failure mode: immediately verifiable on server start
- Deferred to Sprint 2 per Product Owner (zero player impact, engineering hygiene only)
