---
topic: "Per-Player Data Persistence"
category: "Plugins"
updated: 2026-04-30
sources: ["DiskPlayerStorageProvider.java", "PluginBase.java", "PendingLoadJavaPlugin.java", "BarterShopState.java", "HytalePermissionsProvider.java", "Config.java", "ComponentRegistry.java", "BsonUtil.java"]
---

# Per-Player Data Persistence

## Summary

Hytale provides **three distinct persistence mechanisms** that plugins can use, each suited to different use cases. For per-player UI preferences, the recommended approach is **plugin-managed file I/O** using the plugin's `dataDirectory`, following the pattern established by the built-in `BarterShopState`.

---

## Mechanism 1: Plugin Data Directory (RECOMMENDED for UI prefs)

### How It Works

Every plugin gets a dedicated data directory at:
```
mods/{group}_{name}/
```

This is assigned during plugin loading in `PendingLoadJavaPlugin.java`:
```java
Path dataDirectory = PluginManager.MODS_PATH.resolve(manifest.getGroup() + "_" + manifest.getName());
JavaPluginInit init = new JavaPluginInit(manifest, dataDirectory, this.getPath(), this.urlClassLoader);
```

The path is accessible via `PluginBase.getDataDirectory()` (public method).

### Key APIs

| Class | Method | Purpose |
|-------|--------|---------|
| `PluginBase` | `getDataDirectory()` | Returns the plugin's writable data directory (`mods/{group}_{name}/`) |
| `BsonUtil` | `writeDocument(Path, BsonDocument)` | Async JSON write with backup (.bak) |
| `BsonUtil` | `readDocument(Path)` | Async JSON read with fallback to .bak |
| `BsonUtil` | `writeSync(Path, BuilderCodec<T>, T, Logger)` | Synchronous codec-based write |
| `BsonUtil` | `readDocumentNow(Path)` | Synchronous JSON read |

### Built-In Example: BarterShopState

The `ShopPlugin` initializes its state storage using the data directory:

```java
// In ShopPlugin.setup0():
BarterShopState.initialize(this.getDataDirectory());
```

`BarterShopState` then reads/writes to `{dataDirectory}/barter_shop_state.json`:

```java
// Save
public static void save() {
    Path file = saveDirectory.resolve("barter_shop_state.json");
    BsonUtil.writeSync(file, CODEC, instance, LOGGER);
}

// Load
public static void load() {
    Path file = saveDirectory.resolve("barter_shop_state.json");
    BsonDocument document = BsonUtil.readDocumentNow(file);
    instance = CODEC.decode(document, extraInfo);
}
```

### Recommended Pattern for Per-Player UI Preferences

Store one JSON file per player UUID in a subdirectory of the plugin's data folder:

```
mods/{group}_{name}/
  └── player_prefs/
      ├── {uuid1}.json
      └── {uuid2}.json
```

Each file would contain the serialized preferences:
```json
{
  "ActiveTab": "structural",
  "FilterSelections": ["wood", "stone"],
  "ToggleStates": { "showAll": true, "compactView": false },
  "SearchQuery": "door",
  "SelectedRecipeIndex": 5
}
```

