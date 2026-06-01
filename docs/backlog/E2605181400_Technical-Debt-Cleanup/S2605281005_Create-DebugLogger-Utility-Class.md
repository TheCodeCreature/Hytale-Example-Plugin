---
id: S2605281005
type: story
title: "Create DebugLogger Utility Class"
status: backlog
priority: high
feature: F2605281000
epic: E2605181400
created: 2026-05-28
---

# Create DebugLogger Utility Class

## User Story
As a **developer**, I want **a single utility class that centralizes all debug logging and player messaging with toggleable subsystem flags** so that **I can silence noisy output at runtime without restarting the server or editing code**.

## Acceptance Criteria

### Checklist
- [ ] `DebugLogger` class exists in `com.CodeCreature.util`
- [ ] Enum or registry of subsystems: PLUGIN, STENCIL, BLUEPRINT_BOOK, BLUEPRINT_BOOK, SCALING, REGISTRY, CRAFTING, INGREDIENT_TREE
- [ ] Delegates toggle state to `FeatureFlags` — reads `logging.global` and `logging.<subsystem>` keys
- [ ] Does NOT own any AtomicBooleans — all toggle state lives in FeatureFlags
- [ ] `log(Subsystem, Level, String message)` method that checks `FeatureFlags.get("logging.global") && FeatureFlags.get("logging.<subsystem>")` before delegating to the appropriate JUL Logger
- [ ] `logHytale(Subsystem, String message, Object... args)` method for HytaleLogger call sites
- [ ] `chat(PlayerRef, Subsystem, String message)` method that checks toggles before calling `playerRef.sendMessage(Message.raw(message))`
- [ ] `isEnabled(Subsystem)` returns effective state (global AND subsystem via FeatureFlags)
- [ ] Thread-safe — FeatureFlags handles thread safety internally

### Scenarios

**Log when enabled**
- **Given** global=ON and STENCIL=ON
- **When** `DebugLogger.log(STENCIL, INFO, "msg")` is called
- **Then** the message is written to the JUL Logger

**Log when subsystem disabled**
- **Given** global=ON and STENCIL=OFF
- **When** `DebugLogger.log(STENCIL, INFO, "msg")` is called
- **Then** no output is produced

**Log when global disabled**
- **Given** global=OFF and STENCIL=ON
- **When** `DebugLogger.log(STENCIL, INFO, "msg")` is called
- **Then** no output is produced

## Notes
- DebugLogger is a **consumer** of FeatureFlags, not a flag store. Toggle commands call `FeatureFlags.toggle("logging.global")` or `FeatureFlags.toggle("logging.stencil")`.
- Pattern after `BreakBlockDiagnostic.toggle()` — same AtomicBoolean CAS loop, but now inside FeatureFlags
- The existing `BreakBlockDiagnostic` should be migrated to read `FeatureFlags.get("diagnostics.breakLog")` instead of its own AtomicBoolean
