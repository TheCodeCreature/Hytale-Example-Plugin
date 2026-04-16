---
topic: "Server Configuration"
category: "Server"
updated: 2026-04-16
sources: ["codebase analysis", "run/config.json"]
---

# Server Configuration

## Summary

The Hytale server is configured via JSON files in the `run/` directory. The primary configuration file is `config.json`.

## config.json

```json
{
  "Version": 4,
  "ServerName": "Hytale Server",
  "MOTD": "",
  "Password": "",
  "MaxPlayers": 100,
  "MaxViewRadius": 32,
  "Defaults": {
    "World": "default",
    "GameMode": "Adventure"
  },
  "ConnectionTimeouts": {},
  "RateLimit": {},
  "Modules": {
    "PathPlugin": {
      "Modules": {}
    }
  },
  "LogLevels": {},
  "Mods": {},
  "DefaultModsEnabled": true,
  "DisplayTmpTagsInStrings": false,
  "PlayerStorage": {
    "Type": "Hytale"
  },
  "AuthCredentialStore": {
    "Type": "Encrypted",
    "Path": "auth.enc"
  },
  "Update": {}
}
```

## Key Fields

| Field | Type | Description |
|-------|------|-------------|
| `Version` | Integer | Config format version |
| `ServerName` | String | Server display name |
| `MOTD` | String | Message of the day |
| `Password` | String | Server password (empty = no password) |
| `MaxPlayers` | Integer | Maximum concurrent players |
| `MaxViewRadius` | Integer | Maximum view distance in chunks |
| `Defaults.World` | String | Default world name |
| `Defaults.GameMode` | String | Default game mode: `Adventure`, `Creative`, `Spectator` |
| `DefaultModsEnabled` | Boolean | Whether vanilla mods are enabled |
| `Mods` | Object | Per-mod configuration overrides |

## Other Configuration Files

### permissions.json

Controls player permissions and admin access.

### whitelist.json

When enabled, only listed players can join.

### bans.json

List of banned player UUIDs.

### auth.enc

Encrypted authentication credentials for the server's connection to Hytale services.

## See Also

- [Folder Structure](./folder-structure.md)
- [Build & Run](../plugins/build-and-run.md)
