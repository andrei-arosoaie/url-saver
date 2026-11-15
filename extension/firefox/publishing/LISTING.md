# AMO listing draft

Name: Know

Summary: Save articles and visible-tab screenshots to your Know library.

Description:

Know adds a floating Save button to permitted web pages. Choose an article to
save its URL, title and readable text, together with a screenshot of the visible
tab, to your private Know account. Google sign-in and an internet connection are
required. Signing in does not save pages automatically.

Temporary connection failures show retry progress. Existing articles are replaced
only after an explicit Update existing confirmation. When readable article text
is unavailable, Know offers a link-only save. The control does not run in private
browsing or Firefox-protected pages.

Broad website access supports the floating button and screenshot capture; this
release is not a toolbar-only/current-tab-permission design. Review the privacy
notice for account data, screenshots, storage, retry retention and backend
processing services.

Suggested categories: Bookmarks; Other (verify current AMO category choices).

Public support contact: REQUIRED_FROM_PUBLISHER

Privacy policy: copy the completed `PRIVACY.md` notice into AMO's privacy field.
An externally hosted link can also be supplied if available; none is invented here.

Original-code license: publisher must choose the appropriate AMO license; do not
assume the extension is MIT merely because React uses MIT. Third-party licenses
remain applicable and are included separately.

Screenshots: capture the actual signed release's Save feedback, Google sign-in
handoff (without private account information), duplicate confirmation and web
library. Do not submit mockups as screenshots of working functionality or include
personal library content. Branding artwork/publication screenshots are not
represented as completed by this technical package.

Version notes: Android Google sign-in uses a bound normal-tab PKCE flow; account
status and errors support explicit retry; website/account data declarations now
cover the implemented transmissions; reviewer sources include every build input.