Implementation approach using `BsonUtil` (the engine's own utility):

```java
// In your plugin class:
private Path prefsDir;

@Override
protected void setup0() {
    this.prefsDir = getDataDirectory().resolve("player_prefs");
    try {
        Files.createDirectories(prefsDir);
    } catch (IOException e) { /* handle */ }
}

// Save prefs for a player
public CompletableFuture<Void> savePrefs(UUID uuid, BsonDocument prefs) {
    Path file = prefsDir.resolve(uuid + ".json");
    return BsonUtil.writeDocument(file, prefs);
}

// Load prefs for a player
public CompletableFuture<BsonDocument> loadPrefs(UUID uuid) {
    Path file = prefsDir.resolve(uuid + ".json");
    return BsonUtil.readDocument(file, false);
}
```

### Gotchas
- `BsonUtil.writeDocument` automatically creates parent directories and writes a `.bak` backup
- `BsonUtil.readDocument` falls back to the `.bak` file if the primary is corrupted/empty
- These are async operations returning `CompletableFuture` — safe for the server thread
- The data directory is created lazily; you must call `Files.createDirectories()` yourself for subdirectories

---

## Mechanism 2: Persistent ECS Components (Player Entity Store)

### How It Works

Player entity data is stored as a serialized `EntityStore` in:
```
universe/players/{uuid}.json
```

The `DiskPlayerStorageProvider` handles this. When a player connects, the engine:
1. Calls `PlayerStorage.load(uuid)` → reads `universe/players/{uuid}.json`
2. Deserializes the `BsonDocument` into a `Holder<EntityStore>` using `EntityStore.REGISTRY.deserialize(document)`
3. All **components registered with an id + codec** are deserialized from the JSON

When a player disconnects or data is explicitly saved:
1. Calls `PlayerStorage.save(uuid, holder)` → serializes via `EntityStore.REGISTRY.serialize(holder)`
2. Writes to `universe/players/{uuid}.json`

### The Key Distinction: Persistent vs Transient Components

```java
// TRANSIENT — no id, no codec, NOT serialized to player file
registerComponent(MyComponent.class, MyComponent::new);

// PERSISTENT — has id + codec, IS serialized to player file
registerComponent(MyComponent.class, "MyComponentId", MyComponent.CODEC);
```

Components registered with `(Class, String id, BuilderCodec codec)` are automatically serialized/deserialized with the player's entity store. The `String id` becomes the JSON key.

### Built-In Example: SpawningPlugin

```java
// Persistent component (has id + codec → survives restart)
this.spawnMarkerComponentType = this.getEntityStoreRegistry()
    .registerComponent(SpawnMarkerEntity.class, "SpawnMarkerComponent", SpawnMarkerEntity.CODEC);

// Transient component (no id → lost on restart)
this.localSpawnControllerComponentType = this.getEntityStoreRegistry()
    .registerComponent(LocalSpawnController.class, LocalSpawnController::new);
```

### How to Use for Player Preferences

A plugin could register a persistent component on the player entity:

```java
// Define the component
public class UiPrefsComponent extends Component<EntityStore> {
    public static final BuilderCodec<UiPrefsComponent> CODEC = BuilderCodec.builder(...)
        .append(new KeyedCodec<>("ActiveTab", Codec.STRING, true), ...)
        .append(new KeyedCodec<>("SearchQuery", Codec.STRING, true), ...)
        .build();
}

// In setup0():
ComponentType<EntityStore, UiPrefsComponent> prefsType = getEntityStoreRegistry()
    .registerComponent(UiPrefsComponent.class, "PlaceBlockUiPrefs", UiPrefsComponent.CODEC);
```

This data would automatically serialize into `universe/players/{uuid}.json` alongside inventory, position, etc.

### Gotchas
- Component data is saved to the **universe** player directory, not the plugin's data directory
- If the plugin is removed, orphaned data stays in player files (stored under "Unknown" components)
- The `ComponentRegistry` handles unknown components gracefully — it preserves them as `UnknownComponents`
- Player data save is triggered on disconnect (`Player.saveConfig()`) and during world transfers
- Components are per-entity-store — player components are on the player entity specifically
- **Thread safety**: ECS component mutations must happen on the world thread (use `world.execute(...)`)

---

## Mechanism 3: Built-In Config System

### How It Works

`PluginBase` provides a `withConfig(name, codec)` method that creates a `Config<T>` backed by a JSON file in the plugin's data directory:

```java
Config<T> config = new Config<>(this.dataDirectory, name, configCodec);
// File: mods/{group}_{name}/{name}.json
```

The config is loaded during `preLoad()` and can be saved with `config.save()`.

### Limitations for Per-Player Data
- Designed for **singleton configuration**, not per-player data
- Single file per config name
- Not suitable for many-key maps (would need a single giant file for all players)

---

## Mechanism 4: Direct File I/O (BlockingDiskFile Pattern)

### How It Works

The `BlockingDiskFile` abstract class provides a thread-safe read/write pattern using `ReadWriteLock`. Built-in systems like `HytalePermissionsProvider`, `HytaleBanProvider`, and `HytaleWhitelistProvider` all use this pattern.

```java
public class HytalePermissionsProvider extends BlockingDiskFile {
    public HytalePermissionsProvider() {
        super(Paths.get("permissions.json")); // Relative to working dir (run/)
    }
    
    // Implements read(BufferedReader), write(BufferedWriter), create(BufferedWriter)
    // Uses Gson for JSON serialization
    // Calls syncSave() after mutations
    // Calls syncLoad() on startup
}
```

### Key APIs

| Class | Method | Purpose |
|-------|--------|---------|
| `BlockingDiskFile` | `syncLoad()` | Read file with write lock; creates if absent |
| `BlockingDiskFile` | `syncSave()` | Write file with read lock |
| `BlockingDiskFile` | `toLocalFile()` | Resolve path to `File` |

### File Locations Used by Built-In Systems
- `permissions.json` — relative to server working directory (`run/`)
- `bans.json` — relative to server working directory
- `whitelist.json` — relative to server working directory
- `universe/players/{uuid}.json` — player entity data
- `universe/memories.json` — MemoriesPlugin uses `Constants.UNIVERSE_PATH`

---

## Comparison Matrix

| Mechanism | Scope | Survives Restart | Per-Player | Thread Safe | Recommended For |
|-----------|-------|-----------------|------------|-------------|-----------------|
| Plugin Data Dir + BsonUtil | Plugin | Yes | Yes (one file per UUID) | Yes (async) | **UI preferences, player state** |
| Persistent ECS Component | Universe | Yes | Yes (auto per entity) | Yes (world thread) | Game-affecting state (inventory-like) |
| Config System | Plugin | Yes | No (singleton) | No | Plugin settings |
| BlockingDiskFile | Server | Yes | Manual | Yes (locks) | Global server data |

---

## Recommendation for UI Preferences

**Use Mechanism 1: Plugin Data Directory with BsonUtil.**

Rationale:
1. **Isolation** — Data lives in the plugin's own directory, not mixed into player entity stores
2. **Clean uninstall** — Removing the plugin removes all its data
3. **No ECS coupling** — UI preferences aren't game state; they shouldn't be ECS components
4. **Proven pattern** — `BarterShopState` uses exactly this approach in production
5. **Async I/O** — `BsonUtil.writeDocument/readDocument` return `CompletableFuture`, safe for server thread
6. **Backup safety** — Automatic `.bak` file creation on every write

The persistent ECS component approach (Mechanism 2) is technically viable but couples non-game-state data into the universe's player save files. It's better suited for data that semantically belongs on the player entity (like unlocked recipes, skill levels, etc.).

---

## See Also
- [lifecycle.md](./lifecycle.md) — Plugin startup/shutdown lifecycle
- [capabilities.md](./capabilities.md) — What plugins can and cannot do
- [events.md](./events.md) — PlayerDisconnectEvent for triggering saves
