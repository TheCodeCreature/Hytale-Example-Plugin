# ItemGrid Crash Fix

ItemGrid with ItemStack crashes with NullReferenceException if the layout structure is wrong.

## Working pattern (required)
```
Group {
    Anchor: (Width: 240);
    LayoutMode: Top;

    ItemGrid #MyGrid {
        FlexWeight: 1;
        SlotsPerRow: 4;
        DisplayItemQuantity: true;
        RenderItemQualityBackground: false;
        Style: (SlotSize: 48, SlotSpacing: 4);
    }
}
```

## What crashes
- ItemGrid with fixed `Anchor: (Width: X, Height: Y)` instead of `FlexWeight: 1`
- ItemGrid without a parent Group that has `LayoutMode: Top`
- ItemGrid directly inside `#Content` without a wrapper Group

## Key: the parent Group MUST have `LayoutMode: Top` and the ItemGrid MUST use `FlexWeight: 1`.

## Click/Drag events require `setActivatable(true)`
- `isActivatable` defaults to `false` — only mouse enter/exit fire without it
- Call `slot.setActivatable(true)` on each ItemGridSlot to enable SlotClicking, drag events, etc.
- No `setActivatable` calls exist in engine source — they set `isActivatable` directly via codec
