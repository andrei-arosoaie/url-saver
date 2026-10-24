# Firefox extension and temporary-account saving

Current iteration: article saving without authentication, using one explicitly approved shared temporary account. Google sign-in remains out of scope.

- All four article endpoints are implemented: save, explicit versioned update, read, and cursor-paginated list.
- know.temporary-account.enabled is true in application.yml. Account UUID: 00000000-0000-4000-8000-000000000001. It is created automatically; the server assigns ownership. All unauthenticated installations share these articles. Setting enabled=false restores the authentication requirement; authenticated accounts take precedence.
- Temporary-account provisioning uses its own transaction so read-only article operations can safely resolve the account. Existing account data is never overwritten.
- The Firefox extension captures URL/title/Readability HTML, sends saves without requiring a token, confirms results, prompts for duplicates and uses versioned explicit updates. No persistent capture queue.

Verification: 42 backend tests passed (18 unit/architecture; 24 integration), with all ITs sharing one context and real disposable PostgreSQL through Debian WSL Docker. Full temporary-account HTTP flow covers save, read, duplicate rejection, explicit update, list and persisted ownership/content. OpenAPI drift check and packaging pass.

Extension: 20 tests, type checks, ESLint, Prettier and production Firefox build pass. Mozilla validation has no errors and four documented bundled-dependency warnings. Actual desktop/Android device verification remains outstanding.

Updated local container know-server:temporary-account passes the PostgreSQL smoke test, including unauthenticated saving and persisted content under 512 MiB / 1 CPU. This image has not been published or deployed to Render. Render must receive the updated image before the rebuilt extension can use temporary-account saving there.

Next review checkpoint: Firefox extension. Responsive webpage and real security remain unimplemented. Startup target remains under five seconds to save-ready; the earlier local Supabase startup measured 11.6 seconds and is not a save-ready benchmark.
