# Phase 4 — screenshot contract review

ImageKit is the first storage adapter. Application interfaces, database associations and client contracts remain independent of ImageKit types and URLs. Existing article JSON endpoints stay compatible with Phase 3.

## Capture policy

- Capture only the visible viewport, at native pixel density, with no resizing.
- Extension sends losslessly compressed PNG at native quality and resolution. It performs no resizing, lossy encoding, or final-format selection.
- Backend owns all transformations through application.yml: initially JPEG quality 90, with PNG selectable later. No extension rebuild or capture-settings endpoint is required to change backend transformations.
- Stored-image limit: 5 MiB, independent of the existing 4 MiB HTML limit. Incoming lossless-upload limit: 20 MiB, configurable independently.
- Hide Know controls before capture, restore afterwards, and discard captures affected by tab/navigation races.
- Store screenshots privately. Signed download URLs expire after 30 minutes, configurable in mounted YAML.

## Proposed endpoints

### GET /api/v1/articles/{articleId}/screenshot

Checks article ownership first. Missing articles and foreign articles both return 404. An owned article without a screenshot returns 200 with MISSING.

Reuse the same signed URL and expiry for an unchanged screenshot while valid. The bounded backend URL cache is scoped to account, screenshot version and immutable provider file reference; `know.screenshots.signed-url-cache-entries` defaults to 1000. Refresh within five seconds of expiry. `?refresh=true` explicitly renews a failed image URL after ownership validation. The configured 30-minute validity is never extended by cache hits.

The webapp rechecks metadata on each article opening and reuses its valid URL only for the same screenshot version. It retains at most 100 URL records in memory; browser HTTP caching retains the image bytes according to ImageKit's response headers. Screenshot replacements use a new file/version. MISSING and denied responses discard client URL entries. Metadata remains `Cache-Control: no-store` to preserve ownership and replacement checks. Stable image URLs enable ImageKit CDN cache reuse; CDN deliveries still consume bandwidth.

```json
{
  "status": "AVAILABLE",
  "version": 1,
  "url": "https://provider.example/private-file?temporary-signature=...",
  "expiresAt": "2026-10-28T19:30:00Z",
  "contentType": "image/jpeg",
  "byteSize": 280000,
  "width": 1920,
  "height": 1080
}
```

For MISSING, url, expiresAt, contentType, byteSize, width and height are null. A screenshot version is independent of article metadata: renaming or changing category does not alter the screenshot association. Return Cache-Control: no-store for responses containing signed URLs.

### PUT /api/v1/articles/{articleId}/screenshot

Multipart request with:

- `metadata`: JSON containing a client-generated captureId UUID and expectedVersion from the screenshot response.
- `file`: losslessly compressed native-resolution PNG bytes. Omit only to explicitly record a failed/unavailable capture and show the placeholder.

```json
{
  "captureId": "15ddf2ea-b21a-489b-b45b-062cb7554b01",
  "expectedVersion": 0
}
```

Only upload after the article save succeeds or an explicit Update existing succeeds. Repeated attempts use the same captureId and bytes. Replaying a confirmed capture returns its existing result without another provider upload. An older capture cannot overwrite a newer one. New replacement captures require the current expectedVersion; conflicts return 409 and never retry automatically.

Responses:

| Status | Meaning |
|---|---|
| 200 | AVAILABLE after confirmed upload, or MISSING after explicitly recording an unavailable capture |
| 400 | Invalid metadata, image format or file contents; no truncation |
| 404 | Missing or foreign article |
| 409 | Stale screenshot version, superseded capture, or captureId reused with different bytes |
| 413 | File exceeds the configured limit |
| 503 | Temporary provider failure; screenshot remains MISSING and response identifies it as retryable |

Provider/network failures never roll back an already saved article. An accepted replacement stops serving the previous screenshot while its new upload is unconfirmed. Failed/superseded provider files and replaced references are cleaned up through a persisted cleanup journal.

## Client flow

1. Capture article data and the viewport once, as a losslessly compressed native-resolution PNG. Retain those exact bytes for retries; never recapture a different page later.
2. Save the article through its existing JSON endpoint. Handle duplicates and explicit updates as before.
3. Upload only the screenshot as multipart. Article and screenshot retry jobs are independent, with ten retries after the initial attempt, each one minute after the preceding failure. No overlapping attempts or automatic content overwrite.
4. Show an inline preview below entry details and above article content; click to enlarge. Show a missing-image placeholder when unavailable.
5. If the download URL expires, request a fresh owned URL and retry image loading once. Keep the private API key exclusively in mounted server YAML.

## Storage boundary

The application interface supports upload, deletion and temporary delivery URLs. Persist the adapter ID plus opaque provider reference/path, file hash and image metadata. Select the adapter for new uploads through configuration; dispatch existing references to their original adapter. Provider migration is explicit and does not change article use cases or client contracts.

## Review checkpoint

Review these proposed endpoints before backend integration. Then review backend integration and Firefox desktop/Android verification separately, as required by the Phase 4 implementation guide.
