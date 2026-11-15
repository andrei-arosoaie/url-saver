# Validator warning review

The expected baseline is six `UNSAFE_VAR_ASSIGNMENT` warnings. The release audit
maps each exact minified file/line/column to an upstream source-map entry and
records the source location and surrounding code in `.output/warning-review.json`.
It rejects new warning codes, a changed count, or a location outside the two
reviewed libraries. This is a review baseline, not a claim that a validator warning
is harmless merely because it comes from a dependency.

## React DOM 19.3.1: four warnings

Two `dangerouslySetInnerHTML` assignments exist in React DOM's standard-element
and custom-element property setters. Each appears in both the popup and injected
control bundles. The branch requires a `dangerouslySetInnerHTML` prop containing
`__html`. First-party components do not supply that prop. Article HTML is never
passed to a React element as HTML; messages, titles and account status are rendered
as ordinary escaped React text. The audit rejects a future introduction of this
prop or a direct HTML/eval sink in first-party runtime code.

React/React DOM are pinned, unmodified official npm packages. Removing internal
branches from vendor code would make dependency review harder and is not used as
a way to hide warnings. Their MIT notices must accompany distribution.

## Mozilla Readability 0.6.0: two warnings

`Readability.js` restores `page.innerHTML` from its saved `pageCacheHtml` when
retrying article extraction, and assigns `noscript.innerHTML` to a temporary
element when recovering an image. `capture()` passes Readability a detached
`document.cloneNode(true)`, removes Know's own UI from that clone, and never appends
the processed clone or returned article HTML to the live page or extension UI.

Readability is an extractor, **not an HTML sanitizer**. The returned article HTML
is untrusted input sent to the authenticated Know API on an explicit Save. The
backend's sanitization is a separate trust boundary; it must not be replaced by
an assumption that extraction sanitizes HTML. Existing extraction tests check
that the live document is unchanged and Know's controls are excluded. React text
rendering tests cover HTML-looking messages remaining text.

The Apache-2.0 Readability dependency is unmodified. Include its license and NOTICE
where provided. Do not suppress these warnings or claim Mozilla has waived them.

## Authentication and transmission

Desktop uses Firefox's native identity flow; Android uses a normal tab. Both use
the fixed callback for `know@url-saver.local`, random state and S256 PKCE. Android
also checks the exact callback tab, origin/path and single code/state parameters.
Access/refresh tokens and pending verifiers stay in extension local storage and
background code; no credential is returned by the extension's page-message API.
No client secret is embedded. Google passwords are entered on Google's site, not
in Know's injected control. Runtime code is packaged locally; API responses are
data, not executable code. No analytics or remote-code loader was found in the
extension runtime source examined for this release.

## Retention limitation (not hidden from reviewers)

Pending article text and PNG screenshots are journaled locally for retry. Completed
jobs normally drop their captured body/image. Duplicate-review jobs can retain
captured content until resolved. Ending a session isolates old keys but currently
does not purge every abandoned session's jobs, and there is no global storage TTL.
Logout requires server confirmation and may retain credentials when it fails.
The privacy notice must describe this accurately; uninstalling the extension
removes its local data, but does not delete server-side articles/accounts.

This document records a focused source/artifact review, not penetration-test
certification or a guarantee of Mozilla approval.
