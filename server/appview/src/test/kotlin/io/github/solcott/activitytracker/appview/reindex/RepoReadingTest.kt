package io.github.solcott.activitytracker.appview.reindex

import io.github.solcott.activitytracker.appview.TestAccountKey
import io.github.solcott.activitytracker.appview.ipld.BlockCid
import io.github.solcott.activitytracker.appview.ipld.CarFile
import io.github.solcott.activitytracker.appview.ipld.CarLimits
import io.github.solcott.activitytracker.appview.ipld.DagCbor
import io.github.solcott.activitytracker.appview.ipld.RepoFormatException
import io.github.solcott.activitytracker.appview.ipld.RepoLimitException
import io.github.solcott.activitytracker.appview.ipld.atprotoJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import java.nio.file.Files
import kotlin.io.path.listDirectoryEntries
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** CIDs, CAR files, DAG-CBOR and the repo walk (admin/repo-reindex: "Only a verified repo is applied", "Reindex work is bounded"). */
class RepoReadingTest {
    private val dir = Files.createTempDirectory("reindex-test")
    private val roomy = CarLimits(maxBytes = 10_000_000, maxBlocks = 10_000)

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    private fun car(bytes: ByteArray, limits: CarLimits = roomy) = CarFile.read(bytes.inputStream(), limits, dir)

    @Test
    fun `the reference implementation's repo is read whole`() {
        car(RepoFixture.bytes()).use { car ->
            assertEquals(RepoFixture.ROOT, car.root.string)
            val repo = RepoSnapshot.open(car, Did(RepoFixture.DID), RepoFixture.key)
            assertEquals(RepoFixture.REV, repo.rev)
            val entries = repo.entries().toList()
            assertEquals(RepoFixture.RECORDS, entries.size)
            assertEquals(entries.map { "${it.collection}/${it.rkey}" }.sorted(), entries.map { "${it.collection}/${it.rkey}" })
            val collections = entries.map { it.collection }.toSet()
            assertTrue(Nsid.ACTOR_PROFILE in collections && Nsid.GRAPH_FOLLOW in collections && Nsid.BSKY_GRAPH_BLOCK in collections)
            assertEquals(40, entries.count { it.collection == Nsid.FEED_POST })
            // Each record's block hashes to its CID, so reading it back is checked too.
            val post = entries.first { it.collection == Nsid.FEED_POST }
            val record = repo.record(post)
            assertEquals(Nsid.FEED_POST, record["\$type"]?.jsonPrimitive?.content)
            assertEquals("AQID", record["bytesField"]?.jsonObject?.get("\$bytes")?.jsonPrimitive?.content)
            assertEquals(
                "did:plc:bob",
                repo.record(entries.first { it.collection == Nsid.GRAPH_FOLLOW })["subject"]?.jsonPrimitive?.content,
            )
        }
    }

    @Test
    fun `the temp file is deleted on close and on failure`() {
        car(RepoFixture.bytes()).close()
        assertFailsWith<RepoFormatException> { car(RepoFixture.bytes().copyOf(200)) }
        assertEquals(emptyList(), dir.listDirectoryEntries())
    }

    @Test
    fun `a truncated CAR is refused`() {
        val failure = assertFailsWith<RepoFormatException> { car(RepoFixture.bytes().copyOf(RepoFixture.bytes().size - 10)) }
        assertEquals("truncated", failure.code)
    }

    @Test
    fun `a CAR over the size limit is refused`() {
        val failure = assertFailsWith<RepoLimitException> { car(RepoFixture.bytes(), roomy.copy(maxBytes = 1000)) }
        assertEquals("too_large", failure.code)
    }

    @Test
    fun `a CAR over the block limit is refused`() {
        val failure = assertFailsWith<RepoLimitException> { car(RepoFixture.bytes(), roomy.copy(maxBlocks = 10)) }
        assertEquals("too_many_blocks", failure.code)
    }

    @Test
    fun `a block that doesn't match its CID is never used`() {
        val repo = TestRepo("did:plc:alice")
        val tree = repo.tree(mapOf("${Nsid.FEED_POST}/3k1" to mapOf("\$type" to Nsid.FEED_POST, "text" to "hi")))
        val root = repo.commit(tree)
        // Swap the record's bytes for others under the same CID.
        val record = repo.blocks.keys.first()
        repo.blocks[record] = DagCbor.encode(mapOf("\$type" to Nsid.FEED_POST, "text" to "tampered"))
        car(repo.car(root)).use { car ->
            val snapshot = RepoSnapshot.open(car, Did("did:plc:alice"), repo.signingKey)
            val entry = snapshot.entries().single()
            assertEquals("bad_block", assertFailsWith<RepoFormatException> { snapshot.record(entry) }.code)
        }
    }

    @Test
    fun `a commit signed by another key is refused`() {
        val repo = TestRepo("did:plc:alice")
        car(repo.repo(mapOf("${Nsid.FEED_POST}/3k1" to mapOf("text" to "hi")))).use { car ->
            val other = TestRepo("did:plc:alice", TestAccountKey()).signingKey
            assertEquals("bad_signature", assertFailsWith<RepoFormatException> { RepoSnapshot.open(car, Did("did:plc:alice"), other) }.code)
        }
    }

    @Test
    fun `a commit edited after signing is refused`() {
        val repo = TestRepo("did:plc:alice")
        val root = repo.commit(repo.tree(emptyMap())) { it["rev"] = "3zzzzzzzzzzzz" }
        car(repo.car(root)).use { car ->
            assertEquals(
                "bad_signature",
                assertFailsWith<RepoFormatException> { RepoSnapshot.open(car, Did("did:plc:alice"), repo.signingKey) }.code,
            )
        }
    }

