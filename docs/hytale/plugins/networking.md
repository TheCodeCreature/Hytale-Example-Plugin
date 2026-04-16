---
topic: "Networking & Packets"
category: "Plugins"
updated: 2026-04-16
sources: ["codebase analysis"]
---

# Networking & Packets

## Summary

Hytale uses a custom binary protocol for server-client communication. Plugins can send certain packets to modify client-side state without changing actual world data.

## Packet Sending

All packets are sent through the player's packet handler:

```java
PlayerRef playerRef = ...;
PacketHandler handler = playerRef.getPacketHandler();
handler.send(packet);
```

## Key Packet Types

### ServerSetBlock

Sends a fake block update to a specific client. The server's actual world data is unchanged:

```java
ServerSetBlock packet = new ServerSetBlock(x, y, z, blockTypeId);
playerRef.getPacketHandler().send(packet);
```

**Use cases:**
- Transparency volumes (show transparent blocks around camera)
- Visual effects (temporary block changes)
- Per-player block overrides

### SetServerCamera (via ServerCameraSettings)

Controls the player's camera:

```java
ServerCameraSettings settings = new ServerCameraSettings();
settings.distance = 3.0f;
settings.positionOffset = new Vector3f(0, 1, 0);
settings.eyeOffset = true;
settings.isFirstPerson = false;
settings.displayReticle = true;
settings.positionLerpSpeed = 0.9f;
settings.rotationLerpSpeed = 0.9f;
// ... send via appropriate API
```

### UpdateBlockTypes

Updates block type definitions on the client. Used to create transparent block variants:

```java
// Register a new transparent variant of a block type on the client
// Used by transparency volume systems
```

## Message Sending

Send chat messages to players:

```java
playerRef.sendMessage(Message.raw("§a[Plugin] Hello!"));
```

## Packet Direction

| Direction | Description |
|-----------|-------------|
| Server → Client | Plugin can send (ServerSetBlock, camera settings, messages) |
| Client → Server | Plugin can intercept via packet handlers (interaction chains) |

## Threading

Sending packets is generally thread-safe — `PacketHandler.send()` can be called from any thread. However, reading world state to build packet data must happen on the world thread.

## See Also

- [Server vs Client](../server-client-boundary.md)
- [Plugin Capabilities](./capabilities.md)
