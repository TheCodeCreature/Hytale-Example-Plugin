---
topic: "State Item PlaceBlock Interaction & RemoveItemInHand Research"
category: "Items / Interactions / State System"
updated: 2026-04-26
sources: ["Item.java (decompiled)", "PlaceBlockInteraction.java (decompiled)", "BlockPlaceUtils.java (decompiled)", "ContainedAssetCodec.java (decompiled)", "MapUtil.java (decompiled)", "UnarmedInteractions.java (decompiled)", "RootInteraction.java (decompiled)", "item-state-system.md", "bucket-pattern.md", "placeblock-building-tool-api-research.md", "placeblock-risk-investigation.md"]
---

# State Item PlaceBlock Interaction & RemoveItemInHand Research

## Summary

This document investigates why `RemoveItemInHand: false` is not respected on state items with BlockType definitions, despite explicit `Interactions` blocks. The root cause analysis reveals the engine's interaction resolution order, how `stateToBlock` relates to interactions, and the critical role of `append` vs `appendInherited` in the PlaceBlockInteraction codec.

---

## Q1: Does `stateToBlock` Create an Implicit PlaceBlock Interaction?

### Answer: **NO. `stateToBlock` does NOT create any interaction.**

### Evidence

The `stateToBlock` field is a simple `Map<String, String>` mapping state name → generated item ID. From [Item.java line 527](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/Item.java#L527):

```java
protected Map<String, String> stateToBlock;
```

The codec that populates it (Item.java line 1127-1130):
```java
new KeyedCodec<>("State", new MapCodec(
    new ContainedAssetCodec<>(Item.class, CODEC, ContainedAssetCodec.Mode.INJECT_PARENT), HashMap::new)),
(item, m) -> item.stateToBlock = m,
item -> item.stateToBlock,
(item, parent) -> item.stateToBlock = parent.stateToBlock
```

The setter `(item, m) -> item.stateToBlock = m` only stores the name→ID mapping. There is **zero interaction creation** in this setter.

### What DOES Create Interactions on State Items

State items get their interactions from three sources, evaluated in this order:

1. **Copy constructor** (Item.java line 603): `this.interactions = other.interactions`
   - The state item starts with the parent's **finalized** interactions (including any UnarmedInteractions defaults already merged in by the parent's `processConfig`)

2. **Codec setter** (Item.java line 397-400):
   ```java
   (item, v) -> item.interactions = MapUtil.combineUnmodifiable(item.interactions, v, ...)
   ```
   - If the state's JSON defines `"Interactions"`, the setter MERGES the state's parsed interactions INTO the inherited ones. The state's entries **overwrite** inherited entries for the same `InteractionType` key.

3. **`processConfig()` UnarmedInteractions fallback** (Item.java line 1048-1064):
   ```java
   UnarmedInteractions fallbackInteractions = this.playerAnimationsId != null
       ? unarmedInteractionsAssetMap.getAsset(this.playerAnimationsId) : null;
   if (fallbackInteractions != null) {
       for (Entry<InteractionType, String> entry : fallbackInteractions.getInteractions().entrySet()) {
           interactions.putIfAbsent(entry.getKey(), entry.getValue());
       }
   }
   ```
   - Uses `putIfAbsent` — will NOT overwrite any interaction already defined.

### The Relationship Between BlockType and Interactions

Having a `BlockType` on the state item sets `hasBlockType = true` and `blockId = this.id` (in `processConfig`). This does NOT directly create a PlaceBlock interaction. However, the `playerAnimationsId` (inherited from parent as `"Block"`) causes the "Block" `UnarmedInteractions` to provide a **default Secondary PlaceBlock** via `putIfAbsent`. This default has `RemoveItemInHand: true`.

But since the state explicitly defines `Secondary`, and `putIfAbsent` doesn't override, this default should NOT be used.

---

## Q2: Interaction Resolution — Explicit vs UnarmedInteractions Default

### Answer: **The explicit Secondary interaction SHOULD win.** The engine uses `putIfAbsent`, which does not overwrite.

### Proof: Full Resolution Trace

**Step 1 — Parent (`Block_Placeholder`) finalization:**
1. Parent JSON decoded: `Interactions: {Use: "PlaceBlock_Menu"}`
2. Parent's `processConfig()`:
   - `playerAnimationsId` = `"Block"` → loads "Block" `UnarmedInteractions`
   - "Block" UnarmedInteractions includes `{Secondary: <default_PlaceBlock_ID>}` (with `RemoveItemInHand: true`)
   - `putIfAbsent(Secondary, <default_PlaceBlock_ID>)` → **adds** it (parent has no explicit Secondary)
   - Parent finalized interactions: `{Use: "PlaceBlock_Menu", Secondary: <default_PlaceBlock_ID>, SwapFrom: ...}`

