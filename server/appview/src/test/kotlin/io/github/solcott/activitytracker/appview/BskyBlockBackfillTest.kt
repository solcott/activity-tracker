package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.PdsLookup
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.Blocks
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.appview.sync.BskyBlockBackfill
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll

/** `Existing Bluesky blocks are backfilled` (`graph/blocks`): an athlete's earlier `app.bsky.graph.block` records, read from their PDS. */
class BskyBlockBackfillTest {
    private val alice = "did:plc:alice"
    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val indexer = Indexer(db, { did -> did.value.substringAfterLast(':') + ".test" })
    private var now = Instant.parse("2026-10-21T12:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = now
        }
    private var pdsUp = true
    private val requests = mutableListOf<String>()

    /** Alice's repo holds three Bluesky blocks, two to a page. */
    private val http =
        HttpClient(
            MockEngine { request ->
                requests += request.url.toString()
                if (!pdsUp) return@MockEngine respond("down", HttpStatusCode.BadGateway)
                assertEquals(Nsid.BSKY_GRAPH_BLOCK, request.url.parameters["collection"])
                val body =
                    when (request.url.parameters["cursor"]) {
                        null -> page(listOf("3kb1" to "did:plc:bob", "3kb2" to "did:plc:carol"), cursor = "p2")
                        "p2" -> page(listOf("3kb3" to "did:plc:dave"), cursor = null)
                        else -> page(emptyList(), cursor = null)
                    }
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        )
    private val backfill = BskyBlockBackfill(db, indexer, http, PdsLookup { "https://pds.test" }, clock)

    private fun page(records: List<Pair<String, String>>, cursor: String?): String {
        val items =
            records.joinToString(",") { (rkey, subject) ->
                """{"uri":"at://$alice/${Nsid.BSKY_GRAPH_BLOCK}/$rkey","cid":"bafy$rkey",""" +
                    """"value":{"${'$'}type":"${Nsid.BSKY_GRAPH_BLOCK}","subject":"$subject","createdAt":"2025-01-01T00:00:00Z"}}"""
            }
        return """{"records":[$items]${cursor?.let { ""","cursor":"$it"""" }.orEmpty()}}"""
    }

    private fun joins() = runBlocking {
        indexer.handle(
            JetstreamEvent.Commit(
                did = alice,
                time = "2026-10-21T12:00:00.000000Z",
                operation = "create",
                collection = Nsid.ACTOR_PROFILE,
                rkey = "self",
                record = AtJson.parseToJsonElement("""{"displayName":"Alice"}""").jsonObject,
                cid = "bafyself",
            )
        )
    }

    private fun blocked(): List<String> = runBlocking { db.tx { Blocks.selectAll().map { it[Blocks.subjectDid] }.sorted() } }

    private fun syncedAt(): Long? = runBlocking {
        db.tx { Actors.selectAll().where { Actors.did eq alice }.single()[Actors.bskyBlocksSyncedAt] }
    }

    @Test
    fun newAthletesBlocksAreBackfilledAcrossPages(): Unit = runBlocking {
        joins()
        assertNull(syncedAt())
        backfill.runDue()
        assertEquals(listOf("did:plc:bob", "did:plc:carol", "did:plc:dave"), blocked())
        assertNotNull(syncedAt())

        requests.clear()
        backfill.runDue()
        assertEquals(emptyList(), requests, "once stamped, an athlete isn't fetched again")
    }

    @Test
    fun unreachablePdsIsRetriedLater(): Unit = runBlocking {
        pdsUp = false
        joins()
        assertEquals(1, db.tx { Actors.selectAll().count() }, "the profile is indexed whatever the PDS does")
        backfill.runDue()
        assertNull(syncedAt())
        assertEquals(emptyList(), blocked())

        pdsUp = true
        requests.clear()
        now += 5.minutes
        backfill.runDue()
        assertEquals(emptyList(), requests, "backing off")

        now += 10.minutes
        backfill.runDue()
        assertEquals(3, blocked().size)
        assertNotNull(syncedAt())
    }
}
