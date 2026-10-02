# syntax=docker/dockerfile:1
# bluesky-social/indigo relay, pinned and patched for the local stack.
#
# The relay's HostClient (the describeServer check on requestCrawl, and account-status lookups) is
# hard-wired to an SSRF "public IPs only" transport, so it rejects our PDS at localhost:2583 even
# though HACKING.md says explicit localhost hosts are exempt. The patch swaps in the default
# transport. It is pinned to a commit so the sed target can't drift.

FROM golang:1.26-alpine AS build
# cgo toolchain for go-sqlite3 (the relay's default sqlite DB).
RUN apk add --no-cache build-base
ARG INDIGO_COMMIT=b2619d864df00a66455bd9f5f0c83c1ac13ad73d
ADD https://github.com/bluesky-social/indigo.git#${INDIGO_COMMIT} /src
WORKDIR /src
RUN sed -i -e 's/Transport: ssrf.PublicOnlyTransport(),/Transport: http.DefaultTransport,/' \
        -e '\#"github.com/bluesky-social/indigo/util/ssrf"#d' cmd/relay/relay/host_checker.go \
    && grep -q 'http.DefaultTransport' cmd/relay/relay/host_checker.go
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=1 go build -tags timetzdata -o /relay ./cmd/relay

FROM alpine:3.22
RUN apk add --no-cache dumb-init ca-certificates && mkdir -p /data/relay
COPY --from=build /relay /relay
WORKDIR /
ENV GODEBUG=netdns=go
ENTRYPOINT ["dumb-init", "--"]
CMD ["/relay", "serve"]
