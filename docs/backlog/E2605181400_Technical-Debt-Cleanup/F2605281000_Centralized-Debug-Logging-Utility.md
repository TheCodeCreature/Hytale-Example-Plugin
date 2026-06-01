---
id: F2605281000
type: feature
title: "Centralized Debug Logging Utility"
status: backlog
priority: high
epic: E2605181400
created: 2026-05-28
---

# Centralized Debug Logging Utility

## Description
Extract all log calls (53 call sites across 18 files) and player chat messages (13 call sites across 5 files) into a centralized `DebugLogger` utility class. The utility provides a global master toggle and per-subsystem overrides, controlled via `/Debug Logging` commands. This eliminates the current pattern of per-class Logger declarations, inconsistent logger naming, and inline chat messages with no way to silence debug output at runtime.

## Acceptance Criteria

### Checklist
- [ ] A single `DebugLogger` utility class exists in `com.CodeCreature.util`
- [ ] All ~53 log call sites route through `DebugLogger` instead of direct Logger/HytaleLogger calls
- [ ] All ~13 player chat messages route through `DebugLogger` instead of direct `sendMessage` calls
- [ ] A global master toggle enables/disables all debug output with one command
- [ ] Per-subsystem toggles allow enabling/disabling output for individual subsystems
- [ ] The `/Debug Logging` command toggles the global flag
- [ ] The `/Debug Logging <subsystem>` command toggles a specific subsystem
- [ ] Both JUL (`java.util.logging.Logger`) and HytaleLogger backends are supported
- [ ] All log levels (INFO, WARNING, FINE) are toggleable
- [ ] The existing `BreakBlockDiagnostic` toggle pattern is preserved or migrated into the new system

### Scenarios

**Global toggle off**
- **Given** the global debug logging flag is ON (default)
- **When** a player runs `/Debug Logging`
- **Then** all log output and debug chat messages are suppressed
- **And** the player receives a confirmation message: "§e[Debug] Logging disabled"

**Per-subsystem toggle**
- **Given** the global debug logging flag is ON
- **When** a player runs `/Debug Logging Stencil`
- **Then** only Stencil-subsystem logs and chat messages are suppressed
- **And** all other subsystems continue logging normally

**Global off overrides subsystem on**
- **Given** the global debug logging flag is OFF
- **When** a player runs `/Debug Logging Stencil` to enable Stencil logging
- **Then** Stencil logs remain suppressed because the global flag takes precedence

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605281025 | Create Standalone FeatureFlags System | backlog |
| S2605281005 | Create DebugLogger Utility Class | backlog |
| S2605281010 | Migrate All Log Call Sites to DebugLogger | backlog |
| S2605281015 | Migrate All Debug Chat Messages to DebugLogger | backlog |
| S2605281020 | Register /Debug Logging Command | backlog |

## Notes
- **Architecture**: FeatureFlags is a standalone, general-purpose system persisted to JSON. DebugLogger consumes FeatureFlags for its toggles but does not own them.
- Two logging backends in use: `java.util.logging.Logger` (14 files) and `HytaleLogger` (4 files)
- Subsystems to register as feature flags: `logging.global`, `logging.stencil`, `logging.BlueprintBook`, `logging.blueprintBook`, `logging.scaling`, `logging.registry`, `logging.crafting`, `logging.ingredientTree`, `logging.plugin`
- The existing `BreakBlockDiagnostic` AtomicBoolean should be migrated to `FeatureFlags.get("diagnostics.breakLog")`
- Two unused logger declarations exist (`StencilRadialMenuPage`, `AffordabilityCoalescer`) — clean those up during migration
- **Chat message classification**: Only debug/diagnostic messages (7 sites) route through DebugLogger. Player feedback messages (6 sites: placement confirmations, denial reasons) remain as direct `sendMessage()` calls and are NOT toggleable.
