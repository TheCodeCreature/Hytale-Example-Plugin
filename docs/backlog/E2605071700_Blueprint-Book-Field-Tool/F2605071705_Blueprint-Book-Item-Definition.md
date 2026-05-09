---
id: F2605071705
type: feature
title: "Blueprint Book Item Definition"
status: backlog
priority: high
epic: E2605071700
created: 2026-05-07
---

# Blueprint Book Item Definition

## Description
Define the Blueprint Book as a held item using the Spellbook model, with a Q-key (Use) interaction wired to a custom interaction class that opens the Quick Select UI.

## Acceptance Criteria

### Checklist
- [ ] Item JSON defined with Spellbook model and Tool quality
- [ ] `/give BlueprintBook` produces a valid held item with book model
- [ ] Q-key fires the registered interaction type
- [ ] Item has MaxStack 1 and belongs to Tool.BlueprintBook category

### Scenarios
**Player receives Blueprint Book**
- **Given** a player with an empty hotbar
- **When** the server gives them a BlueprintBook item
- **Then** it appears in their hotbar with the book model visible when held

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605071706 | Create BlueprintBook.json item config | backlog |
| S2605071707 | Create interaction chain JSONs | backlog |
| S2605071708 | Register interaction codec in plugin | backlog |

## Notes
- Uses `Items/Weapons/Spellbook/Book.blockymodel` — confirm icon asset path
- Follows exact pattern of PortableBench_Builders.json
