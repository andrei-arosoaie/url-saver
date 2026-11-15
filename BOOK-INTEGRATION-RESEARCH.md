# Deferred: Book information integration — investigation

Researched 6 November 2026. Proposal only; not implemented or approved scope.

## Recommendation

Use a replaceable book catalog adapter, initially evaluating Open Library for
human-triggered, modest-volume lookup. Its open metadata is attractive for
commercial reuse, but its public API is not an unrestricted commercial backend.
For a growing paid service, obtain agreement for the use case or operate a local
index of permitted data dumps. Free data still incurs hosting and maintenance.
Do not select Google Books as the default for a paid Know application without
written permission: its API-specific terms prohibit charging application users
without a separate agreement or permission.

This investigation compares documented terms and capabilities; it is not a
measured coverage, latency or production reliability benchmark.

## Providers and reuse

| Provider | Commercial reuse and service limits | Proposed fit |
| --- | --- | --- |
| Open Library | Catalog donations are described as public domain; contributions requested under CC0. Public APIs prioritize human discovery, encourage caching and identified requests, and exclude high-traffic commercial backend use. Published default limit is 1 request/second, or 3 for identified requests. | Best initial candidate, subject to use-case fit. Work/edition records, author/title/ISBN search and covers. |
| Wikidata | Structured data is CC0 and reusable commercially. This does not license linked images or Wikipedia prose. Public endpoint policies remain separate from data licensing. | Optional identifier/author enrichment; completeness for the target library must be tested. Not the proposed sole book discovery backend. |
| BookBrainz | Core data is CC0; public web service and data exports are available. Public hosting capacity and usage rules still need checking before production. | Alternative open catalog worth benchmarking. No assumption of complete synopsis, cover or recent-edition coverage. |
| Google Books | Accessible API, but application fees require separate agreement or written permission under Books API terms. General API terms also restrict permanent copies and caching beyond permitted headers. | Conditional option for a free application; not a blanket free-commercial choice for paid Know. |

### Covers and descriptions

Open metadata licensing does not establish that every jacket image or publisher
description is freely licensed. Track field provenance and image source links;
do not label all content CC0 or mirror jackets automatically into ImageKit.
Open Library's cover guidance asks public pages to use covers.openlibrary.org
URLs and appreciates a backlink. Prefer CoverID/OLID URLs: other lookup keys
have a separately documented limit of 100 requests/IP per five minutes.
Use `default=false` and a local placeholder on missing covers. A direct image
request exposes the viewer's IP to the image host; document this and use an
appropriate referrer policy. A rights-cleared cover strategy remains a release
decision for commercial distribution.

Library of Congress is a possible specialist supplement, not the proposed
general modern-book source: API availability does not grant rights to every
collection item. Project Gutenberg is useful for selected older works and
reading links, rather than a general modern catalog; ebook reuse depends on
territory, individual rights and trademark conditions. Neither is needed for
initial scope. ISBNdb is not counted as a verified free option: its primary
pricing/terms pages could not be retrieved during this investigation.

## Proposed user experience

1. Keep Books inside Entities, with Table / Board / By year and existing
   To read / Reading / Read states.
2. Add book accepts title, author or ISBN. Suggestions show a legible cover,
   title, author and first-publication year. Explicit selection is required;
   never match automatically by title. Keep manual entry available.
3. Start with a work-level result. Offer an optional edition selector displaying
   language, publisher, edition year, format and ISBN. An ISBN search points to
   its matching edition where the provider supplies a verified relationship.
4. Details reuse the compact TV/movie design: clickable cover with accessible
   enlarged preview, BOOK label, prominent title, authors, first-publication
   year, status and timeline-formatted added date. Display expandable description
   and a discreet linked source. Missing metadata produces no empty separators.
5. Edition-specific information appears together: edition title, language,
   publisher, publication date, page count and ISBN. Do not mix an edition's cover
   or page count with another edition, or present edition year as original year.
