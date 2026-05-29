---
id: S2605281020
type: story
title: "Register /Debug Logging Command"
status: backlog
priority: high
feature: F2605281000
epic: E2605181400
created: 2026-05-28
---

# Register /Debug Logging Command

## User Story
As a **server operator**, I want **a `/Debug Logging` command** so that **I can toggle debug output at runtime without restarting the server**.

## Acceptance Criteria

### Checklist
- [ ] `/Debug Logging` toggles the global master flag and prints state to the executing player
- [ ] `/Debug Logging <subsystem>` toggles a specific subsystem and prints state to the executing player
- [ ] Invalid subsystem names produce a helpful error listing valid subsystems
- [ ] Command is registered using the same pattern as `/Debug BreakLog`
- [ ] Tab completion for subsystem names works

### Scenarios

**Toggle global on→off**
- **Given** global logging is ON
- **When** a player runs `/Debug Logging`
- **Then** global logging becomes OFF
- **And** the player sees `§e[Debug] Logging disabled`

**Toggle global off→on**
- **Given** global logging is OFF
- **When** a player runs `/Debug Logging`
- **Then** global logging becomes ON
- **And** the player sees `§e[Debug] Logging enabled`

**Toggle subsystem**
- **Given** global logging is ON and STENCIL is ON
- **When** a player runs `/Debug Logging Stencil`
- **Then** STENCIL logging becomes OFF
- **And** the player sees `§e[Debug] Stencil logging disabled`

**Invalid subsystem**
- **Given** a player runs `/Debug Logging FooBar`
- **Then** the player sees `§c[Debug] Unknown subsystem: FooBar. Valid: Plugin, Stencil, BlueprintBench, BlueprintBook, Scaling, Registry, Crafting, IngredientTree`

## Notes
- Find how `/Debug BreakLog` is registered and follow the same pattern — **Note: Hytale Expert found that `/Debug BreakLog` was never actually wired. This story will also create the `/Debug` command group.**
- The command handler should call `FeatureFlags.toggle("logging.global")` or `FeatureFlags.toggle("logging.<subsystem>")`
- Also wire `/Debug BreakLog` to call `FeatureFlags.toggle("diagnostics.breakLog")` since it was never connected
- Tab completion should list subsystem names from `DebugLogger.Subsystem` enum values
