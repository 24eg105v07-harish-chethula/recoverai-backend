# RecoverAI P0 Audit & Local Patch Report

Date: 2026-10-12

## Scope and limitations

Inspected the uploaded frontend and backend ZIPs. Changes in these archives are local patches only; they have **not** been pushed to GitHub or deployed to Vercel/Render. No production database was accessed or modified.

## Confirmed findings from source

| ID | Severity | Finding | Status |
|---|---|---|---|
| P0-1 | High | `src/App.jsx` called `http://localhost:8080/api`, so the deployed frontend would attempt to call the visitor's own computer. | Fixed in patched frontend |
| P0-2 | High | `src/services/api.js` used a Render hostname that did not match the supplied backend URL. | Fixed in patched frontend |
| P0-3 | High | Global CORS configuration allowed localhost origins only; the deployed Vercel origin was absent. | Fixed in patched backend; exact production origin is allowed |
| P0-4 | High | `application.properties` contained a literal database password fallback. | Removed; `DB_PASSWORD` is now environment-driven |
| P0-5 | Medium | PostgreSQL Hibernate dialect was not explicitly configured. | Fixed |
| P0-6 | Medium | Maven compiler target was Java 17 despite the Java 21 deployment target. | Aligned to Java 21 |
| P0-7 | High | Recovery case status enum had no `EXHAUSTED` state and failed third-attempt handling did not transition the case to a terminal state. | Added `EXHAUSTED`; third failed recovery attempt transitions to it |
| P0-8 | High | Recovery-case attempt checks were not serialized with a row lock, leaving a race risk under concurrent requests. | Added pessimistic case-row locking for state-changing action and collection paths |
| P0-9 | Medium | No Actuator health endpoint dependency/configuration was present. | Added Actuator health exposure |
| P0-10 | Medium | Existing test suite only had a Spring context-load test and depended on an external PostgreSQL default. | Added H2 test dependency and `test` profile for context-load tests |

## Additional important findings

- Authentication and authorization were not found in the inspected source: no Spring Security configuration, password encoder, login/register routes, or JWT/session implementation was present. The API should be treated as unauthenticated until this is implemented and verified.
- The source included a committed database password value. It has been removed from the patched configuration, but if that credential was ever used or committed to a shared repository, rotate it in Neon/Render and remove it from repository history where appropriate. Do not share the new value in chat.
- `spring.jpa.hibernate.ddl-auto=update` remains configured to preserve the existing project's current behavior. For production hardening, introduce a reviewed Flyway/Liquibase migration plan rather than relying on automatic schema updates.
- The recovery collection endpoint accepts a reference code supplied by the caller. It validates uniqueness and amount bounds, but this is not equivalent to verifying a payment with a real payment provider. For the demonstration, describe this as manually recorded/confirmed collection unless actual payment-provider verification is integrated.
- CORS currently allows the exact deployed Vercel origin and local development origins. Configure `CORS_ALLOWED_ORIGINS` in Render for any additional approved preview domains; do not use a blanket wildcard unless deliberately accepted.
- The existing AI controller returns generic server errors, but the AI behavior and runtime provider configuration were not exhaustively validated in this patch.

## Changes made in the local patch

### Backend
- Removed the literal DB password from application configuration.
- Kept the `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `PORT` environment-variable contract.
- Explicitly configured PostgreSQL dialect.
- Added configurable CORS origins with the production Vercel origin.
- Removed the AI controller's separate localhost-only CORS override.
- Aligned Maven Java version to 21.
- Added Actuator and exposed the health endpoint at `/actuator/health`.
- Added H2 test dependency and `src/test/resources/application-test.properties`.
- Added `EXHAUSTED` recovery-case status.
- Added pessimistic row locking to serialize recovery state changes.
- After a recovery action fails on attempt 3, the case transitions to `EXHAUSTED`.
- Prevented new actions and collection recording for exhausted cases pending human review.
- Included exhausted cases in dashboard summary.

### Frontend
- Replaced the hardcoded localhost API URL with `VITE_API_BASE_URL` and the supplied Render URL as fallback.
- Corrected the API-client base URL to the supplied backend hostname.
- Added `.env.example`.
- Displayed exhausted-case metrics in the dashboard.

## Verification performed

- Static checks confirmed the patched frontend source uses `VITE_API_BASE_URL`.
- Static checks confirmed no hardcoded `http://localhost:8080/api` remains in the frontend source.
- Static checks confirmed the configured CORS origin, environment-driven DB password, PostgreSQL dialect, Java 21 setting, `EXHAUSTED` status, row-lock finder, and Actuator health exposure.
- `pom.xml` parsed as valid XML.
- **Frontend build: BLOCKED.** `npm run build` could not run because Vite was not installed (`vite: not found`). Dependency installation did not complete in the available execution window.
- **Backend Maven tests: BLOCKED.** `bash mvnw test` could not download the Maven distribution from Maven Central in this environment.
- **Live deployment verification: BLOCKED.** The current environment could not fetch the supplied Vercel/Render URLs. No live endpoint or database behavior is claimed as verified.
- No test is reported as passing unless it was actually executed successfully.

## Required deployment settings

### Vercel
Set:
- `VITE_API_BASE_URL` = `https://recoverai-backend-klo0.onrender.com`

Redeploy the frontend after setting the variable.

### Render
Set:
- `DB_URL` = the full Neon JDBC URL, typically `jdbc:postgresql://<host>/<database>?sslmode=require`
- `DB_USERNAME` = the Neon database role
- `DB_PASSWORD` = the rotated database password
- `CORS_ALLOWED_ORIGINS` = `https://recoverai-frontend-cyan.vercel.app`
- `PORT` is usually supplied by Render; keep the app's `${PORT:8080}` binding.

Do not paste credentials into source code or chat. After deployment, test `https://recoverai-backend-klo0.onrender.com/actuator/health`, then open the frontend and inspect browser Network/Console errors.

## Current go/no-go

**NO-GO for production or a claim of fully working deployment.** Source-level P0 patches are prepared, but builds, tests, live API behavior, Neon connectivity, and deployment settings remain unverified. Authentication/authorization is a separate high-priority gap and is not included in this P0 patch.
