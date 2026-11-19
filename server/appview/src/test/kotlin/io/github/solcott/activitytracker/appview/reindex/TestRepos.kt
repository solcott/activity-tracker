package io.github.solcott.activitytracker.appview.reindex

import io.github.solcott.activitytracker.appview.TestAccountKey
import io.github.solcott.activitytracker.appview.auth.Multikey
import io.github.solcott.activitytracker.appview.auth.SigningKey
import io.github.solcott.activitytracker.appview.ipld.BlockCid
import io.github.solcott.activitytracker.appview.ipld.DagCbor
import io.github.solcott.activitytracker.appview.ipld.Varint
import java.io.ByteArrayOutputStream

/**
 * `fixture-repo.car`, written by the reference `@atproto/repo` (`gen-fixture.mjs`): a profile, a follow, a Bluesky block, a Bluesky post
 * (not indexed) and 40 `feed.post`s, each carrying a `bytesField`, signed with a test-only key.
 */
internal object RepoFixture {
    const val DID = "did:plc:reindexfixture0000000000"
    const val REV = "3n2ne67qz4s27"
    const val ROOT = "bafyreiakk3vte5qmmb7gpmhi6zj5z7lbdfkwv4revhbgh2xufrmdz3zdbm"
    const val RECORDS = 44
    private const val KEY = "zQ3shokFTS3brHcDQrn82RUDfCZESWL1ZdCEJwekUDPQiYBme"

    /** The multibase key a DID document would publish as `#atproto`. */
    const val MULTIBASE_KEY = KEY

    val key: SigningKey = Multikey.decode(KEY)

    fun bytes(): ByteArray = checkNotNull(RepoFixture::class.java.getResourceAsStream("/reindex/fixture-repo.car")).use { it.readBytes() }
}

/**
 * Builds small repos for tests: blocks are DAG-CBOR values, the tree is one node holding every record in key order (enough for the walker,
 * which checks order, not layers), and the commit is signed with [key] unless a test breaks it.
 */
internal class TestRepo(val did: String, val key: TestAccountKey = TestAccountKey()) {
    val blocks = LinkedHashMap<BlockCid, ByteArray>()

    fun block(value: Any?): BlockCid {
        val bytes = DagCbor.encode(value)
        return BlockCid.ofBlock(bytes).also { blocks[it] = bytes }
    }

    /** A one-node tree of [records] (key `collection/rkey` → record), returned as its root link. */
    fun tree(records: Map<String, Any?>): DagCbor.Link {
        val entries =
            records.toSortedMap().map { (key, record) ->
                mapOf("p" to 0L, "k" to key.encodeToByteArray(), "v" to link(block(record)), "t" to null)
            }
        return link(block(mapOf("l" to null, "e" to entries)))
    }

    /** A signed commit over [data]. [edit] changes it after signing (to break it). */
    fun commit(data: DagCbor.Link, rev: String = "3n2ne67qz4s27", edit: (MutableMap<String, Any?>) -> Unit = {}): BlockCid {
        val unsigned = mapOf("did" to did, "version" to 3L, "data" to data, "rev" to rev, "prev" to null)
        val signed = (unsigned + ("sig" to key.sign(DagCbor.encode(unsigned)))).toMutableMap()
        edit(signed)
        return block(signed)
    }

    fun car(root: BlockCid): ByteArray = carOf(root, blocks)

    /** A repo of [records], as a CAR. */
    fun repo(records: Map<String, Any?>, rev: String = "3n2ne67qz4s27"): ByteArray = car(commit(tree(records), rev))

    val signingKey: SigningKey
        get() = Multikey.decode(key.multibase)

    companion object {
        fun link(cid: BlockCid): DagCbor.Link = DagCbor.Link(cid)

        fun carOf(root: BlockCid, blocks: Map<BlockCid, ByteArray>): ByteArray {
            val out = ByteArrayOutputStream()
            val header = DagCbor.encode(mapOf("version" to 1L, "roots" to listOf(link(root))))
            Varint.write(out, header.size.toLong())
            out.write(header)
            for ((cid, bytes) in blocks) {
                Varint.write(out, (cid.bytes.size + bytes.size).toLong())
                out.write(cid.bytes)
                out.write(bytes)
            }
            return out.toByteArray()
        }
    }
}
