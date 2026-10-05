# syntax=docker/dockerfile:1
# bluesky-social/pds, patched for the local stack.
#
# The OAuth sign-in page sets its CSRF cookie with `Secure` and its JS echoes the cookie back as a
# header. The local PDS serves plain http://localhost:2583: Chrome treats localhost as a secure
# context and keeps the cookie, but WebKit (iOS's ASWebAuthenticationSession) drops it, so iOS
# sign-in fails with "Missing CSRF header". The patch makes only that cookie non-Secure; the session
# cookies were never Secure. Upstream notes the CSRF cookie is redundant with SameSite sessions.

FROM ghcr.io/bluesky-social/pds:0.4
RUN f=$(ls /app/node_modules/.pnpm/@atproto+oauth-provider@*/node_modules/@atproto/oauth-provider/dist/router/assets/csrf.js) \
    && sed -i 's/^    secure: true,$/    secure: false, \/\/ local stack: WebKit drops Secure cookies on http:\/\/localhost/' "$f" \
    && grep -q 'secure: false, // local stack' "$f"
