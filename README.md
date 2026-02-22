# MISO Energy LES / MECT PoC

Proof of Concept integration between **LES** (Locational Enrollment Service) and **MECT** (Module E Capacity Tracking) using **Kafka** for eventing and distributed state synchronization. This PoC shows how LMR enrollment approval and withdrawal are coordinated without synchronous back-and-forth calls, with minimal race conditions and MECT as the authoritative system.

---

## Architecture Overview

```
                    +------------------+                    +------------------+
                    |                  |   lmr.approved.v1   |                  |
                    |   LES (8081)     | -----------------> |   MECT (8082)    |
                    |   Load           |                    |   Module E       |
                    |   Enrollment     |   lmr.withdraw.     |   Capacity       |
                    |   System         |   requested.v1     |   Tracking       |
                    |                  | -----------------> |                  |
                    |                  |                    |                  |
                    |  Read-model:     |   lmr.withdraw.    |  Source of       |
                    |  eligibility     | <----------------- |  truth: LMR      |
                    |  (from Kafka)    |   eligibility.v1   |  state, flags,   |
                    |                  |   completed.v1     |  seasonal cap    |
                    |                  |   rejected.v1      |                  |
                    +--------+---------+                    +------------------+
                             |                                        |
                             |                                        |
                             v                                        v
                    +------------------+                    +------------------+
                    |  Postgres (lesdb)|                    |  Postgres (mectdb)|
                    |  :5432           |                    |  :5433           |
                    +------------------+                    +------------------+
                             ^                                        ^
                             |                                        |
                    +--------+----------------------------------------+--------+
                    |                    Kafka (:9092)                           |
                    |  Topics: lmr.approved.v1, lmr.withdraw.requested.v1,       |
                    |          lmr.withdraw.completed.v1, lmr.withdraw.rejected.v1|
                    |          lmr.withdraw.eligibility.v1 (log-compacted)        |
                    +----------------------------------------------------------------+
```

### Why Eligibility Is a Read-Model

- **MECT** is the authority for “can this LMR be withdrawn?” (blocking flags, status).
- **LES** needs to show a “Withdraw” button and block invalid attempts **without** calling MECT on every click (no synchronous RPC).
- **Eligibility** is therefore published by MECT on Kafka (`lmr.withdraw.eligibility.v1`). LES consumes these events and maintains a **local read-model** (table) of the latest eligibility per `planningYear:lmrId`.
- The topic is **log-compacted**, so the full state can be rebuilt by consuming from the topic from the beginning.

### Why MECT Is Authoritative

- Only MECT creates LMRs (from `lmr.approved.v1`) and stores seasonal capacity and blocking flags.
- Withdrawal is **decided** in MECT: it consumes `lmr.withdraw.requested.v1` and publishes either `lmr.withdraw.completed.v1` or `lmr.withdraw.rejected.v1`.
- LES only **requests** withdrawal and then **reconciles** its local state when it receives the result event. This avoids races: if LES thought “can withdraw” but MECT later rejects (e.g. flag was added), the rejection event brings LES back in sync.

### How Race Conditions Are Handled

- **No synchronous “check then act”** between LES and MECT. LES uses its **cached eligibility** to decide whether to show the button and whether to allow the withdraw request (409 if cached eligibility says no).
- **Final authority**: MECT may still reject (e.g. a flag was added after the eligibility event). When that happens, MECT publishes `lmr.withdraw.rejected.v1` and updates eligibility to `canWithdraw=false`.
- **Reconciliation**: LES consumes completed/rejected events and updates enrollment status (and optionally eligibility cache). So even in a “race” (withdraw + flag added), the outcome is consistent: rejection event and updated eligibility.

### Event Flow (Sequence)

1. **Approval**
   - User enrolls LMR in LES → admin approves → LES sets status APPROVED and publishes `lmr.approved.v1`.
   - MECT consumes, creates LMR, computes seasonal capacity, publishes eligibility `canWithdraw=true`.

2. **Withdrawal (happy path)**
   - User clicks Withdraw in LES. LES checks **local** eligibility; if `canWithdraw=true`, LES sets status WITHDRAWN_REQUESTED and publishes `lmr.withdraw.requested.v1`.
   - MECT consumes, checks flags; if none, marks LMR WITHDRAWN, publishes `lmr.withdraw.completed.v1` and eligibility `canWithdraw=false`.
   - LES consumes completed, sets status WITHDRAWN.

