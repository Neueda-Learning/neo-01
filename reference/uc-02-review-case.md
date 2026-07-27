# Module 1 · Application Verification — UC 02 · Review Case

> AI implementation brief, generated from the v5 spec (`spec/.../use-cases/`). Source of truth is the spec; regenerate, don't hand-edit.

## Context

- Module: 1 · Application Verification · category Rule · domain `verification` · command `verify-application` · outcomes: PASSED, FAILED, REVIEW
- Use case: 02 · Review Case · track B · prerequisite: after 00 + 06 — the engine decides against ProductConfig · build shape: API+FE (engine: DB) · primary screen: Case Detail
- Data effect: read-only (row written earlier)
- Platform rules (non-negotiable): the orchestrator is the only caller; the whole application arrives in the envelope (plus the v5 `outputs` block, Option A); steps are independent and re-orderable; the payload is NEVER stored — only `applicationId`; every module ships `GET /cases/{id}/applicant` proxying the orchestrator; big lists are empty by default and capped at 10 rows (≤10 hydration calls); ALL APIs are idempotent — same request twice, same result once; every endpoint appears in the service's OpenAPI 3.0 (Swagger) spec.

## Story

As a bank employee I want to open a case and see its outcome and full rule breakdown, so I understand exactly why it decided what it decided.

## Contract

```
GET /cases/{applicationId} →
{"outcome":"FAILED","reference":"ver-000123",
 "productConfigVersion":3,
 "ruleResults":[{"ruleName":"age",
   "passed":false,
   "reasonCodes":["VER_AGE_BELOW_MINIMUM"]},…]}
```

## Acceptance criteria

1. GET /cases/{applicationId} → 200 + outcome, reference, productConfigVersion, ruleResults[] (4 sections: wellFormedness, age, productActive, channel — each passed + reasonCodes[]).
2. Maria Nowak (app-1234) → PASSED, all four sections passed, reasons = [VER_ALL_CHECKS_PASSED].  ⟵ **checkpoint — exact value**
3. Priya Raman, 17, STUDENT card → FAILED with VER_AGE_BELOW_MINIMUM on the age section.  ⟵ **checkpoint — exact value**
4. Tom Bright's incomplete form → EVERY missing/malformed field its own VER_MISSING_FIELD/VER_INVALID_FIELD reason with a field dot-path — never just the first.
5. Age exactly at minAge → REVIEW (VER_AGE_EXACT_MINIMUM); boundary REVIEW outranks a simultaneous hard failure.
6. Repeated /execute for the same applicationId → still one row, rules NOT re-run, callback replays the stored outcome.
7. Unknown applicationId → 404 with a JSON error body (never a 500).

## Expected data changes

- **This GET changes nothing.** The row it reads was written once, off-thread, by /execute.
- On /execute: INSERT verification_record (outcome, ruleResults JSON, productConfigVersion pinned).
- Unique key on application_id is what makes the idempotency AC provable.

## Diagrams

> Rendered images first (for PDF/preview); the mermaid source is collapsed underneath for machine consumption.

### Sequence — this use case

![Sequence — this use case](diagrams/uc-02-sequence.jpg)

<details><summary>mermaid source</summary>

```mermaid
sequenceDiagram
    autonumber
    participant UI
    participant Controller
    participant Service
    participant MySQL
    UI->>Controller: GET /cases/app-1235
    Controller->>Service: getCase(applicationId)
    Service->>MySQL: SELECT … WHERE application_id = ?
    MySQL-->>Service: row + embedded ruleResults JSON
    Service-->>Controller: CaseDetailDto (4 rule sections)
    Controller-->>UI: 200 OK — outcome + breakdown
    Note over UI,MySQL: The engine runs at /execute time, not at read time — reviewing a case replays stored results, it never re-decides.
```

</details>

### Entity model (suggested — the shape to beat)

![Entity model](diagrams/er-suggested.jpg)

**VerificationRecord — one row per applicationId (unique)**

| field | type | key | meaning |
|---|---|---|---|
| applicationId | string | PK | the journey key from the envelope — one row per application, and the ONLY applicant-related column |
| outcome | enum |  | the final answer: PASSED, FAILED or REVIEW — changed only through the Override endpoint |
| reference | string |  | human-facing case reference shown on every screen, e.g. ver-000123 |
| productConfigVersion | int | FK | the ProductConfig version this decision used — pinned forever for explainability |
| ruleResults | JSON |  | embedded results of the four checks — wellFormedness with per-field reasons, then age, productActive and channel, each with passed + reason code |
| submittedAt | timestamp |  | when the orchestrator submitted the case |