    @Test
    fun `a commit naming another account is refused`() {
        val repo = TestRepo("did:plc:mallory")
        car(repo.repo(emptyMap())).use { car ->
            assertEquals(
                "bad_commit",
                assertFailsWith<RepoFormatException> { RepoSnapshot.open(car, Did("did:plc:alice"), repo.signingKey) }.code,
            )
        }
    }

    @Test
    fun `a tree deeper than the limit is refused`() {
        val repo = TestRepo("did:plc:alice")
        // 70 empty nodes, each the left subtree of the next.
        var link: DagCbor.Link? = null
        repeat(70) { link = TestRepo.link(repo.block(mapOf("l" to link, "e" to emptyList<Any>()))) }
        val root = repo.commit(checkNotNull(link))
        car(repo.car(root)).use { car ->
            val snapshot = RepoSnapshot.open(car, Did("did:plc:alice"), repo.signingKey)
            assertEquals("bad_tree", assertFailsWith<RepoFormatException> { snapshot.entries().toList() }.code)
        }
    }

    @Test
    fun `tree keys out of order are refused`() {
        val repo = TestRepo("did:plc:alice")
        val record = TestRepo.link(repo.block(mapOf("text" to "hi")))
        val entries =
            listOf("${Nsid.FEED_POST}/3k2", "${Nsid.FEED_POST}/3k1").map {
                mapOf("p" to 0L, "k" to it.encodeToByteArray(), "v" to record, "t" to null)
            }
        val root = repo.commit(TestRepo.link(repo.block(mapOf("l" to null, "e" to entries))))
        car(repo.car(root)).use { car ->
            val snapshot = RepoSnapshot.open(car, Did("did:plc:alice"), repo.signingKey)
            assertEquals("bad_tree", assertFailsWith<RepoFormatException> { snapshot.entries().toList() }.code)
        }
    }

    @Test
    fun `only SHA-256 CIDv1s are read`() {
        // A CIDv0 starts with the multihash itself.
        assertFailsWith<RepoFormatException> { BlockCid.of(byteArrayOf(0x12, 0x20) + ByteArray(32)) }
        // CIDv1, dag-cbor, but a sha2-512 multihash.
        assertFailsWith<RepoFormatException> { BlockCid.of(byteArrayOf(0x01, 0x71, 0x13, 0x40) + ByteArray(64)) }
        val block = DagCbor.encode(mapOf("a" to 1L))
        val cid = BlockCid.ofBlock(block)
        assertEquals(cid, BlockCid.of(cid.bytes))
        assertTrue(cid.matches(block))
    }

    @Test
    fun `DAG-CBOR links and bytes read as atproto JSON`() {
        val cid = BlockCid.ofBlock(byteArrayOf(1))
        val value = DagCbor.decode(DagCbor.encode(mapOf("ref" to DagCbor.Link(cid), "data" to byteArrayOf(1, 2, 3), "n" to -5L)))
        val json = atprotoJson(value) as JsonObject
        assertEquals(JsonObject(mapOf("\$link" to JsonPrimitive(cid.string))), json["ref"])
        assertEquals(JsonObject(mapOf("\$bytes" to JsonPrimitive("AQID"))), json["data"])
        assertEquals(JsonPrimitive(-5L), json["n"])
    }

    @Test
    fun `DAG-CBOR refuses floats, other tags and deep nesting`() {
        // {"a": 1.5} as a double.
        val float = byteArrayOf(0xA1.toByte(), 0x61, 0x61, 0xFB.toByte(), 0x3F, 0xF8.toByte(), 0, 0, 0, 0, 0, 0)
        assertEquals("bad_cbor", assertFailsWith<RepoFormatException> { DagCbor.decode(float) }.code)
        // Tag 1 (epoch time) on an integer.
        assertEquals("bad_cbor", assertFailsWith<RepoFormatException> { DagCbor.decode(byteArrayOf(0xC1.toByte(), 0x00)) }.code)
        var deep: Any? = 1L
        repeat(80) { deep = listOf(deep) }
        assertEquals("bad_cbor", assertFailsWith<RepoFormatException> { DagCbor.decode(DagCbor.encode(deep)) }.code)
    }

    @Test
    fun `DAG-CBOR map keys must be text`() {
        // {1: "x"}: an integer key, which Jackson would name "1".
        val intKey = byteArrayOf(0xA1.toByte(), 0x01, 0x61, 0x78)
        assertEquals("bad_cbor", assertFailsWith<RepoFormatException> { DagCbor.decode(intKey) }.code)
        // {"a": {h'00': 1}}: a byte-string key, nested.
        val bytesKey = byteArrayOf(0xA1.toByte(), 0x61, 0x61, 0xA1.toByte(), 0x41, 0x00, 0x01)
        assertEquals("bad_cbor", assertFailsWith<RepoFormatException> { DagCbor.decode(bytesKey) }.code)
        // Text keys after a value of every shape still read, so each key's offset is where its head is.
        val nested = mapOf("a" to listOf(1L, "two", mapOf("b" to byteArrayOf(1, 2))), "cc" to mapOf("d" to null), "eee" to 300L)
        val decoded = DagCbor.decode(DagCbor.encode(nested)) as Map<*, *>
        assertEquals(setOf("a", "cc", "eee"), decoded.keys)
        assertEquals(300L, decoded["eee"])
    }
}
