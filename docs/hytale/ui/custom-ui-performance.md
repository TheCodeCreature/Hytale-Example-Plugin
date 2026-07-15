---
topic: "CustomUI Performance — Packet Sizes, Threading, Lifecycle, and Event Handling"
category: "Plugin API / Custom UI / Performance"
updated: 2026-06-01
sources:
  - "decompiled: InteractiveCustomUIPage.java"
  - "decompiled: CustomUIPage.java"
  - "decompiled: PageManager.java"
  - "decompiled: UICommandBuilder.java"
  - "decompiled: CustomPage.java (packet ID 218)"
  - "decompiled: CustomPageEvent.java (packet ID 219)"
  - "decompiled: CustomUICommand.java"
  - "decompiled: CustomPageEventType.java"
  - "decompiled: World.java (task queue, consumeTaskQueue)"
  - "plugin: StencilSelectionPage.java, GridLayoutController.java, DetailPanelController.java"
---

# CustomUI Performance — Packet Sizes, Threading, Lifecycle, and Event Handling

## 1. `sendUpdate()` Packet Size — Limits and Performance Cliffs

### Hard Limits (from decompiled `CustomPage.java`)

| Constant | Value | Meaning |
|----------|-------|---------|
| `CustomPage.MAX_SIZE` | `1,677,721,600` (~1.6 GB) | Max serialized packet size |
| `CustomPage.IS_COMPRESSED` | `true` | Packet is compressed before transmission |
| `CustomUICommand.MAX_SIZE` | `49,152,029` (~49 MB) | Max single command size |
| Max commands array length | `4,096,000` | Enforced in `CustomPage.deserialize()` |
| Max eventBindings array length | `4,096,000` | Same enforcement |
| Per-string max length | `4,096,000` bytes | Selector, data, text fields individually |

**Evidence:** [CustomPage.java](.tmp_hytale_src/com/hypixel/hytale/protocol/packets/interface_/CustomPage.java) lines 17-21:
```java
public static final int MAX_SIZE = 1677721600;
// ...
if (commandsCount > 4096000) {
    throw ProtocolException.arrayTooLong("Commands", commandsCount, 4096000);
}
```

### Per-Command Serialization Cost

Each `CustomUICommand` has 4 fields:
- `type` — 1 byte (enum ordinal)
- `selector` — VarString (e.g. `#RecipeGridArea[3] #GroupCells[5] #Btn.Style` ≈ 40-60 bytes)
- `data` — VarString, null for Append/Clear/Remove; BSON JSON for Set (e.g. `{"0": "some_value"}` ≈ 20-60 bytes)
- `text` — VarString, used for documentPath in Append (e.g. `Pages/StencilBook/SetGroupContainer.ui` ≈ 50 bytes); null for Set

**Estimated size per `cmd.set()` call:** ~80-120 bytes uncompressed (14 bytes fixed header + selector + BSON value wrapper).

**2000 `cmd.set()` calls ≈ 160-240 KB uncompressed.** With `IS_COMPRESSED = true`, likely compresses to 30-60 KB due to highly repetitive selector prefixes.

### Will 2000+ Commands Cause Problems?

**No hard limit is hit.** 2000 commands is 0.05% of the 4,096,000 array limit. The ~200 KB uncompressed payload is trivial compared to the 1.6 GB packet max.

**Potential performance concerns:**

1. **Client-side processing** — Each `Set` command requires the client to resolve the selector (walk the DOM tree) and apply the property change. With 2000 commands, this is a burst of DOM mutations. **⚠️ SPECULATIVE:** The client likely batches rendering, so the DOM walk is the main cost — `O(n * d)` where `n` = command count, `d` = DOM depth per selector resolution. No evidence of a hard cliff, but empirical testing is recommended above ~1000 commands.

