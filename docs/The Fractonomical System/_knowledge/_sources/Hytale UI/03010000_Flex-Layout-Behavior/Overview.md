---
id: "03010000"
type: knowledge-topic
title: "Flex Layout Behavior"
parent: "03000000"
created: 2026-07-18
updated: 2026-07-18
tags:
  - domain:hytale-ui
  - kind:topic
key_terms:
  - FlexWeight
  - LayoutMode
  - LeftCenterWrap
  - TopScrolling
---

# Flex Layout Behavior

## Navigation
- [Back to Knowledge Catalog](../../../Catalog.md)
- [Back to Source Overview](../Overview.md)
- [Source Topic Map](../Topics.md)

## Topic Summary
Observed behavior indicates `FlexWeight` is used to consume remaining parent space, while `LeftCenterWrap` controls flow and wrapping of child containers. Content-sized grouping is achieved by avoiding `FlexWeight` on the grouped child containers and using wrap layout in the parent.

## Key Questions
- Does `FlexWeight` force fill behavior across siblings?
- How should grouped icon containers be configured so small groups do not stretch?
- Can engine-level flex algorithm details be confirmed from the available source path?

## Findings
1. `FlexWeight: 1` is used in multiple pages specifically on panels meant to expand into available space.
2. `LayoutMode: LeftCenterWrap` is used for cell/group regions that should wrap to new rows based on available width.
3. In available source files under `com/hypixel/hytale/server/core/ui`, layout algorithm classes are not present; only command/data helper classes are available.
4. Practical pattern for "1 icon, 2 icons, 10 icons" grouping:
   - Parent: `LayoutMode: LeftCenterWrap`
   - Child group containers: no `FlexWeight`, no forced full-width anchors
   - Result: small groups remain content-sized; large groups wrap to next row when they no longer fit.

## Evidence
- UI usage of flex and wrap in stencil page:
  - `FlexWeight` expansion regions in [src/main/resources/Common/UI/Custom/Pages/StencilBook/StencilBookPage.ui](../../../../../src/main/resources/Common/UI/Custom/Pages/StencilBook/StencilBookPage.ui)
  - `LayoutMode: LeftCenterWrap` grid/group regions in [src/main/resources/Common/UI/Custom/Pages/StencilBook/StencilBookPage.ui](../../../../../src/main/resources/Common/UI/Custom/Pages/StencilBook/StencilBookPage.ui)
- Group container wrap pattern:
  - [src/main/resources/Common/UI/Custom/Pages/StencilBook/Components/SetGroupContainer.ui](../../../../../src/main/resources/Common/UI/Custom/Pages/StencilBook/Components/SetGroupContainer.ui)
  - [src/main/resources/Common/UI/Custom/Pages/StencilBook/Components/IngredientButtonGrid.ui](../../../../../src/main/resources/Common/UI/Custom/Pages/StencilBook/Components/IngredientButtonGrid.ui)
- Available core UI path contents (no layout engine classes found):
  - [.tmp_hytale_src/com/hypixel/hytale/server/core/ui/Anchor.java](../../../../../.tmp_hytale_src/com/hypixel/hytale/server/core/ui/Anchor.java)
  - [.tmp_hytale_src/com/hypixel/hytale/server/core/ui/Area.java](../../../../../.tmp_hytale_src/com/hypixel/hytale/server/core/ui/Area.java)
  - [.tmp_hytale_src/com/hypixel/hytale/server/core/ui/builder/UICommandBuilder.java](../../../../../.tmp_hytale_src/com/hypixel/hytale/server/core/ui/builder/UICommandBuilder.java)

## Gaps
- Unknown from available source evidence: exact internal flex measurement/placement algorithm implementation.
- Unknown from available source evidence: whether additional engine repos/modules contain hidden layout semantics not present in `.tmp_hytale_src` snapshot.
