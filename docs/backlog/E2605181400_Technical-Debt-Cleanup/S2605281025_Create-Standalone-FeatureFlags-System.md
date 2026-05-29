---
id: S2605281025
type: story
title: "Create Standalone FeatureFlags System"
status: backlog
priority: high
feature: F2605281000
epic: E2605181400
created: 2026-05-28
---

# Create Standalone FeatureFlags System

## User Story
As a **developer**, I want **a standalone, general-purpose feature flag system persisted to a JSON file** so that **I can toggle any behavior at runtime and have those toggles survive server restarts**.

## Acceptance Criteria

### Checklist
- [ ] `FeatureFlags` class exists in `com.CodeCreature.util`
- [ ] Stores flags as key-value pairs (`String` key → `boolean` value)
- [ ] Uses `ConcurrentHashMap<String, AtomicBoolean>` for thread-safe runtime access
- [ ] `initialize(Path dataDirectory)` loads flags from `{dataDirectory}/feature_flags.json`; creates file with defaults if missing
- [ ] `save()` persists current flag state to `feature_flags.json`
- [ ] Auto-saves on every `set()` / `toggle()` call
- [ ] `get(String key)` returns current flag value (defaults to `true` if key not registered)
- [ ] `set(String key, boolean value)` sets a flag and persists
- [ ] `toggle(String key)` flips a flag, persists, and returns the new value
- [ ] `register(String key, boolean defaultValue)` registers a flag with a default (used at startup to define known flags)
- [ ] `getAll()` returns an unmodifiable snapshot of all flags and their states
- [ ] System is fully static — callable from anywhere without an instance reference
- [ ] JSON file format matches existing plugin data patterns (plain JSON, human-readable)
- [ ] Thread-safe across server/world/executor threads (AtomicBoolean + ConcurrentHashMap)

### Scenarios

**First boot — no file exists**
- **Given** no `feature_flags.json` exists in the data directory
- **When** `FeatureFlags.initialize(dataDir)` is called
- **Then** a new file is created with all registered defaults
- **And** all flags are set to their default values in memory

**Toggle persists across restart**
- **Given** flag `logging.global` is ON
- **When** a player runs `/Debug Logging` which calls `FeatureFlags.toggle("logging.global")`
- **Then** the flag becomes OFF in memory
- **And** `feature_flags.json` is updated on disk with `"logging.global": false`
- **And** after server restart, the flag loads as OFF

**Unknown flag defaults to true**
- **Given** code calls `FeatureFlags.get("some.new.flag")` but never registered it
- **When** the flag is not in the JSON file
- **Then** `get()` returns `true` (fail-open default)

### JSON File Format
```json
{
  "logging.global": true,
  "logging.stencil": true,
  "logging.blueprintBench": true,
  "logging.blueprintBook": true,
  "logging.scaling": true,
  "logging.registry": true,
  "logging.crafting": true,
  "logging.ingredientTree": true,
  "logging.plugin": true,
  "diagnostics.breakLog": true
}
```

## Notes
- Follow `BlueprintBenchPrefsStore` pattern: `initialize(Path dataDirectory)` called from `Plugin.setup()`
- Use `BsonUtil.readDocumentNow()` / `BsonUtil.writeDocument()` for JSON I/O (established codebase pattern), OR plain `Files.readString()` / `Files.writeString()` with a lightweight JSON library if BSON is too heavy for simple key-value pairs
- The system is general-purpose — not coupled to logging. Future uses could include: gameplay feature toggles, experimental features, A/B testing, etc.
- Dot-notation keys (`logging.global`, `diagnostics.breakLog`) allow logical grouping without nested objects
- The existing `BreakBlockDiagnostic.enabled` AtomicBoolean should be migrated to use `FeatureFlags.get("diagnostics.breakLog")` so all toggles live in one place
