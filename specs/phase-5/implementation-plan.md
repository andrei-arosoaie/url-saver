# Phase 5 — Category management

## Scope

Create personal categories, disable predefined categories, and remove personal categories. Preserve article and entity associations. Category names are labels; stable keys remain unchanged when renamed. Changes affect only the owning account.

## Approved behavior

- Show the article count for every category.
- Block disabling a category while its article count is greater than zero.
- Block removing a custom category while its article count is greater than zero. Never move articles automatically.
- Allow editing names and colors for both predefined and custom categories; preserve stable keys and all associations.
- Category names are unique per account, ignoring case and surrounding spaces.
- Provide a dedicated, responsive Categories page.
- Add permanent article deletion after confirmation. Delete its screenshot and article-to-entity associations; keep the entities themselves.

## Existing implementation

- `GET /api/v1/categories` returns the account catalog and seeds missing defaults.
- Categories already contain a stable key, display name, color, default flag, enabled flag, creation time, and optimistic-lock version.
- Articles reference categories through an account-scoped database foreign key.
- Timeline filters and article details already consume category records.
- Reuse the `com.know.categories` feature and its presentation/application/infrastructure boundaries.

## Review checkpoints

1. **Behavior and contracts:** Review request/response examples together before implementation. Define concurrency and validation errors using existing API conventions.
2. **Backend:** implement approved owner-scoped operations, migrations only where needed, and version checks. Verify unit tests and complete HTTP-to-PostgreSQL integration flows before review.
3. **Webapp:** implement the approved responsive management view. Update filters and assignment controls while keeping existing article labels and relationships accurate. Verify component tests and production build before review.
4. **Complete verification:** exercise create, rename, recolor, disable/re-enable, and approved removal behavior through the clients and backend. Verify stale-version conflicts, account isolation, reference integrity, and existing timeline/entity/screenshot behavior. Report results and remaining manual checks for review.

## Test requirements

- Unit tests end in `Test`; backend integration tests end in `IT`.
- Integration tests share the existing Spring context and PostgreSQL container.
- Test complete user flows through HTTP, including persisted results and subsequent reads; repository-only tests do not establish completion.
- Renaming must preserve stable keys, article associations, and entity relationships.
- Do not silently delete or reclassify articles. Do not change production data during verification.
- Authentication and deployment changes are outside this phase.

Category behavior, permanent deletion and the endpoint contracts in contracts.md are approved. Backend implemented: 34 unit/architecture tests and 45 end-to-end integration tests pass; OpenAPI exported and JAR packaged. Backend review approved. The webapp and complete real-Firefox flow are verified; final user review remains.

## Checkpoint status

- [x] Behavior and endpoint contracts approved.
- [x] Backend implemented and all 79 backend tests passed against disposable PostgreSQL.
- [x] OpenAPI exported, application JAR built, and packaged Docker image smoke verification passed.
- [x] Backend review approved by the user.
- [x] Dedicated Categories page and confirmed article deletion UI; 82 webapp tests, generated types, lint, formatting and production build pass.
- [x] Real Firefox category lifecycle, protected actions, article deletion and entity preservation verified against the disposable packaged server; desktop/mobile screenshots reviewed with no horizontal overflow.
- [ ] Final user review, commit/PR and deployment.
