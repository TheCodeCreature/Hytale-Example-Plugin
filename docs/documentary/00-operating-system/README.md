# Director Operating System

Purpose: run long-form documentary projects with high alignment, low drift, and controlled flexibility.

## Layers

1. Canonical Layer: stable truth that changes rarely.
2. Iteration Layer: current stage and branch decisions.
3. Working Layer: immediate step context only.

## Artifact Map

- [Governance Standard](00-governance-standard.md)
- [Project Charter](01-project-charter.md)
- [Master Line](02-master-line.md)
- [Decision Ledger](03-decision-ledger.md)
- [Iteration Brief](04-iteration-brief.md)
- [Step Card](05-step-card.md)
- [Branch Experiment](06-branch-experiment.md)
- [Reintegration Note](07-reintegration-note.md)
- [Checkpoint Review](08-checkpoint-review.md)

## Operating Loop

```mermaid
flowchart TD
    A[Load Project Charter] --> B[Load Master Line]
    B --> C[Load Current Iteration Brief]
    C --> D[Create One Step Card]
    D --> E{Evidence Complete?}
    E -->|No| F[Ask Clarifying Questions]
    F --> G[Record Decision In Ledger]
    G --> H[Execute Step]
    E -->|Yes| H[Execute Step]
    H --> I{Unknown Hurdle?}
    I -->|Yes| J[Run Branch Experiment]
    J --> K[Write Reintegration Note]
    K --> L[Update Iteration Brief]
    I -->|No| L[Update Iteration Brief]
    L --> M[Update Master Line]
    M --> N[Schedule Next Step]
```

## Hard Rules

All hard rules are inherited from [Governance Standard](00-governance-standard.md).

Local reminder:

- Every session must update the master line and next action.
