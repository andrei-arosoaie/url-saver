# Backend listing and OpenAPI — review checkpoint

Backend foundation and save/update checkpoints accepted. Listing/OpenAPI is the current review checkpoint; do not start clients until user review.

All four article endpoints are implemented: POST capture/reuse/enrichment, PATCH explicit versioned update, GET owned detail, and GET cursor-paginated summaries. Listing defaults to 20 (maximum 100), newest first by saved_at DESC and UUID DESC. Fetch one extra summary to determine nextCursor; no total count or article HTML in list queries/responses. Bookmarks are opaque, strictly validated and never grant account access.

Springdoc 3.1.1 exports the running backend contract to build/openapi/openapi.yaml. The reviewed contract is server/openapi/openapi.yaml. Gradle verifyOpenApi compares them and is part of check/CI; exportOpenApi deliberately updates the reviewed file. Swagger UI/API docs are disabled by default and enabled in integration tests. Spring Boot's BOM remains authoritative for managed dependencies, including Jackson.

Verification: 15 unit/architecture tests, 12 complete HTTP contract/behavior tests and 11 supporting persistence tests (38 total). All backend IT classes reuse one Spring context, random-port HTTP server and disposable PostgreSQL container; an inherited assertion verifies context identity. Fixtures reset between tests under a shared resource lock. No Docker-dependent skips or H2.

Full HTTP flows cover saving, reading, listing, pagination after new saves, timestamp ties, default/max limits, malformed cursors, enrichment, explicit updates, stale versions, ownership, invalid requests, size rejection and concurrency. The OpenAPI test checks all four operations, payloads, nullability, integer pagination, error fields, Location header and deterministic YAML export. Authentication fixtures remain test-only.

Repeat full verification: .\server\verify-wsl.ps1. Refresh the reviewed contract intentionally: .\server\verify-wsl.ps1 -ExportOpenApi. PowerShell helpers check native process exit codes, so compiler warnings do not cause false failures in Windows PowerShell 5. Integration classes end in IT; regular tests end in Test.

Next checkpoint: Firefox extension client, then responsive webpage, then security. Clients and real security are not implemented. Startup target remains under five seconds to save-ready without first-save warm-up; deployment-like benchmarking has not yet been performed. No deployment or remote CI result is claimed.

Toolchain: Spring Boot 4.1.2, Temurin Java 27+35, Gradle 9.8.2, PostgreSQL 17.11 Alpine, Spotless 8.10.4 / Google Java Format 1.36.0, MapStruct 1.6.3, ArchUnit 1.5.1, OWASP Java HTML Sanitizer 20261011.1, Springdoc 3.1.1. Other managed versions inherit Spring BOM.
