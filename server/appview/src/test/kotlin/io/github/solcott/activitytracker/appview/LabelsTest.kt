package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.AppViewConfig.Labeler
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.Labels
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.labels.DagCbor
import io.github.solcott.activitytracker.appview.labels.Label
import io.github.solcott.activitytracker.appview.labels.LabelFrame
import io.github.solcott.activitytracker.appview.labels.LabelStore
import io.github.solcott.activitytracker.appview.labels.LabelSubscriber
import io.github.solcott.activitytracker.appview.labels.LabelVerifier
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

/** `moderation/labels`: decoding and verifying labels, and which ones the AppView keeps. */
class LabelsTest {
    private val own = "did:plc:labeler"
    private val bsky = "did:plc:ar7c4by46qjdydhdevvrndac"
    private val bob = "did:plc:bob"
    private val activity = "at://$bob/app.activitytracker.feed.activity/3k"

    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val now = Instant.parse("2026-10-21T12:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = now
        }
    private val store = LabelStore(db, own, bsky, clock)

    private fun label(
        value: String,
        uri: String = activity,
        cts: String = "2026-10-21T10:00:00.000Z",
        src: String = own,
        neg: Boolean = false,
    ) = testLabel(src, uri, value, cts, labelExtras(neg = neg))

    private suspend fun stored(): Set<Triple<String, String, String>> = db.tx {
        Labels.selectAll().map { Triple(it[Labels.src], it[Labels.uri], it[Labels.value]) }.toSet()
    }

    private suspend fun athlete(did: String) = db.tx {
        Actors.insert {
            it[Actors.did] = did
            it[handle] = "athlete.test"
            it[indexedAt] = 0
        }
    }

    // ---------------------------------------------------------------- decoding and verification

    private fun fixture(name: String): ByteArray =
        javaClass.getResource("/labels/$name.hex")!!.readText().trim().chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun decodesALocalOzoneFrame() {
        val frame = LabelFrame.decode(fixture("ozone-graphic-media")) as LabelFrame.Labels
        assertEquals(1, frame.seq)
        val label = frame.labels.single()
        assertEquals("graphic-media", label.value)
        assertEquals("at://did:plc:fixturefixturefixture/app.activitytracker.feed.activity/3kfixture", label.uri)
        assertEquals("bafyreie5737gdxlw5i64vzichcalba3z2v5n6icifvx5xytvske7mr3hpm", label.cid)
        assertEquals(Instant.parse("2026-10-21T04:14:18.334Z"), label.cts)
    }

    /** Re-encoding the decoded body gives the labeler's bytes back: the encoder is canonical. */
    @Test
    fun reencodesFramesByteForByte() {
        for (name in listOf("ozone-graphic-media", "bsky-impersonation")) {
            val bytes = fixture(name)
            val frame = LabelFrame.decode(bytes) as LabelFrame.Labels
            val header = DagCbor.encode(mapOf("op" to 1L, "t" to "#labels"))
            val body = DagCbor.encode(mapOf("seq" to frame.seq, "labels" to frame.labels.map { it.fields }))
            assertEquals(bytes.toList(), (header + body).toList(), name)
        }
    }

    @Test
    fun verifiesALocalOzoneLabelAndACapturedBlueskyOne() = runTest {
        val keys =
            mapOf(
                // moderation.test's #atproto_label on the local PLC when the fixture was captured.
                "did:plc:dhvxfivkfp5qnatvho7s4vef" to "zQ3shPMLXayTqAJ15sQCBQrBWPPk5TT6udDjEuLYuUFfURfxZ",
                // Bluesky's moderation labeler's #atproto_label.
                bsky to "zQ3shmV1BNcX17coaDbfen6zArEad6SCLT3jVWCbC6Y9iinTa",
            )
        val verifier = LabelVerifier { did, _ -> keys[did] }
        val ozone = (LabelFrame.decode(fixture("ozone-graphic-media")) as LabelFrame.Labels).labels.single()
        assertTrue(verifier.verify(ozone, "did:plc:dhvxfivkfp5qnatvho7s4vef"))
        val blueskyLabel = (LabelFrame.decode(fixture("bsky-impersonation")) as LabelFrame.Labels).labels.single()
        assertEquals("impersonation", blueskyLabel.value)
        assertTrue(verifier.verify(blueskyLabel, bsky))

        // Tampered: the value changed after signing.
        assertFalse(verifier.verify(Label(ozone.fields + ("val" to "porn")), "did:plc:dhvxfivkfp5qnatvho7s4vef"))
        // Arrived on another labeler's stream.
        assertFalse(verifier.verify(ozone, bsky))
        // Unsigned.
        assertFalse(verifier.verify(Label(ozone.fields - "sig"), "did:plc:dhvxfivkfp5qnatvho7s4vef"))
    }

