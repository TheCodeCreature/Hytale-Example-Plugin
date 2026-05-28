---
id: S2605271730
type: story
title: "Fix Manifest ServerVersion Semver Range for 0.5.1"
status: done
priority: critical
feature: F2605261200
epic: E2605181400
created: 2026-05-27
---

# Fix Manifest ServerVersion Semver Range for 0.5.1

## User Story
As a **plugin developer**, I want **the manifest ServerVersion to use proper semver range syntax** so that **the plugin loads correctly on Hytale 0.5.1+**.

## Acceptance Criteria

### Checklist
- [x] When HytaleServer.jar reports version `0.5.1`, the manifest gets `^0.5.1` (not bare `0.5.1`)
- [x] When server_version is pre-semver format (`2026.03.26-89796e57b`), it passes through unchanged
- [x] Plugin manifest decodes without errors on 0.5.1
- [x] Plugin appears in the PluginManager loading list

### Scenarios
**Bare semver with non-zero patch**
- **Given** HytaleServer.jar Implementation-Version is `0.5.1`
- **When** Gradle processResources runs
- **Then** manifest.json ServerVersion is `^0.5.1`

**Bare semver with zero patch (backward compat)**
- **Given** HytaleServer.jar Implementation-Version is `0.5.0`
- **When** Gradle processResources runs
- **Then** manifest.json ServerVersion is `^0.5.0`

## Notes
Root cause: Hytale 0.5.1 tightened `SemverRange.fromString()` — bare versions with non-zero patches are rejected.
Fix applied in `build.gradle.kts` processResources task.
