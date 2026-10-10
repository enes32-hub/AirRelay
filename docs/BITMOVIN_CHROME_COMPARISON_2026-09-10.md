# Bitmovin direct-address comparison

On 2026-09-10 the exact user-confirmed 4K manifest URL was opened in the
user's Google Chrome through the Chrome extension (not the Codex in-app browser).
Chrome displayed `Attention Required! | Cloudflare` and `Sorry, you have been
blocked`. Ray ID: `a38e055248cc8b96`. No challenge was solved, browser settings
changed, or cookies extracted/replayed.

Earlier Python GET and Windows GStreamer also received HTTP 403. The in-app
browser was a separate earlier test and must not be described as Google Chrome.

The user reports successful playback in Chrome. That successful page/request
has not yet been identified or reproduced. A fresh direct navigation and an
embedded player in an existing session are not equivalent tests. The exact WAF
rule remains unknown; an IP ban or missing cookie is not established.

Source inspection: direct AirPlay Content-Location playback passes URL and start
position to the renderer. The captured play plist has no explicit HTTP-header
or cookie field. The Windows playbin is given the URI without a custom
source-setup/header handler. These are implementation facts, not proof that
adding headers would resolve this particular block.

Next evidence needed: the actual Chrome page on which this same video plays.
Compare the manifest URL, redirects, response status, request context, and cache
usage without exporting credentials. Do not add guessed headers or copy browser
security cookies as a speculative fix.
