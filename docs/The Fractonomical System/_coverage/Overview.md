# Consistency Checker Overview

This checker provides a lightweight guardrail for the Fractonomical guide/wiki.

## What It Checks

1. Broken markdown links to local files/folders.
2. Legacy `Details.md` references.
3. Unknown tag namespaces (allowed: `domain`, `layer`, `type`, `kind`, `signal`, `team`).
4. Non-namespaced tags (warning).
5. Missing `updated` in key ticket/knowledge frontmatter types.
6. Missing `status` and `progress` on backlog ticket types.
7. Basic `status`/`progress` mismatches:
   - `done` with progress below 100 (error)
   - `backlog` with progress above 0 (warning)

## Run It

From repository root in PowerShell:

```powershell
./_coverage/fractonomical-consistency-check.ps1
```

Optional root path:

```powershell
./_coverage/fractonomical-consistency-check.ps1 -Root .
```

After each run, the checker rewrites **[`_coverage/Report.md`](./Report.md)** with a full timestamped issue table.

## Run From VS Code Task UI

Use task label: `Fractonomical: Run Consistency Check`

1. Open Command Palette.
2. Run `Tasks: Run Task`.
3. Select `Fractonomical: Run Consistency Check`.

## Exit Codes

- `0`: no errors found
- `1`: one or more errors found

Warnings do not fail the command.

## Suggested Usage

1. Run before major rule/template edits.
2. Run before committing governance changes.
3. Treat warnings as hygiene debt and clear them regularly.