3. **Withdrawal (blocked)**
   - Same as above, but MECT has blocking flags → publishes `lmr.withdraw.rejected.v1` and eligibility `canWithdraw=false` with reason.
   - LES consumes rejected, sets status WITHDRAW_REJECTED and stores reason for UX.

4. **Eligibility updates**
   - When MECT enables/disables a blocking flag (e.g. via demo APIs), it recomputes eligibility and publishes to `lmr.withdraw.eligibility.v1`.
   - LES consumes and updates its local eligibility table so the UI stays in sync.

### Withdrawal: immediate feedback and edge case

- **Standard workflow**: The UI only shows the Withdraw button when eligibility says `canWithdraw=true`. So the user gets immediate feedback from the read-model; if they cannot withdraw, the button does not render and the message from MECT is shown. Withdrawal is not part of the standard path when ineligible.
- **Edge case**: If the button is shown and then MECT state changes before the user clicks (e.g. a blocking flag is added), the user can still submit a withdraw request. MECT will reject and publish `lmr.withdraw.rejected.v1`. LES does **not** handle this in the standard workflow with retries or automatic reconciliation; it records the rejection on the enrollment and makes it **visible to admins** so they can act (e.g. contact user, resolve the blocker, or later build automatic reconciliation if production sees enough of these).

**Admin visibility**: `GET /api/admin/withdraw-rejections` returns all enrollments in status `WITHDRAW_REJECTED` (lmrId, planningYear, message from MECT, `withdrawRejectedAt`). Admins can use this to identify when the edge case occurred and decide next steps. If many such rejections appear in production, teams may add features to reconcile or refresh eligibility more aggressively.

### User workflow (LES UI)

The **LES UI** (Angular app at `les-ui/`) drives the full lifecycle from the browser:

1. **Create** – User creates a new enrollment (LMR ID, name, participant, resource type, planning year). Status: **DRAFT**.
2. **Submit** – User clicks “Submit for approval”. Status: **SUBMITTED**.
3. **Approve** – Admin clicks “Approve enrollment”. LES sets status **APPROVED** and publishes `lmr.approved.v1`; MECT creates the LMR and later publishes eligibility.
4. **Withdraw (when eligible)** – Once MECT has sent eligibility with `canWithdraw=true`, the UI shows a “Withdraw” button. User clicks it; LES sets **WITHDRAWN_REQUESTED** and publishes `lmr.withdraw.requested.v1`. MECT processes and publishes either completed or rejected; LES consumes and shows **WITHDRAWN** or **WITHDRAW_REJECTED** with the reason.

The UI avoids race conditions and keeps the right action visible by:

- **Single source of truth after actions** – After Submit / Approve / Withdraw, the UI updates from the **API response** only (no immediate refetch that could return stale status). That way the correct status and next action appear without a refresh.
- **Eligibility from LES cache** – The Withdraw button and “cannot withdraw” message use `GET /api/lmrs/{id}/withdraw-eligibility` (LES read-model from Kafka). There is no synchronous call to MECT on each click; the backend returns 200 with a default when eligibility is not yet available, so the UI never 404s.
- **Polling for live state** – When status is **APPROVED** or **WITHDRAWN_REQUESTED**, the detail screen polls every 2 seconds for enrollment and eligibility. So when MECT publishes eligibility (or completed/rejected), the Withdraw button and messages update automatically. Polling stops when status becomes a terminal state (e.g. WITHDRAWN, WITHDRAW_REJECTED).
- **No double submit** – Buttons are disabled while `actionLoading` is true, so the user cannot trigger the same action twice before the first response returns.

See **`les-ui/README.md`** for a detailed description of the workflow, how the UI prevents race conditions, and how it decides which action to display.

---

## Quick Start

### Option A: Start everything with Docker Compose (easiest)

From the **repo root**:

```bash
docker compose up -d
```

This starts: Zookeeper, Kafka, Postgres (LES + MECT), LES service, MECT service, LES UI, and Kafka UI. Wait a minute or two for services to be healthy, then:

