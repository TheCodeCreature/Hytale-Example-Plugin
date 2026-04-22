---
id: F2604221050
type: feature
title: "Rarity-Based Availability Indicators"
status: backlog
priority: high
epic: E2604221030
created: 2026-04-22
---

# Rarity-Based Availability Indicators

## Description
The `Block_Placeholder` item uses the engine's rarity/quality highlight system to provide real-time visual feedback about resource availability. The highlight color changes based on the placeholder's state and whether the player can afford the selected recipe. Updates occur on inventory change events and after each placement action.

## Acceptance Criteria

### Checklist
- [ ] Unarmed placeholder (no recipe selected) shows blue "Tool" quality highlight
- [ ] Armed placeholder with sufficient resources shows green "Uncommon" quality highlight
- [ ] Armed placeholder with insufficient resources shows red "Developer" quality highlight
- [ ] Indicator updates when player inventory changes (items added, removed, moved)
- [ ] Indicator updates after each right-click placement (resources consumed)
- [ ] Indicator updates when nearby chest contents change (if observable)
- [ ] Indicator must never show green when resources are insufficient (Contract #13)

### Scenarios
**Placeholder starts unarmed**
- **Given** a new `Block_Placeholder`
- **When** the player holds it
- **Then** it shows the blue "Tool" highlight

**Recipe selected with available resources**
- **Given** the player arms the placeholder with "Cobble Wall"
- **When** they have 48+ cobblestone available
- **Then** the highlight changes to green "Uncommon"

**Resources become insufficient**
- **Given** the placeholder is armed and showing green
- **When** the player drops cobblestone reducing their count below 48
- **Then** the highlight changes to red "Developer"

**Resources restored**
- **Given** the placeholder is armed and showing red
- **When** the player picks up cobblestone bringing their count to 48+
- **Then** the highlight changes to green "Uncommon"

**After placement consumes last batch of resources**
- **Given** the player has exactly 48 cobblestone
- **When** they place one Cobble Wall
- **Then** the highlight changes from green to red immediately

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2604221145 | Quality State Machine for Placeholder | backlog |
| S2604221150 | Inventory Change Event Listener for Rarity Updates | backlog |

## Notes
- The engine's `Quality` field on items (`"Tool"`, `"Uncommon"`, `"Developer"`) controls the highlight color. Changing the quality at runtime may require reflection or item replacement.
- The portable bench system already uses `ItemContainer.registerChangeEvent()` for inventory monitoring — same pattern applies here.
- Risk: Changing item quality at runtime may not be supported by the engine. May need to swap the item instance with a new one of different quality. This needs Architect investigation.
- Risk: "Nearby chests" monitoring may not have events — polling or restricting to player inventory only may be needed.