6. Preserve repeat completion events and the existing local date/time picker.
   Metadata refresh or changing edition never marks a book read or changes dates.
7. Existing manual books get an explicit Find book match action. Confirm changing
   a match; preserve alias, status, completions and article links. Returning to
   manual entry should preserve the current display title and personal data.

No ratings, reviews, purchases, full-text distribution, author biographies,
barcode-camera access or Goodreads scraping are proposed for the initial scope.
Optional page progress is a separate decision: show current/total pages only for
a selected edition with a known page count; never invent a denominator or carry
the same numeric position silently across editions. Audiobook time needs its own
model and is not equivalent to pages. Completion remains explicit.

## Identity and architecture

Follow Phase 11's provider-neutral pattern, reusing shared infrastructure only
where it actually fits. Introduce a BookCatalogProvider port and neutral work,
edition, author and external-reference records. Keep provider DTOs and IDs behind
the adapter; clients use stable Know UUIDs.

Keep separate catalog works and editions. A selected edition must belong to the
selected work. Personal book identity is proposed at work level, with an optional
edition preference; rereads use completion history. Multiple owned copies are
outside initial scope. Title equality and ISBN equality alone must not merge
records or translations. Provider replacement uses verified identifiers and
explicit handling of ambiguous matches, rather than automatic title merging.

Use atomic create-with-match, account ownership and optimistic versions. Delete
personal mappings/history through existing entity deletion while retaining
shared catalog metadata. Keep account history out of provider requests.
Proposed storage: book_works, book_editions, book_external_references,
entity_books, shared search cache, durable refresh jobs and provider cooldown.
Final names, contracts and migrations remain implementation design work.

Server-side requests identify Know with a real contact address, use bounded
timeouts and a shared request budget across pods, coalesce cache misses and
honor rate limits. No startup provider calls or background full-catalog crawl.
Initial configurable proposals: 3-character search, 400 ms debounce, eight
results; positive searches cached 24 hours, empty searches 15 minutes, metadata
30 days and stale metadata up to 90 days on transient failure, only where provider
terms permit. Provider errors must not masquerade as no results. Fetch only the
selected work/edition details, avoiding one detail request per suggestion.

## Decisions before implementation

- Approve provider and commercial operating model: modest public-API lookup,
  agreed partner access or self-hosted metadata index.
- Choose preferred search/edition language, including Romanian coverage needs.
- Approve one library entry per work and optional edition selection, or require
  multiple editions/copies as independent entries.
- Decide whether page progress belongs in this phase; recommended initial scope
  is metadata and existing completion history only.
- Confirm cover and description usage approach before commercial launch.

Next investigation should benchmark a fixed title/author/ISBN set, including
Romanian editions, translations, classics, recent books, duplicate titles and
missing covers. Measure correctness and missing fields before choosing the
adapter. Automated tests should use provider fixtures and cover cross-account
isolation, stale versions, edition mismatches, duplicate identity, preserved
history, refresh failures, sanitization, keyboard selection and mobile layout.

## Primary sources

- Open Library data: https://openlibrary.org/help/faq/using
- Open Library API policies and limits: https://openlibrary.org/developers/api
- Search and editions: https://openlibrary.org/dev/docs/api/search
- Work versus edition fields: https://openlibrary.org/dev/docs/api/books
- Cover guidance and limits: https://openlibrary.org/dev/docs/api/covers
- Wikidata licensing: https://www.wikidata.org/wiki/Wikidata:Licensing
- BookBrainz licensing: https://bookbrainz.org/licensing
- BookBrainz development access: https://bookbrainz.org/develop
- Google Books terms: https://developers.google.com/books/terms
- General Google API terms: https://developers.google.com/terms/
- Library of Congress rights: https://www.loc.gov/legal/security-copyright-and-privacy/understanding-copyright/
- Project Gutenberg license: https://www.gutenberg.org/policy/license.html
