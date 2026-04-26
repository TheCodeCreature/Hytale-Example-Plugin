---
topic: "Inventory & Hotbar Events"
category: "Events / Inventory"
updated: 2026-04-26
sources: ["decompiled source: Inventory.java", "decompiled source: InventoryPacketHandler.java", "decompiled source: ItemContainer.java", "decompiled source: CombinedItemContainer.java", "decompiled source: SwitchActiveSlotEvent.java", "decompiled source: LivingEntityInventoryChangeEvent.java"]
---

# Inventory & Hotbar Change Events

## Summary

Hytale provides **two distinct mechanisms** for inventory change detection and **one ECS event** for hotbar slot switching. There is NO single "HeldItemChanged" event — you must combine `SwitchActiveSlotEvent` (slot switch) with `ItemContainerChangeEvent` (item mutation in the active slot) to fully detect held-item changes.

---

## 1. Inventory Change Events

### 1a. `ItemContainer.ItemContainerChangeEvent` (Low-Level, Per-Container)

**File**: `.tmp_hytale_src/com/hypixel/hytale/server/core/inventory/container/ItemContainer.java`

A `record` that fires whenever any `ItemContainer` is mutated (item added, removed, set, moved, cleared).

```java
public record ItemContainerChangeEvent(ItemContainer container, Transaction transaction) implements IEvent<Void> {}
```

**Registration**:
```java
// On any specific container (hotbar, storage, armor, etc.)
EventRegistration reg = itemContainer.registerChangeEvent(event -> {
    Transaction tx = event.transaction();
    ItemContainer source = event.container();
    // ...
});

// With priority
EventRegistration reg = itemContainer.registerChangeEvent(EventPriority.LAST, event -> { ... });
```

**Key**: This is a **synchronous callback** on the container's internal `SyncEventBusRegistry`. It fires inline during the mutation. The `Transaction` object tells you what changed (slot index, before/after ItemStack, action type).

**Unregistration**: Call `reg.unregister()` on the returned `EventRegistration`.

### 1b. `LivingEntityInventoryChangeEvent` (Global Event Bus)

**File**: `.tmp_hytale_src/com/hypixel/hytale/server/core/event/events/entity/LivingEntityInventoryChangeEvent.java`

A **global event bus event** dispatched by `Inventory.registerChangeEvents()` whenever ANY inventory section (storage, armor, hotbar, utility, tools, backpack) is modified.

```java
public class LivingEntityInventoryChangeEvent extends EntityEvent<LivingEntity, String> {
    public ItemContainer getItemContainer();   // which container changed
    public Transaction getTransaction();       // what changed
}
```

**Registration** (plugin-level):
```java
// Global listener
this.getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, this::onInventoryChange);

// World-scoped listener
this.eventRegistry.register(LivingEntityInventoryChangeEvent.class, world.getName(), event -> { ... });
```

**Key**: This wraps `ItemContainerChangeEvent` and adds the `LivingEntity` context. Dispatched via `HytaleServer.get().getEventBus().dispatchFor(...)`. The key type is `String` (world name), so it supports world-scoped listeners.

**Fired for**: storage, armor, hotbar, utility, tools, backpack changes — ALL sections dispatch this event.

---

## 2. Hotbar Slot Switch Event

### `SwitchActiveSlotEvent` (ECS Event, Cancellable)

**File**: `.tmp_hytale_src/com/hypixel/hytale/server/core/event/events/ecs/SwitchActiveSlotEvent.java`

An **ECS entity event** (extends `CancellableEcsEvent`) dispatched when the player switches their active slot in any switchable inventory section.

```java
public class SwitchActiveSlotEvent extends CancellableEcsEvent {
    public int getInventorySectionId();  // -1=hotbar, -5=utility, -8=tools
    public int getPreviousSlot();        // previous slot index
    public byte getNewSlot();            // target slot index
    public void setNewSlot(byte newSlot); // redirect to a different slot
    public boolean isServerRequest();    // true = server-initiated, false = client packet
    public boolean isClientRequest();    // inverse of isServerRequest
}
```

**Dispatch mechanism**: `store.invoke(ref, event)` — dispatched on the entity's `Store<EntityStore>`, so it is handled by registered `EntityEventSystem<EntityStore, SwitchActiveSlotEvent>` systems.

