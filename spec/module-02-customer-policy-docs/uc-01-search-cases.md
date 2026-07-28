# Module 2 · Customer Policy — UC 01 · Search Cases

> AI implementation brief, generated from the v5 spec (`spec/.../use-cases/`). Source of truth is the spec; regenerate, don't hand-edit.

## Context

- Module: 2 · Customer Policy · category Rule · domain `policy` · command `check-policy` · outcomes: APPROVED, REFERRED, REJECTED
- Use case: 01 · Search Cases · track A · prerequisite: after 00 — the rows it lists come from intake · build shape: DB→API→FE · primary screen: Policy Board
- Data effect: read-only · zero payload stored
- Platform rules (non-negotiable): the orchestrator is the only caller; the whole application arrives in the envelope (plus the v5 `outputs` block, Option A); steps are independent and re-orderable; the payload is NEVER stored — only `applicationId`; every module ships `GET /cases/{id}/applicant` proxying the orchestrator; big lists are empty by default and capped at 10 rows (≤10 hydration calls); ALL APIs are idempotent — same request twice, same result once; every endpoint appears in the service's OpenAPI 3.0 (Swagger) spec.

## Story

As a bank employee I want to find a policy case by application id or applicant name — without this module ever storing applicant data.

## Contract

```
GET /cases?q={id-or-name}&limit=10 →
[{"applicationId":"app-1234",
  "submittedAt":"2026-07-21T21:40:00Z",
  "outcome":"APPROVED","sampled":false,
  "reasonCount":1}, …]
// applicantName is NOT in the row —
// the UI hydrates ≤10 rows live
```

## Acceptance criteria

1. The Policy Board is EMPTY by default — no query, no rows fetched, a hint invites a search.
2. GET /cases?q=… → 200 + at most 10 matches, newest first; an 11th match means the response flags "more — refine your search".
3. Search by applicationId works entirely from the local table; search by name resolves ids through the orchestrator first — nothing about the applicant is ever persisted.
4. The applicant-name column hydrates live via the module's application-fetch GET — at most 10 calls per render, cached per page view.
5. Searching "Maria" shows her case with outcome = "APPROVED" and no sampled badge.  ⟵ **checkpoint — exact value**
6. Searching "Sofia" shows her case with outcome = "REJECTED".  ⟵ **checkpoint — exact value**
7. No matches → [] and HTTP 200 (never a 500); orchestrator down → rows render with ids, names show a retryable "—".

## Expected data changes

- **No rows change, and no applicant data ever lands in MySQL.** The schema has no name column to search.
- Name → ids via the orchestrator; ids → local records; names on screen via ≤10 live hydration calls.
- Empty by default + LIMIT 10 is what keeps the hydration bill bounded — never 100s of requests.

## Diagrams

> Rendered images first (for PDF/preview); the mermaid source is collapsed underneath for machine consumption.

### Sequence — this use case

![Sequence — this use case](diagrams/uc-01-sequence.jpg)

<details><summary>mermaid source</summary>

```mermaid
sequenceDiagram
    autonumber
    participant UI
    participant Controller
    participant Service
    participant MySQL
    participant Orchestrator
    UI->>Controller: GET /cases?q=Maria&limit=10
    Controller->>Service: search(q, 10)
    Service->>Orchestrator: name → ids
    Service->>MySQL: SELECT id IN (…) LIMIT 10
    MySQL-->>Service: ≤ 10 rows
    Controller-->>UI: 200 — ids only
    UI->>Controller: ≤10 × GET …/applicant
```

</details>

### Entity model (suggested — the shape to beat)

![Entity model](diagrams/er-suggested.jpg)

**PolicyRecord — one row per applicationId (unique)**

| field | type | key | meaning |
|---|---|---|---|
| applicationId | string | PK | the journey key from the envelope — one row per application, and the ONLY applicant-related column |
| outcome | enum |  | the final answer: APPROVED, REFERRED or REJECTED — starts equal to machineOutcome; only a human decision changes it |
| machineOutcome | enum |  | what rules 1–3 computed before sampling — shown to the reviewer, never changed |
| reference | string |  | human-facing case reference shown on every screen, e.g. pol-000187 |
| policyConfigVersion | int | FK | the PolicyConfig version that decided this case — pinned forever, never re-pointed |
| ruleResults | JSON |  | embedded results of the four checks — existingProduct (with registryChecked), taxResidency (with matchedList), restrictionList, sampling (sampled + position) |
| claimedBy | string, nullable |  | referral queue — the operator who claimed the case |
| claimedAt | timestamp, nullable |  | referral queue — when it was claimed |
| decidedBy | string, nullable |  | who made the human decision (queue or override) — null means the machine's answer stands |
| decidedAt | timestamp, nullable |  | when the human decision was made |
| decisionReason | string, nullable |  | the mandatory reason recorded with every human decision |
| submittedAt | timestamp |  | when the orchestrator submitted the case |

