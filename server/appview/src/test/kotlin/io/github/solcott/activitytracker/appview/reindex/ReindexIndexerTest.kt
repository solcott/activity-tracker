package io.github.solcott.activitytracker.appview.reindex

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.Follows
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.appview.index.RepoWriteResult
import io.github.solcott.activitytracker.appview.index.repoHoldingCollections
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * The indexer's side of a reindex (admin/repo-reindex): what it holds from a repo, and how live Jetstream commits and a reindex's snapshot
 * writes are ordered by revision ("Live events during a reindex win when newer").
 */
class ReindexIndexerTest {
    private val alice = Did("did:plc:alice")
    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val indexer = Indexer(db, { did -> did.value.substringAfterLast(':') + ".test" })

    private fun json(text: String): JsonObject = AtJson.parseToJsonElement(text).jsonObject

    private fun profile(name: String) = json("""{"${'$'}type":"${Nsid.ACTOR_PROFILE}","displayName":"$name"}""")

    private fun follow(subject: String) =
        json("""{"${'$'}type":"${Nsid.GRAPH_FOLLOW}","subject":"$subject","createdAt":"2026-09-30T09:00:00Z"}""")

    private fun run(rkey: String) =
        json(
            """{"${'$'}type":"${Nsid.FEED_ACTIVITY}","sport":"run","startedAt":"2026-09-30T10:00:00Z","elapsedTime":1800,""" +
                """"createdAt":"2026-09-30T10:31:00Z","distance":5000,"name":"$rkey"}""",
        )

    private fun live(collection: String, rkey: String, record: JsonObject?, rev: String?, op: String = "create") = runBlocking {
        indexer.handle(
            JetstreamEvent.Commit(
                did = alice.value,
                time = "2026-09-30T12:00:00.000000Z",
                operation = op,
                collection = collection,
                rkey = rkey,
                record = record,
                cid = record?.let { "bafy$rkey$rev" },
                rev = rev,
            ),
        )
    }

    private fun displayName() = runBlocking {
        db.tx { Actors.select(Actors.displayName).where { Actors.did eq alice.value }.single()[Actors.displayName] }
    }

    private fun follows() = runBlocking { db.tx { Follows.selectAll().map { it[Follows.subjectDid] }.toSet() } }

    @Test
    fun `every indexed collection can be listed from a repo`() {
        assertEquals(Nsid.indexedCollections.toSet(), repoHoldingCollections)
    }

    @Test
    fun `the holdings are what Jetstream indexed from the repo, not the spaces`() = runBlocking {
        live(Nsid.ACTOR_PROFILE, "self", profile("Alice"), "3a1")
        live(Nsid.GRAPH_FOLLOW, "3kf1", follow("did:plc:bob"), "3a2")
        live(Nsid.FEED_ACTIVITY, "3kr1", run("3kr1"), "3a3")
        live(Nsid.BSKY_GRAPH_BLOCK, "3kb1", json("""{"subject":"did:plc:carol","createdAt":"2026-09-30T09:00:00Z"}"""), "3a4")
        // An activity held only from her followers space isn't the repo's.
        val followers = SpaceUri.of(alice, Nsid.SPACE_ATHLETE, AthleteSpace.FOLLOWERS)
        indexer.spaceWrite(followers, Nsid.FEED_ACTIVITY, "3kr2", "bafy3kr2", run("3kr2"))
        // A repo copy waiting behind a narrower space copy still is.
        live(Nsid.FEED_ACTIVITY, "3kr3", run("3kr3"), "3a5")
        indexer.spaceWrite(
            SpaceUri.of(alice, Nsid.SPACE_ATHLETE, AthleteSpace.MUTUALS),
            Nsid.FEED_ACTIVITY,
            "3kr3",
            "bafyspace",
            run("3kr3"),
        )

        val held = indexer.repoHoldings(alice)
        assertEquals(mapOf("self" to null), held[Nsid.ACTOR_PROFILE])
        assertEquals(mapOf("3kf1" to null), held[Nsid.GRAPH_FOLLOW])
        assertEquals(mapOf("3kr1" to "bafy3kr13a3", "3kr3" to "bafy3kr33a5"), held[Nsid.FEED_ACTIVITY])
        assertEquals(setOf("3kb1"), held[Nsid.BSKY_GRAPH_BLOCK]?.keys)
        assertEquals(emptyMap(), held[Nsid.FEED_POST])
    }

    @Test
    fun `a watch registered while a covered commit waits for its lock still drops it`() = runBlocking {
        // The first sight of a new account checks its handle before the URI lock: the reindex starts and applies the snapshot then.
        lateinit var racing: Indexer
        var raced = false
        racing =
            Indexer(
                db,
                { did ->
                    if (!raced) {
                        raced = true
                        val watch = racing.watch(alice)
                        watch.snapshotRev = "3c"
                        racing.reindexWrite(alice, Nsid.FEED_ACTIVITY, "3kr1", "bafysnap", run("Snapshot"), watch)
                    }
                    did.value.substringAfterLast(':') + ".test"
                },
            )
        racing.handle(
            JetstreamEvent.Commit(
                did = alice.value,
                time = "2026-09-30T12:00:00.000000Z",
                operation = "create",
                collection = Nsid.FEED_ACTIVITY,
                rkey = "3kr1",
                record = run("Old"),
                cid = "bafyold",
                rev = "3a",
            ),
        )
        assertTrue(raced)
        assertEquals("bafysnap", db.tx { Activities.select(Activities.cid).single()[Activities.cid] })
    }

