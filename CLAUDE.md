# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`AGENTS.md` (Vietnamese) holds the binding project rules — read it first. Key points are summarized below.

## Project

"Nền tảng khóa học vẽ và dụng cụ vẽ": marketplace where Teachers sell video courses and drawing supplies, Parents buy for Children. Java 21 + Spring Boot 4.1.1 (Gradle), PostgreSQL, JPA, Flyway, ShedLock, JWT (HS256). Base package `com.example.art_course`.

## Commands

```
./gradlew build                 # compile + test
./gradlew test                  # tests (Testcontainers Postgres; needs Docker)
./gradlew test --tests 'com.example.art_course.modules.payment.PaymentFulfillmentTests'
./gradlew test --tests '*CommerceTests.methodName'
./gradlew bootRun --args='--spring.profiles.active=dev'
```

No lint task is configured. `scripts/*.ps1` manage the local dev DB (`dev-up`, `reset-dev-db`, `dev-seed`, `dev-tokens`); they refuse any DB other than `art_course_dev`, localhost only.

## Database safety (hard rules from AGENTS.md)

- Never read `.env` (real Supabase credentials); never run the app, tests or migrations against Supabase (details in `AGENTS.md`).
- Only the `dev` profile enables Flyway (`DATABASE_URL_DEV` in `.env`); `supabase` is runtime-only. Only `.env` is loaded; real OS env vars win over it.
- Migrations: `src/main/resources/db/migration/V*__*.sql`, applied on top of base schema `docs/ART_COUSE.sql`.

## Architecture

Two backend teams share one codebase and DB:

- **BE1** (read-only for BE2 work): users, profiles, courses, lessons, lesson_supplies, conversations, messages, submissions, customize_requests, offers — packages like `auth`, `child`, `course`, `lesson`, `customize_request`, `enrollment`, `video`, etc.
- **BE2** (owned): carts, orders/order_details, payments, refunds, withdrawals, money ledger, system_config, moderation, notifications, webhook logs, scheduled jobs — under `modules/*` plus `common/statemachine`, `jobs`.
- Needed BE1 table changes are NOT made directly; record them in `docs/handoff-be1.md`.
- BE1↔BE2 coupling goes only through `contracts/` (`IEnrollmentContract`, `IModerationContract`, `IVideoAccessContract`, and `contracts/events` ApplicationEvents).

Business invariants to preserve:

- One order = one Teacher (BR-04); multi-teacher carts split into per-teacher orders, each with its own payment.
- Order detail is a course XOR a supply (DB CHECK). Commission computed once at payment time (rate per product type, admin-configured) and stored; `gross = commission + net`. Shipping fee goes to the Teacher with no commission; gateway fee is borne by the platform.
- Money is `BigDecimal`, `HALF_UP`, VND, rounded in one place. Never floats.
- Money/stock state changes are `@Transactional`; oversell prevented with pessimistic lock or conditional `UPDATE ... stock >= qty`.
- Money ledger is append-only (no UPDATE/DELETE) — see `V2__ledger_hardening.sql`.
- Funds release only when every line of the order is eligible; no withdrawable balance on payment success.
- Payment webhooks: idempotent, signature-verified, amount must match order total; on mismatch change nothing and log a warning. Gateways (VNPay/MoMo) sit behind `PaymentGateway` interface, sandbox/mock via env vars.
- State machines use the exact SRS §8.1 status names (`common/statemachine`); invalid transitions rejected, each transition tested.
- Scheduled jobs use `@Scheduled` + ShedLock (JDBC) and must be idempotent.
- Role checks enforced in backend; Child accounts can call no purchase/payment API.
- New code carries short SRS-code comments (e.g. `// PAY-02`). No out-of-scope refactors or features.

Source of truth priority: SRS draft 3 (`docs/SRS_v3.pdf`) > ERD (`docs/erd.drawio.svg`) + `docs/decisions.md` > old SQL. Ambiguities: pick the minimal option and log it in `docs/open-questions.md`; don't stop work.

## Git workflow

Work on the current branch; do not create branches, commit, or push unless the user asks.

## API docs

OpenAPI is generated from code by springdoc (`/swagger-ui.html`, `/v3/api-docs` when running); sample requests in `docs/api/requests.http`; JWT contract with BE1 in `docs/contracts/jwt-contract.md`. `docs/legacy-ts/` is the abandoned Node/TS implementation, reference only.