**Step 2 — State item loading (via `INJECT_PARENT`):**
1. Copy constructor: `child.interactions = parent.interactions` = `{Use: "PlaceBlock_Menu", Secondary: <default_PlaceBlock_ID>, SwapFrom: ...}`
2. State JSON has `"Interactions"` → codec setter runs:
   ```java
   item.interactions = MapUtil.combineUnmodifiable(item.interactions, parsedFromJSON)
   ```
3. `MapUtil.combineUnmodifiable` (MapUtil.java line 19-24):
   ```java
   Map<T, V> map = supplier.get();
   map.putAll(one);    // parent's finalized interactions
   map.putAll(two);    // state's parsed interactions (overwrites overlapping keys)
   return Collections.unmodifiableMap(map);
   ```
4. `parsedFromJSON` = `{Use: "PlaceBlock_Menu", Secondary: <generated_RootInteraction_ID>}`
   - The `<generated_RootInteraction_ID>` is a NEW RootInteraction containing `[PlaceBlockInteraction(removeItemInHand=false)]`
5. After combine: `{Use: "PlaceBlock_Menu", Secondary: <generated_RootInteraction_ID>, SwapFrom: ...}`
   - The state's explicit Secondary **OVERWRITES** the parent's default

**Step 3 — State item's `processConfig()`:**
1. `putIfAbsent(Secondary, <default_PlaceBlock_ID>)` → **NO-OP** because Secondary already has `<generated_RootInteraction_ID>`
2. Final state interactions: `{Use: "PlaceBlock_Menu", Secondary: <generated_RootInteraction_ID>, SwapFrom: ...}`

### Conclusion

The resolution order is correct. The state item's explicit `Secondary` with `RemoveItemInHand: false` should be the one the engine uses at runtime.

---

## Q3: How to Ensure `RemoveItemInHand: false` Is Respected

### Current Analysis Says It Should Already Work

Based on the codec trace above, the state item's interaction map points to the correct RootInteraction (the one with `RemoveItemInHand: false`). At runtime in `PlaceBlockInteraction.tick0()` (PlaceBlockInteraction.java line 163-165):

```java
BlockPlaceUtils.placeBlock(
    ref, heldItemStack, ...,
    this.removeItemInHand,  // <-- from the executing PlaceBlockInteraction instance
    ...);
```

If the correct instance is executing, `this.removeItemInHand` = `false`, and the item is NOT consumed.

### Possible Root Causes If It's Still Consumed

#### Hypothesis A: Client-Side Pre-Consumption

The protocol `PlaceBlockInteraction` (PlaceBlockInteraction.java protocol line 22):
```java
public boolean removeItemInHand;
```

The server sends `removeItemInHand` to the client via `configurePacket()` (line 217-218):
```java
p.removeItemInHand = this.removeItemInHand;
```

The client MAY perform local prediction based on this flag. If the client's interaction packet reflects a different interaction ID than what the server resolves (e.g., due to a race condition during `UpdateItems`), the client might predict consumption while the server doesn't — or vice versa.

**Diagnostic:** Log the actual RootInteraction ID that the server resolves for the state item's Secondary. Compare it to the generated RootInteraction that contains the PlaceBlock with `RemoveItemInHand: false`.

#### Hypothesis B: The `PlaceBlockEvent` Handler is Not the First Observer

The `BlockPlaceUtils.placeBlock()` flow (BlockPlaceUtils.java line 107-113):
```java
PlaceBlockEvent event = new PlaceBlockEvent(itemStack, blockPosition, targetRotation);
entityStore.invoke(ref, event);
if (event.isCancelled()) {
    targetBlockSection.invalidateBlock(...);
} else {
    ...
    if (isAdventureMode && removeItemInHand) {
        // Consumption happens HERE — AFTER the event
        itemContainer.removeItemStackFromSlot(activeSlot, itemStack, 1);
    }
}
```

The event fires BEFORE consumption. If the PRE-consume diagnostic shows the slot as NULL, the removal is happening BEFORE `BlockPlaceUtils.placeBlock()` is called — i.e., outside the PlaceBlock interaction flow entirely.

