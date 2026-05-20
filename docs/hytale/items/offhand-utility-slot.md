---
topic: "Offhand / Utility Slot System"
category: "Items / Inventory / Interactions"
updated: 2026-05-20
sources:
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/inventory/Inventory.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/ItemUtility.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/entity/InteractionContext.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/entity/InteractionManager.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/modules/interaction/interaction/config/InteractionConfiguration.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/modules/interaction/interaction/config/InteractionPriority.java"
  - ".tmp_hytale_src/com/hypixel/hytale/protocol/PrioritySlot.java"
  - ".tmp_hytale_src/com/hypixel/hytale/protocol/InteractionType.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/npc/systems/NPCInteractionSystems.java"
  - "docs/Reference Assets/Assets/Server/Item/Items/Furniture/Crude/Unique/Furniture_Crude_Torch.json"
  - "docs/Reference Assets/Assets/Server/Item/Items/Wood/Wood_Torch_Wall.json"
  - "docs/Reference Assets/Assets/Server/Item/Items/Weapon/Shield/Template_Weapon_Shield.json"
  - "docs/Reference Assets/Assets/Server/Item/Items/Weapon/Sword/Template_Weapon_Sword.json"
  - "docs/Reference Assets/Assets/Server/Item/Items/_Debug/Debug_Continue.json"
---

# Offhand / Utility Slot System

## Summary

Hytale uses a **Utility Slot** system (commonly called "offhand") that allows players to hold a secondary item alongside their main-hand item. The utility slot is a separate inventory container with up to 4 slots, controlled by a client-side radial selector (`HudComponent.UtilitySlotSelector`). Items must be explicitly marked as offhand-eligible via the `"Utility"` JSON field — only items with `"Usable": true` can be placed in the utility container.

The interaction system has a **priority resolution** mechanism that determines whether a given input (Primary, Secondary, etc.) fires the main-hand or offhand item's interaction.

---

## 1. Making an Item Offhand-Eligible

### The `Utility` JSON Field

Add a `"Utility"` object to the item JSON with the following fields:

| Field | Type | Default | Purpose |
|-------|------|---------|---------|
| `Usable` | boolean | `false` | **Required for offhand.** If `true`, the item can be placed in the utility/offhand container. The inventory has a slot filter that rejects items where `getUtility().isUsable() == false`. |
| `Compatible` | boolean | `false` | Affects interaction priority tiebreaking. When both hands have equal priority for `Secondary`, and the **main-hand** item has `Compatible: true`, the **offhand** item wins the `Secondary` interaction. Think of it as "this main-hand item is compatible with offhand use." |
| `StatModifiers` | Map\<string, Modifier[]\> | null | Entity stat modifiers applied while the item is in the active utility slot. |
| `EntityStatsToClear` | string[] | null | Entity stats to clear when this item is equipped/unequipped in the utility slot. |

### Slot Filter (Decompiled Evidence)

From `Inventory.java` line 165-166:

```java
this.utility = ItemContainerUtil.trySetSlotFilters(
    utility, (type, container, slot, itemStack) -> itemStack == null || itemStack.getItem().getUtility().isUsable()
);
```

**Only items with `Utility.Usable = true` pass the slot filter.** All other items are rejected by the utility container.

### Default Behavior

The `ItemUtility.DEFAULT` instance has `usable = false` and `compatible = false`. If an item has no `"Utility"` field in its JSON, it uses this default — meaning it **cannot** go in the offhand.

---

## 2. Reference Item Configs

### Torch (`Furniture_Crude_Torch.json`) — The canonical offhand item

```json
{
  "Utility": {
    "Usable": true
  },
  "InteractionConfig": {
    "Priorities": {
      "Secondary": {
        "MainHand": 1,
        "OffHand": -1
      }
    }
  },
  "Interactions": {
    "Primary": "Root_Unarmed_Attack_Swing_Left",
    "Secondary": "Block_Secondary"
  }
}
```

Key points:
- `Usable: true` → can go in offhand
- `Priorities.Secondary.OffHand = -1` → when the torch is in offhand, its `Secondary` interaction **loses** to the main-hand item (priority -1 < default 0). This means right-click fires the main-hand's Secondary, not the torch's block-placing.
- The torch provides **light** from the offhand, but its interactions defer to the main hand.

### Shield (`Template_Weapon_Shield.json`) — Offhand with active interactions

```json
{
  "Utility": {
    "Usable": true
  },
  "Interactions": {
    "Primary": "Root_Unarmed_Attack_Swing_Left",
    "Secondary": "Root_Weapon_Shield_Secondary_Guard"
  }
}
```

Key points:
- `Usable: true` → can go in offhand
- No explicit `Priorities` → defaults to 0 for all slots
- The shield's `Secondary` (Guard) can compete with the main hand's `Secondary` via priority resolution

### Sword (`Template_Weapon_Sword.json`) — Main-hand item compatible with offhand

```json
{
  "Utility": {
    "Compatible": true
  },
  "Interactions": {
    "Primary": "Root_Weapon_Sword_Primary",
    "Secondary": "Root_Weapon_Sword_Secondary_Guard",
    "Ability1": "Root_Weapon_Sword_Signature_Vortexstrike"
  }
}
```

Key points:
- `Compatible: true` (NOT `Usable`) → the sword **cannot** go in offhand, but it is **compatible** with offhand items
- When a sword is in main hand and a shield is in offhand, and both have equal `Secondary` priority, the sword's `Compatible: true` causes the offhand (shield) to win `Secondary` → the player guards with the shield on right-click

### Debug Item (`Debug_Continue.json`) — Both Usable and Compatible, with HeldOffhand

