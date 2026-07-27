# Module 1 · Application Verification — UC 07 · View Version History

> AI implementation brief, generated from the v5 spec (`spec/.../use-cases/`). Source of truth is the spec; regenerate, don't hand-edit.

## Context

- Module: 1 · Application Verification · category Rule · domain `verification` · command `verify-application` · outcomes: PASSED, FAILED, REVIEW
- Use case: 07 · View Version History · track C · prerequisite: after 06 · build shape: API+FE · primary screen: Product Configuration
- Data effect: read-only
- Platform rules (non-negotiable): the orchestrator is the only caller; the whole application arrives in the envelope (plus the v5 `outputs` block, Option A); steps are independent and re-orderable; the payload is NEVER stored — only `applicationId`; every module ships `GET /cases/{id}/applicant` proxying the orchestrator; big lists are empty by default and capped at 10 rows (≤10 hydration calls); ALL APIs are idempotent — same request twice, same result once; every endpoint appears in the service's OpenAPI 3.0 (Swagger) spec.

## Story

As a bank employee I want to see every past version of a product's rules so I can explain an old decision.

## Contract

```
GET /products/{code}/versions →
[{"version":1,"minAge":21,…,
  "effectiveFrom":"2026-07-01T…"},
 …oldest first, current last…]
```

## Acceptance criteria

1. GET /products/{code}/versions → 200 + every version, oldest first, each {version, minAge, limitMin, limitMax, active, channels, effectiveFrom}.
2. The current version is flagged in the response.
3. Unknown productCode → 404.
4. After UC 06's demo insert, CREDIT_CARD_REWARDS shows v1…v4 with v4 current.  ⟵ **checkpoint — exact value**
5. The Product Configuration screen shows the history beside the product list — selecting a product loads its versions.

## Expected data changes

- **No rows change.** Reads product_config only.
- Current = MAX(version) — computed, not stored.
- Pairs with UC 02: a case's pinned version deep-links here to show the thresholds that decided it.

## Diagrams

> Rendered images first (for PDF/preview); the mermaid source is collapsed underneath for machine consumption.

### Sequence — this use case

![Sequence — this use case](diagrams/uc-07-sequence.jpg)

<details><summary>mermaid source</summary>

```mermaid
sequenceDiagram
    autonumber
    participant UI
    participant Controller
    participant Service
    participant MySQL
    UI->>Controller: GET /products/REWARDS/versions
    Controller->>Service: versions(code)
    Service->>MySQL: SELECT … ORDER BY version ASC
    MySQL-->>Service: rows
    Service-->>Controller: List‹VersionDto› (current marked)
    Controller-->>UI: 200 OK + versions
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

### State transitions — the versioned configuration

![State transitions — the versioned configuration](diagrams/config-states.jpg)

<details><summary>mermaid source</summary>

```mermaid
stateDiagram-v2
    direction LR
    [*] --> CURRENT : new version inserted (POST /products)
    CURRENT --> SUPERSEDED : a newer version is inserted
    note right of CURRENT
        current = MAX(version) per productCode
        active flag flips ONLY via a new version
        INACTIVE current → new applications FAIL
    end note
    note right of SUPERSEDED
        never edited, never deleted
        still pinned by the cases it decided
    end note
    classDef cur fill:#ffffff,stroke:#2EA98D,color:#2EA98D,font-weight:bold
    classDef sup fill:#ECF6F1,stroke:#5E736B,color:#5E736B,font-weight:bold
    class CURRENT cur
    class SUPERSEDED sup
```

</details>

## Out of scope

Diffing versions in the UI; restoring an old version (create a new one instead).

## Build notes

Plain read over product_config ordered by version; mark the current (MAX) row so the UI can badge it.

## Tests

Repository test: ordering, unknown code → 404.

## Definition of done

All acceptance criteria verified against the running app · `./mvnw test` green · teammate-reviewed PR merged to main · fresh clone builds green · endpoint idempotent + present in the OpenAPI 3.0 (Swagger) spec · demoable from the UI.
