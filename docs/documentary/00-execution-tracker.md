# Documentary Execution Tracker

Use this file as the control center for execution order, small-task cadence, and high-level alignment.

Governance source of truth: [Governance Standard](00-operating-system/00-governance-standard.md).

## North Star

Deliver a clear documentary where viewers can explain the user problem, trust the build journey, and understand how to use the outcome.

## Runtime Targets

Inherited from [Governance Standard](00-operating-system/00-governance-standard.md).

## Single-Path Governance

Inherited from [Governance Standard](00-operating-system/00-governance-standard.md).

Operational reminder:

- Update Master Line and Session Log before moving to the next task.

## Operating System Anchors

Load-order rules are inherited from [Governance Standard](00-operating-system/00-governance-standard.md).

Quick links:

- [Project Charter](00-operating-system/01-project-charter.md)
- [Master Line](00-operating-system/02-master-line.md)
- [Iteration Brief](00-operating-system/04-iteration-brief.md)
- [Step Card](00-operating-system/05-step-card.md)

If uncertainty appears, route through:

- [Branch Experiment](00-operating-system/06-branch-experiment.md)
- [Reintegration Note](00-operating-system/07-reintegration-note.md)
- [Decision Ledger](00-operating-system/03-decision-ledger.md)
## Stage Gates

| Stage | Objective | Required Inputs | Exit Criteria | Status |
|---|---|---|---|---|
| SG-1 Story Lock | Align on the core conflict and audience promise | `01-story/01-logline.md`, `01-story/02-narrative-arc.md`, `01-story/03-character-beats.md` | Logline approved, 3-act arc approved, interview personas approved | [ ] |
| SG-2 Production Ready | Prepare recording and interview execution | `02-production/00-production-timeline.md`, `02-production/01-preproduction-checklist.md`, `02-production/02-shoot-plan.md`, `02-production/03-interview-questions.md`, `02-production/04-broll-shot-list.md` | Shoot plan filled, checklist completed, question bank prioritized | [ ] |
| SG-3 Capture Complete | Record key footage and interview soundbites | SG-2 outputs plus asset index | All must-capture shots recorded, top interview soundbites captured, assets logged | [ ] |
| SG-4 Git Story Verified | Convert repo history into narrative turning points | `04-git-story/01-commit-arc-map.md`, `04-git-story/02-annotated-milestones.md`, `04-git-story/03-pr-commentary-template.md` | At least 1 milestone per act, claims tied to commits/PRs/docs | [ ] |
| SG-5 Edit Structure Locked | Build rough narrative edit and VO structure | `03-edit/01-paper-edit.md`, `03-edit/02-beat-sheet.md`, `03-edit/03-voiceover-script-draft.md`, `03-edit/04-layered-timeline-mermaid.md` | Paper edit complete, beat sheet complete, VO draft complete, layered timeline and clip notes complete | [ ] |

## SG-1 Story Quality Rubric (Mandatory)

SG-1 is not complete unless every rubric item below is passable using repo evidence and planned footage coverage.

| Rubric Item | Pass Standard | Evidence Source | Pass/Fail |
|---|---|---|---|
| Core player pain is explicit | One sentence clearly states what is frustrating today and for whom | `01-story/01-logline.md` | [ ] |
| Stakes are concrete | Story states what players lose if the issue is not solved | `01-story/01-logline.md`, `01-story/02-narrative-arc.md` | [ ] |
| 3-act progression has escalation | Act 2 introduces real friction, tradeoffs, or failures before payoff | `01-story/02-narrative-arc.md` | [ ] |
| Character voices are distinct | Owner, engineer, and player beats are non-duplicative and role-authentic | `01-story/03-character-beats.md` | [ ] |
| Claim-to-proof feasibility exists | Every major narrative claim has a planned proof shot, artifact, or repo citation | `03-edit/01-paper-edit.md`, `04-git-story/02-annotated-milestones.md` | [ ] |
| Audience onboarding is clear | A first-time viewer can explain what the plugin does by end of Act 1 | `01-story/02-narrative-arc.md` | [ ] |
| Ending payoff is earned | Final beat directly resolves opening pain and shows player value | `01-story/02-narrative-arc.md`, `03-edit/02-beat-sheet.md` | [ ] |

Rubric rule: if any item is fail or unknown, SG-1 remains open.

## Completion Integrity Gate (Mandatory)

Inherited from [Governance Standard](00-operating-system/00-governance-standard.md).

Do not mark any step, iteration, or stage as complete until tracking artifacts are updated.

- [ ] Relevant file output is updated.
- [ ] Session Log entry is added.
- [ ] Master Line reflects current status.
- [ ] Iteration Brief reflects current focus.
- [ ] Timeline change log is updated when timing or clip logic changed.
- [ ] SG-1 rubric is fully passed before SG-1 can be marked complete.

## Bite-Sized Work Units

Run work in 30 to 60 minute units.

1. Pick one stage only.
2. Pick one deliverable file only.
3. Complete one visible delta.
4. Update Status and Notes.

Examples of one-unit deltas:

- Add one approved soundbite to interview guide.
- Fill one segment row in shoot plan.
- Annotate one commit milestone with player impact.
- Lock one beat in the beat sheet.

## Session Start Ritual (Do Not Skip)

1. Read the North Star section above.
2. Read current stage objective and exit criteria.
3. Confirm the one file you will move forward this session.

## Session End Ritual (Do Not Skip)

1. Mark completed checklist items.
2. Add one short note in Session Log.
3. Set next session's first task in one sentence.

## Session Log

| Date | Stage | File Updated | What Changed | Why It Matters For Players | Next Task |
|---|---|---|---|---|---|
| YYYY-MM-DD | SG-X | `path/to/file.md` | Brief delta summary | Survival and creative value statement | One concrete next step |

## Drift Guardrails

If a task does not support at least one of the following, defer it:

- Makes player pain more visible.
- Makes design/engineering struggle more credible.
- Makes final player payoff clearer.
