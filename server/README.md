# Know server

Spring Boot 4.1.2, Temurin Java 27, Gradle 9.8.2; PostgreSQL 17.11 Alpine for development/tests.

## Implemented

- POST /api/v1/articles: create, reuse link-only saves, enrich missing content; duplicate content returns 409 without overwriting.
- PATCH /api/v1/articles/{id}: explicit versioned update; stale versions return 409; omitted fields stay unchanged, null/empty content is rejected.
- GET /api/v1/articles/{id}: read owned content; missing/other-account articles both return 404.
- GET /api/v1/articles: owned summaries, newest first; default 20/max 100; opaque nextCursor for Load more, null at the end. No HTML or total-count query.
- Sanitized HTML only: preserve article prose, remove executable markup and images. Configurable UTF-8 content and Unicode title limits; reject without truncation.
- Flyway schema, per-account exact-URL SHA-256 uniqueness, database hash verification, transactional concurrency protection and version locking.

All four article endpoints and the Firefox capture client are implemented. The responsive webpage and real authentication come later. See ../extension/firefox/README.md for client checks and current limitations. This functionality-only iteration enables one shared temporary account via know.temporary-account.enabled in application.yml. Unauthenticated requests use account 00000000-0000-4000-8000-000000000001; it is created automatically. Setting enabled=false restores the authentication requirement. An authenticated UUID principal still takes precedence.

## Verify

From the workspace root, run .\server\verify-wsl.ps1. It uses Debian_Recovered WSL Docker, the checksum-pinned Gradle wrapper and a digest-pinned Java image. No Windows Docker installation or TCP exposure is required. Override the distribution with -Distribution if needed.

- Full verification: gradlew.bat check bootJar (requires reachable Docker).
- Unit/architecture tests: gradlew.bat test.
- Integration suite: gradlew.bat integrationTest.

ArticleFlowsIT sends real HTTP requests to a randomly bound server and verifies real PostgreSQL state. Complete flows cover create/read/update, duplicate/explicit update, enrichment, stale versions, ownership, validation, size rejection and concurrent saves/updates. Authentication fixtures exist only in integration-test sources. ArticlePersistenceIT supplies additional database coverage, not a substitute for complete flows. No H2 or Docker-dependent skips.

Reports: build/reports/tests/test and build/reports/tests/integrationTest. Integration classes end with IT; unit classes end in Test; Gradle enforces both.

## OpenAPI contract

The reviewed schema is openapi/openapi.yaml, exported from the real Springdoc HTTP endpoint during integration tests. Full check includes verifyOpenApi and fails on drift. To intentionally update it, run .\server\verify-wsl.ps1 -ExportOpenApi from the workspace root (or ./gradlew exportOpenApi from server), review the YAML and commit it. Generated client types remain build outputs.

Swagger UI and /v3/api-docs are disabled by default. Mounted local YAML can enable springdoc.api-docs.enabled and springdoc.swagger-ui.enabled. Integration tests enable both; no Spring profiles are introduced. Spring Boot BOM versions remain authoritative, including Jackson used by Springdoc.

## Render image

See [DEPLOYMENT.md](DEPLOYMENT.md) for the prepared Dockerfile, manual private-GHCR publishing workflow and Render Existing Image setup. The image is digest-pinned, runs as UID 10001, contains the JRE and application only, and reads mounted /etc/secrets/secrets.yml. PORT is supplied by Render; IDE use defaults to 8080.

Local image verification: build the application with verify-wsl.ps1, then in Debian run docker build --platform linux/amd64 -t know-server:local . from server, followed by sh scripts/verify-image.sh know-server:local. A private GHCR image was subsequently published and a Render deployment attempted; the observed failure was database configuration.

## Local configuration

Set KNOW_LOCAL_DATABASE_PASSWORD for Docker Compose, then run docker compose up -d from server. Copy config/secrets.example.yml to config/secrets.yml and replace CHANGE_ME. Spring Boot uses mounted YAML, not the Compose bootstrap variable. Missing secrets.yml fails startup; secrets are never committed.

Application records/ports stay separate from presentation records and infrastructure entities. MapStruct maps both boundaries; constructor injection only. Application transactions, explicit database queries, OSIV disabled, Flyway migrations and Hibernate validation. Only the minimal accounts table exists until security implementation.

Local startup with Supabase succeeded in 11.6 seconds and liveness returned UP; the process was stopped as requested. This exceeds the under-five-second target and is not a save-ready benchmark.

All backend IT classes extend BackendIntegrationSupport: one cached Spring context, random-port HTTP server and JVM-scoped disposable PostgreSQL container. An inherited assertion verifies context identity across classes. Shared database fixtures reset between tests; a resource lock prevents parallel fixture resets. No per-class container shutdown or context eviction.
