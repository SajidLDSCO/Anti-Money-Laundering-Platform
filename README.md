# AML Monitor

An anti-money-laundering transaction monitoring and case management app.
**Backend:** Spring Boot 3.5 (Java 25 LTS, JPA, H2 or PostgreSQL). **Frontend:** Angular 21 (standalone, signals, zoneless).

What it does:

- **Real-time transaction monitoring.** Every transaction is scored by a rule engine before it is posted. Score ≥ 40 raises an alert; ≥ 80 holds the transaction. A sanctions match always holds.
- **Sanctions screening.** Fuzzy name matching (typos, transliteration, word order, legal suffixes) against a list you can import as CSV. Customers are screened at onboarding and on name change; counterparties on every payment.
- **Customer risk rating.** A transparent additive scorecard (PEP, jurisdiction, occupation, KYC status, open alerts, SARs), with every point explained.
- **Case management.** Alert queue, assignment, notes, escalation, close as false positive (releases a held payment), file SAR. Every action is written to an append-only trail.
- **Dashboard** with 30-day activity, open alerts by severity, alerts by rule.

## Run it

Prerequisites: Java 25 LTS, Maven 3.9+, Node 20.19+ / 22.12+ / 24.

```bash
# 1. API on :8080 (in-memory H2, loads a fictional demo portfolio)
cd backend
mvn spring-boot:run

# 2. UI on :4200 (proxies /api to :8080)
cd frontend
npm install
npm start
```

Running `npm start` inside WSL? WSL's `localhost` is not Windows', so the dev proxy checks on startup where a backend is listening on 8080 (inside WSL first, then the Windows host) and prints the target it picked. Start the backend before `npm start`; `API_TARGET=http://host:8080` skips the check.

Open http://localhost:4200 and sign in with a demo account (password `aml-demo-2026` for all three):

| Username | Email | Role |
|---|---|---|
| `m.chen` | `m.chen@aml.example` | Analyst |
| `a.hossain` | `a.hossain@aml.example` | MLRO |
| `admin` | `admin@aml.example` | Admin |

Start with **Overview**, open an alert, then try **Submit transaction → Try a scenario** to watch the rules fire.

Tests: `cd backend && mvn test` (name-matcher unit tests, end-to-end pipeline tests, and security tests).

## Sign-in and roles

Users sign in with their username or email, and a password (BCrypt-hashed). Usernames cannot contain `@`, so anything with `@` is looked up as an email. The session is an HttpOnly cookie; mutating requests also need the `XSRF-TOKEN` cookie echoed in the `X-XSRF-TOKEN` header, which Angular's HttpClient does automatically. Sessions expire after 30 minutes idle.

Anyone can **sign up** at `/signup` with a name, username, email and password; each username and email can be used once. New accounts get the Analyst role and, by default, stay disabled until an admin approves them on the **Users** page.

| Role | Can |
|---|---|
| Analyst | Everything except the items below: work alerts (assign, notes, escalate, close as false positive), submit transactions, onboard customers, screen names |
| MLRO | Also file SARs and freeze / unfreeze accounts |
| Admin | Also manage users, switch detection rules, maintain the sanctions list, open the H2 console |

The analyst recorded on assignments, notes, status changes and SARs is always the signed-in user; names sent by the client are ignored. Admins cannot change their own role or disable themselves, so at least one admin always remains.

| Variable | Default | Meaning |
|---|---|---|
| `AML_ADMIN_USERNAME` / `AML_ADMIN_PASSWORD` | `admin` / empty | Admin created at startup when no enabled admin exists. Set this for the `postgres` profile, which has no demo users. |
| `AML_SIGNUP_REQUIRES_APPROVAL` | `true` | `false` lets new sign-ups sign in straight away |
| `SESSION_COOKIE_SECURE` | `false` | Set `true` when served over HTTPS |

### PostgreSQL

```bash
docker compose up -d            # starts postgres:16 on :5432 (db/user/password: aml)
cd backend
SPRING_PROFILES_ACTIVE=postgres mvn spring-boot:run
```

The `postgres` profile does not load demo data or demo users: set `AML_ADMIN_PASSWORD` on first start so there is an admin to approve sign-ups. Schema is created by Hibernate (`ddl-auto: update`); switch to Flyway or Liquibase before production.

## Email notifications

The backend emails people when they need to act:

| When | Who |
|---|---|
| Someone signs up and needs approval | Admins |
| An admin approves (enables) an account | That user |
| An alert is escalated to the MLRO | MLROs and admins |
| A critical alert is raised, or a transaction is held | MLROs and admins |

Only enabled accounts with an email address receive mail, one message each. Emails are sent in the background after the change is saved, so a slow or failing mail server never delays or undoes an action; failures are logged. Nothing is sent while demo data is loading.

Mail is off until you set an SMTP host; until then the backend only logs what it would have sent. Try it locally with Mailpit, which catches every message instead of delivering it:

```bash
docker compose up -d mailpit        # SMTP on :1025, inbox at http://localhost:8025
cd backend
SPRING_MAIL_HOST=localhost SPRING_MAIL_PORT=1025 mvn spring-boot:run
```

To use a real account such as Gmail, copy `backend/local.properties.example` to `backend/local.properties` and fill it in (Gmail needs an App Password). That file is git-ignored and read on startup; the tests never load it. Set `aml.security.bootstrap-admin-email` there to your own address, so the admin's sign-up notifications reach you.