This would mean **a different code path is consuming the item.** Possibilities:
- Another interaction in the chain running before PlaceBlock
- A different system listening for a different event
- The InteractionManager itself clearing the held item

#### Hypothesis C: Wrong PlaceBlockInteraction Instance Executing

If the server somehow resolves the Secondary to the "Block" UnarmedInteractions default (the one with `RemoveItemInHand: true`) instead of the state's explicit one, consumption would happen.

**Diagnostic:** In `PlaceBlockInteraction.tick0`, add a log:
```java
LOGGER.info("PlaceBlock executing: id=" + this.getId() + ", removeItemInHand=" + this.removeItemInHand);
```

If the logged `removeItemInHand` is `true`, the wrong interaction instance is executing.

### Recommended Investigation Steps

1. **Verify the state item's actual interactions map at runtime:**
   ```java
   Item stateItem = Item.getAssetMap().getAsset("<state_item_id>");
   String secondaryId = stateItem.getInteraction(InteractionType.Secondary);
   RootInteraction root = RootInteraction.getAssetMap().getAsset(secondaryId);
   // Log the root's interaction chain to confirm PlaceBlock has removeItemInHand=false
   ```

2. **Check if consumption happens inside or outside `BlockPlaceUtils.placeBlock()`:**
   - If the slot is NULL when `PlaceBlockEvent` fires → consumption is OUTSIDE `placeBlock()`
   - If the slot has the item when `PlaceBlockEvent` fires but is NULL after → consumption is inside `placeBlock()` with `removeItemInHand=true`

3. **Check if the Item has two Secondary interactions somehow:**
   - Log the full `interactions` map of the state item at load time

---

## Q4: Does `append` (not `appendInherited`) Prevent Inheritance of `RemoveItemInHand`?

### Answer: **Yes, `append` means no inheritance — but this is NOT the problem in this scenario.**

### The Codec Definition

