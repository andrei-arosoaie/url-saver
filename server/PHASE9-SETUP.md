# Phase 9 sign-in configuration

Phase 9 implements Google OIDC sign-in, a Know authorization-code flow with S256 PKCE,
RS256 access tokens, rotating refresh tokens, and current-session logout. The application
requires `config/auth-secrets.yml` at startup and rejects placeholders or invalid signing
keys. The local Google credentials still need filling before real local sign-in can run.
Production credentials and all PEM files must remain uncommitted.

## Firefox extension ID

The existing stable ID is `know@url-saver.local`, in
`extension/firefox/wxt.config.ts` under `browser_specific_settings.gecko.id`.
It is already assigned; no Mozilla registration or publication is needed for development.
Keep it unchanged when signing/publishing this add-on. This is different from the
random `moz-extension://` installation UUID shown in Firefox.

The manifest now requests `identity` permission for Phase 9. After rebuilding/reloading
the extension, open `about:debugging#/runtime/this-firefox`, find Know, select Inspect,
and run `browser.identity.getRedirectURL()` in its console. This callback belongs in
Know's extension OAuth client configuration, **not** Google's redirect list.

Reference: [Mozilla identity API](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/identity).

## Prepared files

Run from the repository root:

```sh
node server/scripts/prepare-auth-config.mjs
```

The script creates the following files only when absent, preserving filled credentials
and existing keys on subsequent runs:

| File in `server/config/`                                    | Purpose                                                              |
| ----------------------------------------------------------- | -------------------------------------------------------------------- |
| `auth-secrets.example.yml`                                  | Committed local template                                             |
| `render-auth-secrets.example.yml`                           | Committed production template                                        |
| `auth-secrets.yml`                                          | Actual local configuration; fill Google credentials and owner email  |
| `render-auth-secrets.yml`                                   | Actual Render configuration; fill Google credentials and owner email |
| `auth-private-key.pem`, `auth-public-key.pem`               | Generated local RSA 3072-bit key pair                                |
| `render-auth-private-key.pem`, `render-auth-public-key.pem` | Separate generated production key pair                               |

Actual YAML and all PEM files are Git-ignored and excluded from the server Docker context.
Google client secrets never belong in extension or webapp configuration. The extension's
`know-firefox` client ID is a public Know identifier, different from Google's client ID.

## Google Cloud configuration

