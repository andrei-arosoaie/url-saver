# Release data and permission map

| Data                                         | Source / initiation                                            | Destination                                                      | Local/server retention                                                          |
| -------------------------------------------- | -------------------------------------------------------------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| Current page URL                             | Local navigation/status checks; explicit Save for transmission | Extension background locally; Know API on Save/retry             | Pending/terminal job keys; saved article record                                 |
| Title and readable HTML                      | Explicit Save; Readability on a detached clone                 | Know API; configured text-processing providers when enabled      | Pending/duplicate jobs; server library                                          |
| Viewport screenshot                          | Explicit Save/Update after tab checks                          | Know API, then ImageKit                                          | Pending PNG until completion; provider image until deletion/cleanup             |
| Verified Google identity and display profile | Explicit Google sign-in                                        | Google and Know backend                                          | Provider-account association/display profile; operator retention                |
| Know access/refresh tokens                   | PKCE exchange and refresh                                      | Know authentication/API endpoints                                | Firefox local extension storage; server session/revocation records              |
| PKCE verifier / random state                 | Starting Android tab sign-in                                   | Verifier to Know token endpoint; state on authorization/callback | Bounded local pending-sign-in record; removed on completion/cancellation/expiry |
| Error/progress messages                      | Save and authentication processing                             | Local extension UI/console                                       | Local feedback; no extension telemetry exporter                                 |

Manifest categories: `browsingActivity` for saved URLs, `websiteContent` for article
HTML/screenshots, `authenticationInfo` for account-service authentication, and
`personallyIdentifyingInfo` for the Google account/profile flow. Required categories
are shown by Firefox on installation/update. Minimum versions remain desktop 140
and Android 142, which support built-in consent. No telemetry permission is declared
because no extension telemetry upload is implemented; deployment logging still
needs an accurate separate disclosure.

| Permission                                         | Current purpose / constraint                                                                                                                        |
| -------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| HTTP/HTTPS content-script matches and `<all_urls>` | Automatically show the floating control on permitted pages; capture visible-tab screenshots. No file/private/protected-page capture is implemented. |
| `tabs`                                             | Validate active tab/URL and implement the bound Android OAuth tab flow.                                                                             |
| `identity`                                         | Native desktop authentication; Android does not call this unsupported API.                                                                          |
| `storage`                                          | Persist account credentials, PKCE state, jobs and progress locally.                                                                                 |
| `unlimitedStorage`                                 | Native PNG retry journals can exceed ordinary local storage quotas. Not permission to collect unrelated content.                                    |
| `alarms`                                           | Restart-safe retries and pending Android sign-in expiry.                                                                                            |

The broad website permission is part of the existing floating-button design. An
`activeTab` design would require a browser-supplied activation gesture and is a
separate product change; it must not be implemented by merely removing permissions
while retaining assumptions that capture will work.

Publication requires operator confirmation of the actual database host, enabled
AI processing, Google OAuth audience/reviewer access, and log/backup retention.
Configuration templates are not evidence of live production settings. Production
secret files are not build inputs and must never enter the reviewer archive.
