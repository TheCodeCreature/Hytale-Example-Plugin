---
topic: "Plugin Manifest"
category: "Plugins"
updated: 2026-04-16
sources: ["codebase analysis"]
---

# Plugin Manifest (manifest.json)

## Summary

Every plugin requires a `manifest.json` in its resources root. This file declares the plugin's identity, dependencies, and capabilities.

## Full Schema

```json
{
  "Group": "MyPluginGroup",
  "Name": "My Plugin",
  "Version": "1.0.0",
  "Description": "What this plugin does",
  "Authors": [
    { "Name": "AuthorName" }
  ],
  "Website": "https://example.com",
  "ServerVersion": "2026.03.26-89796e57b",
  "Dependencies": {
    "Hytale:EntityModule": "*"
  },
  "OptionalDependencies": {},
  "DisabledByDefault": false,
  "IncludesAssetPack": true,
  "Main": "com.myplugin.MyPlugin"
}
```

## Field Reference

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `Group` | String | Yes | Plugin group/organization |
| `Name` | String | Yes | Plugin display name |
| `Version` | String | Yes | Semantic version |
| `Description` | String | No | Human-readable description |
| `Authors` | Array | No | List of author objects with `Name` field |
| `Website` | String | No | Project website URL |
| `ServerVersion` | String | Yes | Minimum compatible server version |
| `Dependencies` | Object | No | Required dependencies (key: `"Module:Name"`, value: version) |
| `OptionalDependencies` | Object | No | Optional dependencies |
| `DisabledByDefault` | Boolean | No | Whether the plugin starts disabled |
| `IncludesAssetPack` | Boolean | No | Whether the plugin contains asset files |
| `Main` | String | Yes | Fully qualified class name of the plugin entry point |

## Dependencies

Dependencies are specified as `"Namespace:ModuleName": "version"`:

```json
"Dependencies": {
  "Hytale:EntityModule": "*"
}
```

- `"*"` means any version
- `"Hytale:EntityModule"` — the built-in entity module, required for ECS component access (physics, movement, etc.)

Without declaring `EntityModule` as a dependency, components like `Velocity`, `MovementManager` may not be available.

## IncludesAssetPack

When `true`, the plugin's `Server/` resource directory is loaded as an asset pack:

```
src/main/resources/
  ├── manifest.json
  └── Server/
      └── Item/
          └── Items/
              └── MyCustomBlock.json    ← Loaded as a game asset
```

This allows plugins to:
- Add new blocks, items, NPCs
- Override existing vanilla assets
- Define custom drop lists, interactions, etc.

## Template Variables

The `processResources` Gradle task replaces `${variable_name}` placeholders in manifest.json with values from `gradle.properties`:

```json
{
  "Group": "${plugin_group}",
  "Name": "${plugin_name}",
  "Version": "${plugin_version}",
  "Main": "${plugin_main_entrypoint}"
}
```

## See Also

- [Getting Started](./getting-started.md)
- [Plugin Lifecycle](./lifecycle.md)
- [Asset Pipeline](../assets/asset-pipeline.md)
