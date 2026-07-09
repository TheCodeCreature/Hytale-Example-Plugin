# Prompt: Director Operating System Bootstrap

Use this prompt to start any new conversation with the Documentary Director rules and operating system already loaded.

## Objective

Initialize work from `docs/documentary/` as the canonical guideline source so the agent can:

1. Stay aligned to the long-running plan.
2. Work in small, immediate-context steps.
3. Handle unknowns without drifting from the goal.

## Required Context Sources

Load these files first and treat them as source of truth:

- [Documentary Hub](../../docs/documentary/README.md)
- [Execution Tracker](../../docs/documentary/00-execution-tracker.md)
- [Operating System Hub](../../docs/documentary/00-operating-system/README.md)
- [Project Charter](../../docs/documentary/00-operating-system/01-project-charter.md)
- [Master Line](../../docs/documentary/00-operating-system/02-master-line.md)
- [Decision Ledger](../../docs/documentary/00-operating-system/03-decision-ledger.md)
- [Iteration Brief](../../docs/documentary/00-operating-system/04-iteration-brief.md)
- [Step Card](../../docs/documentary/00-operating-system/05-step-card.md)

## Bootstrap Instructions

1. Ask concise clarification questions if any of these are missing:
- Target project folder to operate in
- Current stage gate
- Immediate session objective

2. Build the session from the Operating System load order:
- Canonical: Charter + Master Line
- Iteration: Iteration Brief
- Immediate: one Step Card only
- Path policy: no parallel steps, no parallel branch experiments

3. Enforce evidence-first execution:
- No decisions without documented evidence
- If unclear, ask questions before editing
- Record decisions in the ledger

4. Handle unknowns with controlled flexibility:
- Open [Branch Experiment](../../docs/documentary/00-operating-system/06-branch-experiment.md)
- Close with [Reintegration Note](../../docs/documentary/00-operating-system/07-reintegration-note.md)
- Do not proceed to new stage work until reintegration is complete

5. Close the session by updating:
- [Iteration Brief](../../docs/documentary/00-operating-system/04-iteration-brief.md)
- [Master Line](../../docs/documentary/00-operating-system/02-master-line.md)
- [Execution Tracker](../../docs/documentary/00-execution-tracker.md)

## If Target Folder Is Missing

If the target project folder does not yet have documentation artifacts:

1. Create a `docs/documentary/` folder in that target project.
2. Scaffold the same operating-system files using this repository's templates as guide.
3. Keep relative markdown links valid from each file depth.

## Output Requirements

- Use markdown-native Mermaid for process maps when useful.
- Use repository-relative links with correct `../` depth.
- Keep one active step at a time.
- Always include a one-sentence next action.
