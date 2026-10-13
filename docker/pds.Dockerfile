# syntax=docker/dockerfile:1
# The atproto Spaces alpha PDS, patched for the local stack.
#
# Image: ghcr.io/bluesky-social/atproto:pds-spaces-alpha, built 2026-10-01. Pinned by digest because the alpha ships breaking
# changes under the same tag; bump deliberately and re-run docker/scripts/spaces-smoke.sh. It is amd64-only, so compose runs it
# with `platform: linux/amd64` (Rosetta on Apple Silicon). Unlike the pds distribution image this is the atproto monorepo, so
# packages live under /app/packages rather than node_modules/.pnpm.
#
# The OAuth sign-in page sets its CSRF cookie with `Secure` and its JS echoes the cookie back as a
# header. The local PDS serves plain http://localhost:2583: Chrome treats localhost as a secure
# context and keeps the cookie, but WebKit (iOS's ASWebAuthenticationSession) drops it, so iOS
# sign-in fails with "Missing CSRF header". The patch makes only that cookie non-Secure; the session
# cookies were never Secure. Upstream notes the CSRF cookie is redundant with SameSite sessions.
# The find must match exactly one file, and the grep fails the build if the line has drifted.

FROM ghcr.io/bluesky-social/atproto:pds-spaces-alpha@sha256:1cf9349e9f0e89789784569fed376cd647742ea7c368948625f44779ba03f150
# The image runs as `node`, which can't write under /app; patch as root, then drop back.
USER root
RUN set -e; \
    f=$(find /app -path '*/oauth-provider/dist/router/assets/csrf.js' -not -path '*/node_modules/*'); \
    if [ "$(printf '%s' "$f" | grep -c .)" -ne 1 ]; then echo "expected exactly one oauth-provider csrf.js, found: '$f'" >&2; exit 1; fi; \
    sed -i 's/^    secure: true,$/    secure: false, \/\/ local stack: WebKit drops Secure cookies on http:\/\/localhost/' "$f"; \
    grep -q 'secure: false, // local stack' "$f"
# PDS_DATA_DIRECTORY. A new named volume copies this directory's ownership, so `node` can write its SQLite files and blobs.
RUN mkdir -p /pds && chown node:node /pds
USER node
