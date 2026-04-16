---
topic: "Plugin Lifecycle"
category: "Plugins"
updated: 2026-04-16
sources: ["codebase analysis"]
---

# Plugin Lifecycle

## Summary

Hytale plugins extend `JavaPlugin` and implement `setup()` to register their commands, events, and systems. The lifecycle is managed by the server's plugin loader.

## JavaPlugin

The base class all plugins extend:

```java
public class MyPlugin extends JavaPlugin {

    public MyPlugin(@NonNull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // All registration happens here
    }
}
```

## Plugin Lifecycle Phases

```
1. Plugin Discovery
   └── Server scans run/mods/ for plugin JARs
   └── Reads manifest.json from each JAR

2. Dependency Resolution
   └── Orders plugins by Dependencies / OptionalDependencies

3. Plugin Instantiation
   └── Constructor called with JavaPluginInit
   └── Provides access to registries

4. setup() Called
   └── Register commands → getCommandRegistry()
   └── Register global events → getEventRegistry()
   └── Register ECS systems → getEntityStoreRegistry()
   └── Register asset load event → getEventRegistry().register(LoadAssetEvent.class, ...)

5. Asset Loading
   └── All asset packs loaded (vanilla + plugins)
   └── LoadAssetEvent fires → plugins modify assets

6. World Startup
   └── Worlds are created/loaded
   └── ECS systems begin ticking

7. Player Connections
   └── PlayerConnectEvent
   └── PlayerSetupConnectEvent
   └── PlayerReadyEvent → player fully loaded

8. Gameplay
   └── ECS ticking, events, commands active

9. Shutdown
   └── ShutdownEvent fires
   └── Worlds saved, connections closed
```

## Registration Methods

### Commands

```java
this.getCommandRegistry().registerCommand(new MyCommand());
```

### Global Events (IEvent)

```java
this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, MyPlugin::onPlayerReady);
this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, MyPlugin::onDisconnect);
this.getEventRegistry().register(LoadAssetEvent.class, MyPlugin::onAssetsLoaded);
```

### ECS Systems (EntityEventSystem / EntityTickingSystem)

```java
this.getEntityStoreRegistry().registerSystem(new PlacementCostScaler());
this.getEntityStoreRegistry().registerSystem(new NewMovementSystem());
```

## JavaPluginInit

The initialization context passed to the constructor:

- Provides access to the plugin's metadata (name, version, etc.)
- Provides the registries used in `setup()`

## HytaleLogger

Plugin-friendly logging:

```java
private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

LOGGER.info("Plugin initialized");
LOGGER.warning("Something unexpected");
```

Alternative: standard Java logging:

```java
private static final Logger LOGGER = Logger.getLogger("MyPlugin");
```

Or direct console output:

```java
System.out.println("[MyPlugin] message");
```

## See Also

- [Getting Started](./getting-started.md)
- [Manifest](./manifest.md)
- [Commands](./commands.md)
- [Events](./events.md)