**PolicyConfig — insert-only, versioned lists; the current version is the highest**

| field | type | key | meaning |
|---|---|---|---|
| version | int | PK | one new row per change — rows are inserted, never updated |
| supportedResidencies | string[] |  | rule 2 — at least one tax residency must be on this list, else REJECTED (seeded GB, IE, PL, DE, FR, ES, NL) |
| excludedResidencies | string[] |  | rule 2 — any residency on this list REJECTS, even beside a supported one (seeded US) |
| restrictionList | JSON |  | rule 3 — the bank's own blocked people as {fullName, dateOfBirth, reason} entries (seeded Victor Sable and Dana Kovacs) |
| sampleEvery | int |  | rule 4's X — every Xth first-time decision is REFERRED for human review (seeded 7) |
| effectiveFrom | timestamp |  | when this version became the current one |

**OverrideLog — audit trail; one row per manual override, none ever deleted**

| field | type | key | meaning |
|---|---|---|---|
| applicationId | string | FK | the case that was overridden |
| oldOutcome | enum |  | the outcome before the override |
| newOutcome | enum |  | the outcome after the override |
| reason | string |  | the mandatory justification typed by the operator |
| operator | string |  | who performed the override |
| overriddenAt | timestamp |  | when it happened |

Relationships: PolicyRecord N:1 PolicyConfig — each decision pins the version it used · PolicyRecord 1:N OverrideLog — every override is audited to its case

<details><summary>mermaid source (generated from the spec tables)</summary>

```mermaid
flowchart LR
    PolicyRecord["<b>PolicyRecord</b><br/>————————<br/>applicationId (PK)<br/>outcome<br/>machineOutcome<br/>reference<br/>policyConfigVersion (FK)<br/>ruleResults<br/>claimedBy<br/>claimedAt<br/>decidedBy<br/>decidedAt<br/>decisionReason<br/>submittedAt"]
    PolicyConfig["<b>PolicyConfig</b><br/>————————<br/>version (PK)<br/>supportedResidencies<br/>excludedResidencies<br/>restrictionList<br/>sampleEvery<br/>effectiveFrom"]
    OverrideLog["<b>OverrideLog</b><br/>————————<br/>applicationId (FK)<br/>oldOutcome<br/>newOutcome<br/>reason<br/>operator<br/>overriddenAt"]
    PolicyRecord -->|"each decision pins the version it used (N:1)"| PolicyConfig
    PolicyRecord -->|"every override is audited to its case (1:N)"| OverrideLog
    classDef ent fill:#ffffff,stroke:#2EA98D,color:#22302B
    class PolicyRecord ent
    class PolicyConfig ent
    class OverrideLog ent
```

</details>

### State transitions — the case record

![State transitions — the case record](diagrams/case-states.jpg)

<details><summary>mermaid source</summary>

```mermaid
stateDiagram-v2
    direction LR
    [*] --> IN_PROGRESS : /execute accepted (202)
    IN_PROGRESS --> APPROVED : all rules pass
    IN_PROGRESS --> REJECTED : any rejection
    IN_PROGRESS --> REFERRED : every 7th sampled (wins) · registry down
    REFERRED --> APPROVED : queue decision
    REFERRED --> REJECTED : queue decision
    APPROVED --> REJECTED : override
    REJECTED --> APPROVED : override
    note right of REFERRED
        sampling stores machineOutcome —
        the reviewer ALWAYS sees the machine's answer
        queue decision / override = operator + mandatory reason
        → override_log / decision trace
        → callback local-manual, journey resumes
    end note
    classDef ok fill:#ffffff,stroke:#1F8A5D,color:#1F8A5D,font-weight:bold
    classDef warn fill:#ffffff,stroke:#B7791F,color:#B7791F,font-weight:bold
    classDef bad fill:#ffffff,stroke:#B3403A,color:#B3403A,font-weight:bold
    classDef trans fill:#ECF6F1,stroke:#4A635B,color:#22302B
    class APPROVED ok
    class REFERRED warn
    class REJECTED bad
    class IN_PROGRESS trans
```

</details>

## Out of scope

Pagination beyond the 10-row cap (refine the search instead); free browsing of the whole book.

## Build notes

v5 payload rule: no applicant columns exist, so name search resolves ids via the orchestrator (GET /applications?name= — a v5 contract addition) and id search hits the local table. The board hydrates the applicant-name column live, one GET per visible row, 10 max. The sampled flag comes straight off ruleResults.sampling — the board badges forced-review rows.

## Tests

Web-slice test: empty default, 10-row cap, id search local, name search via mocked orchestrator client, empty [] case.

## Definition of done

All acceptance criteria verified against the running app · `./mvnw test` green · teammate-reviewed PR merged to main · fresh clone builds green · endpoint idempotent + present in the OpenAPI 3.0 (Swagger) spec · demoable from the UI.
