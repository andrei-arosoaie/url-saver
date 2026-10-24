# Know server

Spring Boot 4.1.2, Temurin Java 27, Gradle 9.8.2; PostgreSQL 17.11 Alpine for development/tests.

## Implemented

- POST /api/v1/articles: create, reuse link-only saves, enrich missing content; duplicate content returns 409 without overwriting.
- PATCH /api/v1/articles/{id}: explicit versioned update; stale versions return 409; omitted fields stay unchanged, null/empty content is rejected.
- GET /api/v1/articles/{id}: read owned content; missing/other-account articles both return 404.
- Sanitized HTML only: preserve article prose, remove executable markup and images. Configurable UTF-8 content and Unicode title limits; reject without truncation.
- Flyway schema, per-account exact-URL SHA-256 uniqueness, database hash verification, transactional concurrency protection and version locking.

List/cursor HTTP endpoint and OpenAPI export are the next checkpoint. Clients and real authentication come later. Runtime requests require an authenticated UUID principal from Spring Security context; no runtime fixture or bypass exists.

## Verify

From the workspace root, run .\server\verify-wsl.ps1. It uses Debian_Recovered WSL Docker, the checksum-pinned Gradle wrapper and a digest-pinned Java image. No Windows Docker installation or TCP exposure is required. Override the distribution with -Distribution if needed.

- Full verification: gradlew.bat check bootJar (requires reachable Docker).
- Unit/architecture tests: gradlew.bat test.
- Integration suite: gradlew.bat integrationTest.

ArticleFlowsIT sends real HTTP requests to a randomly bound server and verifies real PostgreSQL state. Complete flows cover create/read/update, duplicate/explicit update, enrichment, stale versions, ownership, validation, size rejection and concurrent saves/updates. Authentication fixtures exist only in integration-test sources. ArticlePersistenceIT supplies additional database coverage, not a substitute for complete flows. No H2 or Docker-dependent skips.

Reports: build/reports/tests/test and build/reports/tests/integrationTest. Integration classes end with IT; unit classes end in Test; Gradle enforces both.

## Local configuration

Set KNOW_LOCAL_DATABASE_PASSWORD for Docker Compose, then run docker compose up -d from server. Copy config/secrets.example.yml to config/secrets.yml and replace CHANGE_ME. Spring Boot uses mounted YAML, not the Compose bootstrap variable. Missing secrets.yml fails startup; secrets are never committed.

Application records/ports stay separate from presentation records and infrastructure entities. MapStruct maps both boundaries; constructor injection only. Application transactions, explicit database queries, OSIV disabled, Flyway migrations and Hibernate validation. Only the minimal accounts table exists until security implementation.

No deployment or external-service provisioning has been performed. Startup performance has not yet been benchmarked against the under-five-second save-ready target.
