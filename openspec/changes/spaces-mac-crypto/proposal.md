# Proposal

## Why

The Spaces alpha released on 2026-10-22 drops commit signatures in favour of HMACs keyed by a per-request shared secret. A reader agrees on that secret with the author's new `#atproto_kem` DID key. A credentialed `getRecord` or `listRepoOps` that doesn't ask for a MAC (`Atproto-Space-Want-Mac`) is refused with `BadSpaceMac`. So the bump to that alpha (`spaces-alpha-2026-10-22`) can't land until `core/atproto` can do the key agreement and check MACs on every client target.

That crypto is the riskiest part of the bump: ECDH and HKDF in cryptography-kotlin on iOS and wasmJs are untested here. It's also purely additive, so it lands first, on today's Oct 1 stack, and the bump itself stays small. This is change 1 of 2 for the Oct 22 alpha, and no request uses it yet.

## What Changes

- **Key agreement.** For an author's `#atproto_kem` key (a P-256 Multikey), make a fresh ephemeral P-256 key, compute ECDH, and derive the 32-byte shared secret with HKDF-SHA256 using the guide's HPKE-style `prefix` and `info`. Return the ephemeral public key as a `did:key` to send as `key_share`.
- **MAC computation and checking.** Context strings are `atproto-space-repo-v1` for commits and `atproto-space-record-v1` for records, each field prefixed with its uint16 length. HMAC-SHA256 covers a commit's `data`, or a record's URI and binary CID. Comparison is constant time.
- **Header codecs.** Format `Atproto-Space-Want-Mac: type=…, ver=1, key_share="did:key:…"` and parse `Atproto-Space-Mac: type=…, ver=1, mac=:<base64>:`, a Structured Field Dictionary. A parsed MAC whose `type` or `ver` doesn't match the request is rejected.
- **Supporting pieces:**
  - decode a P-256 Multikey or `did:key` into a public key (base58btc decode and point decompression in commonMain; today only encoding exists);
  - decode a CID string into its binary bytes;
  - look up `#atproto_kem` on a `DidDocument`;
  - `XrpcClient` calls that return response headers alongside the decoded body.
- **Test vectors** generated once with the alpha's `@atproto/space` package (`encapsulate`, `verifyCommitMac`, `verifyRecordMac`), checked on the JVM, iOS simulator and, where tests run, wasmJs.

## Capabilities

### New Capabilities
None. The behaviour (verified member reads and syncer pulls) is specified in `spaces-alpha-2026-10-22`, which wires this in.

### Modified Capabilities
None. This change adds library code that no request uses yet, so it sets `skip_specs: true`.

## Impact

- **`core/atproto`:** new `space/SpaceKem.kt` (key agreement), `space/SpaceMac.kt` (contexts, HMAC, header codecs); `crypto/Base58.kt` gains decoding, and `crypto/DidKey.kt` gains Multikey → public key; a CID-bytes helper; `XrpcClient` gains header-returning variants of `query`/`procedure`; tests in `commonTest`.
- **`core/lexicon`:** `DidDocument` may gain a `kemKey()` convenience, or callers use `verificationKey("#atproto_kem")`.
- **Dependencies:** none new, if cryptography-kotlin 0.6.0's `optimal` provider gives ECDH, HKDF and HMAC on every target. The spike (task 1) decides this before anything else is built.
- **No wire, app or AppView behaviour changes.** The Oct 1 PDS pin is unchanged.