**To listen from a plugin**:
```java
public class MySlotSwitchSystem extends EntityEventSystem<EntityStore, SwitchActiveSlotEvent> {
    public MySlotSwitchSystem() {
        super(SwitchActiveSlotEvent.class);
    }
    
    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, 
                       CommandBuffer<EntityStore> commandBuffer, SwitchActiveSlotEvent event) {
        int sectionId = event.getInventorySectionId();
        if (sectionId == Inventory.HOTBAR_SECTION_ID) { // -1
            // Hotbar slot switched from event.getPreviousSlot() to event.getNewSlot()
        }
        // Can cancel: event.setCancelled(true);
        // Can redirect: event.setNewSlot((byte) otherSlot);
    }
}

// Register in plugin setup:
this.getEntityStoreRegistry().registerSystem(new MySlotSwitchSystem());
```

**Where dispatched** (in `InventoryPacketHandler`):
1. **Client-initiated** (`handle(SetActiveSlot packet)`): When client sends `SetActiveSlot` packet (packet ID 177). `serverRequest = false`.
2. **Server-initiated** (during `MoveItemStack` handling): When moving an item to the utility section triggers an auto-select. `serverRequest = true`.

**Cancellation behavior**: If cancelled, the server sends a correction packet back to the client reverting the slot.

---

## 3. `CombinedItemContainer` — Slot Mapping

**File**: `.tmp_hytale_src/com/hypixel/hytale/server/core/inventory/container/CombinedItemContainer.java`

A virtual container that concatenates multiple `ItemContainer` instances into a single flat slot space.

### Construction (from `Inventory.buildCombinedContains()`):

```java
combinedHotbarFirst         = new CombinedItemContainer(hotbar, storage);       // hotbar[0..8], storage[9..44]
combinedStorageFirst        = new CombinedItemContainer(storage, hotbar);       // storage[0..35], hotbar[36..44]
combinedBackpackStorageHotbar = new CombinedItemContainer(backpack, storage, hotbar);
combinedStorageHotbarBackpack = new CombinedItemContainer(storage, hotbar, backpack);
combinedArmorHotbarStorage  = new CombinedItemContainer(armor, hotbar, storage);
combinedArmorHotbarUtilityStorage = new CombinedItemContainer(armor, hotbar, utility, storage);
combinedHotbarUtilityConsumableStorage = new CombinedItemContainer(hotbar, utility, storage);
combinedEverything          = new CombinedItemContainer(armor, hotbar, utility, storage, backpack);
```

### Slot Index Mapping

Slots are **concatenated in order**. For `combinedHotbarFirst` (hotbar=9 slots, storage=36 slots):

| Combined Slot | Underlying Container | Underlying Slot |
|---------------|---------------------|-----------------|
| 0–8           | hotbar              | 0–8             |
| 9–44          | storage             | 0–35            |

**Resolution algorithm** (`internal_getSlot`, `internal_setSlot`, `getContainerForSlot`):
```java
// Walks containers in order, subtracting capacity until slot < capacity
for (ItemContainer container : this.containers) {
    short capacity = container.getCapacity();
    if (slot < capacity) {
        return container.internal_getSlot(slot);  // found the right container
    }
    slot -= capacity;  // move to next container's range
}
```

### Change Event Propagation

`CombinedItemContainer.registerChangeEvent()` registers on **all child containers** and translates slot indices via `transaction.toParent(this, finalStart, container)`:

```java
public EventRegistration registerChangeEvent(short priority, Consumer<ItemContainerChangeEvent> consumer) {
    EventRegistration thisRegistration = super.registerChangeEvent(priority, consumer);
    EventRegistration[] containerRegistrations = new EventRegistration[this.containers.length];
    short start = 0;
    for (int i = 0; i < this.containers.length; i++) {
        ItemContainer container = this.containers[i];
        short finalStart = start;
        containerRegistrations[i] = container.internalChangeEventRegistry.register(
            priority, null,
            event -> consumer.accept(new ItemContainerChangeEvent(
                this, event.transaction().toParent(this, finalStart, container)
            ))
        );
        start += container.getCapacity();
    }
    return EventRegistration.combine(thisRegistration, containerRegistrations);
}
```

**Key**: When you `registerChangeEvent` on `getCombinedHotbarFirst()`, you get notified of changes to BOTH hotbar AND storage, with slot indices translated to the combined space (hotbar slots 0–8, storage slots 9–44).

---

## 4. Getting the Player's Active Hotbar Slot

### From `Inventory`

