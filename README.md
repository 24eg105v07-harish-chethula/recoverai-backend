# RecoverAI backend

Spring Boot API for RecoverAI. The React app is in `../recoverai-frontend-main`.

## Local setup

Use Java 21 and PostgreSQL. Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in your local environment. Set `JWT_SECRET` to at least 32 UTF-8 bytes. For initial admin provisioning, set both `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` (16 or more characters); the bootstrap does not promote or overwrite an existing account. Configure `CORS_ALLOWED_ORIGINS` with the exact frontend origins.

Start the API with `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`). Run backend verification with `./mvnw clean test`.

## Security and data

Signup creates USER accounts only. Admin accounts are provisioned from environment configuration. JWTs are short lived and account status and role are checked against the database on each authenticated request. Payment ownership is assigned by an administrator; users can create cases only from failed payments assigned to them. Existing unowned cases remain admin-only until assigned. Confirmed collection records remain the source of recovered-money metrics. Recovery attempt limits and approval requirements remain enforced by the API.

The schema uses Hibernate's additive `ddl-auto=update` behavior for existing deployments. Back up and validate the existing database before deployment; do not reset or drop it. A reviewed, versioned migration process is recommended before production schema evolution. Login throttling is in-memory and should be replaced with a shared store before running multiple API instances.

## Performance work

- Dashboard status counts and revenue at risk are computed by two database aggregate queries; confirmed collections use one aggregate query. The API no longer loads all recovery cases and actions into Java just to calculate dashboard metrics. User-scoped aggregates filter by case owner in the database.
- Recovery cases, actions, owned failed payments, users, and admin assignment lists use newest-first pagination (50 rows by default, capped at 100); user queries apply ownership filters before paging.
- Indexes cover the observed owner/status filters on payments and cases.
- Unchanged rule-based recommendations reuse the stored result using a fingerprint of the case status, risk, amount, confirmed recovered amount, and attempt count. This does not cache across users or cases and does not change action authorization.
- The frontend defers the dashboard app and admin user-management route until those routes are opened. Its latest production build reports a 288.50 kB (94.56 kB gzip) initial JS chunk, with 16.17 kB dashboard and 5.70 kB admin chunks loaded separately.

No production database or load test was used. Endpoint latency and throughput have not been benchmarked; frontend chunk sizes are build measurements, not end-to-end response-time claims.
