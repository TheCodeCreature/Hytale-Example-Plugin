---
area: "Economy Admin Commands"
updated: 2026-05-19
---

# Economy Admin Commands

## Player Experience Goal

Server admins need a simple, predictable way to manage the 12× resource economy. They should be able to calculate the economy once, inspect any item or block to understand what changed, and recalculate or reset on demand. The commands should feel like a toolkit — not a black box.

## Command Structure

```
/economy calculate       — Compute and apply all 12× modifications, persist to disk
/economy reset           — Remove all modifications, restore vanilla values, clear saved data
/economy inspect <item>  — Show the economy status of a specific item or block
/economy status          — Show summary: how many recipes scaled, blocks modified, etc.
```

### `/economy calculate`

Computes the full 12× economy pipeline and persists the result. This replaces the current `DropScaler.apply()` that runs on every boot.

**What the player sees:**
```
§a[Economy] Calculating 12× resource economy...
§a[Economy] Complete: 577 recipes scaled, 4615 natural blocks modified,
           576 recipe blocks configured, 89 synthetic drop lists created,
           2438 stack sizes boosted.
§a[Economy] Saved to economy_manifest.json.
```

**Behavioral contracts:**
1. Safe to run multiple times — produces identical results (idempotent when state hasn't changed).
2. If a manifest already exists, warns the admin and asks for confirmation (or accepts a `--force` flag / `confirm` subcommand).
3. Persists a manifest file to the server's data directory (`run/` or world data folder).
4. The manifest includes enough metadata to detect staleness (asset hash, timestamp, plugin version).

### `/economy reset`

Reverts all modifications to vanilla values and deletes the saved manifest.

**What the player sees:**
```
§e[Economy] Resetting all economy modifications...
§a[Economy] Reset complete. Vanilla values restored.
§e[Economy] Note: Server restart recommended to fully clear in-memory state.
```

**Behavioral contracts:**
1. Deletes the persisted manifest file.
2. Restores in-memory assets to vanilla values where possible (recipe inputs, stack sizes).
3. Block gathering configs that were modified by the pipeline may require a server restart to fully revert (reflection-mutated objects may not be cleanly restorable).

### `/economy inspect <item>`

Shows the economy status of a specific item or block type. Uses Hytale's `ArgTypes.ITEM_ASSET` or `ArgTypes.STRING` for flexible lookup.

**What the player sees (for a modified recipe block):**
```
§b[Economy] === Deco_Rope ===
§f  Type: Block (Builders bench)
§f  Recipe: Deco_Rope_Recipe_Generated_0
§f  Original inputs: 1× Ingredient_Fibre
§f  Scaled inputs:   1× Ingredient_Fibre (crafted — not scaled)
§f  Break drops:     1× Ingredient_Fibre
§f  Base recipe: No
§f  Status: §aModified
```

**What the player sees (for a natural resource):**
```
§b[Economy] === Rock_Stone_Cobble ===
§f  Type: Natural block
§f  Original drop: 1× Rock_Stone_Cobble
§f  Scaled drop:   12× Rock_Stone_Cobble
§f  Stack size:    100 → 1200
§f  Status: §aModified
```

**What the player sees (for an unaffected item):**
```
§b[Economy] === Sword_Iron ===
§f  Type: Item (no registered bench recipe)
§f  Status: §7Unmodified
```

**Behavioral contracts:**
1. Works whether or not the economy has been calculated — shows "would be modified" vs "is modified."
2. Accepts item IDs, block type IDs, or partial matches with disambiguation.
3. Shows both original and scaled values so the admin can verify correctness.

### `/economy status`

Shows a summary of the current economy state.

**What the player sees:**
```
§b[Economy] === Status ===
§f  Manifest: §aLoaded (saved 2026-04-18 20:33:42)
§f  Asset hash: a3f8c2... (§acurrent)
§f  Recipes scaled: 577 (913 block + 150 non-block, 486 base skipped)
§f  Natural blocks: 4615 modified, 56 skipped
§f  Recipe blocks: 576 configured
§f  Synthetic drop lists: 89
§f  Stack sizes boosted: 2438
§f  Multiplier: 12×
```

## Edge Cases & Decisions

| Scenario | Decision | Rationale |
|----------|----------|-----------|
| Admin runs `/economy calculate` twice | Second run is idempotent — detects existing manifest, warns, applies only if forced | Prevents accidental double-scaling (the ×144 bug) |
| Admin runs `/economy inspect` before calculating | Shows "would be modified" based on dry-run analysis | Useful for preview before committing |
| Game update changes recipes (new items, removed blocks) | `/economy status` shows "asset hash: §cstale" | Prompts admin to recalculate |
| Admin wants to add a new bench to the economy | Future: `/economy calculate --benches Builders,Furniture_Bench,Workbench` | Extensible bench list |

## Anti-Patterns to Reject

- **Automatic calculation on every server boot.** This is what the current system does and it's fragile, slow, and non-inspectable.
- **Silent failure.** If a recipe can't be resolved or an item is missing, the command must log it visibly — not silently skip.
- **Requiring code changes to add benches.** The bench list should be configurable, not hardcoded.
