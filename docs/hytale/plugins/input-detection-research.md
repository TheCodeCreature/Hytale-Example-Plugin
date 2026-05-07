---
topic: "Player Input Detection for Server Plugins"
category: "Plugins / Input"
updated: 2026-05-06
sources: [
  "decompiled: MovementStates.java",
  "decompiled: MovementStatesComponent.java",
  "decompiled: GamePacketHandler.java",
  "decompiled: PlayerInput.java",
  "decompiled: InteractionModule.java",
  "decompiled: PlayerMouseButtonEvent.java",
  "decompiled: MouseButtonType.java",
  "decompiled: MouseButtonState.java",
  "decompiled: AnimationSlot.java",
  "decompiled: EmoteCommand.java",
  "decompiled: InteractionType.java",
  "decompiled: CustomPageEvent.java"
]
---

# Player Input Detection for Server Plugins

## Summary

This document covers what player inputs a server-side plugin can detect and how, with specific focus on crouch state, emote actions, mouse clicks, and item-in-hand checks.

---

## 1. Crouch / Sneak Detection — YES, POSSIBLE

The client sends `ClientMovement` packets (ID 108) every tick that include a `MovementStates` object. This object has a **`crouching`** boolean field that reflects the player's current crouch state.

### How it works

```
Client press crouch key
  → ClientMovement packet (movementStates.crouching = true)
  → GamePacketHandler.handle(ClientMovement)
  → PlayerInput.queue(new SetMovementStates(packet.movementStates))
  → ProcessPlayerInput system applies it
  → MovementStatesComponent.setMovementStates(movementStates)
```

### MovementStates fields

All boolean fields available on `MovementStates`:

| Field | Description |
|-------|-------------|
| `idle` | Player is idle |
| `horizontalIdle` | No horizontal movement |
| `jumping` | Currently jumping |
| `flying` | In flight mode |
| `walking` | Walking speed |
| `running` | Running speed |
| `sprinting` | Sprint speed |
| **`crouching`** | **Player is crouching / sneaking** |
| `forcedCrouching` | Forced crouch (e.g., under low ceiling) |
| `falling` | Currently falling |
| `climbing` | On a ladder/climbable |
| `inFluid` | In water/fluid |
| `swimming` | Swimming in fluid |
| `swimJumping` | Jumping while swimming |
| `onGround` | Feet on solid ground |
| `mantling` | Mantling over edge |
| `sliding` | Sliding on slope |
| `mounting` | Mounting a vehicle |
| `rolling` | Performing a roll |
| `sitting` | Sitting |
| `gliding` | Gliding |
| `sleeping` | Sleeping |

### Reading crouch state from an ECS system

```java
// In an EntityTickingSystem, query for MovementStatesComponent:
MovementStatesComponent msc = archetypeChunk.getComponent(index, MovementStatesComponent.getComponentType());
if (msc != null) {
    boolean isCrouching = msc.getMovementStates().crouching;
}
```

### Reading crouch state from an event handler

```java
// From any context where you have a Ref<EntityStore>:
Store<EntityStore> store = ref.getStore();
MovementStatesComponent msc = store.getComponent(ref, MovementStatesComponent.getComponentType());
if (msc != null && msc.getMovementStates().crouching) {
    // Player is crouching
}
```

### Key source files

| File | Line | What |
|------|------|------|
| `protocol/MovementStates.java` | L25 | `public boolean crouching;` |
| `server/core/entity/movement/MovementStatesComponent.java` | L28 | `getMovementStates()` |
| `server/core/io/handlers/game/GamePacketHandler.java` | ~L284 | `new PlayerInput.SetMovementStates(packet.movementStates)` |
| `server/core/modules/entity/player/PlayerInput.java` | L211 | `SetMovementStates` record |

---

## 2. Emote Action Detection — NOT POSSIBLE FROM CLIENT

**There is no client-to-server emote request packet.**

Emotes are a server-only system:
- The `/emote` command is the only trigger
- `EmoteCommand.execute()` calls `AnimationUtils.playAnimation(ref, AnimationSlot.Emote, ...)`
- `AnimationSlot.Emote(4)` is an animation output slot, NOT an input action
- `GamePacketHandler.registerHandlers()` has NO emote request handler

The `InteractionType` enum also has no emote entry — it covers: Primary, Secondary, Ability1-3, Use, Pick, Pickup, CollisionEnter/Leave, Collision, EntityStatEffect, SwapTo/From, Death, Wielding, Projectile*, Held, HeldOffhand, Equipped, Dodge, GameModeSwap.

**Conclusion: The emote button/key press is handled entirely client-side. No packet is sent to the server when the player presses the emote key.**

---

## 3. Item in Hand Detection — YES, POSSIBLE

### From inventory directly

```java
Inventory inventory = player.getInventory();
ItemStack itemInHand = inventory.getItemInHand();      // active hotbar item
byte activeSlot = inventory.getActiveHotbarSlot();      // active slot index
ItemStack offhand = inventory.getUtilityItem();         // offhand item

// Check specific item
if (itemInHand != null && !itemInHand.isEmpty()) {
    String itemId = itemInHand.getItemId();
}
```

### From PlayerMouseButtonEvent

```java
Item item = event.getItemInHand();  // resolved Item (not ItemStack)
```

### From hotbar container

```java
ItemContainer hotbar = inventory.getHotbar();
short capacity = hotbar.getCapacity();
for (short slot = 0; slot < capacity; slot++) {
    ItemStack stack = hotbar.getItemStack(slot);
}
```

---

## 4. Sending Chat Messages — YES, STANDARD API