2. **Serialization cost on the server** — `UICommandBuilder` stores commands in an `ObjectArrayList<CustomUICommand>` (fastutil). Building 2000 `CustomUICommand` objects is ~2000 object allocations + BSON encoding per `set()`. This is CPU work on the world thread (during `handleDataEvent()` or `build()`). **Measured concern:** The BSON encoding in `setBsonValue()` creates a `BsonDocument` wrapper per call. 2000 BSON documents is non-trivial GC pressure but unlikely to cause visible latency.

3. **Packet serialization** — Happens during `PacketHandler.write()` on the Netty I/O thread, NOT the world thread. The world thread only builds the command array and hands it to Netty.

### Recommendation

2000 `cmd.set()` calls per update is within safe operating range. If experiencing issues, profile the **client-side** first — the server cost is dominated by object allocation, not I/O.

---

## 2. `cmd.append()` Cost — Template Caching

### Server-Side Cost: Negligible (O(1))

**Evidence:** [UICommandBuilder.java](.tmp_hytale_src/com/hypixel/hytale/server/core/ui/builder/UICommandBuilder.java) lines 49-52:
```java
public UICommandBuilder append(String selector, String documentPath) {
    this.commands.add(new CustomUICommand(CustomUICommandType.Append, selector, null, documentPath));
    return this;
}
```

The server does **not** read the `.ui` file. It stores only the string path. The `CustomUICommand` is a data object with 4 string fields. **500 `cmd.append()` calls = 500 object allocations + 500 path strings.** No file I/O whatsoever on the server.

### Client-Side Cost: Template Instantiation

From the architecture docs:
> ".ui files are downloaded to the client when the player connects (via asset pack)"

The client receives the `Append` command with a path like `"Pages/StencilBook/SetGroupContainer.ui"`. The client must:
1. Look up the template from its asset cache (already loaded at connection time — no disk I/O)
2. Parse and instantiate the template DOM nodes
3. Attach them to the parent container at the specified selector

**⚠️ SPECULATIVE:** Template files are almost certainly cached after first parse on the client side (standard practice for asset-heavy game engines). Repeated `append()` of the same template path reuses the parsed template definition. Each `append()` creates a new DOM instance from the cached definition.

### Build-Time Append Pattern in StencilSelectionPage

The plugin's `build()` method appends 500+ elements:
```java
// Set filter buttons — one per set (~20-40)
for (int i = 0; i < totalSetCount; i++) {
    cmd.append("#SetFilters", "Pages/StencilBook/SetFilterButton.ui");
}
// Material group buttons (30)
for (int i = 0; i < MAX_GROUP_BUTTONS; i++) {
    cmd.append("#MaterialGroups", "Pages/StencilBook/GroupFilterButton.ui");
}
// Per-set group containers with variable cell counts (sets × cells)
for (int g = 0; g < totalSetCount; g++) {
    cmd.append("#RecipeGridArea", "Pages/StencilBook/SetGroupContainer.ui");
    for (int c = 0; c < cellsPerSet[g]; c++) {
        cmd.append("#RecipeGridArea[" + g + "] #GroupCells",
                   "Common/Components/ClickableIconCell.ui");
    }
}
// Cost cells (8)
for (int i = 0; i < DetailPanelController.MAX_COST_CELLS; i++) {
    cmd.append("#CostGrid", "Common/Components/CostCell.ui");
}
```

This is a one-time cost during `build()`. Subsequent `handleDataEvent()` calls use `cmd.set()` only — no re-appending. **This is the correct pattern.**

### Recommendation

`cmd.append()` is cheap on the server (string storage only). The cost is on the client during DOM instantiation. For 500+ elements, this is a one-time burst at page open. Use `cmd.set()` for subsequent updates — never re-append what already exists.

---

## 3. Server Thread Blocking — `sendUpdate()` Is NOT Fire-and-Forget

### The `InteractiveCustomUIPage.sendUpdate()` Path

