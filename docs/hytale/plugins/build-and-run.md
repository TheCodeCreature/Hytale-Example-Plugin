---
topic: "Build & Run"
category: "Plugins"
updated: 2026-04-16
sources: ["codebase analysis"]
---

# Build & Run

## Summary

Hytale plugins are built with Gradle using the `hytale-mod` plugin, which handles server SDK setup, asset packaging, and server launch.

## Build Commands

| Command | Purpose |
|---------|---------|
| `./gradlew build` | Compile and package the plugin JAR |
| `./gradlew runServer` | Start the Hytale server with the plugin |
| `./gradlew runServer --debug-jvm` | Start with remote debugger on port 5005 |
| `./gradlew test` | Run unit tests |
| `./gradlew clean` | Clean build outputs |

## Build Output

After `./gradlew build`:

```
build/
  ├── libs/
  │   ├── my-plugin-1.0.0.jar        ← Plugin JAR
  │   └── my-plugin-1.0.0-sources.jar ← Source JAR
  ├── classes/java/main/              ← Compiled classes
  └── resources/main/                 ← Processed resources
```

## Resource Processing

The `processResources` task replaces template variables in `manifest.json`:

```kotlin
tasks.named<ProcessResources>("processResources") {
    var replaceProperties = mapOf(
        "plugin_group" to findProperty("plugin_group"),
        "plugin_name" to project.name,
        "plugin_version" to project.version,
        "server_version" to findProperty("server_version"),
        "plugin_main_entrypoint" to findProperty("plugin_main_entrypoint"),
        // ...
    )
    filesMatching("manifest.json") {
        expand(replaceProperties)
    }
}
```

Variables like `${plugin_group}` in `manifest.json` are replaced with values from `gradle.properties`.

## Run Directory Structure

When `./gradlew runServer` is executed, the server runs from `run/`:

```
run/
  ├── config.json        ← Server configuration
  ├── permissions.json   ← Player permissions
  ├── whitelist.json     ← Whitelisted players
  ├── bans.json          ← Banned players
  ├── auth.enc           ← Authentication credentials
  ├── logs/              ← Server log files
  ├── mods/              ← Plugin JAR files (auto-deployed)
  └── universe/
      ├── players/       ← Per-player data
      └── worlds/        ← World data
```

## Debug Configuration (VS Code)

For `--debug-jvm`, configure a VS Code launch configuration:

```json
{
  "type": "java",
  "name": "Attach to Hytale Server",
  "request": "attach",
  "hostName": "localhost",
  "port": 5005
}
```

1. Run `./gradlew runServer --debug-jvm`
2. Wait for "Listening for transport dt_socket at address: 5005"
3. Attach the debugger from VS Code

## Hytale Gradle Plugin Options

```kotlin
hytale {
    // Include the game's Assets.zip on the classpath (WARNING: large file)
    // addAssetsDependency = true
    
    // Use pre-release server version
    // updateChannel = "pre-release"
}
```

## See Also

- [Getting Started](./getting-started.md)
- [Manifest](./manifest.md)
- [Server Config](../server/config.md)
