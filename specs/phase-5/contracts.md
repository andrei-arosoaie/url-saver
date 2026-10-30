# Phase 5 — Endpoint contracts

**Approved.** Routes, payloads, name limits, fallback-category protection, and permanent deletion rules are approved for implementation.

## Shared rules

- Resolve the account from the existing caller mechanism. Clients never supply an account ID.
- Missing resources and resources belonging to another account both return 404: categories use `RESOURCE_NOT_FOUND`; articles retain the existing `ARTICLE_NOT_FOUND` reason.
- Preserve Spring's error JSON and add `reason` and a clear `message`.
- Writes require the current version, except creation. Stale versions return `409 VERSION_CONFLICT`; clients reload rather than retry automatically.
- Category keys never change. Names and colors can change for predefined and custom categories without changing article or entity associations.
- Category names are unique within an account, including disabled categories. Trim surrounding whitespace for storage and compare without case. Maximum: 100 Unicode code points, configurable in YAML. Empty names are invalid; never truncate.
- Colors use `#RRGGBB`. Reject unknown JSON fields, explicit nulls, invalid types, negative versions, and empty PATCH requests with `400 INVALID_REQUEST`.
- Category article counts are computed from current owned articles, including link-only saves. Counts are informational; the backend checks references again inside each write transaction.

## 1. List categories

`GET /api/v1/categories` → **200**

Extend the existing response with `articleCount`; return enabled and disabled categories so the management page can re-enable them. Preserve existing catalog ordering.

```json
{
  "items": [{
    "key": "BOOKS",
    "displayName": "Books",
    "color": "#168e91",
    "isDefault": true,
    "enabled": true,
    "createdAt": "2026-10-30T18:00:00Z",
    "version": 0,
    "articleCount": 12
  }]
}
```

No article HTML or screenshot URLs are included. Renamed defaults are not overwritten by default seeding.

## 2. Create a custom category

`POST /api/v1/categories` → **201**, with `Location: /api/v1/categories/{key}`

```json
{"displayName":"Research","color":"#305ea7"}
```

Return one category object with the same fields as a catalog item. The server assigns an opaque stable key, `isDefault=false`, `enabled=true`, `version=0`, and `articleCount=0`.

- `409 DUPLICATE_CATEGORY_NAME`: that account already has the name.
- Clients cannot set key, owner, default flag, count, timestamps, or initial version.

## 3. Edit, disable, or re-enable a category

`PATCH /api/v1/categories/{key}` → **200**, returning the updated category object.

```json
{"version":0,"displayName":"Reading","color":"#2876dc"}
```

```json
{"version":1,"enabled":false}
```

Omitted fields remain unchanged. Increment version only for an actual change.

- `409 CATEGORY_IN_USE`: disabling is blocked while any articles reference the category. All requested changes roll back together.
- `409 DUPLICATE_CATEGORY_NAME`: name already exists in the account.
- `409 VERSION_CONFLICT`: reload before trying again.
- Re-enabling an empty disabled category is supported.

**Fallback category:** keep the predefined `OTHER` category enabled because extension saves without an explicit category currently use it. Its name and color remain editable. Disabling it returns `409 CATEGORY_REQUIRED`. This avoids silently re-enabling a category or breaking normal saves. No other empty category has this restriction.

## 4. Remove an empty custom category

`DELETE /api/v1/categories/{key}?version=2` → **204**, no body.

- `409 CATEGORY_IN_USE`: articles still reference it; nothing is deleted or reclassified.
- `409 DEFAULT_CATEGORY_PROTECTED`: predefined categories can be disabled when eligible, but cannot be removed.
- `409 VERSION_CONFLICT`: stale version.
- Missing or foreign category: `404`.

The Categories page shows article counts and explains blocked actions. A count can change after loading; backend validation remains authoritative.

## 5. Permanently delete an article

`DELETE /api/v1/articles/{id}?version=3` → **204**, no body.

The webapp asks for confirmation before sending the request. The backend atomically deletes the owned article, its article-to-entity associations, and screenshot metadata, and durably queues deletion of its ImageKit file using the existing provider-independent cleanup mechanism. Entities and their completion history remain.

- The article immediately disappears from list/detail views and category counts; future reads return `404`.
- ImageKit cleanup runs with retries. A temporary provider outage does not restore the article or lose the cleanup task.
- Already downloaded browser images cannot be recalled; previously issued signed URLs can remain usable until provider deletion or expiry.
- In-progress screenshot uploads must not recreate a deleted article or leave an untracked file; their existing journal handles cleanup.
- `409 VERSION_CONFLICT`: article changed after it was displayed; reload and confirm again.
- Missing or foreign article: `404`; no other account's data is touched.
- Removing an article does not automatically disable or remove its now-empty category.

## Verification before review

- Full HTTP flow: create category → assign article → verify count → rename/recolor → verify unchanged associations → reject disable/removal → permanently delete article → verify count zero → disable/re-enable → remove custom category.
- Verify account isolation, duplicate names (case/whitespace), simultaneous writes, stale versions, and reference integrity when assignment races disabling/removal.
- Verify durable screenshot cleanup, provider failure and retry, deletion during upload, and preservation of entities/history.
- Verify default seeding never restores disabled categories or overwrites renamed defaults.
- Unit tests use `Test`; end-to-end integration tests use `IT` and the shared Spring/PostgreSQL context.