    @Test
    fun aFailedCheckRefreshesTheKeyOnce() = runTest {
        val old = TestAccountKey()
        val current = TestAccountKey()
        val unsigned = label("!hide")
        val signed = Label(unsigned.fields + ("sig" to current.sign(unsigned.signedBytes())))
        val fetches = mutableListOf<Boolean>()
        // The cached key is from before a rotation; the fresh read has the new one.
        val verifier = LabelVerifier { _, fresh ->
            fetches += fresh
            if (fresh) current.multibase else old.multibase
        }
        assertTrue(verifier.verify(signed, own))
        assertEquals(listOf(false, true), fetches)
        assertTrue(verifier.verify(signed, own))
        assertEquals(listOf(false, true), fetches, "the refreshed key is kept")
    }

    @Test
    fun anErrorFrameEndsTheStream() {
        val bytes = DagCbor.encode(mapOf("op" to -1L)) + DagCbor.encode(mapOf("error" to "FutureCursor", "message" to "ahead"))
        assertFailsWith<LabelFrame.StreamError> { LabelFrame.decode(bytes) }
        assertFailsWith<IllegalArgumentException> { LabelFrame.decode(byteArrayOf(0x01)) }
    }

    // ---------------------------------------------------------------- rules

    @Test
    fun onlyTrustedSourcesCount() = runTest {
        store.apply(own, 1, listOf(label("!hide"), label("porn", src = "did:plc:stranger")))
        store.apply("did:plc:stranger", 1, listOf(label("spam", src = "did:plc:stranger")))
        assertEquals(setOf(Triple(own, activity, "!hide")), stored())
    }

    @Test
    fun blueskyCountsOnlyOnAthletesAccounts() = runTest {
        athlete(bob)
        store.apply(
            bsky,
            7,
            listOf(
                label("spam", uri = bob, src = bsky),
                // A record, and an account that isn't an athlete here.
                label("porn", uri = activity, src = bsky),
                label("spam", uri = "did:plc:someone", src = bsky),
            ),
        )
        assertEquals(setOf(Triple(bsky, bob, "spam")), stored())
        assertEquals(7, store.cursor(bsky))
    }

    @Test
    fun theNewestCtsWinsAndANegationRemoves() = runTest {
        store.apply(own, 1, listOf(label("!takedown", cts = "2026-10-21T10:00:00Z")))
        store.apply(own, 2, listOf(label("!takedown", cts = "2026-10-21T11:00:00Z", neg = true)))
        assertEquals(emptySet(), stored(), "negated")
        store.apply(own, 3, listOf(label("!warn", cts = "2026-10-21T11:00:00Z")))
        store.apply(own, 4, listOf(label("!warn", cts = "2026-10-21T10:00:00Z", neg = true)))
        assertEquals(setOf(Triple(own, activity, "!warn")), stored(), "an older negation doesn't undo a newer label")
        assertEquals(4, store.cursor(own))
    }

    @Test
    fun expiredLabelsAreSwept() = runTest {
        store.apply(own, 1, listOf(Label(label("!warn", uri = bob).fields + ("exp" to "2026-10-21T11:00:00Z")), label("spam", uri = bob)))
        assertEquals(2, stored().size)
        store.sweepExpired()
        assertEquals(setOf(Triple(own, bob, "spam")), stored())
    }

