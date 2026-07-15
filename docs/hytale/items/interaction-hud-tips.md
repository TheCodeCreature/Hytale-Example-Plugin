---
topic: "Item HUD Action Tips & Interaction Hints"
category: "Items / Interactions / UI"
updated: 2025-05-09
sources:
  - ".tmp_hytale_src/com/hypixel/hytale/protocol/InteractionType.java"
  - ".tmp_hytale_src/com/hypixel/hytale/protocol/ItemBase.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/Item.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/modules/interaction/interaction/config/RootInteraction.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/blocktype/config/BlockType.java"
  - "docs/Reference Assets/Assets/Server/Item/Items/Weapon/Sword/Template_Weapon_Sword.json"
  - "docs/Reference Assets/Assets/Server/Languages/en-US/server.lang"
---

# Item HUD Action Tips & Interaction Hints

## Summary

Hytale has **two separate systems** for showing on-screen input/action prompts:

1. **Item HUD Action Tips** — the keybind labels (e.g. "Left Click", "Right Click", "Q") shown on the HUD when holding items like swords. Driven by the `Interactions` config on the item.
2. **Block/NPC Interaction Hints** — the contextual "Press [E] to open" prompts shown when looking at interactable blocks or NPCs. Driven by the `InteractionHint` field on `BlockType` or sent via NPC state updates.

These are **completely independent systems**.

---

## System 1: Item HUD Action Tips

### How It Works

When a player holds an item, the client reads the item's `Interactions` map from the `ItemBase` protocol packet. This map uses `InteractionType` enum keys to associate each input slot with a `RootInteraction` (by integer index).

The **client** (not the server) renders HUD tips based on:
- **Which `InteractionType` slots** have mapped RootInteractions (slots with no mapping = no tip)
- **The keybind** associated with each InteractionType (hardcoded client-side mapping)
- **The label text** — derived client-side, likely from the `RootInteraction.id` string or its tags

### InteractionType → Keybind Mapping

From `InteractionType.java`:

| InteractionType | Enum Value | Default Keybind | HUD Tip Shows |
|-----------------|------------|-----------------|---------------|
| `Primary`       | 0          | Left Click      | Yes — attack/primary action |
| `Secondary`     | 1          | Right Click     | Yes — guard/block/secondary |
| `Ability1`      | 2          | Q               | Yes — signature/ability |
| `Ability2`      | 3          | ?               | Likely yes |
| `Ability3`      | 4          | ?               | Likely yes |
| `Use`           | 5          | E (Interact)    | Yes — use/interact |
| `Pick`          | 6          | Middle Click?   | Unknown |
| `Pickup`        | 7          | E?              | Unknown |
| `SwapTo`        | 12         | N/A             | No — lifecycle event |
| `SwapFrom`      | 13         | N/A             | No — lifecycle event |
| `Death`         | 14         | N/A             | No — lifecycle event |
| `Wielding`      | 15         | N/A             | No — held state |
| `Held`          | 20         | N/A             | No — passive |
| `HeldOffhand`   | 21         | N/A             | No — passive |
| `Equipped`      | 22         | N/A             | No — passive |
| `Dodge`         | 23         | Spacebar?       | Maybe |

Only input-bound types (`Primary`, `Secondary`, `Ability1`, `Ability2`, `Ability3`, `Use`) generate visible HUD tips.

### Item JSON Config — `Interactions` Field

The `Interactions` field on an item JSON maps `InteractionType` names to `RootInteraction` asset IDs:

```json
// Template_Weapon_Sword.json
{
  "Interactions": {
    "Primary": "Root_Weapon_Sword_Primary",
    "Secondary": "Root_Weapon_Sword_Secondary_Guard",
    "Ability1": "Root_Weapon_Sword_Signature_Vortexstrike"
  }
}
```

This produces three HUD tips:
- **Left Click** → label derived from `Root_Weapon_Sword_Primary`
- **Right Click** → label derived from `Root_Weapon_Sword_Secondary_Guard`
- **Q** → label derived from `Root_Weapon_Sword_Signature_Vortexstrike`

### How Labels Are Derived

The label text displayed next to the keybind (e.g. "Attack", "Guard", "Vortex Strike") is **resolved client-side**. The server sends:
- The `RootInteraction.id` string (e.g. `"Root_Weapon_Sword_Primary"`)
- The `RootInteraction.tags` (integer array of tag indexes)

The server does **NOT** send an explicit display name or translation key for the action tip label. Evidence:
- The `RootInteraction` protocol class has no `name`, `label`, `displayName`, or `translationKey` field
- There are no `rootInteractions.*` entries in `server.lang`
- The RootInteraction CODEC has no `"Name"` or `"TranslationProperties"` field

The client likely derives the label via one of:
1. **Translation key convention**: `server.rootInteractions.{id}.name` (unverified — no server-side lang entries exist, client may have its own)
2. **Tag-based label**: The `Tags` on the RootInteraction (e.g. `"Attack": ["Melee"]`) may map to display text
3. **RootInteraction ID transformation**: Converting the ID string to a display name (e.g. `Root_Weapon_Sword_Primary` → "Attack")

> **Open question**: The exact label derivation mechanism is client-side and not visible in server decompilation. The `Tags` field on RootInteraction JSON configs (e.g. `"Attack": ["Melee"]`) is the most likely candidate for driving display names, since tags are sent in the packet.

### Serialization Path

```
Item.json (server config)
  → Item.java (interactions: Map<InteractionType, String>)
    → Item.toPacket() converts String IDs to int indexes
      → ItemBase (protocol): interactions: Map<InteractionType, Integer>
        → Client: reads InteractionType keys, resolves RootInteraction,
                   renders keybind + label for each populated slot
```

