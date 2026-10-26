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
- Missing readable content prompts before saving URL/title only. Nothing is silently truncated or queued.
- Wait for server confirmation: green success, then blue saved; red with a clearly branded error message on failure. Retry is manual; request timeout is 60 seconds.
- Duplicate content requires Update existing or Cancel. Fetch the current version, then PATCH only on explicit confirmation. Version conflicts never trigger automatic overwrite or retry.
- No captured article or failed request is stored persistently. Pending confirmation data lives only in component memory.

## Temporary account

Saving works without sign-in in this iteration. The server assigns unauthenticated requests to one shared temporary account (UUID 00000000-0000-4000-8000-000000000001). All installations share its articles. The temporary path is controlled by know.temporary-account.enabled in application.yml; disable it when real authentication is implemented. No account ID is trusted from the extension. If an access token is available later, the extension sends it normally.

## Review in Firefox

Desktop: open `about:debugging#/runtime/this-firefox`, choose **Load Temporary Add-on**, select `.output/firefox-mv3/manifest.json`, then visit an ordinary HTTP/HTTPS article. Firefox-protected pages cannot host the control. The development extension ID is `know@url-saver.local`; signing remains deferred.

Android: the build declares Android compatibility and Mozilla's required browsing-activity/website-content consent. Actual Android installation and device testing remain outstanding; unsigned desktop temporary loading does not establish Android compatibility.

Automated checks exercise extraction, API contract handling, message validation and React feedback with test-only fixtures. They do not establish a complete live Firefox → authenticated backend → PostgreSQL flow. That requires the security checkpoint and real browser/device verification.

Mozilla's validator reports four `innerHTML` warnings inside bundled React/Readability. Our control never renders captured HTML; Readability operates on a detached clone. These dependency warnings require review during signing and are not suppressed.
