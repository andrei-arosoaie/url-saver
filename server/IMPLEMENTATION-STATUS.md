# Implementation status

Phase 2 Timeline and Entities are implemented locally on feature/webapp-library. Render deployment is separate from this implementation.

## Working functionality

- Firefox saves URL, title and Readability HTML through the approved temporary account. Duplicate confirmation and explicit versioned updates preserve existing content.
- The responsive React webapp is served by Spring Boot. Timeline supports category/search filters, reading, versioned metadata edits, browser navigation and system/light/dark themes.
- Entities supports Book/Movie/TV show/Game, Table/Board/By year, manual creation, versioned title/status edits, repeated completions, precise UTC history correction/removal and linked source pages.
- Timeline Add/Remove links or unlinks an entity without deleting either record or completion history.
- AI is included in the eight-category catalog. Supabase public has migrations V2 and V3 applied; existing article content is preserved.

- Renaming an article or entity preserves every existing association. Links use stable IDs and change only through explicit link/unlink actions.

## Verification

- Backend: 58 passing tests (24 unit/architecture, 34 integration), one shared Spring context and disposable PostgreSQL through Debian WSL Docker. Integration tests exercise complete HTTP/application/database flows, concurrency, ownership, versions, history, links, filtering and constraints. OpenAPI drift and JAR packaging pass.
- Webapp: 61 passing tests; generated types, TypeScript, ESLint, Prettier and production build pass.
- Firefox: 21 passing tests; generated types, TypeScript, ESLint, Prettier and production build pass.
- Browser verification against disposable PostgreSQL covers desktop/mobile views, manual creation, status transitions, repeated and historical completions, microsecond timestamp correction, version conflicts, source navigation and Add/Remove preservation. No test entities, history or links were written to Supabase.
- Live Supabase verification: eight real saved articles and all eight categories loaded; Entities returned its genuine empty state. No sample records were inserted.
- Supabase preview: http://127.0.0.1:18084, process 20992. This is local; Render still runs the earlier 6509809 build.

## Remaining

- Renaming an article or entity preserves every existing association. Links use stable IDs and change only through explicit link/unlink actions.
- Timeline excerpts await a product decision; the current list API supplies no excerpt.
- Real authentication and Firefox desktop/Android device verification remain outstanding.
- Startup remains above the under-five-second target; the prior Supabase startup reported 10.41 seconds. This is not a save-ready benchmark.
