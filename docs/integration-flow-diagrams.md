# LES / MECT Integration — Flow Diagrams (Mermaid)

Use these diagrams to derive sequence diagrams and to explain states, events, and integration flows.

---

## 1. Enrollment state machine (LES)

Enrollment status and allowed transitions. Triggers are either **user/API action** (LES) or **Kafka event consumed** (LES).

```mermaid
stateDiagram-v2
    [*] --> DRAFT : Create enrollment (POST /api/lmrs)

    DRAFT --> SUBMITTED : Submit for approval\n(POST .../submit)

    SUBMITTED --> APPROVED : Approve enrollment\n(POST .../approve)\n→ publish lmr.approved.v1

    APPROVED --> WITHDRAWN_REQUESTED : Withdraw\n(POST .../withdraw)\nonly if eligibility.canWithdraw\n→ publish lmr.withdraw.requested.v1

    WITHDRAWN_REQUESTED --> WITHDRAWN : Consume\nlmr.withdraw.completed.v1
    WITHDRAWN_REQUESTED --> WITHDRAW_REJECTED : Consume\nlmr.withdraw.rejected.v1

    WITHDRAWN --> [*] : terminal
    WITHDRAW_REJECTED --> [*] : terminal

    note right of APPROVED
        UI shows Withdraw only when
        eligibility (from MECT via Kafka) says canWithdraw=true
    end note
```

---

## 2. LMR state in MECT (authoritative)

MECT owns LMR lifecycle and blocking flags. Withdrawal outcome is decided here.

```mermaid
stateDiagram-v2
    [*] --> DoesNotExist : (no LMR yet)

    DoesNotExist --> Active : Consume lmr.approved.v1\ncreate LMR, compute capacity\n→ publish lmr.withdraw.eligibility.v1

    Active --> WithdrawRequested : Consume lmr.withdraw.requested.v1

    WithdrawRequested --> Withdrawn : No blocking flags\n→ lmr.withdraw.completed.v1\n→ eligibility canWithdraw=false
    WithdrawRequested --> Active : Blocking flags present\n→ lmr.withdraw.rejected.v1\n→ eligibility canWithdraw=false + reason

    Withdrawn --> [*] : terminal

    note right of Active
        Blocking flags (e.g. ZRC_TRANSACTION_EXISTS)
        can be toggled via MECT API;
        each change republishes eligibility.
    end note
```

---

## 3. End-to-end integration flow (swimlanes)

Shows who does what and where events go. Use this to derive a sequence diagram.

```mermaid
flowchart TB
    subgraph User ["👤 User / Admin"]
        A1[Create enrollment]
        A2[Submit for approval]
        A3[Approve enrollment]
        A4[Click Withdraw]
    end

    subgraph LES_UI ["LES UI (Angular)"]
        U1[POST /api/lmrs]
        U2[POST .../submit]
        U3[POST .../approve]
        U4[GET .../withdraw-eligibility]
        U5[POST .../withdraw]
        U6[Poll enrollment + eligibility every 2s when APPROVED or WITHDRAWN_REQUESTED]
    end

    subgraph LES_API ["LES API (8081)"]
        L1[Store DRAFT]
        L2[Set SUBMITTED]
        L3[Set APPROVED, write outbox]
        L4[Read eligibility from cache]
        L5{eligibility.canWithdraw?}
        L6[Return 409 if no]
        L7[Set WITHDRAWN_REQUESTED, write outbox]
        L8[Consume completed/rejected → update status]
        L9[Consume eligibility → update read-model]
    end

    subgraph Kafka ["Kafka"]
        K1[(lmr.approved.v1)]
        K2[(lmr.withdraw.requested.v1)]
        K3[(lmr.withdraw.eligibility.v1)]
        K4[(lmr.withdraw.completed.v1)]
        K5[(lmr.withdraw.rejected.v1)]
    end

    subgraph MECT_API ["MECT API (8082)"]
        M1[Consume approved → create LMR]
        M2[Publish eligibility]
        M3[Consume withdraw.requested]
        M4{Blocking flags?}
        M5[Publish completed + eligibility]
        M6[Publish rejected + eligibility]
    end

    A1 --> U1 --> L1
    A2 --> U2 --> L2
    A3 --> U3 --> L3 --> K1
    K1 --> M1 --> M2 --> K3
    K3 --> L9

    U4 --> L4
    A4 --> U5 --> L5
    L5 -->|no| L6
    L5 -->|yes| L7 --> K2
    K2 --> M3 --> M4
    M4 -->|no| M5 --> K4 --> K3
    M4 -->|yes| M6 --> K5 --> K3
    K4 --> L8
    K5 --> L8

    U6 -.->|refetch| L4
    U6 -.->|refetch| L8
```

