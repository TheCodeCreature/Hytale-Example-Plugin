# Documentary Governance Standard

Purpose: define one canonical governance rule set so execution stays aligned without repeating policy text in every file.

## Default Rule

- This file is the source of truth for execution governance.
- Other documents should reference this file instead of redefining shared policy.

## Single-Path Governance

- One active stage gate at a time.
- One active step card at a time.
- At most one temporary branch experiment at a time.
- No stage advancement while a branch experiment is unresolved.
- Reintegration Note is required before normal flow resumes.

## Completion Integrity Governance

- Do not mark any step, iteration, or stage complete until tracking artifacts are updated.
- Minimum tracking updates: Master Line, Iteration Brief, and Execution Tracker Session Log.
- If timing or clip logic changes, update the timeline change log in the same work unit.
- SG-1 may not be marked complete unless the SG-1 Story Quality Rubric is fully passed.

## Evidence and Clarification Governance

- No major decision without documented evidence.
- If evidence is missing, pause and ask clarifying questions.
- Record clarified decisions in the Decision Ledger before proceeding.

## Runtime and Clip Target Governance

- Full-length runtime target: 12:00 (+/- 1:00).
- Highlight extraction target: multiple standalone 1:00 clips.
- Coherence rule: no minimum clip count is required, but the full narrative must remain compelling and complete.

## Operating Load Order Governance

At task start, load context in this order:

1. Project Charter
2. Master Line
3. Iteration Brief
4. Step Card

If blocked by unknowns:

1. Branch Experiment
2. Reintegration Note
3. Decision Ledger

## Bootstrap Governance

- For new conversations, load canonical docs first: Charter, Master Line, Execution Tracker.
- Execute one active Step Card at a time.
- Use the Director OS bootstrap prompt when available.

## Override Protocol

If a different governance pattern is required for a special case:

1. Record the temporary override in Decision Ledger.
2. Note active override scope in Master Line.
3. Time-box the override to one iteration.
4. Reinstate this standard at reintegration.