1. Open [Google Cloud Console](https://console.cloud.google.com/) and select/create a project.
2. Open Google Auth Platform. Configure Branding (Know, support email, developer contact)
   and Audience. Use External for personal Google accounts. While in Testing, add your
   Google account under Test users; publishing later allows the configured wider audience.
3. Under Clients, create an OAuth client with application type **Web application**.
   Use separate clients for local development and production.
4. For the local client, add this exact Authorized redirect URI:
   `http://localhost:8080/login/oauth2/code/google`.
5. For the production client, add this exact Authorized redirect URI:
   `https://url-saver-server.onrender.com/login/oauth2/code/google`.
6. Copy each client ID and secret into its actual YAML file under
   `spring.security.oauth2.client.registration.google`. Fill
   `know.identity.legacy-account.owner-email` with the Google email that should own
   the existing saved data. Phase 9 must verify the authenticated Google identity before
   linking that account; this configuration does not migrate data by itself.

These paths are implemented with Spring Security. Google's callback goes to the backend, which completes Google sign-in and
returns a Know authorization code to the webapp or extension using PKCE.
No Authorized JavaScript origins are needed for this backend redirect flow.
Use only `openid`, `profile`, and `email` scopes; no Gmail or Drive access is required.

Reference: [Google web-server OAuth setup](https://developers.google.com/identity/protocols/oauth2/web-server).

## Render secret files

In the existing Render service, open Environment → Secret Files and upload:

| Local file                    | Render secret-file name |
| ----------------------------- | ----------------------- |
| `render-auth-secrets.yml`     | `auth-secrets.yml`      |
| `render-auth-private-key.pem` | `auth-private-key.pem`  |
| `render-auth-public-key.pem`  | `auth-public-key.pem`   |

Keep the existing `secrets.yml` database configuration. The production auth configuration
already points at `/etc/secrets/auth-private-key.pem` and `/etc/secrets/auth-public-key.pem`.
Do not put the `render-` prefix in Render's uploaded names.

The Docker image links `/app/config/auth-secrets.yml` to Render's mounted secret file.
Deploy the new image together with all three auth secret files. The shared temporary
account is disabled by default; anonymous library requests now return 401. Its data stays
under the existing account UUID. The first Google identity with a verified email matching
`legacy-account.owner-email` claims that account transactionally; subsequent sign-ins use
the Google issuer/subject mapping, even if the email changes. Other identities receive
independent accounts. A later Google subject cannot claim an already-linked legacy account.

Tomcat handles forwarded headers only from its trusted internal proxy ranges. Do not
configure a catch-all trusted proxy pattern. Render's proxy must supply the external HTTPS
scheme. Production cookies use Secure, HttpOnly refresh storage, SameSite=Lax, Path=/,
and the `__Host-` prefix. Local HTTP uses non-Secure development cookies.

## Client flow and session policy

- `GET /oauth2/authorize`: registered client, exact callback, state, S256 challenge.
  Clients are `know-web` and the configured `know-firefox` public client. Unregistered
  callbacks return an error without redirecting. Pending Google login state expires in five minutes.
- Google callback: validates OIDC signature, issuer, audience, nonce and verified email;
  returns a single-use Know code (two-minute expiry), never provider tokens.
- `POST /oauth2/token`: form-encoded authorization-code or refresh-token grant. Both
  clients share rotation, expiry and reuse detection. Codes and refresh tokens are stored
  only as SHA-256 hashes. Refresh replay revokes that session and its existing access tokens.
- Web requests use an HttpOnly refresh cookie. Obtain CSRF from `GET /oauth2/csrf` and
  send `X-Know-CSRF`; token and logout requests must also match the configured web Origin.
  Web token JSON contains only the access token and expiry. Access tokens stay in memory.
  Browser Web Locks serialize refresh-cookie rotation across tabs.
  Refresh and logout include the tab's expected session when known; a cookie belonging
  to another sign-in cannot be rotated or revoked by a stale tab. Sign-in/out broadcasts
  clear peer tabs, and pending API writes never retry under a different session.
- Extension requests omit cookies and keep credentials in extension storage, handled by
  the background worker. Refreshes are serialized. Save and screenshot jobs bind to the
  sign-in session that created them, and are never restored into another session.
- `POST /oauth2/logout`: form-encoded `client_id`; extension also supplies `refresh_token`.
  Web logout uses its refresh cookie and CSRF. Logout works after access-token expiry,
  revokes only that session, and is idempotent. Other sessions stay signed in.
- Access tokens receive the full configured lifetime. Refresh expiry defaults to a fixed
  window from sign-in; `refresh-expiry-mode: sliding` renews the window on each refresh.
  Every API request verifies both the signed JWT and its persisted session revocation state.

The webpage shows Google sign-in before loading the library. The Firefox toolbar popup
provides sign-in and sign-out. A save that needs authentication shows a sign-in action;
signing in does not save the article. Return to the page and click Save explicitly.

The webpage header's account menu shows the Google name and photo captured at sign-in,
and provides sign-out and photo removal. After removal, the menu offers Restore Google photo
instead of Remove profile photo. The latest verified Google photo reference is retained while
hidden, so restoration is immediate. The choice is saved across devices and future sign-ins.
Existing sessions created before profile
support show a generic avatar until the next Google sign-in. Profile reads and removal use
the authenticated `/api/v1/identity/profile` and `/api/v1/identity/profile/picture` endpoints;
DELETE hides the photo and PUT restores it for the current account only.

For local development, use `http://localhost:5173` (matching the configured web Origin).
Vite proxies `/api`, `/oauth2`, and `/login` to port 8080. Do not substitute `127.0.0.1`
without updating the registered web origin and callback consistently.

## Verification

Run `npm run check` in both `webapp` and `extension/firefox`. Run
`server/verify-wsl.ps1 -ExportOpenApi` for the backend checks, disposable PostgreSQL tests,
and packaged JAR. Authentication integration tests use freshly generated keys and a local
OIDC provider that signs ID tokens; they do not read production credentials or call Google.
The image smoke test generates separate fixture keys/config, verifies anonymous denial,
exchanges a disposable PKCE code, and saves through the authenticated API.

Real Google sign-in and Render proxy/cookie verification still require the configured
Google project and deployed image; automated fixtures do not establish those live checks.
