---
id: S2605181416
type: story
title: "Move Plugin.java to CodeCreature Package"
status: done
priority: medium
feature: F2605181415
epic: E2605181400
created: 2026-05-18
---

# Move Plugin.java to CodeCreature Package

## User Story
As a **developer**, I want **the plugin entry point in the same package as all other project code** so that **the package hierarchy is consistent and discoverable**.

## Acceptance Criteria

### Checklist
- [ ] File moved: `src/main/java/com/Plugin.java` → `src/main/java/com/CodeCreature/Plugin.java`
- [ ] Package declaration changed: `package com;` → `package com.CodeCreature;`
- [ ] Redundant `com.CodeCreature.*` imports removed (now same-package)
- [ ] `gradle.properties` line 10: `plugin_main_entrypoint=com.CodeCreature.Plugin`
- [ ] `./gradlew clean build` passes
- [ ] Old file `src/main/java/com/Plugin.java` no longer exists

### Scenarios
**Compilation**
- **Given** the move is complete
- **When** Gradle compiles the project
- **Then** zero errors — all import paths resolve correctly

## Notes
- 3 files changed total (move + property update)
- No other Java files reference `com.Plugin` via import
- Verify by grepping `import com.Plugin` — should return zero hits
