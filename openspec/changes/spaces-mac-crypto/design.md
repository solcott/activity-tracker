# Design

## Context

- **The source.** The protocol is in Bluesky's migration guide `alpha-migrations/prompt-10-22-26.md` (atproto `permissioned-data` branch), sections 4b–4f and 9, and its reference is `@atproto/space` `0.0.0-spaces-alpha-20261022141228`. Proposal 125 in `bluesky-social/proposals` is the design behind it.
- **Crypto today.** `DpopKey` (`core/atproto/crypto`) is a P-256 ECDSA key on cryptography-kotlin 0.6.0 with the `optimal` provider on every target (JDK, WebCrypto on wasmJs, Apple on iOS). `DidKey.kt` encodes an uncompressed P-256 point as a `did:key` (compressing it), and `Base58.kt` only encodes. The AppView has its own Multikey decoder (`server/appview/auth/Multikey.kt`, BouncyCastle), which the clients can't use.
- **XRPC today.** `XrpcClient.query`/`procedure` return only the decoded body, and `execute` holds the `HttpResponse` internally. `Cid` is a string value class.
- **Who will call this** (in `spaces-alpha-2026-10-22`): `SpaceMember` for member `getRecord`, on clients, and `SpaceSyncer` for `listRepoOps`, on the AppView's JVM.

## Goals / Non-Goals

**Goals:**
- Byte-exact agreement with `@atproto/space` for key agreement, both MAC contexts and the two headers, on every client target.
- APIs shaped for the calls that need them: one call gives a `(wantMacHeader, verifier)` pair for a request.

**Non-Goals:**
- Wiring into any request, `Space.kt` wire-type changes, or the PDS bump (all in `spaces-alpha-2026-10-22`).
- Checking a record's value against its CID (DRISL/DAG-CBOR re-encoding), or recomputing a repo's set hash. We verify the MAC over what the host returns: the commit's `data`, and the record's URI and CID. Full verification is a later change if it's ever wanted.
- STAR archives (`getRepo`) and the author side (`decapsulate`). We never serve space repos.

## Decisions

### D1. Spike the crypto on every target first
cryptography-kotlin's `ECDH` and `HKDF` support differs by provider. Task 1 is a throwaway `commonTest` that runs ECDH on P-256, HKDF-SHA256 with salt and info, and HMAC-SHA256 under `jvmTest`, `iosSimulatorArm64Test` and the wasmJs browser test (or a manual run in the web app, since wasm tests are disabled). Each output must match a value from the Node reference.
- **If iOS lacks ECDH or HKDF:** HKDF is a few lines over HMAC (RFC 5869 extract then expand), so we hand-write it in commonMain. ECDH without provider support needs an Apple `actual` over CryptoKit (`P256.KeyAgreement`) through an `expect fun ecdhP256(...)`. Other targets keep the provider. The spike records which way it went in this file before task 2.
- **Alternative:** a pure-Kotlin EC implementation. Rejected: not constant-time, and more to maintain.
- **Spike result (2026-10-23): no fallback on any target.** A throwaway `commonTest` compared cryptography-kotlin 0.6.0's `optimal` provider with `@atproto/space`/`@atproto/crypto` `0.0.0-spaces-alpha-20261022141228` (fixed keys `0x11…11` author, `0x22…22` ephemeral) and RFC 5869 case 1. ECDH on P-256 (the x-coordinate), HKDF-SHA256 with salt and info and with an empty salt, HMAC-SHA256, and private-key import as `RAW` and `DER.SEC1` all matched on `jvmTest` (JDK), `iosSimulatorArm64Test` (CryptoKit/Apple) and `wasmJsNodeTest` (WebCrypto under Node, run with the test task enabled temporarily). So `SpaceKem` uses the provider everywhere, with no `expect`/`actual` and no hand-written HKDF.
- **Compressed points:** `RAW.Compressed` public keys decoded on all three, and Chromium's WebCrypto accepts them, but the JDK provider decompresses in its own Kotlin and the WebCrypto provider hands the bytes to the browser, where Safari and Firefox are unverified. So D5 decompresses in commonMain and providers only ever see uncompressed points, the format `DpopKey` already uses.

### D2. Shared secret exactly as the guide specifies
Here `dh` is the 32-byte x-coordinate of the shared point, and `keys` is `0x04‖syncer.x‖syncer.y ‖ 0x04‖author.x‖author.y`. Then `prefix = "HPKE-v1"‖"KEM"‖u16be(0x0010)`, `ikm = prefix‖"eae_prk"‖dh` and `info = u16be(32)‖prefix‖"shared_secret"‖keys`, and the secret is `HKDF-SHA256(ikm, salt = empty, info, L = 32)`. It's an HPKE DHKEM(P-256) shared secret, but we don't take an HPKE dependency for 30 lines. A fresh ephemeral key per request, retries and pages included, is the caller's job. The API makes reuse awkward by returning the key share and secret together from one `encapsulate(authorKem)` call, with no way to pass a key in.

### D3. MAC API is a request-scoped verifier
`SpaceMac.forCommit(space, repo)` and `SpaceMac.forRecord(uri)` take the author's KEM key and return:
- the `Atproto-Space-Want-Mac` header value;
- a `verify(macHeader: String?, payload)` function: `payload` is the commit's `rev` and `data` bytes, or the record's CID.

`verify` returns a sealed result: `Valid`, `Missing` (no header), `Mismatch`, or `Malformed` (wrong `type` or `ver`, bad base64, length ≠ 32). The caller then decides between refreshing the DID doc and retrying, or failing. Comparison is constant time over the full 32 bytes.
- **Why a verifier and not a free function:** the secret never leaves the object, so it can't be cached or logged by mistake (the logging rule bans keys).

### D4. Structured-field headers by hand, narrowly
We write only our three keys (`type` string, `ver` integer, `key_share` string) and parse a dictionary with `type`, `ver` and `mac` (a byte sequence `:…:`). Unknown keys are ignored and a duplicate key keeps the last value, as RFC 8941 says. There's no general SF library. This is the same choice as `HttpMessageSignature`.

### D5. Key and CID decoding in commonMain
- `Base58` gains `decode`.
- `DidKey` gains `p256PublicKey(multibaseOrDidKey)`: strip `did:key:`, require `z`, base58-decode, require the `0x1200` varint prefix, decompress the 33-byte point (or hand the compressed form to the provider if it accepts it; the spike checks), and reject anything else.
- `Cid.bytes()`: multibase `b` (base32 lower), decoded to the raw CID bytes, and only CIDv1 accepted. The record MAC covers these bytes.

### D6. Header-returning XRPC calls
`XrpcClient.queryWithHeaders(method, params, headers)` returns `XrpcResponse(value, headers)`. The existing `query` delegates to it, so no caller changes. On a browser, the MAC header is readable only if the PDS sends `Access-Control-Expose-Headers: Atproto-Space-Mac`. That's checked in the bump, not here.

## Risks / Trade-offs

- [The Apple provider lacks ECDH] → D1's CryptoKit `actual`. The spike runs first, so the cost is known before the rest is built.
- [The vectors drift from the next alpha (Oct 29)] → the vectors live in one test file with the package version in a comment, and are regenerated on each bump.
- [We don't verify the value-to-CID binding] → a host could pair a valid (uri, CID) MAC with another value. The host is the author's own PDS, which also holds the KEM private key, so the MAC already trusts it for everything a value check would cover. Recorded as a known gap in `data-and-auth.md` when it's wired in.
