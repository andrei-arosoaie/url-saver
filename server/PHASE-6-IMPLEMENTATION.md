# Phase 6 implementation

## Review checkpoints

1. Backend: review durable processing, contracts, provenance and complete integration tests.
2. Webapp: review identification status, multiple subjects/main subject and explicit retry controls.
3. Final verification: review real-provider behavior before release. Deployment is separate.

Backend review is approved. Backend and webapp integration are implemented locally; final review precedes release.

## Behavior

- First save queues work in the article transaction when identification is enabled. Saving never waits for a provider. Existing articles are not automatically backfilled.
- PostgreSQL stores jobs, claims, results and provider health. Expired jobs recover after a restart; claim IDs prevent old workers from applying stale results. Article deletion removes its job.
- Category and name track `DEFAULT`, `USER` or `AI` provenance independently. Explicit PATCH fields become `USER`, including choosing Other or clearing the name. AI fills unprotected fields only; explicit identification can refresh previous AI results. Manual fields remain protected even when edited during an AI call.
- Missing text or two manual fields skip AI. Changed article text cancels applying an in-flight result. Identification never creates or changes entity memberships or completion history.
- Only article text and enabled category keys/labels are sent. URLs, page titles, screenshots and account IDs are excluded. HTML is parsed to text locally; no source URLs are fetched.
- Large text is examined in bounded chunks with a small overlap. Stop at the first sufficient result. The configurable chunk budget prevents unbounded free-quota consumption; inconclusive results may use the fallback provider.
- Multiple central subjects are permitted; exactly one is main when subjects exist. The main name populates article metadata. All subjects remain available from identification status.
- Try OpenRouter `openrouter/free`, then Groq `openai/gpt-oss-120b`. Invalid responses also trigger fallback. SDK retries are disabled. If neither succeeds, store a failure and wait for a user request.
- Consecutive failures default to 3; confirmed daily quota exhaustion starts cooldown immediately. Cooldown defaults to one day. Both survive restarts and are configurable. Successful provider processing resets its consecutive-failure count.

## Contracts

| Method | Path | Result |
| --- | --- | --- |
| GET | `/api/v1/articles/{articleId}/identification` | 200: status, reason, provider/model, subjects, field provenance and timestamps |
| POST | `/api/v1/articles/{articleId}/identification` | 202: enqueue/reuse background work; manual metadata remains protected |

Both endpoints return 404 for missing and foreign-owned articles. POST returns 503 `IDENTIFICATION_DISABLED` if disabled. POST while a job is pending/running reuses that job. Other terminal attempts can be requested again. Cooldown is not bypassed by requesting a new attempt.

Statuses: `NOT_REQUESTED`, `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `SKIPPED`.
Reasons include `NO_ARTICLE_TEXT`, `NO_MISSING_FIELDS`, `PROVIDERS_UNAVAILABLE`, `INSUFFICIENT_EVIDENCE` and `ARTICLE_CHANGED`.

## Configuration

`./config/identification-secrets.yml` is imported automatically when the server runs from `server/`. The actual file is ignored by Git. Use `config/identification-secrets.example.yml` as the template. Production can instead merge its `know.identification` block into the existing mounted secrets YAML.

Identification is disabled by default until explicitly enabled. Defaults beneath `know.identification`:

| Property | Default |
| --- | --- |
| `provider-order` | `[openrouter, groq]` |
| `consecutive-failure-threshold` | `3` |
| `provider-cooldown` | `1d` |
| `request-timeout` | `30s` |
| `job-lease` | `10m` |
| `chunk-characters` | `6000` |
| `max-chunks` | `8` per provider |
| `poll-interval` | `2s` |

Each provider has `api-key`, `model` and an optional `base-url`. The default URLs are OpenRouter and Groq's official API endpoints. Runtime settings use configuration-property records. The job lease must exceed the configured total provider request budget.

Migration V6 adds provenance, durable jobs and provider health. Existing categories and non-null names are conservatively treated as manual. New provider SDK/framework types belong only in infrastructure; application contracts remain unchanged when adding adapters.

## Verification

Unit tests end in `Test`; integration tests end in `IT`. All integration tests share the same Spring context and disposable PostgreSQL container from Debian WSL Docker. The HTTP provider fixture exercises the real Spring AI adapter and SDK without real credentials or quota. No static mocking is used.

Covered flows include save-to-result, multiple subjects, manual edits during processing, partial manual metadata, explicit clearing, fallback, daily quota cooldown, three-failure cooldown, cooldown expiry, expired-job recovery, stale-worker fencing, deletion during processing, account isolation, chunk early stopping and preservation of entity memberships.

Run `./gradlew test integrationTest exportOpenApi`, then review the exported contract and run `./gradlew check bootJar`. Live provider probes use synthetic text only and do not require a production database.

Verified 31 October 2026: 38 unit/architecture tests and 56 complete integration tests pass; formatting, API drift checks and JAR packaging pass. Both live provider probes returned BOOKS and one subject using synthetic text. The webapp production build and Firefox type-check pass against the exported contract. Supabase was not modified.

## Webapp integration

- The reader shows pending/running/completed/failed/skipped identification, provider, field provenance and identified subjects with one main subject. The timeline and reader metadata refresh after completion.
- The client polls only active work, pauses while hidden and cancels requests when leaving an article. Failed identification requires explicit Retry; failed/unconfirmed POSTs require refreshing status before another request.
- Metadata editing submits only explicitly chosen fields. Choosing the current category deliberately marks it manual; an unchanged field stays available to AI. The user can explicitly preserve an empty name. The editor retains the version shown when editing began so background changes cause a reviewable version conflict.
- All 93 webapp tests, generated API types, TypeScript, ESLint, Prettier and production build pass. Real Firefox 158.0.1 verified automatic completion, multiple subjects, manual choices, explicit retry with provider fallback, link-only and fully manual skips, preserved entity links, and desktop/mobile layouts without horizontal overflow. Screenshots include light and dark themes.

### Reproduce browser verification

Run `./gradlew identificationBrowserPreview` with Docker available. This test-only entry point starts disposable PostgreSQL and controlled provider responses; it loads integration configuration and never production credentials. Application/control ports are 18096/18097. If running inside Docker, publish those ports to loopback only. Start an isolated Firefox BiDi profile, then set `KNOW_IT_API_ORIGIN`, `KNOW_IDENTIFICATION_CONTROL_ORIGIN`, `KNOW_FIREFOX_BIDI_URL` and `KNOW_BROWSER_PROOF_DIR` before running `npm run test:browser:identification` from webapp. The browser script refuses non-loopback application origins. Stop the preview after verification to clean up its container.