```json
{
  "Utility": {
    "Usable": true,
    "Compatible": true
  },
  "Interactions": {
    "Held": { ... },
    "HeldOffhand": {
      "Interactions": [
        { "Type": "ApplyForce", "Direction": { "Y": 1 }, "Force": 15, ... }
      ]
    }
  }
}
```

Shows that `HeldOffhand` can be used for passive/continuous effects while in the utility slot.

---

## 3. Interaction Resolution: Main Hand vs Offhand

### How the Engine Decides Which Item Handles an Input

When the player triggers an input-bound interaction (Primary, Secondary, Ability1-3, Pick), the `InteractionContext.forInteraction()` method resolves which item should handle it:

```
1. If using Tools slot → tools item handles it
2. If main hand is empty and offhand has item → offhand handles it
3. If both hands have items:
   a. Get main-hand priority:  primary.getInteractionConfig().getPriorityFor(type, PrioritySlot.MainHand)
   b. Get offhand priority:    secondary.getInteractionConfig().getPriorityFor(type, PrioritySlot.OffHand)
   c. If priorities are equal:
      - For Secondary: if main-hand item has Utility.Compatible=true → offhand wins
      - Otherwise: main hand wins
   d. If main-hand priority < offhand priority → offhand wins
   e. If main-hand priority > offhand priority → main hand wins, UNLESS:
      - For Primary: main hand has no Primary interaction → offhand wins
      - For Secondary: main hand has no Secondary interaction → offhand wins
4. Default: main hand handles it
```

### Priority Values

Configured via `InteractionConfig.Priorities` in item JSON:

```json
"InteractionConfig": {
  "Priorities": {
    "Secondary": {
      "Default": 0,
      "MainHand": 1,
      "OffHand": -1
    }
  }
}
```

`PrioritySlot` enum values:
| Slot | Value | Meaning |
|------|-------|---------|
| `Default` | 0 | Fallback when no specific slot priority is set |
| `MainHand` | 1 | Priority when item is in main hand |
| `OffHand` | 2 | Priority when item is in offhand |

Higher numeric priority wins. Default is 0 if no priorities configured.

### The `Compatible` Tiebreaker

From `InteractionContext.java`:

```java
if (prioPrimary == prioSecondary) {
    if (type == InteractionType.Secondary && primary.getItem().getUtility().isCompatible()) {
        selectedInventory = -5; // offhand wins
    }
}
```

When a main-hand weapon is `Compatible: true`, it signals: "I'm designed to work alongside an offhand item — let the offhand item handle Secondary (block/guard) while I handle Primary (attack)." This is the sword+shield pattern.

---

## 4. `HeldOffhand` Interaction Type

`HeldOffhand` (enum value 21) is a **passive interaction** that fires every tick via `NPCInteractionSystems` for any item in the active utility slot:

```java
// NPCInteractionSystems.java — runs every tick for entities with InteractionManager
interactionManager.tryRunHeldInteraction(ref, commandBuffer, InteractionType.Held);
interactionManager.tryRunHeldInteraction(ref, commandBuffer, InteractionType.HeldOffhand);
```

When `HeldOffhand` fires, the `InteractionManager` resolves the item:

```java
case HeldOffhand -> inventory.getUtilityItem();
```

Then checks if the item has a `HeldOffhand` interaction defined:

```java
String rootId = itemStack.getItem().getInteractions().get(type); // type = HeldOffhand
```

**`HeldOffhand` is NOT an input-bound interaction.** It does not respond to any key press. It runs continuously while the item is held in the utility slot. Use it for:
- Passive stat effects
- Continuous force application (like the debug item)
- Light emission effects (though torch light is likely client-side rendering)

---

## 5. Server API for Utility Slot

```java
Inventory inventory = player.getInventory();

// Read utility items
ItemStack offhandItem = inventory.getUtilityItem();          // active utility slot item
byte activeSlot = inventory.getActiveUtilitySlot();          // -1 if none
ItemContainer utilityContainer = inventory.getUtility();     // full 4-slot container

// Modify utility slot
inventory.setActiveUtilitySlot((byte) 0);                   // select slot 0
utilityContainer.setItemStackForSlot((short) 0, itemStack); // set item in slot 0

// HUD visibility
hudManager.showHudComponents(playerRef, HudComponent.UtilitySlotSelector);
hudManager.hideHudComponents(playerRef, HudComponent.UtilitySlotSelector);
```

Default player utility capacity: 4 slots (`DEFAULT_UTILITY_CAPACITY = 4`).

---

## 6. Gotchas

- **`Usable` vs `Compatible` are different roles.** `Usable` = "can this item go in offhand?" `Compatible` = "does this main-hand item yield Secondary to offhand?" Mixing them up will break offhand behavior.
- **Interactions CAN fire from offhand** — but only through the priority resolution system. If the main-hand item has higher or equal priority for that InteractionType, the main hand wins.
- **`HeldOffhand` is passive, not input-driven.** It fires every tick regardless of player input. Don't put input-dependent logic in a `HeldOffhand` interaction.
- **The utility slot selector UI is client-built.** The server can show/hide it and read/write slot data, but cannot customize its appearance.
- **Slot filter is enforced server-side.** Even if you programmatically try to place a non-Usable item in the utility container, the slot filter will reject it.
- **`Pick` (middle-click) is subject to the same priority resolution** as Primary/Secondary. If a main-hand item has higher priority for Pick, it will consume the input.

---

## See Also

- [Interaction HUD Tips](./interaction-hud-tips.md) — InteractionType enum and HUD tip rendering
- [Input Detection Research](../plugins/input-detection-research.md) — `inventory.getUtilityItem()` usage
- [Radial Menu Research](../ui/radial-menu-research.md) — Utility slot selector (offhand menu) is client-built