---

## 4. Event flow (who publishes / who consumes)

Use this to annotate a sequence diagram with topic names and direction.

```mermaid
flowchart LR
    subgraph LES ["LES"]
        LES_PUB[Publish]
        LES_CONS[Consume]
    end

    subgraph Kafka ["Kafka topics"]
        T1[lmr.approved.v1]
        T2[lmr.withdraw.requested.v1]
        T3[lmr.withdraw.eligibility.v1]
        T4[lmr.withdraw.completed.v1]
        T5[lmr.withdraw.rejected.v1]
    end

    subgraph MECT ["MECT"]
        MECT_PUB[Publish]
        MECT_CONS[Consume]
    end

    LES_PUB -->|on Approve| T1
    LES_PUB -->|on Withdraw| T2
    T1 --> MECT_CONS
    T2 --> MECT_CONS

    MECT_PUB -->|after create LMR / flag change| T3
    MECT_PUB -->|withdrawal accepted| T4
    MECT_PUB -->|withdrawal rejected| T5
    T3 --> LES_CONS
    T4 --> LES_CONS
    T5 --> LES_CONS
```

---

## 5. Step-by-step flows for sequence diagrams

### 5a. Approval flow (create → approve → eligibility available)

| Step | Actor | Action | Notes for sequence diagram |
|------|--------|--------|-----------------------------|
| 1 | User | Create enrollment (form) | LES UI → LES API: POST /api/lmrs |
| 2 | LES API | Persist enrollment, status = DRAFT | Response 201 |
| 3 | User | Submit for approval | LES UI → LES API: POST .../submit |
| 4 | LES API | Set status = SUBMITTED | Response 200 |
| 5 | Admin | Approve enrollment | LES UI → LES API: POST .../approve |
| 6 | LES API | Set status = APPROVED, write outbox | Response 200 (enrollment) |
| 7 | LES (outbox job) | Publish lmr.approved.v1 to Kafka | Async |
| 8 | MECT | Consume lmr.approved.v1 | Create LMR, compute capacity |
| 9 | MECT | Publish lmr.withdraw.eligibility.v1 | canWithdraw=true (typically) |
| 10 | LES | Consume lmr.withdraw.eligibility.v1 | Update eligibility read-model |
| 11 | LES UI | Poll GET .../withdraw-eligibility | Sees canWithdraw=true, shows Withdraw button |

### 5b. Withdrawal — happy path (eligible, MECT accepts)

| Step | Actor | Action | Notes for sequence diagram |
|------|--------|--------|-----------------------------|
| 1 | User | Click Withdraw | LES UI → LES API: POST .../withdraw |
| 2 | LES API | Check local eligibility; if canWithdraw, set WITHDRAWN_REQUESTED, write outbox | Response 200 (enrollment) |
| 3 | LES (outbox job) | Publish lmr.withdraw.requested.v1 to Kafka | Async |
| 4 | MECT | Consume lmr.withdraw.requested.v1 | Check blocking flags |
| 5 | MECT | No flags → mark LMR withdrawn, publish lmr.withdraw.completed.v1 and eligibility (canWithdraw=false) | |
| 6 | LES | Consume lmr.withdraw.completed.v1 | Set status = WITHDRAWN |
| 7 | LES | Consume lmr.withdraw.eligibility.v1 | Update read-model |
| 8 | LES UI | Poll GET .../lmrs/{id} | Sees status WITHDRAWN, stops polling, shows success |

### 5c. Withdrawal — rejected (MECT has blocking flags)

| Step | Actor | Action | Notes for sequence diagram |
|------|--------|--------|-----------------------------|
| 1–3 | (same as 5b) | User withdraws, LES sets WITHDRAWN_REQUESTED, publishes lmr.withdraw.requested.v1 | |
| 4 | MECT | Consume lmr.withdraw.requested.v1 | Check blocking flags |
| 5 | MECT | Flags present → publish lmr.withdraw.rejected.v1 + eligibility (canWithdraw=false, reason) | |
| 6 | LES | Consume lmr.withdraw.rejected.v1 | Set status = WITHDRAW_REJECTED, store reason |
| 7 | LES | Consume lmr.withdraw.eligibility.v1 | Update read-model |
| 8 | LES UI | Poll GET .../lmrs/{id} | Sees WITHDRAW_REJECTED, shows rejection message |

