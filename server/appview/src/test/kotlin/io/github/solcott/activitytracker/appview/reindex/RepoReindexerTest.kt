package io.github.solcott.activitytracker.appview.reindex

import io.github.solcott.activitytracker.appview.AppViewConfig.ReindexLimits
import io.github.solcott.activitytracker.appview.MutableClock
import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.Follows
import io.github.solcott.activitytracker.appview.db.Origins
import io.github.solcott.activitytracker.appview.db.RepoReindexes
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.appview.ipld.DagCbor
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.atproto.DidDocument
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import java.nio.file.Files
import java.util.UUID
import kotlin.io.path.listDirectoryEntries
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

/** admin/repo-reindex end to end: a run against a mocked PDS serving a signed repo, read back from the index and the run's record. */
class RepoReindexerTest {
    private val alice = Did("did:plc:alice")
    private val operator = Did("did:plc:operator")
    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val clock = MutableClock(Instant.parse("2026-11-18T12:00:00Z"))
    private val indexer = Indexer(db, { did -> did.value.substringAfterLast(':') + ".test" }, clock)
    private val repo = TestRepo(alice.value)
    private val tempDir = Files.createTempDirectory("reindex-runs")

    /** What alice's PDS answers `getRepo` with: her repo as a CAR, or an XRPC error. */
    private var car: ByteArray = repo.repo(emptyMap())
    private var refusal: String? = null

    /** Called while the PDS is answering, as Jetstream would deliver a commit then. */
    private var whileFetching: suspend () -> Unit = {}
    private var resolvable = true
    private val resyncs = mutableListOf<Did>()