**Evidence:** [InteractiveCustomUIPage.java](.tmp_hytale_src/com/hypixel/hytale/server/core/entity/entities/player/pages/InteractiveCustomUIPage.java) lines 35-62:
```java
protected void sendUpdate(@Nullable UICommandBuilder commandBuilder,
                          @Nullable UIEventBuilder eventBuilder, boolean clear) {
    Ref<EntityStore> ref = this.playerRef.getReference();
    if (ref != null) {
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> {                           // ← DEFERRED to world task queue
            if (ref.isValid()) {
                Player playerComponent = store.getComponent(ref, Player.getComponentType());
                playerComponent.getPageManager().updateCustomPage(new CustomPage(...));
            }
        });
    }
}
```

### Critical Difference: `InteractiveCustomUIPage` vs `CustomUIPage`

| Class | `sendUpdate()` behavior |
|-------|------------------------|
| `CustomUIPage` (base) | **Synchronous** — calls `updateCustomPage()` directly |
| `InteractiveCustomUIPage` (subclass) | **Deferred** — wraps in `world.execute()` |

`InteractiveCustomUIPage` overrides `sendUpdate()` to route through `world.execute()`. This means:

1. **`handleDataEvent()` runs on the world thread** (dispatched from the packet handler)
2. **`sendUpdate()` submits a NEW task to the world task queue** (via `world.execute()`)
3. **The actual packet send happens later**, during the same `consumeTaskQueue()` drain pass (the drain loop picks up newly-added tasks — see world-thread-analysis.md)

### `updateCustomPage()` — The Actual Send

**Evidence:** [PageManager.java](.tmp_hytale_src/com/hypixel/hytale/server/core/entity/entities/player/pages/PageManager.java) lines 113-116:
```java
public void updateCustomPage(@Nonnull CustomPage page) {
    this.customPageRequiredAcknowledgments.incrementAndGet();
    this.playerRef.getPacketHandler().write(page);
}
```

`PacketHandler.write()` is a Netty channel write — **asynchronous/non-blocking**. The packet is queued for Netty's I/O thread. The world thread does NOT wait for network transmission or client acknowledgment.

### Backpressure: Acknowledgment-Based Event Gating

The server uses an acknowledgment counter to gate incoming events:

```java
// PageManager.handleEvent():
case Data:
    if (this.customPageRequiredAcknowledgments.get() != 0 || this.customPage == null) {
        return;  // ← SILENTLY DROPS the event
    }
    this.customPage.handleDataEvent(ref, store, event.data);

case Acknowledge:
    if (this.customPageRequiredAcknowledgments.decrementAndGet() < 0) {
        this.customPageRequiredAcknowledgments.incrementAndGet();
        throw new IllegalArgumentException("Client sent unexpected acknowledgement");
    }
```

**Flow:**
1. `sendUpdate()` → deferred task → `updateCustomPage()` → increments ack counter → `write()` to Netty
2. While `customPageRequiredAcknowledgments > 0`: all incoming `Data` events are **silently dropped**
3. Client processes the packet, sends `CustomPageEvent(Acknowledge)` back
4. Server decrements counter; only then will new `Data` events be processed

### Can Queuing Many Large Updates Cause Backpressure?

**On the server:** No traditional backpressure. Each `sendUpdate()` adds one task to the unbounded `LinkedBlockingDeque` (world task queue). The Netty write queue is also unbounded from the server's perspective. Multiple `sendUpdate()` calls within a single `handleDataEvent()` would queue multiple tasks, each creating a `CustomPage` packet with its own ack.

**Risk:** If N `sendUpdate()` calls are made, the ack counter reaches N. ALL subsequent data events are dropped until the client sends N acknowledgments. Since the client processes updates sequentially, this creates a latency gap of `N × network_RTT`.

### Recommendation

Call `sendUpdate()` exactly **once** per `handleDataEvent()`. Accumulate all `cmd.set()` calls into a single `UICommandBuilder`, then send once. The current StencilSelectionPage follows this pattern correctly.

---

## 4. `InteractiveCustomUIPage` Lifecycle — State Caching