### 5d. Withdrawal — blocked at LES (cached eligibility says no)

| Step | Actor | Action | Notes for sequence diagram |
|------|--------|--------|-----------------------------|
| 1 | User | Attempt withdraw (or API call) | LES UI → LES API: POST .../withdraw |
| 2 | LES API | Check local eligibility; canWithdraw=false | Response **409** with reason (no event published) |
| 3 | LES UI | Show error / no button | Withdraw button not shown when eligibility says no |

### 5e. Eligibility update (MECT flag toggled)

| Step | Actor | Action | Notes for sequence diagram |
|------|--------|--------|-----------------------------|
| 1 | Admin / system | Enable or disable blocking flag via MECT API | MECT API: POST .../flags/.../enable or .../disable |
| 2 | MECT | Recompute eligibility, publish lmr.withdraw.eligibility.v1 | canWithdraw + message |
| 3 | LES | Consume lmr.withdraw.eligibility.v1 | Update read-model |
| 4 | LES UI | Poll GET .../withdraw-eligibility | Button or message updates without refresh |

---

## 6. Simplified sequence-style flow (one diagram)

High-level order of operations; replace arrows with proper sequence diagram participants (User, LES UI, LES API, Kafka, MECT).

```mermaid
sequenceDiagram
    participant User
    participant LES_UI
    participant LES_API
    participant Kafka
    participant MECT

    User->>LES_UI: Create / Submit / Approve
    LES_UI->>LES_API: HTTP (create, submit, approve)
    LES_API->>LES_API: Update status, write outbox
    LES_API->>Kafka: lmr.approved.v1 (on approve)
    Kafka->>MECT: Consume approved
    MECT->>MECT: Create LMR, compute capacity
    MECT->>Kafka: lmr.withdraw.eligibility.v1
    Kafka->>LES_API: Consume eligibility
    LES_API->>LES_API: Update eligibility read-model
    LES_UI->>LES_API: GET withdraw-eligibility (poll)
    LES_API->>LES_UI: canWithdraw, message

    User->>LES_UI: Withdraw (if button shown)
    LES_UI->>LES_API: POST withdraw
    LES_API->>LES_API: Check eligibility, set WITHDRAWN_REQUESTED, outbox
    LES_API->>Kafka: lmr.withdraw.requested.v1
    Kafka->>MECT: Consume withdraw requested
    alt No blocking flags
        MECT->>Kafka: lmr.withdraw.completed.v1 + eligibility
    else Blocking flags
        MECT->>Kafka: lmr.withdraw.rejected.v1 + eligibility
    end
    Kafka->>LES_API: Consume completed or rejected
    LES_API->>LES_API: Update status (WITHDRAWN or WITHDRAW_REJECTED)
    LES_UI->>LES_API: GET enrollment (poll)
    LES_API->>LES_UI: Updated status
```

---

## 7. UI decision flow (what to show)

Use this when documenting the UI layer in a sequence or flow.

```mermaid
flowchart TD
    LOAD[Load enrollment detail]
    LOAD --> STATUS{Enrollment status?}

    STATUS -->|DRAFT| SHOW_SUBMIT[Show: Submit for approval]
    STATUS -->|SUBMITTED| SHOW_APPROVE[Show: Approve enrollment]
    STATUS -->|APPROVED| FETCH_EL[Fetch withdraw-eligibility]
    STATUS -->|WITHDRAWN_REQUESTED| SHOW_PENDING[Show: Withdrawal requested, polling…]
    STATUS -->|WITHDRAWN| SHOW_DONE[Show: Withdrawal completed]
    STATUS -->|WITHDRAW_REJECTED| SHOW_REJ[Show: Rejected + reason]

    FETCH_EL --> EL{eligibility.canWithdraw?}
    EL -->|true| SHOW_WITHDRAW[Show: Withdraw button]
    EL -->|false or null| SHOW_MSG[Show: MECT message, no button]

    POLL[Poll every 2s when APPROVED or WITHDRAWN_REQUESTED]
    POLL --> REFRESH[Refresh enrollment + eligibility]
    REFRESH --> STATUS
```

---

## File location

- **Path**: `docs/integration-flow-diagrams.md`
- Render in any Mermaid-capable viewer (e.g. GitHub, GitLab, VS Code with Mermaid extension, or [mermaid.live](https://mermaid.live)).
