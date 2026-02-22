# LES UI – Locational Enrollment Service

Simple, clean Angular UI for the LES (Locational Enrollment Service) PoC. It follows **MISO-inspired UX**: professional, clear hierarchy, and a restrained color palette suitable for utility/energy-sector workflows.

## Features

- **Enrollment list** – View all LMR enrollments with status; link to detail.
- **New enrollment** – Create a new enrollment (DRAFT) with LMR ID, name, participant, resource type, and planning year.
- **Enrollment detail** – View enrollment and perform workflow actions:
  - **Submit** (when DRAFT) – Submit for approval.
  - **Approve** (when SUBMITTED) – Approve enrollment (publishes to MECT via Kafka).
  - **Withdraw** – Shown only when eligibility says `canWithdraw` is true; otherwise a message from MECT is displayed (no button).
- **Admin – Withdrawal rejections** – List enrollments where the user requested withdrawal but MECT rejected (edge case visibility).

---

## Workflow (enrollment lifecycle)

The UI drives a single, linear workflow per enrollment:

| Step | User action | Enrollment status | What the UI shows |
|------|-------------|--------------------|-------------------|
| 1 | Create enrollment (New enrollment form) | **DRAFT** | “Submit for approval” button |
| 2 | Submit for approval | **SUBMITTED** | “Approve enrollment” button |
| 3 | Approve enrollment | **APPROVED** | Withdraw section: either “Withdraw” button (if eligible) or MECT message (if not), plus “Checking eligibility periodically.” while polling |
| 4a | Withdraw (when eligible) | **WITHDRAWN_REQUESTED** | “Withdrawal requested. Waiting for MECT to respond…” and “Checking status automatically.” |
| 4b | MECT completes | **WITHDRAWN** | “Withdrawal completed.” success message |
| 4c | MECT rejects | **WITHDRAW_REJECTED** | “Withdrawal rejected by MECT” and stored reason |

Eligibility (whether the user can withdraw) comes from **MECT via Kafka**: LES keeps a read-model and exposes it as `GET /api/lmrs/{id}/withdraw-eligibility`. The UI never calls MECT directly.

---

## How the UI prevents race conditions

- **Single source of truth after an action**  
  After Submit, Approve, or Withdraw, the UI updates **only from the API response** for that action. It does not immediately refetch enrollment; that would risk a refetch returning stale status (e.g. still SUBMITTED) and hiding the next action (e.g. Approve) or showing the wrong one. So the correct status and next action appear as soon as the response returns, without a full page reload.

- **Eligibility from LES, not MECT**  
  The Withdraw button and “cannot withdraw” message use the LES eligibility endpoint (LES’s cached read-model from Kafka). There is no synchronous “check with MECT then act” on each click. If eligibility is not yet available (e.g. right after approval), the LES API returns **200** with a default (`canWithdraw: false`, message “Eligibility not yet available from MECT.”), so the UI never gets a 404 and can show a sensible state until MECT’s eligibility event is consumed.

- **No double submit**  
  While an action is in progress, `actionLoading` is true and the action buttons are disabled. The user cannot trigger Submit, Approve, or Withdraw twice before the first response returns.

- **Polling in one place**  
  When the enrollment is in a state that can change (**APPROVED** or **WITHDRAWN_REQUESTED**), the detail screen runs a **single polling loop** (every 2 seconds) that:
  - Refetches enrollment (so status changes like WITHDRAWN or WITHDRAW_REJECTED appear).
  - Refetches eligibility when status is APPROVED / WITHDRAWN_REQUESTED / etc., so when MECT publishes new eligibility, the Withdraw button or message updates without a refresh.  
  Polling stops when status is no longer APPROVED or WITHDRAWN_REQUESTED (e.g. terminal WITHDRAWN or WITHDRAW_REJECTED). All of this is handled in one place (`startPolling` / `tick` / `stopPolling`), so there is no competing or duplicate polling logic.

- **Change detection after async updates**  
  After setting `enrollment` or `eligibility` from an API response (or from the polling tick), the UI calls `ChangeDetectorRef.detectChanges()` so the template updates immediately and the correct action or message is shown without requiring a user interaction or refresh.

---

## How the UI decides what action to display

The Actions section is **status-driven**, with **eligibility** gating only the Withdraw action:

- **DRAFT** → Show **Submit for approval** only.
- **SUBMITTED** → Show **Approve enrollment** only.
- **APPROVED** → Show the **Withdraw** block:
  - If **eligibility?.canWithdraw** is true → Show **Withdraw** button and “Eligible to withdraw. Request will be sent to MECT.”
  - If **eligibility** is set and **canWithdraw** is false → Show “Withdraw button is not shown when eligibility says you cannot withdraw” and the MECT message above in an info alert.
  - If eligibility is not yet loaded or not available → No Withdraw button; the info message appears when the API returns the default or real eligibility. While polling, “Checking eligibility periodically.” is shown.
- **WITHDRAWN_REQUESTED** → Show “Withdrawal requested. Waiting for MECT to respond…” and, while polling, “Checking status automatically.”
- **WITHDRAWN** → Show success message only.
- **WITHDRAW_REJECTED** → Show “Withdrawal rejected by MECT” and the stored reason.

So: **status** determines which “lane” (Submit / Approve / Withdraw / messages) is shown; **eligibility** only controls whether the Withdraw **button** is shown or the “cannot withdraw” message. Polling keeps both status and eligibility up to date so the right action and message are always shown.

## Prerequisites

- **Node.js** 20+ and **npm** (or use the Node version that matches your Angular CLI).
- **LES API** running (e.g. `cd les-service && mvn spring-boot:run` on port **8081**).

## Setup

```bash
cd les-ui
npm install
```

## Run (development)

```bash
npm start
```

The app is served at **http://localhost:4200**. It calls the LES API at **http://localhost:8081/api** (see `src/environments/environment.ts`). Ensure LES is running and CORS is enabled for `http://localhost:4200`.

## Build (production)

```bash
npm run build
```

Output is in `dist/les-ui`. For production, set `apiUrl` in `src/environments/environment.prod.ts` (default is `/api` for same-origin deployment).

## Project structure

```
les-ui/
├── src/
│   ├── app/
│   │   ├── admin/           # Admin views (e.g. withdraw rejections)
│   │   ├── enrollments/     # List, create, detail components
│   │   ├── services/        # LES API client
│   │   ├── app.component.ts
│   │   ├── app.config.ts
│   │   └── app.routes.ts
│   ├── environments/        # apiUrl for dev vs prod
│   ├── index.html
│   ├── main.ts
│   └── styles.scss          # MISO-style globals (colors, cards, buttons)
├── angular.json
├── package.json
└── README.md
```

## MISO-style UX

- **Header** – Dark blue (`#003366`) with logo “LES” and nav (Enrollments, New enrollment, Admin).
- **Content** – Light gray background, white cards, clear typography (Inter).
- **Status badges** – Color-coded by enrollment status (DRAFT, SUBMITTED, APPROVED, WITHDRAWN, etc.).
- **Actions** – Primary (blue) and secondary (outline) buttons; Withdraw only when eligibility allows, with message from MECT when it does not.

## Configuration

- **API base URL (dev):** `src/environments/environment.ts` → `apiUrl: 'http://localhost:8081/api'`.
- **API base URL (prod):** `src/environments/environment.prod.ts` → `apiUrl: '/api'` (adjust if you host the API elsewhere).

## Tech stack

- **Angular 19** (standalone components, reactive forms, `HttpClient`).
- **RxJS** for API calls.
- **SCSS** for MISO-style global styles (no component libraries; minimal, clean UI).
