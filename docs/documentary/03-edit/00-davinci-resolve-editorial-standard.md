# DaVinci Resolve Editorial Standard

Purpose: define one canonical editorial tooling standard so all documentary planning and edit artifacts stay consistent without repeated instructions.

## Default Rule

- DaVinci Resolve is the default NLE for this documentary system.
- Unless the user explicitly overrides, all editorial outputs inherit this standard.
- Do not request repeated confirmation for naming, bins, or track routing when this default applies.

## Clip Naming Standard

- A-Roll: `AR_<segment>_t<nn>.mp4`
- B-Roll: `BR_<segment>_<action>_t<nn>.mp4`
- Voice: `VO_<segment>_t<nn>.wav`
- Music: `MUS_<mood>_<bpm>.wav`
- SFX: `SFX_<type>_<variant>.wav`

## Resolve Media Pool Bin Structure

- `01_ARoll`
- `02_BRoll`
- `03_VO`
- `04_Music`
- `05_SFX`
- `06_GFX`
- `07_Exports`

## Resolve Timeline Track Labels

- Video: `V1_ARoll`, `V2_BRoll`, `V3_GFX`
- Audio: `A1_DX`, `A2_SFX`, `A3_MUS`

## Override Protocol

If a different editor/tooling convention is requested:

1. Record the override decision in `docs/documentary/00-operating-system/03-decision-ledger.md`.
2. Note the active standard in `docs/documentary/00-operating-system/02-master-line.md`.
3. Apply the override consistently across all affected files.
4. Do not run mixed standards in the same iteration.
