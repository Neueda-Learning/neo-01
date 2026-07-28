# neo-01 — Application Verification

**Module 01 of ten.** One step of the neo-bank customer-onboarding journey, owned by
**Team 01**. The journey is driven by
[`neo-00`](https://github.com/Neueda-Learning/neo-00), the orchestrator — which also owns the AWS
environment this repo deploys itself into. You never call another module, and no module
calls you: only the orchestrator does.

Everything that distinguishes one module from another is an env var — `SERVICE_ID`,
`SERVICE_NAME`, `SERVICE_DOMAIN`, `SERVICE_TEAM` — so all ten repos start as the same
image wearing a different name. What makes yours *yours* is the business rules you write
in `service/ApplicationService.java`.

This module performs **application verification** — the first step in the onboarding
journey. It validates the application envelope, checks the applicant's age, credit limit,
product eligibility, and channel availability against configured product rules, then
reports the outcome (`PASSED` / `FAILED` / `REVIEW`) back to the orchestrator.

```
controller/     the HTTP surface (contract + health + info + cases + products)
service/        ApplicationService  ← YOURS
repository/     Spring Data interfaces
model/          VerificationRecord · ProductConfig · Decision enum
dto/            what your UI reads
integrations/
  orchestrator/ the wire, and the typed Application. Fixed — your own
                integrations go BESIDE it, not in it
config/         two beans
```

## API Reference

### Contract Endpoint

#### `POST /api/v1/applications`

The contract entry point. The orchestrator sends an application; this module answers
`202` immediately and processes the application asynchronously.

**Request body** — `ApplicationRequest`:

```json
{
  "applicationId": "SIM-01",
  "correlationId": "sim-0001",
  "command": "process-application",
  "application": {
    "applicant": { "fullName": "Maria Nowak", "dateOfBirth": "1995-03-15", ... },
    "product": { "productCode": "CREDIT_CARD_REWARDS", "requestedCreditLimit": 3000 },
    "channel": "MOBILE_APP",
    ...
  }
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | String | **yes** | the id everything keys on; `@NotBlank` — `400` if missing |
| `correlationId` | String | no | ties this call to one customer journey across all ten modules |
| `command` | String | no | what you are being asked to do (e.g. `process-application`) |
| `application` | Application | no | the whole application form, every field, typed |

**Response** — `202 Accepted`:

```json
{
  "status": "in-progress",
  "applicationId": "SIM-01",
  "serviceId": "neo01",
  "command": "process-application"
}
```

**Validation rules:**
- Missing `applicationId` → `400 Bad Request`
- Missing or blank `command` → `400 Bad Request`
- Malformed JSON → `400 Bad Request`
- Everything else (malformed dates, unknown product codes, etc.) is accepted — judging
  those is the module's job, and a `400` would rob it of the chance to report specific
  field errors via reason codes.

**Idempotency:** a duplicate `applicationId` for an already-decided case returns `202`
again with no reprocessing; the stored outcome is replayed to the orchestrator via callback.

---

#### `GET /api/v1/applications`

Lists all verification records this module has processed, newest first. Read by this
module's own UI; the orchestrator never calls it.

**Response** — `200 OK`:

```json
[
  {
    "applicationId": "SIM-01",
    "fullName": "Maria Nowak",
    "outcome": "PASSED",
    "reference": "VER_ALL_CHECKS_PASSED",
    "ruleResults": "[{\"rule\":\"wellFormedness\",\"pass\":true}, ...]",
    "createdAt": "2026-07-28T17:41:33.962Z"
  }
]
```

| Field | Type | Description |
|---|---|---|
| `applicationId` | String | the id of the application |
| `fullName` | String | applicant's full name |
| `outcome` | String | `IN_PROGRESS` · `PASSED` · `FAILED` · `REVIEW` |
| `reference` | String | primary reason code (e.g. `VER_ALL_CHECKS_PASSED`) |
| `ruleResults` | String | JSON array of rule evaluation results |
| `createdAt` | Instant | when this module received the application |

---

### Callback (outbound)

#### `PUT {ORCHESTRATOR_URL}/api/v1/applications/{applicationId}`

This module PUTs the outcome back to the orchestrator once processing is complete.
The `applicationId` is in the URL, not the body.

**Request body** — `ApplicationStatusUpdate`:

```json
{
  "serviceId": "neo01",
  "status": "PASSED",
  "comment": "VER_ALL_CHECKS_PASSED"
}
```

| Field | Type | Description |
|---|---|---|
| `serviceId` | String | `neo01` — deliberately not the repo name |
| `status` | String | `PASSED` · `FAILED` · `REVIEW` (uppercase) |
| `comment` | String | reason code(s) explaining the outcome |

---

### Case Management (UC-01 · UC-02 · UC-03 · UC-05)

#### `GET /cases`

Search for verification cases (UC-01). The board starts empty — no `q` means no rows.

**Query parameters:**

| Param | Type | Required | Default | Description |
|---|---|---|---|---|
| `q` | String | no | — | applicationId fragment or applicant name |
| `limit` | int | no | `10` | maximum rows (capped at 10) |

**Response** — `200 OK`:

```json
{
  "cases": [
    {
      "applicationId": "SIM-01",
      "fullName": "Maria Nowak",
      "submittedAt": "2026-07-28T17:41:33.962Z",
      "outcome": "PASSED",
      "reasonCount": 0
    }
  ],
  "more": false
}
```

---

#### `GET /cases/{applicationId}`

Review one case that was already decided and stored (UC-02).

**Path parameters:**

| Param | Description |
|---|---|
| `applicationId` | the id of the case to review |

**Response** — `200 OK`:

```json
{
  "outcome": "REVIEW",
  "reference": "VER_AGE_EXACT_MINIMUM",
  "productConfigVersion": 6,
  "ruleResults": [
    { "rule": "wellFormedness", "pass": true },
    { "rule": "age", "pass": false, "reason": "VER_AGE_EXACT_MINIMUM" }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `outcome` | String | `IN_PROGRESS` · `PASSED` · `FAILED` · `REVIEW` |
| `reference` | String | primary reason code |
| `productConfigVersion` | Integer | which product config version was applied |
| `ruleResults` | JsonNode | detailed rule evaluation results |

**Errors:** `404 Not Found` if the applicationId does not exist.

---

#### `GET /cases/{applicationId}/applicant`

Fetches the applicant block live from the orchestrator (UC-03). Applicant data is never
persisted in this module's schema.

**Path parameters:**

| Param | Description |
|---|---|
| `applicationId` | the id of the case |

**Response** — `200 OK`:

```json
{
  "fullName": "Maria Nowak",
  "dateOfBirth": "1995-03-15",
  "product": {
    "productCode": "CREDIT_CARD_REWARDS",
    "requestedCreditLimit": 3000
  },
  "channel": "MOBILE_APP",
  "countryOfResidence": "GB",
  "consents": {
    "termsAccepted": true
  }
}
```

---

#### `POST /cases/{applicationId}/override`

Manually change a verification outcome (UC-05). Updates the outcome, logs the override
for audit, and re-notifies the orchestrator.

**Path parameters:**

| Param | Description |
|---|---|
| `applicationId` | the id of the case to override |

**Request body** — `OverrideCaseRequest`:

```json
{
  "newOutcome": "PASSED",
  "reason": "Manual review approved by supervisor",
  "operator": "operator@example.com"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `newOutcome` | String | **yes** | must be `PASSED`, `FAILED`, or `REVIEW` |
| `reason` | String | **yes** | why the override was made |
| `operator` | String | **yes** | who made the override |

**Response** — `200 OK`: the updated case (same shape as `GET /cases/{applicationId}`).

**Errors:** `400 Bad Request` if validation fails; `404 Not Found` if the case does not exist.

---

### Failure Patterns (UC-04)

#### `GET /reason-codes`

Returns reason-code counts for the given inclusive date window, ranked descending (UC-04).

**Query parameters:**

| Param | Type | Required | Format | Description |
|---|---|---|---|---|
| `from` | LocalDate | **yes** | `YYYY-MM-DD` | start date (inclusive) |
| `to` | LocalDate | **yes** | `YYYY-MM-DD` | end date (inclusive) |

**Response** — `200 OK`:

```json
[
  { "code": "VER_AGE_BELOW_MINIMUM", "count": 3, "kind": "failure" },
  { "code": "VER_LIMIT_EXACT_MAXIMUM", "count": 2, "kind": "review" },
  { "code": "VER_TERMS_NOT_ACCEPTED", "count": 1, "kind": "failure" }
]
```

| Field | Type | Description |
|---|---|---|
| `code` | String | the `VER_` reason code |
| `count` | long | occurrences within the requested window |
| `kind` | String | `"failure"` or `"review"` — derived from the code |

---

### Product Configuration

#### `POST /products`

Create a new version of product rules. Versions are insert-only (never update or delete);
the version number auto-increments per `productCode`.

**Request body** — `CreateProductVersionRequest`:

```json
{
  "productCode": "CREDIT_CARD_REWARDS",
  "minAge": 18,
  "limitMin": 500,
  "limitMax": 10000,
  "active": true,
  "channels": ["WEB", "MOBILE_APP", "BRANCH"]
}
```

| Field | Type | Required | Validation | Description |
|---|---|---|---|---|
| `productCode` | String | **yes** | `@NotBlank` | product identifier |
| `minAge` | Integer | **yes** | `@NotNull`, `@Min(18)` | minimum applicant age |
| `limitMin` | Integer | **yes** | `@NotNull`, `@Min(0)` | minimum credit limit |
| `limitMax` | Integer | **yes** | `@NotNull`, `@Min(0)` | maximum credit limit |
| `active` | Boolean | **yes** | `@NotNull` | whether new applications are accepted |
| `channels` | List\<String\> | **yes** | `@NotEmpty` | eligible channels (`WEB`, `MOBILE_APP`, `BRANCH`, `PHONE`) |

**Additional service-level validation:**
- `limitMin` must be strictly less than `limitMax`
- `channels` must contain only valid values: `WEB`, `MOBILE_APP`, `BRANCH`, `PHONE`

**Response** — `201 Created`:

```json
{ "version": 7 }
```

**Errors:** `400 Bad Request` if validation fails.

---

#### `GET /products/{code}/versions`

List all versions of a product's configuration, ordered by version descending.

**Path parameters:**

| Param | Description |
|---|---|
| `code` | the product code (e.g. `CREDIT_CARD_REWARDS`) |

**Response** — `200 OK`:

```json
[
  {
    "productCode": "CREDIT_CARD_REWARDS",
    "version": 6,
    "minAge": 18,
    "limitMin": 500,
    "limitMax": 10000,
    "active": true,
    "channels": ["WEB", "MOBILE_APP", "BRANCH"],
    "effectiveFrom": "2026-07-29T00:00:00Z",
    "current": true
  },
  {
    "productCode": "CREDIT_CARD_REWARDS",
    "version": 5,
    "minAge": 18,
    "limitMin": 1000,
    "limitMax": 10000,
    "active": true,
    "channels": ["WEB", "MOBILE_APP", "BRANCH"],
    "effectiveFrom": "2026-07-01T00:00:00Z",
    "current": false
  }
]
```

| Field | Type | Description |
|---|---|---|
| `productCode` | String | product identifier |
| `version` | Integer | version number (auto-incremented per productCode) |
| `minAge` | Integer | minimum applicant age |
| `limitMin` | Integer | minimum credit limit |
| `limitMax` | Integer | maximum credit limit |
| `active` | Boolean | whether new applications are accepted |
| `channels` | List\<String\> | eligible channels |
| `effectiveFrom` | Instant | when this version takes effect |
| `current` | Boolean | whether this is the highest version (applied to new applications) |

**Errors:** `404 Not Found` if no versions exist for the given product code.

---

#### `GET /products`

List all product codes that have at least one configured version.

**Response** — `200 OK`:

```json
["CREDIT_CARD_REWARDS", "CREDIT_CARD_STANDARD", "CREDIT_CARD_STUDENT"]
```

---

### Health & Identity

#### `GET /health`

DB-backed health check. Probes the database connection — a green light means "I can
actually serve requests". Used by the compose healthcheck, the orchestrator's service
board, and the ALB target group health check.

**Response** — `200 OK` (database up) or `503 Service Unavailable` (database down):

```json
{
  "status": "UP",
  "serviceId": "neo01",
  "service": "Application Verification",
  "timestamp": "2026-07-28T17:41:33.962Z",
  "database": { "status": "UP" }
}
```

---

#### `GET /info`

Identity and configuration register. Reports who this module is and what it is faking.
The quickest check that a deploy actually landed.

**Response** — `200 OK`:

```json
{
  "serviceId": "neo01",
  "service": "Application Verification",
  "team": "Team 01",
  "domain": "verification",
  "version": "0.1.0-SNAPSHOT",
  "orchestratorUrl": "http://sidecar:8080",
  "mockedDependencies": []
}
```

| Field | Type | Description |
|---|---|---|
| `serviceId` | String | `neo01` — deliberately not the repo name |
| `service` | String | display name |
| `team` | String | which team owns this module |
| `domain` | String | the BIAN domain this module owns |
| `version` | String | application version |
| `orchestratorUrl` | String | where callbacks are sent |
| `mockedDependencies` | List\<String\> | external systems this module fakes; empty = claims nothing is mocked |

---

### Error Handling

All errors return a consistent JSON shape:

```json
{
  "timestamp": "2026-07-28T17:41:33.962Z",
  "status": 400,
  "error": "Bad Request",
  "message": "applicationId must not be blank"
}
```

| HTTP Status | When |
|---|---|
| `400 Bad Request` | missing `applicationId`; blank `command`; malformed JSON; validation failure on `POST /products` or `POST /cases/{id}/override` |
| `404 Not Found` | case or product version not found |
| `503 Service Unavailable` | orchestrator unreachable when sending callback |
| `500 Internal Server Error` | unexpected failure (stack trace hidden) |

---

## Reason Codes

This module uses `VER_` prefixed reason codes. Codes from other modules use different
prefixes (`POL_`, `KYC_`, `SCR_`, `CRE_`, `CRD_`) and are not produced by this module.

| Code | Kind | Description |
|---|---|---|
| `VER_ALL_CHECKS_PASSED` | — | all verification rules passed |
| `VER_AGE_EXACT_MINIMUM` | review | applicant age exactly equals minimum age |
| `VER_AGE_BELOW_MINIMUM` | failure | applicant age below minimum age |
| `VER_LIMIT_EXACT_MAXIMUM` | review | requested limit exactly equals maximum |
| `VER_LIMIT_OUTSIDE_PRODUCT_RANGE` | failure | requested limit outside min/max range |
| `VER_TERMS_NOT_ACCEPTED` | failure | terms and conditions not accepted |
| `VER_MISSING_FIELD` | failure | required field missing in the envelope |
| `VER_INVALID_FIELD` | failure | field format invalid (e.g. date, ISO code) |
| `VER_PRODUCT_INACTIVE` | failure | product is not active for new applications |
| `VER_CHANNEL_NOT_ELIGIBLE` | failure | channel not in product's eligible channels |
| `VER_INVALID_PRODUCT` | failure | unknown product code |

---

## What pushing does

**This repo deploys itself.** Trunk-based:

- push to a **feature branch** → build + test only. Nothing is published, nothing deploys.
- push to **`main`** → build + test → publish two images to ghcr.io pinned by `@sha256` →
  deploy **this service** to dev → smoke it through the load balancer → record the digest
  as the promote source.
- *Run workflow → `promote: true`* on `main` → **prod**, which pauses for a required
  reviewer and then ships the exact digest dev proved. No rebuild.

There are no stored AWS keys: each job assumes this repo's own IAM role via GitHub OIDC, and
that role can only touch this repo's own `neobank-<env>-neo-01` stack. You never hold AWS
credentials yourself — everything that reaches AWS goes through a workflow in this repo.

The front-end image is built with `APP_BASE_PATH=/neo-01`, because in the deployed stack
every UI shares one port and is told apart by its path. Vite bakes asset URLs at build time and
a load balancer cannot rewrite paths, so the prefix has to be a build argument — the pipeline
reads it from `infra/env/dev.params`'s `PathPrefix` so the image and the stack cannot drift.

### Where your module ends up

| | Your module | Your API | The board |
|---|---|---|---|
| **dev** | [`/neo-01/`](http://neobank-dev-571740187.ap-southeast-1.elb.amazonaws.com/neo-01/) | [`/neo-01/health`](http://neobank-dev-571740187.ap-southeast-1.elb.amazonaws.com/neo-01/health) · [`/neo-01/info`](http://neobank-dev-571740187.ap-southeast-1.elb.amazonaws.com/neo-01/info) | [orchestrator](http://neobank-dev-571740187.ap-southeast-1.elb.amazonaws.com/) |
| **prod** | [`/neo-01/`](http://neobank-prod-294820685.ap-southeast-1.elb.amazonaws.com/neo-01/) | [`/neo-01/health`](http://neobank-prod-294820685.ap-southeast-1.elb.amazonaws.com/neo-01/health) · [`/neo-01/info`](http://neobank-prod-294820685.ap-southeast-1.elb.amazonaws.com/neo-01/info) | [orchestrator](http://neobank-prod-294820685.ap-southeast-1.elb.amazonaws.com/) |

**dev is yours** — it moves every time you merge to `main`, so that link is the honest answer
to "is my module working?". **prod is not**: it only ever runs an image dev has already
proven, and only after a human approves the promote. A 404 on prod means your module has not
been promoted yet — not that it is broken.

`/info` is the quickest check that a deploy actually landed: it reports the `serviceId`,
domain, team and mocked-dependency register **this running container** believes in. If that
does not match what you configured, the deploy did not go where you think it did.

Plain HTTP, no DNS name: those hostnames belong to the load balancers and change if one is
ever replaced. The live values are always in SSM
(`/neobank/dev/alb-dns`, `/neobank/prod/alb-dns`), and the instructor can read them out.

## If you break your database

Liquibase owns the schema (`ddl-auto: validate`), and there are a few ways to get stuck. **Read
the symptom before reaching for the destructive fix** — the first row here is the common one and
is not a database problem at all:

| Symptom | What it means | Fix |
|---|---|---|
| `Schema-validation: missing table/column …` | your entity and your changelog disagree | **write a changeset.** Resetting only hides it until the next startup |
| App never starts; log repeats *"waiting for changelog lock"* | a task died mid-migration and left `DATABASECHANGELOGLOCK` held | *Database repair* → **`unlock`**. No data loss |
| You add a changeset and *that* deploy hangs on the lock, though the last one was fine | same stale lock — Liquibase's fast check skips the lock entirely while the schema is up to date, so it stayed hidden until something was actually pending | *Database repair* → **`unlock`**, then re-deploy |
| Crash-loop on a checksum mismatch | you edited a changeset that had already run | *Database repair* → **`reset`** |
| A changeset failed halfway | MySQL DDL isn't transactional, so objects exist with no changelog row | *Database repair* → **`reset`** |
| Local only | — | `docker compose down -v` |

**Never edit a changeset that has already been applied — add a new one.** That is the rule the
checksum row above exists to enforce.

*Database repair* is a workflow in this repo's **Actions** tab (`.github/workflows/db-reset.yml`).
Pick the environment and the action; `reset` also asks you to type your `DbName` from
`infra/env/<env>.params` so you can't fire it by accident. **dev runs straight away. prod pauses
for an approver** — the same reviewer gate that guards a promote.

A `reset` destroys **only this service's schema** (`neo_01`). Every other service and the
orchestrator's own journey data live in separate schemas and are untouched — but the orchestrator
will still remember applications whose module rows you just deleted, so expect stale rows on the
board until it is reset too.

## Run it

Normally you don't: you run the whole system from `neo-00`. To work on this service
alone:

```bash
docker compose up --build
# http://localhost:9000   THE SIDECAR — send applications, watch what comes back
# http://localhost:5173   React UI — what this service has seen and answered
# http://localhost:8080/  zero-build status page served by Spring Boot
# http://localhost:8080/health · /info · /swagger-ui.html
```

**First run after adding the sidecar: `docker compose down -v` once.** MySQL creates the
sidecar's schema from `db/init/*.sql`, and it runs those only on an empty data directory — an
existing volume means the schema was never created. The sidecar's log says so if you forget.

Backend only, for fast iteration:

```bash
docker compose up -d mysql sidecar
cd backend
./mvnw test                                              # unit + web-slice + full-context H2
DB_URL=jdbc:mysql://localhost:3307/neo_01 ./mvnw spring-boot:run
```

Run this way and callbacks still land: the module's default `ORCHESTRATOR_URL` is
`http://localhost:9000`, which is exactly where the sidecar is. Set the sidecar's **module base
URL** field to `http://host.docker.internal:8080` so it can reach back into your IDE.

## Sending applications: the sidecar

The orchestrator is not running on your laptop, and waiting for it is not a development loop —
so a **sidecar** plays it at **http://localhost:9000**. It ships **26 applications** covering the
happy path, both sides of every boundary the rules care about, the integration failure modes,
and one envelope that must be rejected.

It matters that it works both ways. It sends to your real `POST /api/v1/applications`, *and* it
serves `PUT /api/v1/applications/{id}` — so your answer has somewhere to land and you can see the half
of the contract curl cannot show you.

```bash
open http://localhost:9000          # pick a scenario, Send, watch Ack then Callback

# or, if you prefer curl:
curl -s  localhost:9000/api/v1/scenarios  | jq '.scenarios[].id'
curl -sX POST localhost:9000/api/v1/dispatch \
     -H 'Content-Type: application/json' -d '{"scenarioId":"SIM-01"}'
curl -s  localhost:9000/api/v1/dispatches | jq '.[0]'
```

The sidecar lives in **its own repo** ([`Neueda-Learning/neobank-sidecar`](https://github.com/Neueda-Learning/neobank-sidecar))
and compose builds it straight from there — there is no sidecar source in this repo and nothing
for you to maintain. **The first build takes a few minutes**; after that it is cached.
`docker compose up --build sidecar` picks up a new version. Its full guide, the corpus table and
the planted failure triggers are in [its README](https://github.com/Neueda-Learning/neobank-sidecar#readme).

`./scripts/reset-db.sh` empties **this module's** tables for a clean board; the sidecar's own log
is cleared from its page.

## The contract

Full detail in [`api-contract.md`](https://github.com/Neueda-Learning/neo-00/blob/main/api-contract.md).
In short:

**Two endpoints, and only the first is the contract.**

| Endpoint | Purpose |
|---|---|
| `POST /api/v1/applications` | the orchestrator sends an application → `202 {status:"in-progress", applicationId, serviceId, command}` |
| `GET /api/v1/applications` | the verification records — read by *your* UI, never by the orchestrator |
| `GET /health` · `GET /info` | DB-backed health · identity, BIAN domain, and what is mocked |

Add whatever else your operator screen needs — a search, a detail lookup, a manual override. Those
are yours; the `POST` is not.

Once the work is done — off the request thread, so within milliseconds unless you call something
slow — it PUTs `${ORCHESTRATOR_URL}/api/v1/applications/{applicationId}`:

```json
{ "serviceId": "neo01", "status": "PASSED",
  "comment": "VER_ALL_CHECKS_PASSED" }
```

**Three fields: the application id is in the URL, not the body.** This is an update to an
application the orchestrator already owns, so the id identifies the resource. The `comment` is your
reason — write it for the bank employee who has to explain the outcome to a customer: which rule
fired, and with what value.

## Configuration

Every knob is an env var, which is how one image serves as any slot:

| Env | Default | What |
|---|---|---|
| `SERVICE_ID` | `neo01` | the id sent on callbacks — note **no `-a`**, unlike the repo name |
| `SERVICE_NAME` | `Application Verification` | display name |
| `SERVICE_DOMAIN` | `unassigned` | the BIAN domain you own, reported on `/info` |
| `ORCHESTRATOR_URL` | `http://localhost:9000` | where callbacks go — see the three targets below |
| `MOCKED_DEPENDENCIES` | *(empty)* | comma-separated systems you fake — the register, served live |
| `WORKER_POOL_SIZE` | `8` | threads available to run your rules |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | see compose | this service's own schema |

**There are no decision knobs.** What this module answers comes from your rules, not from a
weight, a seed or a delay. Those env vars existed when the decision was a seeded coin flip; the
coin flip is gone.
| `SIDECAR_PORT` / `SIDECAR_REF` / `MODULE_URL` | `9000` / `v1` / `http://backend:8080` | the mock orchestrator (`SIDECAR_REF` is the git ref compose builds) |

### The three things `ORCHESTRATOR_URL` can point at

Only this value changes between them. The module's code does not.

| Set it to | When |
|---|---|
| `http://sidecar:8080` | the mock orchestrator, from inside this repo's compose — **the default there** |
| `http://localhost:9000` | that same sidecar, from a module you run in your IDE — **the default in `application.yml`**, so this needs no configuration at all |
| `http://orchestrator:8080` | the **real** orchestrator, in the `neo-00` system stack — which sets it for you |

## Tests

```bash
cd backend
./mvnw test                       # unit + web-slice + full-context H2
./mvnw verify -DskipITs=false     # + RequestRepositoryIT against real MySQL 8 (needs Docker)
```

`*Test` runs Docker-free. `*IT` needs Docker and is skipped locally unless you ask for it;
CI sets `CI=true`, which activates the `integration` profile and runs it for real.

## Conventions

- **Own schema only.** This service reads and writes its own MySQL schema, never another's.
- **Liquibase owns the schema**; JPA runs `ddl-auto=validate`. Migrations are append-only —
  add a change set, never edit an applied one.
- `backend/` and `frontend/` **stay at the repo root** — the system compose builds
  `./neo-01/backend` and `./neo-01/frontend` by path.
