---
topic: "Server Folder Structure"
category: "Server"
updated: 2026-04-16
sources: ["codebase analysis"]
---

# Server Folder Structure

## Summary

The Hytale server uses a specific directory layout for configuration, plugins, world data, and logs.

## Run Directory Layout

```
run/
  ├── config.json           ← Main server configuration
  ├── config.json.bak       ← Config backup
  ├── permissions.json      ← Player permissions
  ├── whitelist.json        ← Whitelist configuration
  ├── bans.json             ← Banned players
  ├── auth.enc              ← Encrypted auth credentials
  │
  ├── mods/                 ← Plugin JAR files go here
  │   └── my-plugin-1.0.jar
  │
  ├── logs/                 ← Server log files
  │   ├── 2026-04-07_13-02-40_server.log.lck
  │   └── ...
  │
  └── universe/             ← Game world data
      ├── players/          ← Per-player save data
      │   └── <uuid>/
      └── worlds/           ← World data
          └── default/      ← Default world
```

## Plugin Project Layout

```
my-plugin/
  ├── build.gradle.kts          ← Build configuration
  ├── settings.gradle.kts       ← Project settings
  ├── gradle.properties         ← Plugin metadata & variables
  ├── gradlew / gradlew.bat     ← Gradle wrapper
  │
  ├── gradle/
  │   ├── libs.versions.toml    ← Dependency versions
  │   └── wrapper/
  │       └── gradle-wrapper.properties
  │
  ├── src/
  │   ├── main/
  │   │   ├── java/
  │   │   │   └── com/
  │   │   │       └── myplugin/
  │   │   │           ├── MyPlugin.java          ← Entry point (extends JavaPlugin)
  │   │   │           ├── command/               ← Command classes
  │   │   │           ├── system/                ← ECS systems
  │   │   │           └── util/                  ← Utilities
  │   │   └── resources/
  │   │       ├── manifest.json                  ← Plugin manifest
  │   │       ├── icon-256.png                   ← Plugin icon (optional)
  │   │       └── Server/                        ← Asset pack (if IncludesAssetPack)
  │   │           └── Item/
  │   │               └── Items/
  │   │                   └── MyBlock.json
  │   └── test/
  │       └── java/
  │           └── com/
  │               └── myplugin/
  │                   └── MyPluginTest.java
  │
  ├── run/                      ← Server runtime directory
  │   ├── config.json
  │   └── mods/
  │
  └── build/                    ← Build outputs
      └── libs/
          └── my-plugin-1.0.0.jar
```

## Asset Pack Structure

When `IncludesAssetPack` is true in the manifest, resources under `Server/` are loaded as game assets:

```
src/main/resources/Server/
  └── Item/
      ├── Block/               ← Block-specific definitions
      │   └── Fluids/
      │       └── Water_Source.json
      ├── Items/               ← Item and placeable block definitions
      │   ├── Rock/
      │   │   └── Aqua/
      │   │       └── Rock_Stone_Aqua.json
      │   ├── Furniture/
      │   │   └── Human/
      │   │       └── Furniture_Human_Ruins_Ladder.json
      │   ├── Plant/
      │   │   └── Grass/
      │   │       └── Plant_Grass_Sharp_Wild.json
      │   ├── Fluid/
      │   │   └── Fluid_Water.json
      │   └── _Debug/
      │       └── Placeholders/
      │           └── Placeholder_Full.json
      └── NPC/                 ← NPC definitions
          └── ...
```

### Folder Organization Conventions

| Folder | Content |
|--------|---------|
| `Items/Rock/` | Rock and stone block variants |
| `Items/Wood/` | Wood block variants |
| `Items/Plant/` | Plants, flowers, leaves |
| `Items/Furniture/` | Furniture items by culture/set |
| `Items/Fluid/` | Liquid items |
| `Items/_Debug/` | Development/debug items |
| `Block/Fluids/` | Block-specific fluid definitions |

## Build Output Structure

```
build/
  ├── classes/java/main/       ← Compiled .class files
  ├── classes/java/test/        ← Compiled test classes
  ├── generated/sources/        ← Generated sources
  ├── libs/                     ← Output JARs
  │   ├── plugin-1.0.0.jar
  │   └── plugin-1.0.0-sources.jar
  ├── resources/main/           ← Processed resources (manifest expanded)
  ├── resources/test/           ← Test resources
  ├── reports/
  │   ├── tests/                ← Test reports (HTML)
  │   └── problems/             ← Build problem reports
  └── test-results/test/        ← JUnit XML results
```

## See Also

- [Server Config](./config.md)
- [Build & Run](../plugins/build-and-run.md)
- [Manifest](../plugins/manifest.md)
