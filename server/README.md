# Know server

Build the responsive webapp first: from `webapp`, run `npm ci` and `npm run build`. Gradle packages its generated assets into the same Spring Boot JAR and serves the library at `/`. Run backend Gradle commands sequentially when sharing the build directory between Windows and WSL.

Spring Boot 4.1.2, Temurin Java 27, Gradle 9.8.2; PostgreSQL 17.11 Alpine for development/tests.

## Implemented

- POST /api/v1/articles: create, reuse link-only saves, enrich missing content; duplicate content returns 409 without overwriting.
- PATCH /api/v1/articles/{id}: explicit versioned update; stale versions return 409; omitted fields stay unchanged, null/empty content is rejected.
- GET /api/v1/articles/{id}: read owned content; missing/other-account articles both return 404.
- GET /api/v1/articles: owned summaries, newest first; default 20/max 100; opaque nextCursor for Load more, null at the end. No HTML or total-count query.
- Sanitized HTML only: preserve article prose, remove executable markup and images. Configurable UTF-8 content and Unicode title limits; reject without truncation.
- Flyway schema, per-account exact-URL SHA-256 uniqueness, database hash verification, transactional concurrency protection and version locking.

All four article endpoints and the Firefox capture client are implemented. The responsive webpage is implemented in ../webapp and served from this same application. Real authentication comes later. See ../extension/firefox/README.md for client checks and current limitations. This functionality-only iteration enables one shared temporary account via know.temporary-account.enabled in application.yml. Unauthenticated requests use account 00000000-0000-4000-8000-000000000001; it is created automatically. Setting enabled=false restores the authentication requirement. An authenticated UUID principal still takes precedence.

## Entities backend

Phase 8 adds TV-show search, shared catalogs and viewing progress inside Entities.
See [Phase 8 implementation and settings](PHASE8.md).

- `/api/v1/entities`: GET one required type with search/status/completion-year filters and cursor pagination; POST manual creation.
- `/api/v1/entities/{id}`: GET details; PATCH an explicit versioned title/status change.
- `/api/v1/entities/years`: GET UTC completion years with distinct entity and event counts.
- `/api/v1/entities/{id}/completions`: GET paginated history; POST a distinct completion using the entity version.
- `/api/v1/entities/{id}/completions/{eventId}`: PATCH a timestamp or DELETE one event using its completion version.
- `/api/v1/articles/{articleId}/entities`: GET linked entities; POST explicit exact-title/type create-or-reuse.
- `/api/v1/articles/{articleId}/entities/{entityId}`: PUT an existing entity link; DELETE only that association.
- `/api/v1/entities/{id}/articles`: GET paginated source summaries; article content remains on the article-detail endpoint.

Types are BOOK, MOVIE, TV_SHOW and GAME. Status transitions into READ/SEEN/FINISHED append history atomically; repeated unchanged status does not. Explicit completion uses the feature-local UTC Clock when a timestamp is omitted. Correcting/deleting history preserves current status and increments the parent version only for an actual change. Article labels never implicitly relink or rename entities. No general entity-delete route exists.

Exact unmodified titles use per-owner/type SHA-256 uniqueness, verified by PostgreSQL and full-title comparison. Entity mutations use a transaction-scoped advisory lock per account, plus database uniqueness and version checks, so concurrent creates/renames cannot bypass identity rules. Different accounts do not share this lock. List filtering uses Criteria before SQL pagination; completion statistics are batched rather than fetched implicitly through ORM associations.

Settings: `know.entities.max-title-code-points` (1000); `know.pagination.default-limit` (20) and `max-limit` (100). Migration V2 contains the entity tables. V3 adds the AI category to existing accounts; new accounts receive all eight category defaults. See [the exported API contract](openapi/openapi.yaml) for complete payloads and responses.

