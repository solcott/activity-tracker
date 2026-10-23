# Tasks

## 1. Spike and vectors

- [x] 1.1 In the session scratchpad, install `@atproto/space` and `@atproto/crypto` at `0.0.0-spaces-alpha-20261022141228`. Write a Node script that prints, for fixed keys:
  - an `encapsulate` result (the author KEM key, the ephemeral key, `key_share`, `sharedSecret`);
  - a commit MAC (`space`, `repo`, `rev`, `data`);
  - a record MAC (URI, CID);
  - the `formatWantMacHeader` output and a sample `Atproto-Space-Mac` header.

  Verify the script runs and that its `verifyCommitMac`/`verifyRecordMac` accept its own output.
- [x] 1.2 Write a throwaway `commonTest` that computes ECDH (P-256), HKDF-SHA256 (salt and info) and HMAC-SHA256 with cryptography-kotlin's `optimal` provider and compares them to 1.1's values. Run `:core-atproto:jvmTest`, `:core-atproto:iosSimulatorArm64Test`, and a wasmJs check (the web app or a temporary browser test). Record in design D1 which targets need a fallback, and verify the note is in `design.md` before starting group 2.

## 2. Decoding helpers (`core/atproto/crypto`, `core/lexicon`)

- [x] 2.1 Add `Base58` decode and `p256PublicKey(multibaseOrDidKey)` (design D5). Verify a `commonTest` round-trips `p256DidKey` output, decodes 1.1's KEM Multikey to its public point, and rejects a secp256k1 key, a missing `z`, and bad base58.
- [x] 2.2 Add `Cid.bytes()` for CIDv1 base32. Verify a test against 1.1's record CID, and that a CIDv0 or a non-`b` multibase throws.
- [x] 2.3 Add `#atproto_kem` lookup on `DidDocument`. Verify a `core-lexicon` test finds it by full id and by fragment, and returns null when it's absent.

## 3. Key agreement and MACs (`core/atproto/space`)

- [x] 3.1 Implement `encapsulate(authorKem)` (design D2, using D1's fallback where needed). Verify a test reproduces 1.1's `sharedSecret` and `key_share` from fixed keys on JVM and iOS, and that two calls with a generated key give different key shares.
- [x] 3.2 Implement the `SpaceMac` header codecs (design D4). Verify tests:
  - the Want-Mac header is byte-identical to 1.1's for both types;
  - `Atproto-Space-Mac` parses with reordered keys, an unknown key, and padded base64;
  - a wrong `type`/`ver`, a non-32-byte MAC and a malformed dictionary are `Malformed`.
- [x] 3.3 Implement `SpaceMac.forCommit`/`forRecord` verifiers (design D3). Verify tests: 1.1's commit and record MACs are `Valid`; one flipped bit in `data`, `rev`, `space`, `repo`, the URI or the CID is `Mismatch`; no header is `Missing`.

## 4. XRPC headers

- [x] 4.1 Add `XrpcClient.queryWithHeaders` returning `XrpcResponse(value, headers)`, with `query` delegating to it (design D6). Verify a `MockEngine` test in `XrpcClientTest` that a sent header arrives and a response header comes back, and that every existing `core-atproto` test still passes.
- [x] 4.2 Note the new helpers in `.agents/rules/data-and-auth.md` under atproto Spaces: where KEM and MAC live, a fresh key per request, the secret never logged, and no value-to-CID check. Verify `scripts/check-agent-sync.sh` passes.

## 5. Integration check

- [x] 5.1 Run `./gradlew :core-atproto:jvmTest :core-atproto:iosSimulatorArm64Test :core-lexicon:jvmTest` and `./gradlew check`. Verify both pass.

## Workflow follow-up

- Run `openspec validate spaces-mac-crypto --strict`, archive once merged, then apply `spaces-alpha-2026-10-22`.