- **LES UI**: http://localhost:4200 (use this to manage enrollments)
- **LES API**: http://localhost:8081
- **MECT API**: http://localhost:8082
- **Kafka UI**: http://localhost:8080

Create Kafka topics (see step 2 below) before using the full workflow.

### Option B: Start infrastructure only, run apps locally

### 1. Start infrastructure

```bash
docker compose -f infra/docker-compose.yml up -d
```

Wait until Kafka and both Postgres instances are healthy. Optional: [Kafka UI](http://localhost:8080).

### 2. Create Kafka topics (eligibility must be log-compacted)

From the project root, run inside the Kafka container:

```bash
docker compose -f infra/docker-compose.yml exec kafka kafka-topics --bootstrap-server localhost:29092 --create --if-not-exists --topic lmr.approved.v1 --partitions 1 --replication-factor 1
docker compose -f infra/docker-compose.yml exec kafka kafka-topics --bootstrap-server localhost:29092 --create --if-not-exists --topic lmr.withdraw.requested.v1 --partitions 1 --replication-factor 1
docker compose -f infra/docker-compose.yml exec kafka kafka-topics --bootstrap-server localhost:29092 --create --if-not-exists --topic lmr.withdraw.completed.v1 --partitions 1 --replication-factor 1
docker compose -f infra/docker-compose.yml exec kafka kafka-topics --bootstrap-server localhost:29092 --create --if-not-exists --topic lmr.withdraw.rejected.v1 --partitions 1 --replication-factor 1
docker compose -f infra/docker-compose.yml exec kafka kafka-topics --bootstrap-server localhost:29092 --create --if-not-exists --topic lmr.withdraw.eligibility.v1 --partitions 1 --replication-factor 1 --config cleanup.policy=compact --config min.cleanable.dirty.ratio=0.01
```

Or run the script inside the container:

```bash
docker compose -f infra/docker-compose.yml exec kafka sh -c "KAFKA_BOOTSTRAP=localhost:29092 /path not used; paste script lines if needed"
```

(Use the five `kafka-topics` commands above for reliability.)

### 3. Run LES and MECT

```bash
# Terminal 1
cd les-service && mvn spring-boot:run

# Terminal 2
cd mect-service && mvn spring-boot:run
```

- **LES**: http://localhost:8081 (Swagger: http://localhost:8081/swagger-ui.html)
- **MECT**: http://localhost:8082 (Swagger: http://localhost:8082/swagger-ui.html)

### 4. (Optional) Run LES UI (when not using Option A)

```bash
cd les-ui && npm install && npm start
```

- **LES UI**: http://localhost:4200 — MISO-style Angular app to manage enrollments and simulate the workflow. See `les-ui/README.md` for details.

---

## Curl Demo Walkthrough

Use `LMR_ID` and `PLANNING_YEAR` consistently (e.g. `LMR-001`, `2026`).

### 1) Create and approve LMR (LES)

```bash
# Create
curl -s -X POST http://localhost:8081/api/lmrs \
  -H "Content-Type: application/json" \
  -d '{"lmrId":"LMR-001","marketParticipantName":"Acme","lmrName":"Site A","resourceType":"LMR_DR","planningYear":"2026"}' | jq .

# Submit
curl -s -X POST http://localhost:8081/api/lmrs/LMR-001/submit | jq .

# Approve (publishes lmr.approved.v1; MECT will create LMR and publish eligibility)
curl -s -X POST http://localhost:8081/api/lmrs/LMR-001/approve | jq .
```

Wait a couple of seconds for MECT to consume and publish eligibility, then:

```bash
# Check eligibility (from LES cache)
curl -s http://localhost:8081/api/lmrs/LMR-001/withdraw-eligibility | jq .
# Expect: "canWithdraw": true
```

### 2) Block withdrawal via flag (MECT)

```bash
# Enable a blocking flag (MECT publishes updated eligibility)
curl -s -X POST "http://localhost:8082/api/mect/lmrs/2026/LMR-001/flags/ZRC_TRANSACTION_EXISTS/enable" | jq .
```

### 3) Attempt withdrawal (blocked by LES using cache)

```bash
curl -s -X POST http://localhost:8081/api/lmrs/LMR-001/withdraw
# Expect: HTTP 409 with reason (blocking flags)
```

After a short delay, eligibility in LES is updated from Kafka; the same withdraw call would still return 409.

### 4) Remove flag (MECT)

```bash
curl -s -X POST "http://localhost:8082/api/mect/lmrs/2026/LMR-001/flags/ZRC_TRANSACTION_EXISTS/disable" | jq .
```

Wait for eligibility event to be consumed by LES, then:

```bash
curl -s http://localhost:8081/api/lmrs/LMR-001/withdraw-eligibility | jq .
# Expect: "canWithdraw": true
```

### 5) Withdraw successfully

```bash
curl -s -X POST http://localhost:8081/api/lmrs/LMR-001/withdraw | jq .
# Expect: status "WITHDRAWN_REQUESTED"

# After MECT processes (1–2 s), get enrollment again
curl -s http://localhost:8081/api/lmrs/LMR-001 | jq .
# Expect: status "WITHDRAWN"
```

### 6) Simulated race: withdraw + flag added → rejection

Create a second LMR, approve it, then:

- In MECT, add a blocking flag for that LMR.
- In LES, call withdraw (either before or after the eligibility event arrives). If LES still had `canWithdraw=true`, it will send `lmr.withdraw.requested.v1`.
- MECT receives the request, sees the flag, and publishes `lmr.withdraw.rejected.v1`.
- LES consumes the rejection and sets status to WITHDRAW_REJECTED and stores the reason.

This shows that **MECT is the final authority** and reconciliation via events keeps LES in sync.

---

## Repo Layout

```
les-mect-poc/
├── README.md
├── docker-compose.yml       # One command: infra + LES + MECT + LES UI
├── infra/
│   ├── docker-compose.yml    # Kafka, Zookeeper, lesdb, mectdb, Kafka UI
│   └── init-kafka-topics.sh   # Optional: topic creation (run inside Kafka container)
├── les-service/               # Spring Boot 3.x, Java 21
│   ├── pom.xml
│   └── src/...
├── les-ui/                    # Angular 19 – LES workflow UI (MISO-style)
│   ├── README.md
│   ├── package.json
│   └── src/...
└── mect-service/              # Spring Boot 3.x, Java 21
    ├── pom.xml
    └── src/...
```

---

## Kafka Topics

| Topic | Purpose | Compaction |
|-------|---------|------------|
| `lmr.approved.v1` | LES → MECT: enrollment approved | No |
| `lmr.withdraw.requested.v1` | LES → MECT: user requested withdrawal | No |
| `lmr.withdraw.completed.v1` | MECT → LES: withdrawal accepted | No |
| `lmr.withdraw.rejected.v1` | MECT → LES: withdrawal rejected | No |
| `lmr.withdraw.eligibility.v1` | MECT → LES: canWithdraw + reason + blockingFlags | **Yes** (log-compacted) |

---

## Admin: withdrawal rejections (edge case)

- **`GET /api/admin/withdraw-rejections`** (LES): Returns enrollments in status `WITHDRAW_REJECTED` (user had requested withdraw; MECT rejected). Each item includes `lmrId`, `planningYear`, `message` (from MECT), `withdrawRejectedAt`. Use this to see when the “button was shown, then state changed in MECT” case occurred and act (or plan automatic reconciliation if it becomes frequent in production).

---

## Tech Stack

- **Java 21**, **Spring Boot 3.2.x**
- **Spring Kafka**, **Spring Data JPA**, **PostgreSQL**
- **Flyway** for DB migrations
- **Springdoc OpenAPI** (Swagger) on both services
- **Outbox pattern** for publishing (table + scheduled job); **idempotency** via processed event IDs

---

## Observability

- **Actuator**: `GET /actuator/health` on both services.
- **Structured logs**: Include `lmrId` and `eventId` where relevant for tracing.

---

## Quality Notes

- **Package structure**: `domain`, `repository`, `service`, `web`, `kafka`, `outbox`, `idempotency`, `events`.
- **Naming**: Clear REST paths and event types; DTOs aligned with topic payloads.
- **Comments**: Key decisions (authority, read-model, reconciliation) documented in code and README.
