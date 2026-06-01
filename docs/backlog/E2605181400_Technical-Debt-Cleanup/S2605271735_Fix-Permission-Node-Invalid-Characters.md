---
id: S2605271735
type: story
title: "Fix Permission Node Invalid Characters"
status: done
priority: high
feature: F2605261200
epic: E2605181400
created: 2026-05-27
---

# Fix Permission Node Invalid Characters

## User Story
As a **plugin developer**, I want **command permission nodes to use valid characters** so that **commands register without SEVERE errors on startup**.

## Acceptance Criteria

### Checklist
- [ ] Plugin group and name produce permission nodes without brackets or spaces
- [ ] `/placeblock` and `/bookparticle` commands register without errors
- [ ] Permission nodes follow Hytale's `[a-z0-9._-]+` convention

### Scenarios
**Command registration**
- **Given** the plugin group is `Plugin` and name contains `[CodeCreature] Plugin`
- **When** the server starts and `setup()` registers commands
- **Then** permission nodes are formatted as `plugin.codecreature_plugin.command.placeblock` (or similar sanitized form)

## Notes
Pre-existing issue from 0.5.0. The plugin name `[CodeCreature] Plugin` contains brackets which are invalid in permission nodes. The error occurs at `PermissionsModule.registerPermission()`.

Potential fix locations:
- Change `plugin_group` in `gradle.properties` (changes plugin identity)
- Sanitize the plugin name used for permission node generation
- Override the permission node in command registration

Server log evidence:
```
SEVERE: Failed to register command: placeblock - Invalid permission node: plugin.[codecreature] plugin.command.placeblock
```
