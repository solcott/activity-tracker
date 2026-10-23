package io.github.solcott.activitytracker.atproto

/**
 * Key agreement and MAC vectors from `@atproto/space` and `@atproto/crypto` `0.0.0-spaces-alpha-20261022141228` (the Oct 22 Spaces alpha),
 * regenerated on each alpha bump. The ephemeral key stands in for `encapsulate`'s random one: [SHARED_SECRET] is `decapsulate(author,
 * KEY_SHARE)`, the MACs are `computeCommitMac`/`computeRecordMac` under it (each accepted by `verify*Mac`), and the headers are
 * `formatWantMacHeader`/`formatMacHeader`. [CID] is CIDv1 dag-cbor of `sha256("record")`.
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
    const val SHARED_SECRET = "76bccf1264f5f7f5b7df0b78b6c1037f2e22fb513288207ad2dede881710d4c3"

    const val SPACE = "at://did:plc:alice/space/app.activitytracker.space.athlete/followers"
    const val REPO = "did:plc:alice"
    const val REV = "3m2xyzabcde22"
    const val DATA = "00010203a0b0c0d0e0f0ff"
    const val COMMIT_MAC = "e4c10a488236ec05a30d1f4e6b4df1512428b7ef5efcad6d416ea82ded79f327"
    const val COMMIT_MAC_HEADER = """type="atproto-space-repo", ver=1, mac=:5MEKSII27AWjDR9Oa03xUSQot+9e/K1tQW6oLe158yc=:"""

    const val URI =
        "at://did:plc:alice/space/app.activitytracker.space.athlete/followers/did:plc:alice/app.activitytracker.feed.activity/3m2xyzabcde22"
    const val CID = "bafyreidqz2dr7cr5h62etpb4hlhgkr6o6aw7y5h74sgzcjjsu4sl7w7fxe"
    const val CID_BYTES = "0171122070ce871f8a3d3fb449bc3c3ace6547cef02dfc74ffe48d912532a724bfdbe5b9"
    const val RECORD_MAC = "b7678d4b802c66af99b281e9465d5174e8f52b29337c657c16d51ff1642b18b5"
    const val RECORD_MAC_HEADER = """type="atproto-space-record", ver=1, mac=:t2eNS4AsZq+ZsoHpRl1RdOj1KykzfGV8FtUf8WQrGLU=:"""

    const val WANT_MAC_COMMIT = """type="atproto-space-repo", ver=1, key_share="$KEY_SHARE""""
    const val WANT_MAC_RECORD = """type="atproto-space-record", ver=1, key_share="$KEY_SHARE""""

    const val CID_V0 = "QmVw12TXLgQqUGqrJvu19NnoYV7hio3oyfPxrTRjiHhyRA"
    const val K256_DID_KEY = "did:key:zQ3shokFTS3brHcDQrn82RUDfCZESWL1ZdCEJwekUDPQiYBme"
}