```java
Inventory inventory = playerComponent.getInventory();

// Active hotbar slot index (0-based, -1 = no selection)
byte activeSlot = inventory.getActiveHotbarSlot();

// Active hotbar item (null if no selection or slot empty)
ItemStack heldItem = inventory.getActiveHotbarItem();

// Generic getter by section ID
byte slot = inventory.getActiveSlot(Inventory.HOTBAR_SECTION_ID);  // -1

// Item in hand (considers tools override)
ItemStack inHand = inventory.getItemInHand();  // checks _usingToolsItem flag
```

### Section IDs

| Constant | Value | Description |
|----------|-------|-------------|
| `HOTBAR_SECTION_ID` | -1 | Main hotbar |
| `STORAGE_SECTION_ID` | -2 | Storage/backpack grid |
| `ARMOR_SECTION_ID` | -3 | Armor slots |
| `UTILITY_SECTION_ID` | -5 | Utility bar |
| `TOOLS_SECTION_ID` | -8 | Tools bar |
| `BACKPACK_SECTION_ID` | -9 | Backpack |

### Other Active Slot Methods

```java
inventory.getActiveUtilitySlot();     // byte, -1 if none
inventory.getActiveToolsSlot();       // byte, -1 if none
inventory.setActiveHotbarSlot(byte);  // also clears _usingToolsItem
inventory.setActiveUtilitySlot(byte);
inventory.setActiveToolsSlot(byte);   // also sets _usingToolsItem = true
```

### From `InteractionContext`

During interaction handling, the current held item info is available:
```java
InteractionContext context = ...;
byte heldSlot = context.getHeldItemSlot();
ItemStack heldItem = context.getHeldItem();
ItemContainer heldContainer = context.getHeldItemContainer();
int sectionId = context.getHeldItemSectionId();
```

### Programmatically Setting Active Slot (Server → Client)

```java
// Send packet to update client's active slot display
playerRef.getPacketHandler().writeNoCache(new SetActiveSlot(inventorySectionId, (byte)slotIndex));
// Also update server state
inventory.setActiveSlot(inventorySectionId, (byte)slotIndex);
```

---

## 5. Detecting "Held Item Changed"

There is **no single event** for "the item in the player's hand changed." You need to combine two event sources:

| Scenario | Event | Details |
|----------|-------|---------|
| Player scrolls/presses hotbar key | `SwitchActiveSlotEvent` (ECS) | `inventorySectionId == -1` |
| Item in active hotbar slot is modified | `ItemContainerChangeEvent` on hotbar | Check `event.transaction().wasSlotModified(activeHotbarSlot)` |
| Item in active hotbar slot is modified | `LivingEntityInventoryChangeEvent` (global bus) | Check container identity and slot |

The `Inventory.registerChangeEvents()` code already demonstrates the pattern for detecting active-slot item changes:

```java
this.hotbarChange = this.hotbar.registerChangeEvent(e -> {
    if (this.activeHotbarSlot != -1 && this.entity != null && e.transaction().wasSlotModified(this.activeHotbarSlot)) {
        // The item in the active hotbar slot changed
        // Check if it's a different item type (not just quantity change):
        if (e.transaction() instanceof SlotTransaction slot 
            && ItemStack.isEquivalentType(slot.getSlotBefore(), slot.getSlotAfter())) {
            return; // same item type, just quantity change
        }
        // Handle held item type change...
    }
});
```

---

## Gotchas

- **`SwitchActiveSlotEvent` is an ECS event**, dispatched via `store.invoke()`. Listen with `EntityEventSystem`, NOT the global event bus.
- **`LivingEntityInventoryChangeEvent` is a global event bus event**, dispatched via `HytaleServer.get().getEventBus()`. Listen with `getEventRegistry().registerGlobal()` or world-scoped `.register()`.
- **`ItemContainerChangeEvent` is a container-level callback**, NOT an event bus event. Register directly on the `ItemContainer` instance.
- **`CombinedItemContainer` slot indices are offset**: hotbar slot 3 in `combinedHotbarFirst` is combined slot 3, but storage slot 3 is combined slot 12 (9 + 3).
- **`getItemInHand()` is NOT the same as `getActiveHotbarItem()`**: `getItemInHand()` checks `_usingToolsItem` and may return the tools slot item instead.
- **Active slot -1 means "no selection"**: `getActiveHotbarSlot()` returns -1 when nothing is selected.
- The `SetActiveSlot` packet from client for hotbar (`inventorySectionId == -1`) is **rejected** by `InventoryPacketHandler` with a disconnect — hotbar switching goes through the interaction system (`ChangeActiveSlotInteraction`), not direct packets.

## See Also

- [Plugin API Lifecycle](./plugins/lifecycle.md)
- [ECS Event Systems](./ecs/event-systems.md)