`EntityFlowsIT` verifies complete real HTTP/application/PostgreSQL flows, including capture-to-entity, manual creation, linking, version conflicts, completion history, UTC-year queries, correction/deletion, unlink preservation, ownership and concurrent writes. Unit tests verify injected clock behavior, Unicode title limits, invalid transitions and hash-collision refusal. These tests reuse the same context/container as the article tests.
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

Phase 7 Gemini credentials: fill `know.connections.embedding.gemini.api-key`
in `config/embedding-secrets.yml` (git-ignored). The committed template is
`config/embedding-secrets.example.yml`. Both files use `gemini-embedding-001`.
For Render, create a secret file named `embedding-secrets.yml`; it is loaded
from `/etc/secrets/embedding-secrets.yml`. Configuring a key alone does not send API
requests. Both `know.connections.enabled` and
`know.connections.embedding.live-requests-enabled` default to false. Review the
current Gemini terms and confirm an unbilled project before enabling live calls;
`unbilled-project-confirmed` is an operator safeguard, not a provider billing switch.

Phase 7 uses pgvector (`pgvector/pgvector:0.8.7-pg17` for local development and tests).
Migration V7 installs the extension. The database migration role needs permission
to enable it. Application connections include `public, extensions` in the search path.
Exact cosine comparisons initially avoid approximate-index recall loss. The 0.80
threshold and symmetric token-weighted best-chunk aggregation require real-model
relevance validation before release; controlled-vector tests do not establish quality.

Processing is durable in PostgreSQL. Local workers share leased provider permits,
rate-limit cooldowns and budgets across instances. Default local safety budgets
are 5 requests/minute and 100 embedding requests/day; these are configurable caps,
not claims about your Google quota. Token counting and embedding share concurrency;
only embedding calls consume the local daily embedding budget. A crash may repeat
an external call, while cached chunks and publication fences prevent stale results.

### Phase 7 webapp verification

`connectionsBrowserPreview` starts a disposable PostgreSQL/pgvector database, the
real backend on port 18096 and local Gemini HTTP responses controlled on port
18097. It loads only the integration configuration, never local secrets. Run it
with the Debian Docker toolchain and host networking (`TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1`).
Build the webapp first. Then launch an isolated Firefox BiDi profile and run
`npm run test:browser:connections` in `webapp`, with these environment variables:

- `KNOW_IT_API_ORIGIN`: local disposable backend origin.
- `KNOW_CONNECTIONS_CONTROL_ORIGIN`: local fixture control origin.
- `KNOW_FIREFOX_BIDI_URL`: isolated Firefox WebSocket session URL.
- `KNOW_BROWSER_PROOF_DIR`: output directory for screenshots.

The browser check verifies processing, shared and semantic reasons, article
navigation/Back, removal on both sides after reload, manual retry, quota resume
and link-only behavior at desktop and mobile widths. It consumes no live quota.
It does not replace actual extension/device verification or model relevance tests.

Set KNOW_LOCAL_DATABASE_PASSWORD for Docker Compose, then run docker compose up -d from server. Copy config/secrets.example.yml to config/secrets.yml and replace CHANGE_ME. Spring Boot uses mounted YAML, not the Compose bootstrap variable. Missing secrets.yml fails startup; secrets are never committed.

Application records/ports stay separate from presentation records and infrastructure entities. MapStruct maps both boundaries; constructor injection only. Application transactions, explicit database queries, OSIV disabled, Flyway migrations and Hibernate validation. Only the minimal accounts table exists until security implementation.

Local startup with Supabase succeeded in 11.6 seconds and liveness returned UP; the process was stopped as requested. This exceeds the under-five-second target and is not a save-ready benchmark.

All backend IT classes extend BackendIntegrationSupport: one cached Spring context, random-port HTTP server and JVM-scoped disposable PostgreSQL container. An inherited assertion verifies context identity across classes. Shared database fixtures reset between tests; a resource lock prevents parallel fixture resets. No per-class container shutdown or context eviction.