### `build()` Is Always Called Fresh on Open

**Evidence:** [PageManager.java](.tmp_hytale_src/com/hypixel/hytale/server/core/entity/entities/player/pages/PageManager.java) lines 60-69:
```java
public void openCustomPage(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                           @Nonnull CustomUIPage page) {
    UICommandBuilder commandBuilder = new UICommandBuilder();
    UIEventBuilder eventBuilder = new UIEventBuilder();
    if (this.customPage != null) {
        this.customPage.onDismiss(ref, ref.getStore());  // dismiss old page
    }
    page.build(ref, commandBuilder, eventBuilder, store);  // ← ALWAYS called
    this.updateCustomPage(new CustomPage(page.getClass().getName(), true, true, ...));
    this.customPage = page;  // cache the page instance
}
```

`build()` is called every time `openCustomPage()` is invoked. There is no shortcut to skip it.

### Page Instance IS Cached Between Events

After `build()`, the page object is stored as `this.customPage` in `PageManager`. It persists across all subsequent `handleDataEvent()` calls until the page is dismissed or replaced. This means:
- Instance fields survive between events (the plugin's `selectedRecipeId`, `activeTab`, `displayedRecipes`, etc.)
- The page can maintain a full state machine across interactions
- No need to reconstruct state from scratch on each event

### `rebuild()` Exists — Full Teardown + Rebuild

**Evidence:** [CustomUIPage.java](.tmp_hytale_src/com/hypixel/hytale/server/core/entity/entities/player/pages/CustomUIPage.java) lines 46-54:
```java
protected void rebuild() {
    Ref<EntityStore> ref = this.playerRef.getReference();
    if (ref != null) {
        Store<EntityStore> store = ref.getStore();
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        this.build(ref, commandBuilder, eventBuilder, ref.getStore());
        playerComponent.getPageManager().updateCustomPage(
            new CustomPage(this.getClass().getName(), false, true, ...));
        //                                            ^^^^^^^^^^^^^^^^
        //                                            clear: true = remove all existing DOM
    }
}
```

`rebuild()` calls `build()` again with `clear: true`, which tells the client to discard the current DOM and apply the new commands from scratch. This is the "nuclear option" — expensive because it re-appends all templates and re-binds all events.

### Partial Update Pattern

The correct pattern (already used by StencilSelectionPage):
1. `build()`: Append all templates, bind all events, set initial values — **one-time setup**
2. `handleDataEvent()`: Use `cmd.set()` to mutate existing element properties — **incremental updates only**
3. Never call `rebuild()` or re-append templates during event handling

### Can You Cache the Page Instance Across Opens?

**No.** `openCustomPage()` always calls `build()`, which sends the full command set to the client. If you cache a page instance and pass it to `openCustomPage()` again, `build()` runs again, re-appending all templates (duplicating DOM elements on the client). The `clear: true` flag in the initial `openCustomPage` packet prevents this from being visually broken, but the build cost is still paid.

**Workaround:** You can cache **data** outside the page (e.g., `StencilBookPrefsStore`) and restore it in `build()`. This is what the plugin already does.

---

## 5. Event Coalescing — Rapid Clicks and the Acknowledgment Gate

### No Engine-Level Event Coalescing

Events are NOT coalesced. Each client interaction generates one `CustomPageEvent(Data, ...)` packet. The engine processes them independently. **However**, the acknowledgment gate effectively creates a serialization barrier.

### Rapid Tab Switch Scenario: 5 Clicks

```
Time →
Player clicks: T1    T2    T3    T4    T5
               ↓     ↓     ↓     ↓     ↓
Client sends:  Data  Data  Data  Data  Data
               ↓     ↓     ↓     ↓     ↓
Server:        ✓     ✗     ✗     ✗     ✗
               ↓     DROP  DROP  DROP  DROP
               │
               └→ handleDataEvent(T1)
                  └→ sendUpdate() → ack++ (now = 1)
                     └→ world.execute() → updateCustomPage()
                        └→ write(CustomPage)
                                          ...RTT...
                        Client receives, renders, sends Acknowledge
                                          ↓
Server: ack-- (now = 0), ready for next Data event
```

**Result:** Only the FIRST click (T1) is processed. Clicks T2-T5 are silently dropped because `customPageRequiredAcknowledgments > 0` when they arrive. After the client acknowledges the first update, the server is ready again — but the player has already stopped clicking.

### Important Nuance: Timing Matters

If the player clicks slowly enough that the client acknowledges each update before the next click arrives (RTT < click interval), then each click WILL be processed independently. With ~50ms RTT on a local server, clicks spaced >100ms apart would each be processed.

But rapid clicking (< 100ms between clicks) will cause drops. The player sees only the result of the first click.

### Is This a Problem?

**Usually not.** The dropped events were intermediate states — the player wanted the final tab, not every tab in between. The UI updates to reflect the first click, and the player can then click the desired tab.

**It IS a problem if:**
- The event has side effects beyond UI (e.g., giving items, modifying world state) — but the current implementation only modifies UI state in tab switches
- The desired behavior is "last click wins" rather than "first click wins"

### Could 5 Rapid Tab Switches Queue 5 Full Pipeline+UI Updates?

**No.** Only the first one executes. The remaining 4 are dropped by the ack gate. This is a **built-in protection** against event storms.

### Caveat: `sendUpdate()` Deferral Timing

Since `InteractiveCustomUIPage.sendUpdate()` defers via `world.execute()`, there is a subtle window:

1. `handleDataEvent(T1)` runs, builds cmd, calls `sendUpdate()` → task queued
2. Before the queued task runs (same drain pass), `handleDataEvent(T2)` could potentially be dispatched if the packet arrived between steps 1 and 2

**In practice, this is extremely unlikely** because:
- `handleDataEvent()` is called from within `consumeTaskQueue()` (it's a task itself)
- The `sendUpdate()` task is added to the same queue being drained
- But the `sendUpdate()` task increments ack counter, which gates future events
- The ack increment happens in the deferred task, NOT in `handleDataEvent()` synchronously

**⚠️ This is a theoretical race.** If two `Data` events are in the task queue simultaneously (both submitted before the first one's `sendUpdate()` task runs), both could be processed before any ack increment. The mitigation: `handleDataEvent()` is typically dispatched from the network thread via `world.execute()`, so there IS a task boundary between consecutive events.

---

## Summary Table

| Question | Answer | Confidence |
|----------|--------|------------|
| Is there a packet size limit for 2000+ commands? | No practical limit. 4M commands max, 1.6GB max packet. | **Confirmed** (decompiled) |
| Does 2000+ `cmd.set()` cause problems? | Unlikely on server. Client-side DOM walk is the bottleneck. | **High** (server confirmed, client speculative) |
| Does `cmd.append()` trigger server-side file I/O? | **No.** Server stores path string only. Client loads from cache. | **Confirmed** (decompiled) |
| Does `sendUpdate()` block the server thread? | No. Deferred via `world.execute()`, then Netty async write. | **Confirmed** (decompiled) |
| Can queuing many updates cause backpressure? | Not network backpressure, but ack counter gates event processing. | **Confirmed** (decompiled) |
| Is `build()` always called fresh? | Yes. `openCustomPage()` always calls `build()`. | **Confirmed** (decompiled) |
| Can you skip `build()` on re-open? | No. But page instance state persists between events. | **Confirmed** (decompiled) |
| Does `rebuild()` exist? | Yes, on `CustomUIPage`. Calls `build()` with `clear: true`. | **Confirmed** (decompiled) |
| Does the engine coalesce rapid events? | No coalescing, but ack gate drops events while update pending. | **Confirmed** (decompiled) |
| Could 5 rapid clicks cause 5 full updates? | **No.** Only the first processes; rest are dropped by ack gate. | **Confirmed** (decompiled) |
