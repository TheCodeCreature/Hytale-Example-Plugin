---
source_id: "FS-0003"
id: "04000000"
type: knowledge-source
title: "Hytale"
source_kind: code-reference
authors:
  - Code Creature
created: 2026-07-22
updated: 2026-07-22
tags:
  - domain:hytale
  - kind:reference
links:
  canonical: ""
---

# Hytale

## Navigation
- [Back to Knowledge Catalog](../../Catalog.md)
- [Back to Knowledge Hub](../../README.md)
- [Topics](./Topics.md)
- [Topic: Crafting Input Resolution](./04010000_Crafting-Input-Resolution/Overview.md)

## Source Summary
Working reference for Hytale crafting input resolution, focused on how generic recipe inputs expressed through `ResourceTypeId` are represented in the available API surface and where this plugin currently collapses them to representative concrete items.

## Scope
- In scope: `CraftingRecipe` input shape, `MaterialQuantity` usage, plugin-side resolution and affordability behavior, and planning implications for replicating engine-like generic input handling.
- Out of scope: hidden engine implementation details not present in the available local shared-source mirror or GitHub fallback snapshot.

## How To Use This Source
1. Start with the crafting input resolution topic for current behavior and evidence.
2. Treat the gaps section as authoritative when deciding whether a change is engine-backed or plugin-defined.
3. Use the implementation plan there before editing crafting, auto-craft, or ingredient-tree code.