```java
playerRef.sendMessage(Message.raw("§a[Plugin] Hello!"));
playerRef.sendMessage(Message.raw("§c[Error] Something went wrong"));
playerRef.sendMessage(Message.translation("my.translation.key").param("name", "value"));
```

Color codes use `§` prefix: `§a` green, `§c` red, `§e` yellow, `§6` gold, `§b` aqua, `§f` white, `§7` gray.

---

## 5. Alternative Input Combinations

### RECOMMENDED: Crouch + Right-Click (PlayerMouseButtonEvent)

Since emote detection is impossible, **crouch + right-click while holding stencil** is the best alternative.

#### PlayerMouseButtonEvent

A global `IEvent` dispatched by `InteractionModule.doMouseInteraction()` for every mouse button press/release. Fired on `HytaleServer.get().getEventBus()`.

```java
public class PlayerMouseButtonEvent extends PlayerEvent<Void> implements ICancellable {
    PlayerRef getPlayerRefComponent();
    Item getItemInHand();           // resolved Item asset
    Vector3i getTargetBlock();      // block being looked at (nullable)
    Entity getTargetEntity();       // entity being looked at (nullable)
    Vector2f getScreenPoint();      // screen coordinates
    MouseButtonEvent getMouseButton();  // button type + state
    long getClientUseTime();
    void setCancelled(boolean);     // cancellable!
}
```

#### MouseButtonType values

| Value | Enum |
|-------|------|
| 0 | `Left` — primary attack/interact |
| 1 | `Middle` — block pick |
| 2 | `Right` — secondary use/place |
| 3 | `X1` — mouse extra button 1 |
| 4 | `X2` — mouse extra button 2 |

#### MouseButtonState values

| Value | Enum |
|-------|------|
| 0 | `Pressed` — button down |
| 1 | `Released` — button up |

#### Implementation pattern

```java
// In plugin setup, register global listener:
this.getEventRegistry().registerGlobal(PlayerMouseButtonEvent.class, this::onMouseButton);

private void onMouseButton(PlayerMouseButtonEvent event) {
    // Only right-click press
    MouseButtonEvent mb = event.getMouseButton();
    if (mb.mouseButtonType != MouseButtonType.Right || mb.state != MouseButtonState.Pressed) {
        return;
    }

    // Check if holding stencil
    Item item = event.getItemInHand();
    if (item == null || !isStencilItem(item)) {
        return;
    }

    // Check if crouching
    Ref<EntityStore> ref = event.getRef();
    Store<EntityStore> store = ref.getStore();
    MovementStatesComponent msc = store.getComponent(ref, MovementStatesComponent.getComponentType());
    if (msc == null || !msc.getMovementStates().crouching) {
        return;
    }

    // All conditions met!
    event.setCancelled(true); // prevent normal right-click action
    PlayerRef playerRef = event.getPlayerRefComponent();
    playerRef.sendMessage(Message.raw("§a[Stencil] Radial menu trigger detected!"));
}
```

### Other viable alternatives

| Approach | Pros | Cons |
|----------|------|------|
| **Crouch + Right-click** | Natural feel, cancellable, has item context | Conflicts with crouch+place in some contexts |
| **Middle mouse click** | Rarely used, dedicated feel | Conflicts with block picker; not all mice have it |
| **X1/X2 mouse buttons** | No conflicts with vanilla | Not all mice have extra buttons |
| **Chat command** (`/stencil menu`) | Simple, reliable | Not immersive, requires typing |
| **Double-tap slot switch** | No mouse conflict | Hacky detection, poor UX |
| **CustomPageEvent** | Arbitrary string data payload | Requires a custom UI page to already be open |

### CustomPageEvent (for future radial menu)

When you build the radial menu UI later, `CustomPageEvent` (packet ID 219) is how custom UI pages communicate back to the server:

```java
public class CustomPageEvent implements Packet {
    public CustomPageEventType type;  // Acknowledge, etc.
    public String data;              // freeform string payload (up to 4MB)
}
```

The server receives this in `GamePacketHandler.handle(CustomPageEvent)` and dispatches it to the active `Page` manager. This is how your radial menu selections will be communicated back.

---

## 6. Feasibility Assessment

### Can crouch + emote + held item be detected?

**No. Emote button press is not transmitted to the server.** The emote system is server-to-client only (server triggers animations via `AnimationUtils.playAnimation`).

### Recommended alternative: crouch + right-click + held item

**Yes, fully feasible.** All three components are available server-side:

| Component | API | Source |
|-----------|-----|--------|
| Crouch state | `MovementStatesComponent.getMovementStates().crouching` | `ClientMovement` packet → ECS component |
| Right-click | `PlayerMouseButtonEvent` with `MouseButtonType.Right` | `MouseInteraction` packet → global event |
| Item in hand | `PlayerMouseButtonEvent.getItemInHand()` or `inventory.getItemInHand()` | Available on event and inventory |
| Cancel action | `event.setCancelled(true)` | `ICancellable` interface |
| Send feedback | `playerRef.sendMessage(Message.raw(...))` | Standard API |

### Registration

```java
// Register as global event (fires for ALL players, all worlds):
this.getEventRegistry().registerGlobal(PlayerMouseButtonEvent.class, handler);
```

Note: `PlayerMouseButtonEvent` uses `PlayerEvent<Void>` (key type is `Void`), meaning it dispatches **globally only** — you cannot scope it to a specific world name. Use `registerGlobal()`.

---

## See Also

- [Events](./events.md) — full event list
- [Capabilities](./capabilities.md) — what plugins can/cannot do
- [Networking](./networking.md) — packet sending
- [Inventory & Hotbar Events](../inventory-hotbar-events.md) — inventory change detection
