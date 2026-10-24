# Backend save and update — review checkpoint

Backend foundation accepted when the user authorized the next phase. Save/update implementation is ready for review after final verification; do not advance without user review.

Implemented: POST capture/reuse/enrichment, duplicate 409 with existingArticleId, PATCH explicit update with JSON version, GET article detail for full save/read/update flows, and Spring default error attributes extended with reason. Missing/foreign-owned records return the same 404. Responses never expose accountId.

OWASP Java HTML Sanitizer 20261011.1 preserves article text and allowed formatting; removes scripts, unsafe attributes, images and unsafe link destinations. Input/output content limits are UTF-8 byte limits; title limit counts Unicode code points. No truncation, content clearing or silent link-only downgrade. Unsupported wrappers retain text. Invalid/oversized requests do not commit partial changes.

Concurrent inserts use PostgreSQL ON CONFLICT with the existing per-account URL-hash unique index; application transactions lock existing rows before enrichment/update. Version checks prevent lost updates, unchanged records retain their version, and original saved timestamps remain unchanged.

Verification: 13 unit/architecture tests, 7 complete HTTP-to-PostgreSQL flow tests and 11 additional persistence tests. Full check, formatting, test-name enforcement and JAR packaging run through Debian_Recovered WSL Docker. Final verification evidence is recorded in .tools/backend-save-update-verification.log; no test skips are accepted.

HTTP tests start the real server on a random port and verify persisted state after create/read/update, duplicates, enrichment, stale versions, ownership errors, invalid requests, size rejection, and concurrent saves/updates. Test-only authentication supplies Security context; no production bypass exists. Mockito uses subclass mocks/reflection, never static mocks.

Next checkpoint: list/cursor endpoint, remaining retrieval flows, OpenAPI export and drift enforcement. Extension, webpage and real security are not implemented. Startup target is not yet benchmarked. CI definition exists but has not run remotely. No deployment.

Toolchain: Spring Boot 4.1.2, Temurin Java 27+35, Gradle 9.8.2, PostgreSQL 17.11 Alpine, Spotless 8.10.4 / Google Java Format 1.36.0, MapStruct 1.6.3, ArchUnit 1.5.1; other managed versions inherit Spring BOM. Spring Security core supplies context only at this stage.

Repeat from workspace root: .\server\verify-wsl.ps1. Test classes: IT suffix for integration; Test suffix for regular tests. Full-flow integration coverage is mandatory; repository tests alone never establish feature completion.

All backend IT classes extend BackendIntegrationSupport: one cached Spring context, random-port HTTP server and JVM-scoped disposable PostgreSQL container. An inherited assertion verifies context identity across classes. Shared database fixtures reset between tests; a resource lock prevents parallel fixture resets. No per-class container shutdown or context eviction.