### Key Code References

| Class | Location | Purpose |
|-------|----------|---------|
| `InteractionType` | `protocol/InteractionType.java` | Enum: Primary, Secondary, Ability1, Use, etc. |
| `Item` | `server/core/asset/type/item/config/Item.java` | Parses `"Interactions"` as `EnumMap<InteractionType, String>` |
| `Item.toPacket()` | Same, line ~619 | Converts interaction map to `Object2IntOpenHashMap<InteractionType>` |
| `ItemBase` | `protocol/ItemBase.java` | Protocol packet: `interactions: Map<InteractionType, Integer>` |
| `RootInteraction` (server) | `server/core/modules/interaction/interaction/config/RootInteraction.java` | Server asset: id, interactions[], cooldown, rules, tags |
| `RootInteraction` (protocol) | `protocol/RootInteraction.java` | Packet: id, interactions[], cooldown, settings, rules, tags |
| `InteractionConfiguration` | `server/core/modules/interaction/interaction/config/InteractionConfiguration.java` | Per-item interaction config: DisplayOutlines, UseDistance, Priorities |

---

## System 2: Block/NPC Interaction Hints

### How It Works

When looking at an interactable block or NPC, the client shows a contextual prompt like "Press [E] to open Chest". This uses the `interactionHint` field.

### Block Interaction Hints

Set via `BlockType.InteractionHint` in block JSON:

```json
{
  "InteractionHint": "server.interactionHints.open"
}
```

If a block has a container/bench and no explicit hint, it defaults to `"server.interactionHints.open"` (see `BlockType.java` line ~1772).

The hint is a **translation key** resolved from `server.lang`:

```
interactionHints.generic = Press [{key}] to interact
interactionHints.open = Press [{key}] to open {name}
interactionHints.openDoor = Press [{key}] to open
interactionHints.gather = Press [{key}] to gather {name}
interactionHints.pick = Press [{key}] to pick {name}
interactionHints.edit = Press [{key}] to edit
interactionHints.turnon = Press [{key}] to turn on
interactionHints.turnoff = Press [{key}] to turn off
interactionHints.activate = Press [{key}] to activate
interactionHints.deactivate = Press [{key}] to deactivate
interactionHints.sit = Press [{key}] to sit
interactionHints.tame = Press [{key}] to tame
interactionHints.feed = Press [{key}] to feed
interactionHints.harvest = Press [{key}] to harvest {name}
interactionHints.pet = Press [{key}] to pet
interactionHints.trade = Press [{key}] to trade
interactionHints.mount = Press [{key}] to mount
interactionHints.burst = Press [{key}] to burst {name}
interactionHints.pickup = Press [{key}] to pick up {name}
```

### NPC Interaction Hints

Sent dynamically via `StateSupport.sendInteractionHintToPlayer()`:

```java
// StateSupport.java
update.interactionHint = hint;  // e.g. "interactionHints.trade"
```

NPCs can set their interaction hint via the NPC state machine, using `BuilderActionSetInteractable`:
```java
"The interaction hint translation key to show for this player (e.g. 'interactionHints.trade')"
```

### Key Difference from Item Tips

| Aspect | Item HUD Tips | Block/NPC Hints |
|--------|--------------|-----------------|
| Trigger | Holding an item | Looking at a block/NPC |
| Config source | `Item.Interactions` map | `BlockType.InteractionHint` / NPC state |
| Display | Multiple keybind labels on HUD | Single "Press [E] to..." prompt |
| Labels | Client-derived from RootInteraction | Server-sent translation key |
| Customizable | Only via Interactions mapping | Yes — custom translation keys |

---

## StencilBook Analysis

Current config:
```json
{
  "Interactions": {
    "Use": "StencilCrafting_OpenUI",
    "Secondary": "StencilBook_PickStencil"
  }
}
```

This produces:
- **E** (Use) → label derived from `StencilCrafting_OpenUI`
- **Right Click** (Secondary) → label derived from `StencilBook_PickStencil`

No `Primary` or `Ability1` mapping → no Left Click or Q tips.

### Adding Custom Tips

To add more HUD tips, add more `InteractionType` keys with corresponding `RootInteraction` IDs:

```json
{
  "Interactions": {
    "Primary": "SomeRootInteraction",
    "Secondary": "StencilBook_PickStencil",
    "Ability1": "SomeAbilityInteraction",
    "Use": "StencilCrafting_OpenUI"
  }
}
```

**Limitations**:
- You can only use the fixed set of `InteractionType` slots (Primary, Secondary, Ability1, Ability2, Ability3, Use)
- Each slot requires a valid `RootInteraction` asset
- The label text is client-derived — you cannot directly set custom label strings from the server
- Adding an interaction slot **adds a functional interaction**, not just a visual tip — the RootInteraction chain will execute when the keybind is pressed

### Can a Plugin Add Custom HUD Tips?

**Partially.** A server plugin can:
- ✅ Register new `RootInteraction` assets at runtime via `AssetRegistry`
- ✅ Modify the item's `Interactions` map to add/change InteractionType → RootInteraction mappings
- ✅ Use `Tags` on RootInteractions (which are sent to the client) to influence label display
- ❌ Cannot directly set custom label text — labels are client-derived
- ❌ Cannot create new InteractionType enum values — the enum is fixed in protocol code
- ❌ Cannot add purely visual tips without a backing RootInteraction

---

## See Also

- [items.md](./items.md) — Item config overview
- [resource-types.md](./resource-types.md) — Item resource type system
- [state-variant-preview-feasibility.md](../blocks/state-variant-preview-feasibility.md) — Block packet fields including interactionHint