    @Test
    fun aPinnedCidIsKept() = runTest {
        store.apply(own, 1, listOf(Label(label("nudity").fields + ("cid" to "bafyv1"))))
        assertEquals("bafyv1", db.tx { Labels.selectAll().single()[Labels.cid] })
    }

    // ---------------------------------------------------------------- the stream

    private val key = TestAccountKey()

    private fun signed(label: Label) = Label(label.fields + ("sig" to key.sign(label.signedBytes())))

    private fun frame(seq: Long, vararg labels: Label) =
        DagCbor.encode(mapOf("op" to 1L, "t" to "#labels")) +
            DagCbor.encode(mapOf("seq" to seq, "labels" to labels.map { signed(it).fields }))

    private fun subscriber(source: String, frames: suspend (String, suspend (ByteArray) -> Unit) -> Unit) =
        LabelSubscriber(
            Labeler(source, "http://labeler.test"),
            store,
            LabelVerifier { _, _ -> key.multibase },
            endpoint = { null },
            frames = frames,
            maxBackoff = 10.milliseconds,
        )

    @Test
    fun aRestartResumesFromTheStoredCursor() = runTest {
        val urls = mutableListOf<String>()
        val first =
            subscriber(own) { url, onFrame ->
                urls += url
                onFrame(frame(1, label("!hide")))
                onFrame(frame(2, label("porn")))
            }
        first.consume()
        // Another process, after a restart: it asks only for what came after seq 2.
        val second =
            subscriber(own) { url, onFrame ->
                urls += url
                onFrame(frame(3, label("spam")))
            }
        second.consume()
        assertEquals(
            listOf(
                "ws://labeler.test/xrpc/com.atproto.label.subscribeLabels?cursor=0",
                "ws://labeler.test/xrpc/com.atproto.label.subscribeLabels?cursor=2",
            ),
            urls,
        )
        assertEquals(setOf("!hide", "porn", "spam"), stored().map { it.third }.toSet())
    }

    @Test
    fun aDisconnectReconnectsFromWhereItStopped() = runBlocking {
        val urls = mutableListOf<String>()
        val done = CompletableDeferred<Unit>()
        val subscriber =
            subscriber(own) { url, onFrame ->
                urls += url
                when (urls.size) {
                    1 -> {
                        onFrame(frame(5, label("!hide")))
                        error("connection reset")
                    }
                    else -> {
                        onFrame(frame(6, label("porn")))
                        done.complete(Unit)
                    }
                }
            }
        val job = launch { subscriber.run() }
        withTimeout(5_000) { done.await() }
        job.cancel()
        assertEquals("ws://labeler.test/xrpc/com.atproto.label.subscribeLabels?cursor=5", urls[1])
        assertEquals(setOf("!hide", "porn"), stored().map { it.third }.toSet())
    }

    @Test
    fun oneStreamFailingLeavesTheOtherApplying() = runBlocking {
        athlete(bob)
        val bskyDown = subscriber(bsky) { _, _ -> error("unreachable") }
        val got = CompletableDeferred<Unit>()
        val ours =
            subscriber(own) { _, onFrame ->
                onFrame(frame(1, label("!takedown")))
                got.complete(Unit)
            }
        val jobs = listOf(launch { bskyDown.run() }, launch { ours.run() })
        withTimeout(5_000) { got.await() }
        jobs.forEach { it.cancel() }
        assertEquals(setOf(Triple(own, activity, "!takedown")), stored())
    }

    @Test
    fun anUnverifiedLabelIsDroppedButTheCursorMoves() = runTest {
        val forged = Label(label("!takedown").fields + ("sig" to TestAccountKey().sign(label("!takedown").signedBytes())))
        val frame =
            DagCbor.encode(mapOf("op" to 1L, "t" to "#labels")) +
                DagCbor.encode(mapOf("seq" to 9L, "labels" to listOf(forged.fields, signed(label("porn")).fields)))
        subscriber(own) { _, onFrame -> onFrame(frame) }.consume()
        assertEquals(setOf(Triple(own, activity, "porn")), stored())
        assertEquals(9, store.cursor(own))
    }
}