    @Test
    fun `a live commit newer than the snapshot holds the snapshot's write back`() = runBlocking {
        val watch = indexer.watch(alice)
        live(Nsid.ACTOR_PROFILE, "self", profile("Live"), "3b")
        watch.snapshotRev = "3a"
        assertEquals(RepoWriteResult.HELD, indexer.reindexWrite(alice, Nsid.ACTOR_PROFILE, "self", "bafysnap", profile("Snapshot"), watch))
        assertEquals("Live", displayName())
    }

    @Test
    fun `a live commit without a revision counts as newer`() = runBlocking {
        val watch = indexer.watch(alice)
        live(Nsid.ACTOR_PROFILE, "self", profile("Live"), rev = null)
        watch.snapshotRev = "3a"
        assertEquals(RepoWriteResult.HELD, indexer.reindexWrite(alice, Nsid.ACTOR_PROFILE, "self", "bafysnap", profile("Snapshot"), watch))
    }

    @Test
    fun `a live commit the snapshot covers doesn't hold it back`() = runBlocking {
        val watch = indexer.watch(alice)
        live(Nsid.ACTOR_PROFILE, "self", profile("Old"), "39")
        watch.snapshotRev = "3a"
        assertEquals(
            RepoWriteResult.WRITTEN,
            indexer.reindexWrite(alice, Nsid.ACTOR_PROFILE, "self", "bafysnap", profile("Snapshot"), watch),
        )
        assertEquals("Snapshot", displayName())
    }

    @Test
    fun `a lagging commit the snapshot covers is dropped once the snapshot applied its record`() = runBlocking {
        val watch = indexer.watch(alice)
        watch.snapshotRev = "3a"
        indexer.reindexWrite(alice, Nsid.ACTOR_PROFILE, "self", "bafysnap", profile("Snapshot"), watch)
        live(Nsid.ACTOR_PROFILE, "self", profile("Older"), "39")
        assertEquals("Snapshot", displayName())
        // A newer one still applies.
        live(Nsid.ACTOR_PROFILE, "self", profile("Newer"), "3b")
        assertEquals("Newer", displayName())
    }

    @Test
    fun `a record created live during a reindex isn't removed by it`() = runBlocking {
        live(Nsid.ACTOR_PROFILE, "self", profile("Alice"), "31")
        val watch = indexer.watch(alice)
        watch.snapshotRev = "3a"
        live(Nsid.GRAPH_FOLLOW, "3kf9", follow("did:plc:bob"), "3b")
        assertFalse(indexer.reindexDelete(alice, Nsid.GRAPH_FOLLOW, "3kf9", watch))
        assertEquals(setOf("did:plc:bob"), follows())
    }

    @Test
    fun `a removal goes as a repo delete, promoting a space's copy`() = runBlocking {
        live(Nsid.FEED_ACTIVITY, "3kr3", run("3kr3"), "31")
        val mutuals = SpaceUri.of(alice, Nsid.SPACE_ATHLETE, AthleteSpace.MUTUALS)
        indexer.spaceWrite(mutuals, Nsid.FEED_ACTIVITY, "3kr3", "bafyspace", run("3kr3"))
        val watch = indexer.watch(alice)
        watch.snapshotRev = "3a"
        assertTrue(indexer.reindexDelete(alice, Nsid.FEED_ACTIVITY, "3kr3", watch))
        val origins = db.tx { Activities.select(Activities.origin).map { it[Activities.origin] } }
        assertEquals(listOf(mutuals.value), origins)
        assertEquals(emptyMap(), indexer.repoHoldings(alice)[Nsid.FEED_ACTIVITY])
    }

    @Test
    fun `an ended watch stops dropping commits after its grace`() = runBlocking {
        val watch = indexer.watch(alice)
        watch.snapshotRev = "3a"
        indexer.reindexWrite(alice, Nsid.ACTOR_PROFILE, "self", "bafysnap", profile("Snapshot"), watch)
        indexer.unwatch(watch, 10.minutes)
        live(Nsid.ACTOR_PROFILE, "self", profile("Older"), "39")
        assertEquals("Snapshot", displayName())
        indexer.unwatch(watch, Duration.ZERO)
        live(Nsid.ACTOR_PROFILE, "self", profile("Older"), "39")
        assertEquals("Older", displayName())
    }

    @Test
    fun `an undecodable snapshot record is reported invalid`() = runBlocking {
        val watch = indexer.watch(alice)
        watch.snapshotRev = "3a"
        assertEquals(
            RepoWriteResult.INVALID,
            indexer.reindexWrite(alice, Nsid.FEED_ACTIVITY, "3kbad", "bafybad", json("""{"sport":"run"}"""), watch),
        )
        assertEquals(
            RepoWriteResult.INVALID,
            indexer.reindexWrite(alice, Nsid.BSKY_GRAPH_BLOCK, "3kb", "bafyb", json("""{"subject":"nope"}"""), watch),
        )
    }
}
