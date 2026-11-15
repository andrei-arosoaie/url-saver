# Know privacy notice — publication draft

**Publisher contact: CONTACT_REQUIRED_BEFORE_PUBLICATION**

This notice covers the Know Firefox extension and the Know service it connects
to. The publisher must fill in the public contact above and confirm the deployed
processing services before copying this notice into the AMO privacy-policy field.
Do not publish this draft with unresolved fields.

## What happens when you use Know

Know places a Save control on permitted HTTP/HTTPS pages. It reads the current
page URL locally to display that page's save status. Merely visiting a page does
not upload its article body or take a screenshot. Clicking Save sends the page
URL, title and extracted readable article HTML to the Know API over HTTPS. It also
captures and uploads a screenshot of the visible tab. Background retries continue
that user-requested operation after temporary failures; the source tab may have
closed before an already-captured job finishes uploading.

The screenshot can contain visible personal information outside the article,
including information on signed-in pages. Extracted article content and URLs can
also contain personal information or query parameters. Do not save pages whose
contents you do not want stored by Know. There is currently no screenshot-off
setting. Private browsing is disabled. Firefox-protected pages cannot host the
control, and captures reject inactive tabs, frames, private windows and page
navigation mismatches. The extension does not upload your entire browsing history.

## Sign-in and account information

Google authenticates you on Google's own page; Know does not ask for your Google
password. Google provides verified account identity and profile information such
as name, email and profile picture. Know uses this to identify your account, match
the configured legacy owner where applicable, and show your profile. Know stores
the Google provider/subject association and display profile. Google processes the
sign-in under its own privacy terms.

The extension exchanges one-use authorization codes and rotating refresh tokens
with Know. It stores Know access/refresh tokens, their expiry and session binding
in Firefox's local extension storage, not browser sync storage. On Android, a
pending sign-in also stores state and a PKCE verifier locally for up to five
minutes, or for the bounded code exchange. The background uses those values;
the injected Save control does not receive them. Signing in does not save a page.

## Where data goes and why

The extension communicates with `https://url-saver-server.onrender.com` to save
and retrieve your own articles, upload screenshots and authenticate requests.
The service runs on Render. Saved articles/account records are kept in the
service's configured PostgreSQL database; screenshots are handled by the
configured ImageKit image service. Image downloads use authenticated access and
time-limited URLs; those URLs should still be treated as private while valid.

The backend can optionally send extracted article text or text chunks to
OpenRouter/Groq for article identification and to Google's Gemini API for
semantic embeddings. This depends on the operator's enabled server features and
credentials. These optional services are not called directly by the extension.
**Before publication, the publisher must confirm which are enabled and replace
this paragraph with the actual deployed provider list and purposes.** Do not
state that saved text never reaches an AI provider unless deployment settings
have been checked. The database hosting provider, enabled AI services, and their
applicable retention terms must be included in the final notice.

No extension analytics, advertising or remote-code loader is implemented in this
release. Hosting providers may keep operational and security logs such as request
times, addresses, status codes and errors under their own configurations and terms;
the publisher must confirm those practices and retention before publication.

## Local retention, retries and sign-out

Pending jobs store captured URL/title/article HTML, screenshot PNG data, retry
deadlines and progress in local extension storage. Their purpose is to complete
an explicitly requested save after a network interruption or restart. Finished
jobs normally discard article bodies and image data; duplicate-review captures
may remain until Update existing or Cancel. Ending a sign-in session separates
its jobs from another session but currently does not purge all abandoned session
records. There is no global expiry for every stored job. Logout can retain local
credentials if server revocation cannot be confirmed. Do not interpret sign-out
as deletion of every locally stored article or screenshot.

Uninstalling the extension removes its local extension data. It does not delete
your server-side library or Google account. Browser/profile or device backups
are managed separately by Firefox and the device owner. Do not assume this
extension encrypts the browser profile at rest.

## Server retention and deletion

Saved articles remain in your Know library until removed. Deleting an article
removes its record and schedules associated screenshot cleanup; related tracked
entities have their own history and deletion controls. Image-provider cleanup,
server logs and backups may have separate retention. An account-wide deletion
button is not currently implemented. Contact the publisher for account-wide
deletion or privacy requests. The final notice must state the operator's actual
request process and backup/log retention, rather than promise immediate deletion
from every system.

## Your choices and changes

You can decline installation permissions, avoid saving a page, stop using Know,
remove saved records, or uninstall the extension. Removing site access prevents
the floating control/capture from working on those sites. Uninstallation alone
does not revoke Google's own account permissions or delete the server library.
Review Firefox's permission prompt when updating; changes to collection purposes
or recipients must be reflected in this notice and the manifest disclosures.
