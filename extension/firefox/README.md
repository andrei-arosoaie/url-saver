# Know Firefox extension

## Android development package

Run `build-development.cmd` from this folder, or `npm run package:dev`.
Dependencies must be installed first with `npm ci`.
Copy `.output/know-development.xpi` to your phone. This is an unsigned
development package for Firefox Nightly with signature enforcement disabled.
The script rebuilds the extension before creating the XPI; it does not sign or publish it.

WXT, React, TypeScript and Mozilla Readability. Supports Firefox desktop and Android; private browsing is disabled.

## Build and check

Use Node.js LTS and npm:

```sh
npm ci
npm run check
npm run validate
npm run zip
```

Build output: `.output/firefox-mv3/`. Generated OpenAPI types are build outputs, derived from `../../server/openapi/openapi.yaml`; they are not committed. All dependencies are pinned and package-lock.json is committed.

Default server: `https://url-saver-server.onrender.com`. To target the local server, build with `VITE_API_ORIGIN=http://localhost:8080` in the environment. No secrets belong in build variables.

## Behavior

- An upper-right bookmark circle captures the current URL, title and Readability article HTML only on click. The original page remains intact; Know's closed Shadow DOM isolates its controls and styles.
- Only the viewed tab displays the control. Background messages reject inactive tabs, frames, private windows and captures whose URL no longer matches the tab. URL changes reset feedback.
- Missing readable content prompts before saving URL/title only. Nothing is silently truncated. After confirmation, the original capture is queued locally for reliable saving.
- Wait for server confirmation: green success, then blue saved; red with a clearly branded error message on failure. On timeout/connection failure, retry every 60 seconds after the preceding failure, up to ten retries after the initial attempt; request timeout remains 60 seconds. HTTP errors, duplicate responses and version conflicts stop retries.
- Duplicate content requires Update existing or Cancel. Fetch the current version, then PATCH only on explicit confirmation. Version conflicts never trigger automatic overwrite or retry.
- Pending URL/title/article HTML, retry count and deadline are stored in Firefox local extension storage so retries survive restarts and closed tabs. Background alarms resume overdue work once, without catch-up bursts. Reopening the exact source URL shows countdown/progress or its final outcome. Concurrent clicks/tabs share one pending job for that exact URL.
- Captured text is cleared on confirmed success or final failure. Duplicate captures remain only until Update existing or Cancel; no automatic overwrite. Terminal feedback contains no article body. Storage errors never cause an unjournaled request.

## Temporary account

Saving works without sign-in in this iteration. The server assigns unauthenticated requests to one shared temporary account (UUID 00000000-0000-4000-8000-000000000001). All installations share its articles. The temporary path is controlled by know.temporary-account.enabled in application.yml; disable it when real authentication is implemented. No account ID is trusted from the extension. If an access token is available later, the extension sends it normally.

## Review in Firefox

Desktop: open `about:debugging#/runtime/this-firefox`, choose **Load Temporary Add-on**, select `.output/firefox-mv3/manifest.json`, then visit an ordinary HTTP/HTTPS article. Firefox-protected pages cannot host the control. The development extension ID is `know@url-saver.local`; signing remains deferred.

Android: the build declares Android compatibility and Mozilla's required browsing-activity/website-content consent. Actual Android installation and device testing remain outstanding; unsigned desktop temporary loading does not establish Android compatibility.

Automated checks cover extraction, viewport races, API contracts, message validation, persistent retry lifecycles and React feedback. Five end-to-end integration tests connect the React control, background handlers and queues to a disposable Spring Boot/PostgreSQL server and an ImageKit HTTP fixture through the official SDK. They verify article/image restart recovery, lost responses, native dimensions, private JPEG download, explicit screenshot replacement, exhaustion and validation. These tests simulate browser storage/alarms and restarts; actual Firefox desktop/Android restart verification and authentication remain outstanding.

Mozilla's validator reports four `innerHTML` warnings inside bundled React/Readability. Our control never renders captured HTML; Readability operates on a detached clone. These dependency warnings require review during signing and are not suppressed.

## Integration tests

Run npm run test:integration with KNOW_IT_API_ORIGIN and KNOW_IT_IMAGE_ORIGIN set to disposable local backend and provider fixtures. Use server/scripts/verify-image.sh with KNOW_RUN_EXTENSION_IT=1 to provision both automatically. Production and Supabase URLs are rejected. The CI extension workflow builds the real application image and runs these tests plus SQL assertions inside the disposable image-smoke fixture. Unit files use a Test suffix; integration files use IT.

## Screenshots

Save hides the Know controls for native-resolution, lossless PNG viewport capture and restores them afterwards. The backend chooses stored JPEG/PNG and quality. Image uploads start only after article saving succeeds; explicit Update existing also replaces the screenshot. Failed captures mark the image missing while preserving the article.

Screenshot jobs retain the original PNG/capture ID across restarts and retry separately every minute, up to ten retries after the initial attempt. Network failures and screenshot HTTP 503 are retryable; 400/409/413 are final. Feedback distinguishes article success from screenshot progress or a missing image. Finished/cancelled jobs release captured data.

Firefox must grant the declared all-websites permission for viewport capture. The control and capture remain restricted to viewed HTTP/HTTPS tabs; private and protected pages are excluded. unlimitedStorage permits journaling native PNGs above the default storage quota. The transport ceiling is 20 MiB; oversized captures are marked missing.

The development XPI targets Render by default. Screenshot support requires deploying the matching backend migration/configuration; this local implementation has not been deployed. Desktop Firefox 158.0.1 capture is verified at native 2560 x 1440 / 2x density, including exclusion and restoration of Know controls. Android capture and installed-extension restart verification remain the device review checkpoint.

## Live provider and desktop verification

The local image harness accepts KNOW_LIVE_IMAGEKIT_CONFIG pointing to an ignored ImageKit YAML. It uses a disposable database, verifies private upload/signed download/unsigned denial, then deletes every journaled test file through the official SDK. It does not use Supabase.

For desktop verification, run the harness with KNOW_RUN_EXTENSION_IT=1 and KNOW_BROWSER_READY_FILE set to a temporary marker path. It writes local API/provider URLs and waits until that marker is removed. Build a separate extension with VITE_API_ORIGIN set to that local API. Launch Firefox with a fresh isolated profile and --remote-debugging-port 9223. Set KNOW_IT_API_ORIGIN, KNOW_IT_IMAGE_ORIGIN, KNOW_FIREFOX_EXTENSION_DIR and KNOW_FIREFOX_PROOF_DIR, then run node scripts/verify-desktop-firefox.mjs. The check refuses a build that does not target the local API. It temporarily installs the extension, clicks Save, checks native dimensions and screenshot pixels, then uninstalls it. Remove the marker to dispose of the backend fixtures. No automation dependency is added to the extension.