| Variable | Default | Meaning |
|---|---|---|
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` | unset | SMTP server. Mail is off without a host. |
| `MAIL_ENABLED` | `true` | Master switch; the Maven test run sets it off |
| `AML_ADMIN_EMAIL` | unset | Email of the bootstrap / demo admin, where sign-up notifications go |
| `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | unset | SMTP login, if the server needs one |
| `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS` | `false` | Set both `true` for most real providers (port 587) |
| `MAIL_FROM` | `AML Monitor <no-reply@aml.local>` | Sender address |
| `APP_URL` | `http://localhost:4200` | Base URL for links in emails |

## The demo data

On an empty H2 database the seeder creates nine fictional customers and pushes ~60 days of activity through the real engine, including:

| Customer | Pattern | Result |
|---|---|---|
| Daniel Okafor | Four cash deposits just under 10,000 in 48h | Structuring alerts; one closed with a SAR |
| Northwind Import Export | Wire to "Golden Cresent Trading" (misspelt listed entity) | Held, critical alert |
| Marina Kovalenko | 48k in, 46.2k out to Iran 22h later | Rapid movement + high-risk jurisdiction, held, escalated |
| Leonard Aziz (PEP) | 25,000 round sum from an offshore consultancy | Flagged |
| Sam Patel | 7 transfers to new payees within an hour | Velocity alert, closed as false positive |
| Farhana Akter | 15,000 cash deposit vs. a ~200 average | Large cash + out of profile |
| Ivan Petrenkov | Name resembles a listed individual | Onboarding screening alert |

**All names and the sanctions list are invented.** `samples/sanctions-sample.csv` shows the import format.

## How detection works

`backend/src/main/java/com/example/aml/rules/` — each rule is one class implementing `AmlRule`; Spring picks up new ones automatically.

| Rule | Points | Fires when |
|---|---|---|
| `SANCTIONS_MATCH` | 100, always holds | Counterparty name ≥ 0.88 similar to a listed name or alias |
| `STRUCTURING` | 60–80 | ≥ 3 cash deposits at 70–100% of the cash threshold within 48h, total ≥ threshold |
| `RAPID_MOVEMENT` | 50 | Outflow of 85–110% of an inflow received within 48h |
| `HIGH_RISK_JURISDICTION` | 45 (25 for increased monitoring) | Counterparty country on the configured lists |
| `HIGH_VELOCITY` | 35–50 | > 5 transactions in 1h, or 24h volume > 50,000 |
| `UNUSUAL_AMOUNT` | 35 | Amount > 5× the customer's 90-day average (needs 5+ prior transactions) |
| `LARGE_CASH` | 30 | Cash ≥ 10,000 (CTR candidate) |
| `PEP_TRANSACTION` | 25 | PEP customer, amount ≥ 5,000 |
| `HIGH_RISK_CUSTOMER` | 15 | Customer rated HIGH, amount ≥ 5,000 |
| `ROUND_AMOUNT` | 10 | Multiple of 1,000 and ≥ 5,000 |

All thresholds and country lists are in `backend/src/main/resources/application.yml` under `aml:`. Set the cash threshold to your FIU's reporting limit and keep the country lists in line with current FATF publications and your regulator. Rules can be switched on/off at runtime on the **Detection rules** page.

Time windows are measured from each transaction's own timestamp, so back-dated batch loads (`timestamp` in the request) are evaluated exactly as if they arrived live.

## API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/transactions` | Submit for monitoring → decision, score, rule hits, alert id |
| GET | `/api/transactions?status=&customerId=&q=&page=` | Search |
| GET/POST/PUT | `/api/customers`, `/api/customers/{id}` | List, onboard (screens + rates), update, detail |
| POST | `/api/customers/{id}/accounts`, `/api/customers/{id}/recalculate-risk` | Open account, re-rate |
| PATCH | `/api/accounts/{id}/status` | Freeze / unfreeze |
| GET | `/api/alerts?openOnly=&status=&severity=` , `/api/alerts/{id}` | Queue, detail with evidence + trail |
| POST | `/api/alerts/{id}/assign` · `/notes` · `/status` · `/sar` | Workflow |
| GET | `/api/screening?name=` | Ad-hoc name screening |
| GET/POST/DELETE | `/api/sanctions`, POST `/api/sanctions/import` (multipart `file`) | List maintenance |
| GET/PATCH | `/api/rules`, `/api/rules/{code}` | Rule catalogue, enable/disable |
| GET | `/api/dashboard`, `/api/sars` | Overview, filed SARs |
| POST | `/api/auth/login` (form fields `username`, `password`), `/api/auth/logout`, `/api/auth/signup` | Sign in, sign out, register |
| GET | `/api/auth/me` | Signed-in user |
| GET/PATCH | `/api/users`, `/api/users/{id}` (`role`, `enabled`) | User admin |

Every endpoint except sign-in, sign-up and `/actuator/health` needs a session; see **Sign-in and roles** for which role each action needs.

Errors are RFC 9457 problem details; validation errors list each invalid field.

## Before production

This is a solid starting point, not a certified compliance system. In particular:

- **Authentication hardening.** Sign-in is local username/password with roles. Before production add login throttling or lockout, multi-factor authentication or single sign-on through your identity provider (OIDC: Keycloak, Entra ID…), password reset, and four-eyes approval for SAR filing and false-positive closure on held payments.
- **Real sanctions data.** Schedule imports from OFAC SDN, UN Consolidated, EU and your national list; keep list versions for audit.
- **Database migrations** (Flyway), audit columns on every table, and immutable audit storage.
- **Scale.** Rules read up to 90 days of a customer's history per transaction. For high volume, move to pre-aggregated customer profiles or a streaming engine.
- **Regulatory filing.** SARs are recorded here; submission to your FIU (e.g. goAML) is not implemented.
- **Model governance.** Document, back-test and periodically tune thresholds; record who changed what.
