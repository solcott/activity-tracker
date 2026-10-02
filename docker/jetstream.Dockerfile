# syntax=docker/dockerfile:1
# Current bluesky-social/jetstream, pinned and patched for the local stack.
#
# Jetstream bootstraps by downloading repos directly from each PDS the relay lists. For every host
# except the relay itself it uses atmos's SSRF-hardened client (https only, no loopback), so our
# plain-HTTP PDS at localhost:2583 would be unreachable. The patch widens the existing "loopback dev
# relay" exemption to any localhost host. It is pinned to a commit so the sed target can't drift.

FROM golang:1.26-bookworm AS build
ARG JETSTREAM_COMMIT=3fa54fdbb0f47ad3aa43de78a6fbbd8dc362f81d
ADD https://github.com/bluesky-social/jetstream.git#${JETSTREAM_COMMIT} /src
WORKDIR /src
RUN sed -i 's/hostname == relayParsed.Host && isLoopbackHost(relayParsed.Hostname())/strings.HasPrefix(hostname, "localhost:") \&\& isLoopbackHost(relayParsed.Hostname())/' \
        internal/ingest/backfill/run.go \
    && grep -q 'strings.HasPrefix(hostname, "localhost:")' internal/ingest/backfill/run.go
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 go build -trimpath -buildvcs=false -o /out/jetstream ./cmd/jetstream \
    && mkdir -p /out/data

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/jetstream /usr/local/bin/jetstream
# Pre-create the data dir so a fresh named volume inherits nonroot ownership.
COPY --from=build --chown=65532:65532 /out/data /data
USER 65532:65532
ENTRYPOINT ["/usr/local/bin/jetstream"]
CMD ["serve"]