    private val http =
        HttpClient(
            MockEngine { request ->
                val ours = request.url.encodedPath == "/xrpc/com.atproto.sync.getRepo" && request.url.parameters["did"] == alice.value
                whileFetching()
                (refusal ?: "RepoNotFound".takeUnless { ours })?.let {
                    return@MockEngine respond(
                        """{"error":"$it","message":"record text must not leak"}""",
                        HttpStatusCode.BadRequest,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                respond(ByteReadChannel(car), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/vnd.ipld.car"))
            },
        ) {
            expectSuccess = false
        }

    private fun document(did: Did) =
        DidDocument(
            id = did.value,
            verificationMethod = listOf(DidDocument.VerificationMethod("#atproto", "Multikey", did.value, repo.key.multibase)),
            service = listOf(DidDocument.Service("#atproto_pds", "AtprotoPersonalDataServer", "https://pds.test")),
        )

    private fun reindexer(limits: ReindexLimits = ReindexLimits()) =
        RepoReindexer(
            db,
            indexer,
            http,
            { did -> if (resolvable) document(did) else error("PLC unreachable") },
            { did ->
                resyncs += did
                4
            },
            limits,
            clock,
            tempDir,
        )

    private val reindexer = reindexer()

    @AfterTest
    fun cleanUp() {
        assertEquals(emptyList(), tempDir.listDirectoryEntries(), "every spooled repo is deleted")
        tempDir.toFile().deleteRecursively()
    }

    private fun json(text: String) = AtJson.parseToJsonElement(text).jsonObject

    private fun live(collection: String, rkey: String, record: String?, rev: String) = runBlocking {
        indexer.handle(
            JetstreamEvent.Commit(
                did = alice.value,
                time = "2026-11-18T12:00:00.000000Z",
                operation = if (record == null) "delete" else "create",
                collection = collection,
                rkey = rkey,
                record = record?.let(::json),
                cid = "bafylive$rkey",
                rev = rev,
            ),
        )
    }

    private fun profile(name: String) = """{"${'$'}type":"${Nsid.ACTOR_PROFILE}","displayName":"$name"}"""

    private fun follow(subject: String) =
        """{"${'$'}type":"${Nsid.GRAPH_FOLLOW}","subject":"$subject","createdAt":"2026-11-01T09:00:00Z"}"""

    private fun run(text: String) =
        """{"${'$'}type":"${Nsid.FEED_ACTIVITY}","sport":"run","startedAt":"2026-11-01T10:00:00Z","elapsedTime":1800,""" +
            """"createdAt":"2026-11-01T10:31:00Z","distance":5000,"name":"$text"}"""

    /** A repo record as [TestRepo] stores it: the JSON as DAG-CBOR values. */
    @Suppress("UNCHECKED_CAST") private fun record(text: String): Map<String, Any?> = cbor(json(text)) as Map<String, Any?>

    private fun cbor(value: kotlinx.serialization.json.JsonElement): Any? =
        when (value) {
            is kotlinx.serialization.json.JsonObject -> value.mapValues { cbor(it.value) }
            is kotlinx.serialization.json.JsonArray -> value.map(::cbor)
            is kotlinx.serialization.json.JsonNull -> null
            is kotlinx.serialization.json.JsonPrimitive ->
                if (value.isString) value.content else value.content.toLongOrNull() ?: value.content.toBoolean()
        }
            as Any?

    private fun reindex(by: RepoReindexer = reindexer): ReindexRun = runBlocking {
        val run = by.request(alice, operator)
        by.runDue(this)
        assertNotNull(by.run(run.id))
    }

    private fun follows() = runBlocking { db.tx { Follows.selectAll().map { it[Follows.uri].substringAfterLast('/') }.toSet() } }

    /** Alice's activities indexed from her repo, by rkey. */
    private fun repoActivities() = runBlocking {
        db.tx {
            Activities.select(Activities.uri)
                .where { Activities.origin eq Origins.REPO }
                .map { it[Activities.uri].substringAfterLast('/') }
                .toSet()
        }
    }

    private fun displayName() = runBlocking {
        db.tx { Actors.select(Actors.displayName).where { Actors.did eq alice.value }.single()[Actors.displayName] }
    }

    /** Alice's index before the AppView missed events: her profile, two follows, and a non-public run from her followers space. */
    private fun seed() = runBlocking {
        live(Nsid.ACTOR_PROFILE, "self", profile("Alice"), "3a1")
        live(Nsid.GRAPH_FOLLOW, "3kf1", follow("did:plc:bob"), "3a2")
        live(Nsid.GRAPH_FOLLOW, "3kf2", follow("did:plc:carol"), "3a3")
        indexer.spaceWrite(
            SpaceUri.of(alice, Nsid.SPACE_ATHLETE, AthleteSpace.FOLLOWERS),
            Nsid.FEED_ACTIVITY,
            "3kr1",
            "bafyr",
            json(run("hidden")),
        )
    }

    /** Her repo now: the profile renamed, one follow deleted (the delete was missed) and a post created (also missed). */
    private val current =
        mapOf(
            "${Nsid.ACTOR_PROFILE}/self" to record(profile("Alice B")),
            "${Nsid.GRAPH_FOLLOW}/3kf1" to record(follow("did:plc:bob")),
            "${Nsid.FEED_ACTIVITY}/3kp1" to record(run("missed")),
            "app.bsky.feed.post/3kx" to mapOf("text" to "not indexed"),
        )

    @Test
    fun `missed writes are indexed and missed deletes removed, space copies kept`() {
        seed()
        car = repo.repo(current, rev = "3b0")
        val run = reindex()

        assertEquals(ReindexState.SUCCEEDED, run.state, run.errorMessage)
        assertEquals("Alice B", displayName())
        assertEquals(setOf("3kf1"), follows())
        assertEquals(setOf("3kp1"), repoActivities())
        assertEquals(2, runBlocking { db.tx { Activities.selectAll().count() } }, "the followers-space run stays")
        assertEquals("3b0", run.rev)
        assertTrue((run.carBytes ?: 0) > 0)
        assertEquals(ReindexCounts(seen = 3, unchanged = 0, indexed = 3, invalid = 0, removed = 1, live = 0), run.counts)
        assertEquals(operator, run.requestedBy)
        assertEquals(4, run.spacesResynced)
        assertEquals(listOf(alice), resyncs)
        assertNull(run.phase)
        assertNull(run.errorCode)
    }

    @Test
    fun `a second reindex skips what's already indexed at the repo's CID`() {
        car = repo.repo(current)
        reindex()
        val second = reindex()
        assertEquals(ReindexState.SUCCEEDED, second.state)
        // The activity's CID is kept, so it's skipped; the profile and follows keep none, so they're written again (idempotently).
        assertEquals(ReindexCounts(seen = 3, unchanged = 1, indexed = 2, invalid = 0, removed = 0, live = 0), second.counts)
    }

    @Test
    fun `a record created live during the fetch isn't removed`() {
        seed()
        car = repo.repo(current, rev = "3b0")
        whileFetching = { live(Nsid.GRAPH_FOLLOW, "3kf9", follow("did:plc:dave"), "3c0") }
        val run = reindex()
        assertEquals(setOf("3kf1", "3kf9"), follows())
        assertEquals(1, run.counts.live)
    }

    @Test
    fun `asking twice returns the queued run`() =
        runBlocking<Unit> {
            val first = reindexer.request(alice, operator)
            val second = reindexer.request(alice, Did("did:plc:other"))
            assertEquals(first.id, second.id)
            assertEquals(ReindexState.QUEUED, second.state)
            assertEquals(listOf(first.id), reindexer.runs(alice).map { it.id })
        }

    @Test
    fun `a commit signed by another key changes nothing`() {
        seed()
        car = TestRepo(alice.value).repo(current)
        val run = reindex()
        assertEquals(ReindexState.FAILED, run.state)
        assertEquals("bad_signature", run.errorCode)
        assertEquals(setOf("3kf1", "3kf2"), follows())
        assertEquals("Alice", displayName())
        assertEquals(4, run.spacesResynced, "spaces sync on their own, so they're resynced anyway")
    }

    @Test
    fun `a tampered record block changes nothing`() {
        seed()
        val root = repo.commit(repo.tree(current))
        val post = repo.blocks.entries.first { (_, bytes) -> String(bytes, Charsets.ISO_8859_1).contains("missed") }.key
        repo.blocks[post] = DagCbor.encode(record(run("tampered")))
        car = repo.car(root)
        val run = reindex()
        assertEquals("bad_block", run.errorCode)
        assertEquals(emptySet(), repoActivities())
        assertEquals("Alice", displayName())
        assertFalse(run.errorMessage.orEmpty().contains("tampered"))
    }

    @Test
    fun `a repo over the size limit changes nothing`() {
        seed()
        car = repo.repo(current)
        val run = reindex(reindexer(ReindexLimits(maxCarBytes = 100)))
        assertEquals(ReindexState.FAILED, run.state)
        assertEquals("too_large", run.errorCode)
        assertEquals(setOf("3kf1", "3kf2"), follows())
    }

    @Test
    fun `a taken down repo is unavailable and nothing changes`() {
        seed()
        refusal = "RepoTakendown"
        val run = reindex()
        assertEquals(ReindexState.UNAVAILABLE, run.state)
        assertEquals("repo_takendown", run.errorCode)
        assertFalse(run.errorMessage.orEmpty().contains("leak"), "the PDS's own message isn't kept")
        assertEquals(setOf("3kf1", "3kf2"), follows())
        assertEquals(4, run.spacesResynced)
    }

    @Test
    fun `a repo the PDS doesn't have never deletes the account`() {
        seed()
        refusal = "RepoNotFound"
        val run = reindex()
        assertEquals(ReindexState.UNAVAILABLE, run.state)
        assertEquals("repo_not_found", run.errorCode)
        assertEquals("Alice", displayName())
    }

    @Test
    fun `an account whose DID doesn't resolve is unavailable`() {
        resolvable = false
        val run = reindex()
        assertEquals(ReindexState.UNAVAILABLE, run.state)
        assertEquals("did_unresolvable", run.errorCode)
    }

    @Test
    fun `a deactivated account is reindexed and stays deactivated`() =
        runBlocking<Unit> {
            seed()
            indexer.handle(
                JetstreamEvent.Account(
                    alice.value,
                    "2026-11-18T12:00:00.000000Z",
                    account = JetstreamEvent.Account.Body(alice.value, false, "deactivated"),
                ),
            )
            car = repo.repo(current)
            val run = reindex()
            assertEquals(ReindexState.SUCCEEDED, run.state)
            assertEquals(setOf("3kp1"), repoActivities())
            assertEquals("deactivated", db.tx { Actors.select(Actors.status).where { Actors.did eq alice.value }.single()[Actors.status] })
        }

    @Test
    fun `an undecodable record is counted invalid and its content isn't recorded`() {
        car = repo.repo(current + ("${Nsid.FEED_ACTIVITY}/3kbad" to mapOf("\$type" to Nsid.FEED_ACTIVITY, "sport" to "secret-text")))
        val run = reindex()
        assertEquals(ReindexState.SUCCEEDED, run.state)
        assertEquals(1, run.counts.invalid)
        val stored = runBlocking {
            db.tx { RepoReindexes.selectAll().single().let { row -> RepoReindexes.columns.map { row[it]?.toString() } } }
        }
        assertFalse(stored.any { it.orEmpty().contains("secret-text") })
    }

    @Test
    fun `runs wait for the cap, oldest first, one per account`() =
        runBlocking<Unit> {
            val single = reindexer(ReindexLimits(parallelism = 1))
            val first = single.request(alice, operator)
            val second = single.request(Did("did:plc:bob"), operator)
            // Only the first starts: the cap is one. Bob's PDS has no repo for him, which is fine here.
            single.runDue(this)
            assertEquals(ReindexState.SUCCEEDED, single.run(first.id)?.state)
            assertEquals(ReindexState.QUEUED, single.run(second.id)?.state)
            single.runDue(this)
            assertTrue(single.run(second.id)?.state?.active == false)
        }

    @Test
    fun `runs left running by a stopped process are failed as interrupted, queued ones kept`() =
        runBlocking<Unit> {
            val queued = reindexer.request(Did("did:plc:bob"), operator)
            db.tx {
                RepoReindexes.insert {
                    it[did] = alice.value
                    it[requestedBy] = operator.value
                    it[requestedAt] = clock.now().toEpochMilliseconds()
                    it[resyncSpaces] = true
                    it[state] = ReindexState.RUNNING.code
                    it[recordsSeen] = 0
                    it[recordsUnchanged] = 0
                    it[recordsIndexed] = 0
                    it[recordsInvalid] = 0
                    it[recordsRemoved] = 0
                    it[recordsLive] = 0
                }
            }
            reindexer.recoverInterrupted()
            val interrupted = reindexer.runs(alice).single()
            assertEquals(ReindexState.FAILED, interrupted.state)
            assertEquals("interrupted", interrupted.errorCode)
            assertEquals(ReindexState.QUEUED, reindexer.run(queued.id)?.state)
        }

    @Test
    fun `runs are kept 90 days, active ones always`() =
        runBlocking<Unit> {
            car = repo.repo(current)
            val old = reindex()
            val queued = reindexer.request(Did("did:plc:bob"), operator)
            clock.now += 91.days
            reindexer.prune()
            assertNull(reindexer.run(old.id))
            assertNotNull(reindexer.run(queued.id))
        }
}
