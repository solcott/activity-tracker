package io.github.solcott.activitytracker.atproto

/**
 * Key agreement and MAC vectors from `@atproto/space` and `@atproto/crypto` `0.0.0-spaces-alpha-20261112204144` (the Nov 12 Spaces alpha),
 * regenerated on each alpha bump. The ephemeral key stands in for `encapsulate`'s random one: [SHARED_SECRET] is `decapsulate(author,
 * KEY_SHARE)`, the requests are `formatMacRequest`, and the response headers are `createCommitMacHeaders`/`createRecordMacHeaders` under it
 * (each accepted by `verify*Mac`). [CID] is CIDv1 dag-cbor of `sha256("record")`.
 *
 * The `HPKE_*` keys are the export-only DHKEM(P-256, HKDF-SHA256) entry of the RFC 9180 test vectors (`enc` and `skRm`); their secret,
 * under `info` "atproto-mac" and an empty exporter context, was computed with Go's `crypto/hpke` (upstream's `kem.test.ts`).
 */
object SpaceMacVectors {
    const val AUTHOR_PRIVATE = "1111111111111111111111111111111111111111111111111111111111111111"
    const val AUTHOR_DID_KEY = "did:key:zDnaeQa8zprPhHA7Yuxcgc2Uh6XNQgaKjmFZE4EaA9fk5svmJ"
    const val AUTHOR_MULTIKEY = "zDnaeQa8zprPhHA7Yuxcgc2Uh6XNQgaKjmFZE4EaA9fk5svmJ"
    const val AUTHOR_RAW =
        "040217e617f0b6443928278f96999e69a23a4f2c152bdf6d6cdf66e5b80282d4ed194a7debcb97712d2dda3ca85aa8765a56f45fc758599652f2897c65306e5794"

    const val EPHEMERAL_PRIVATE = "2222222222222222222222222222222222222222222222222222222222222222"
    const val EPHEMERAL_RAW =
        "04d65a93977caa3d1b081852ff57a79e465f1660577304baead505dd3a48589cf350185e895372df6221ea3a137557e473fddb6755f05bd507c3c533fce9c91285"
    const val KEY_SHARE = "did:key:zDnaex62me84JZnkEzmeYRa8FCLNe7y1asoSwBMK26GBYpL7c"
    const val SHARED_SECRET = "19ac581c03a25905d77bbcad0b8f9b3eca96089a62b7e7d2984a502a913516d1"

    const val HPKE_ENC =
        "04d804370b7e24b94749eb1dc8df6d4d4a5d75f9effad01739ebcad5c54a40d57aaa8b4190fc124dbde2e4f1e1d1b012a3bc4038157dc29b55533a932306d8d38d"
    const val HPKE_SK_RM = "62c3868357a464f8461d03aa0182c7cebcde841036aea7230ddc7339f1088346"
    const val HPKE_PK_RM_DID_KEY = "did:key:zDnaepxWioy4TQi6a94tSHKU4aqQkz6rksDiBfnuiGbwf4i5N"
    const val HPKE_SECRET = "2060e0558d1b34c10086bc137f849bb79ba59fcdc30544418e35c9cd301f0d0d"

    const val SPACE = "at://did:plc:alice/space/app.activitytracker.space.athlete/followers"
    const val REPO = "did:plc:alice"
    const val REV = "3m2xyzabcde22"
    const val DATA = "00010203a0b0c0d0e0f0ff"

    const val COMMIT_REQUEST = """mac=("atproto-space-commit";sf);keyid="$KEY_SHARE";tag="atproto-space-commit""""
    const val COMMIT_HEADER = """ver=1, repo="$REPO", space="$SPACE", rev="$REV", data=:AAECA6CwwNDg8P8=:"""
    const val COMMIT_SIGNATURE_INPUT = COMMIT_REQUEST
    const val COMMIT_SIGNATURE = "mac=:Okjw1Bd0hIAaUtpX7bT4Fo3A1W4lGR2SQIJeCAikjtc=:"

    const val URI =
        "at://did:plc:alice/space/app.activitytracker.space.athlete/followers/did:plc:alice/app.activitytracker.feed.activity/3m2xyzabcde22"
    const val CID = "bafyreidqz2dr7cr5h62etpb4hlhgkr6o6aw7y5h74sgzcjjsu4sl7w7fxe"
    const val CID_BYTES = "0171122070ce871f8a3d3fb449bc3c3ace6547cef02dfc74ffe48d912532a724bfdbe5b9"

    const val RECORD_REQUEST = """mac=("atproto-space-record";sf);keyid="$KEY_SHARE";tag="atproto-space-record""""
    const val RECORD_HEADER = """uri="$URI", cid="$CID""""
    const val RECORD_SIGNATURE_INPUT = RECORD_REQUEST
    const val RECORD_SIGNATURE = "mac=:0a9P5KzuGnbHxud986r7xiLrlDTdvZaxKRN3uf/bc1k=:"

    const val CID_V0 = "QmVw12TXLgQqUGqrJvu19NnoYV7hio3oyfPxrTRjiHhyRA"
    const val K256_DID_KEY = "did:key:zQ3shokFTS3brHcDQrn82RUDfCZESWL1ZdCEJwekUDPQiYBme"
}