**ProductConfig — insert-only, versioned product terms; the current version is the highest per product**

| field | type | key | meaning |
|---|---|---|---|
| productCode | string | PK | which product the terms govern: CREDIT_CARD_STANDARD, _REWARDS or _STUDENT — key together with version |
| version | int | PK | one new row per change — rows are inserted, never updated; current = the highest version per product |
| minAge | int |  | the age rule's threshold — below it FAILED, exactly at it REVIEW (seeded 18 for all three products) |
| limitMin | int |  | the smallest requestable credit limit in GBP — the sweep fails requests below it |
| limitMax | int |  | the largest requestable credit limit in GBP — above it fails the sweep, exactly at it goes to REVIEW |
| active | boolean |  | the product-active rule — false on the current version fails every new application (VER_PRODUCT_INACTIVE) |
| channels | string[] |  | the channel rule — where the product may be sold, e.g. [WEB, MOBILE_APP]; any other channel fails (VER_CHANNEL_NOT_ELIGIBLE) |
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

Relationships: VerificationRecord N:1 ProductConfig — each decision pins the version it used · VerificationRecord 1:N OverrideLog — every override is audited to its case

<details><summary>mermaid source (generated from the spec tables)</summary>

```mermaid
flowchart LR
    VerificationRecord["<b>VerificationRecord</b><br/>————————<br/>applicationId (PK)<br/>outcome<br/>reference<br/>productConfigVersion (FK)<br/>ruleResults<br/>submittedAt"]
    ProductConfig["<b>ProductConfig</b><br/>————————<br/>productCode (PK)<br/>version (PK)<br/>minAge<br/>limitMin<br/>limitMax<br/>active<br/>channels<br/>effectiveFrom"]
    OverrideLog["<b>OverrideLog</b><br/>————————<br/>applicationId (FK)<br/>oldOutcome<br/>newOutcome<br/>reason<br/>operator<br/>overriddenAt"]
    VerificationRecord -->|"each decision pins the version it used (N:1)"| ProductConfig
    VerificationRecord -->|"every override is audited to its case (1:N)"| OverrideLog
    classDef ent fill:#ffffff,stroke:#2EA98D,color:#22302B
    class VerificationRecord ent
    class ProductConfig ent
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
    IN_PROGRESS --> PASSED : all checks pass
    IN_PROGRESS --> REVIEW : boundary flag (wins)
    IN_PROGRESS --> FAILED : any hard failure
    PASSED --> FAILED : override
    FAILED --> PASSED : override
    REVIEW --> PASSED : override
    REVIEW --> FAILED : override
    note right of REVIEW
        override = operator + mandatory reason
        → override_log row
        → callback local-manual, journey resumes
    end note
    classDef ok fill:#ffffff,stroke:#1F8A5D,color:#1F8A5D,font-weight:bold
    classDef warn fill:#ffffff,stroke:#B7791F,color:#B7791F,font-weight:bold
    classDef bad fill:#ffffff,stroke:#B3403A,color:#B3403A,font-weight:bold
    classDef trans fill:#ECF6F1,stroke:#4A635B,color:#22302B
    class PASSED ok
    class REVIEW warn
    class FAILED bad
    class IN_PROGRESS trans
```

</details>

## Out of scope

Editing a case (records are immutable — override is UC 05); the /execute wiring itself (template gives it).

## Build notes

The engine is plain functions over the application object — build and unit-test it before any Spring wiring. Precedence: any hard failure → FAILED, unless a boundary flag is present → REVIEW wins. No failures, no flags → PASSED.

## Tests

Engine: table-driven unit tests per rule + precedence; slice test for the GET.

## Sequence caption

The engine runs at /execute time, not at read time — reviewing a case replays stored results, it never re-decides.

## Definition of done

All acceptance criteria verified against the running app · `./mvnw test` green · teammate-reviewed PR merged to main · fresh clone builds green · endpoint idempotent + present in the OpenAPI 3.0 (Swagger) spec · demoable from the UI.
