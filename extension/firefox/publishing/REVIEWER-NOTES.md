# Notes for Mozilla reviewers

## Submission preparation — publisher-only checklist

Before pasting the reviewer-facing section below into AMO, verify that the Google
OAuth app accepts the reviewer account (including any Google test-user restriction)
and arrange a usable dedicated reviewer account if Mozilla requests credentials.
Put any credentials only in AMO's private reviewer channel, not this file, the
source ZIP, public issues, or the listing. Never share the publisher's personal
Google password, API credentials or signing keys. There is no production auth
bypass in this submission, and a local mock sign-in does not establish reviewer
access to production. Reviewer access is a manual release gate, not yet verified.

## Reviewer-facing notes

Know saves an explicitly chosen article to a signed-in user's Know library. It
also captures the current viewport screenshot. The backend is
`https://url-saver-server.onrender.com`. Google sign-in is required. First sign-in
with a verified Google account creates a separate Know account automatically; it
does not expose the publisher's library. The publisher will provide an appropriate
test-account arrangement through private reviewer notes where required.

The matching source ZIP includes the extension and the required OpenAPI schema.
Build instructions are in `extension/firefox/publishing/BUILD.md`. It requires only
the specified Node/npm toolchain and public locked npm dependencies, not backend
secrets or a database. The six validator warnings are retained and source-mapped;
see `SECURITY-REVIEW.md` and the accompanying `warning-review.json`.

### Functional checks

1. Install the submitted, Mozilla-signed version into a fresh Firefox profile and
   accept its disclosed website and data permissions. Open an ordinary HTTP/HTTPS
   article with several paragraphs. Protected browser pages/private windows are
   deliberately unsupported.
2. Open Know's toolbar popup and choose Sign in with Google. Desktop uses Firefox's
   native identity flow. Android uses a normal Firefox tab; complete Google login
   there. The tab displays a bundled waiting page and closes after completion.
   Reopening the popup shows pending status while login is unfinished.
3. Return to the article and click Know's upper-right bookmark control explicitly.
   Signing in itself never saves. Wait for confirmed article/image feedback.
4. Open the Know web library at the backend origin and sign in with the same Google
   account. Confirm title, article and screenshot. The web session and extension
   session are separate clients linked to the same account.
5. Save the same exact URL again. Update existing and Cancel are explicit choices;
   a duplicate does not silently overwrite the saved article. A page without
   readable prose offers an explicit Save link only choice.
6. Temporarily disconnect networking after an explicit save to inspect retry
   feedback. Article and screenshot retries are separate; reopening the source
   URL restores persisted progress. Network failures retry once per minute up to
   ten retries. Non-retryable HTTP errors stop the corresponding operation.
7. Cancel Google login or close the Android sign-in tab. Reopen Know and retry;
   pending state expires and cannot authorize a forged callback or a different tab.
8. Sign out from the popup. A later save requires explicit authentication. Logout
   currently requires server confirmation; failed revocation is shown as an error,
   not silently reported as success. See the privacy notice for local retention.

### Device evidence

The publisher reported that the 0.1.1 development artifact worked on Firefox
Android. Exact device/Firefox versions and signed-install details were not recorded;
do not represent that report as a complete signed-device test matrix. Later
publishing changes must be checked on the actual signed release candidate.

Automated coverage exercises PKCE/state binding, missing Android identity APIs,
callback tab/origin checks, background resumption, one-use code handling, popup
recovery, extraction and persistent save/screenshot behavior. It does not exercise
real Google login on every device or certify Mozilla approval.