From [PlaceBlockInteraction.java line 56-63](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/modules/interaction/interaction/config/client/PlaceBlockInteraction.java#L56-L63):

```java
.<Boolean>append(
    new KeyedCodec<>("RemoveItemInHand", Codec.BOOLEAN),
    (placeBlockInteraction, aBoolean) -> placeBlockInteraction.removeItemInHand = aBoolean,
    placeBlockInteraction -> placeBlockInteraction.removeItemInHand
)
```

Compare with `AllowDragPlacement` (line 64-71):
```java
.<Boolean>appendInherited(
    new KeyedCodec<>("AllowDragPlacement", Codec.BOOLEAN),
    (placeBlockInteraction, aBoolean) -> placeBlockInteraction.allowDragPlacement = aBoolean,
    placeBlockInteraction -> placeBlockInteraction.allowDragPlacement,
    (placeBlockInteraction, parent) -> placeBlockInteraction.allowDragPlacement = parent.allowDragPlacement
)
```

### What `append` vs `appendInherited` Means

| Method | Key present in JSON | Key absent in JSON |
|--------|--------------------|--------------------|
| `append` | Setter runs | Nothing happens (field keeps constructor default) |
| `appendInherited` | Setter runs | Inherit function runs (copies from parent) |

For `RemoveItemInHand` with `append`:
- If the JSON includes `"RemoveItemInHand": false` → setter runs → `removeItemInHand = false` ✓
- If the JSON omits `RemoveItemInHand` → field keeps its constructor default: `true`
- Even if a parent PlaceBlockInteraction had `removeItemInHand = false`, the child would NOT inherit it

### Why This Doesn't Affect the Current Scenario

The state items' explicit PlaceBlockInteraction is created via `ContainedAssetCodec` with `GENERATE_ID` mode. From [ContainedAssetCodec.java line 74-77](../../../.tmp_hytale_src/com/hypixel/hytale/assetstore/codec/ContainedAssetCodec.java#L74-L77):

```java
case GENERATE_ID: {
    id = this.keyGenerator.apply(assetExtraInfo);
    boolean inheritContainerTags = false;
    break;
}
```

No parent is assigned. Each PlaceBlockInteraction in the state's interaction chain is a **fresh instance** with no parent. Since the JSON explicitly includes `"RemoveItemInHand": false`, the setter runs and sets it correctly. The `append` vs `appendInherited` distinction is irrelevant here because there IS no parent interaction to inherit from.

### When This WOULD Matter

If you tried to define a "base" PlaceBlock interaction at the parent level and expected states to inherit `RemoveItemInHand: false` automatically:

```json
// Parent item — hypothetical
"Interactions": {
    "Secondary": {
        "Interactions": [{"Type": "PlaceBlock", "RemoveItemInHand": false}]
    }
}

// State item — omits RemoveItemInHand, hoping to inherit
"Interactions": {
    "Secondary": {
        "Interactions": [{"Type": "PlaceBlock"}]
    }
}
```

In this case, the state's PlaceBlockInteraction would be a FRESH instance (GENERATE_ID, no parent), and without `RemoveItemInHand` in the JSON, the field defaults to `true`. The parent's `false` would NOT be inherited because `append` doesn't have an inherit function.

**But this is not your current scenario.** Your states explicitly include `"RemoveItemInHand": false` in every state's JSON.

---

## Q5: Can BlockType Exist on a State Without the Auto-Generated PlaceBlock?

### Answer: **`stateToBlock` does NOT auto-generate a PlaceBlock. The "auto" Secondary comes from UnarmedInteractions, and it can be overridden.**

### The Mechanism

There is NO `stateToBlock` → PlaceBlock auto-generation. The "automatic" PlaceBlock comes from `UnarmedInteractions` during `processConfig()`:

1. The state item inherits `playerAnimationsId = "Block"` from the parent
2. `processConfig()` loads the "Block" `UnarmedInteractions` asset
3. The "Block" UnarmedInteractions has a default `Secondary` mapped to a standard PlaceBlock (with `RemoveItemInHand: true`)
4. `putIfAbsent(InteractionType.Secondary, ...)` tries to add it

**If the state defines its own `Secondary`**, `putIfAbsent` is a no-op and the default is not used.

### Options to Prevent Auto-PlaceBlock (If You Don't Want Placement at All)

1. **Define an explicit Secondary interaction** that is NOT PlaceBlock:
   ```json
   "Interactions": {
       "Secondary": {
           "Interactions": [{"Type": "SendMessage", "Message": "No placement"}]
       }
   }
   ```

2. **Override `playerAnimationsId`** to an animation set without Secondary PlaceBlock:
   ```json
   "PlayerAnimationsId": "Item"
   ```
   (The "Item" UnarmedInteractions likely does NOT include a Secondary PlaceBlock)

3. **Define a BlockType WITHOUT any interactions** and let the UnarmedInteractions default apply — then cancel in the event handler.

### For Your Scenario

You DO want PlaceBlock behavior — you just want `RemoveItemInHand: false`. Your current approach (explicit Secondary with PlaceBlock and RemoveItemInHand: false) is architecturally correct.

---

## Key Finding: The Engine Code Says `RemoveItemInHand: false` Should Work

The full resolution trace (Q2) demonstrates that the state item's explicit Secondary interaction — containing a PlaceBlockInteraction with `removeItemInHand = false` — should be the one used at runtime. The `combineUnmodifiable` merge overwrites the parent's default, and `putIfAbsent` in `processConfig()` does not reverse this.

**If the item is still being consumed, the issue is likely NOT in the interaction resolution.** Investigate:

1. **Is the consumption happening before `BlockPlaceUtils.placeBlock()` is even called?** → Something else is clearing the slot.
2. **Is the WRONG interaction instance executing?** → Runtime log the `removeItemInHand` value inside `PlaceBlockInteraction.tick0()`.
3. **Is the client doing predictive consumption?** → Check if the client receives the correct `removeItemInHand=false` in the interaction packet.

---

## Diagnostic Checklist

```
□ Log Item.getInteraction(InteractionType.Secondary) for the state item at LoadAssetEvent
  → Confirm it points to the generated RootInteraction (not the "Block" default)

□ Log the PlaceBlockInteraction.removeItemInHand field of the actual executing interaction
  → Confirm it's false, not true

□ Log hotbar slot contents INSIDE BlockPlaceUtils.placeBlock(), before the PlaceBlockEvent
  → Determine if the slot is already NULL before the event, or NULL after

□ Log whether another system/event is modifying the hotbar slot between right-click and PlaceBlockEvent

□ Check the client-received interaction packet for the state item
  → Confirm protocol PlaceBlockInteraction.removeItemInHand = false
```

## See Also
- [Item State System](./item-state-system.md) — full state system documentation
- [Bucket Pattern](./bucket-pattern.md) — RemoveItemInHand consumption flow
- [PlaceBlock Risk Investigation](./placeblock-risk-investigation.md) — R3 confirms cancel prevents consumption
- [PlaceBlock Building Tool API Research](./placeblock-building-tool-api-research.md) — PlaceBlockInteraction fields
