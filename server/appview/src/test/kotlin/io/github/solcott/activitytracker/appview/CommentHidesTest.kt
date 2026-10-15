package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.CommentHides
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.atproto.commentHideRkey
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetComments
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** Alice hides comments on her own activity (`feed.commentHide`): who still sees them, and how they're counted. */
class CommentHidesTest {
    private val service = "did:web:localhost%3A8081"
    private val alice = "did:plc:alice"
    private val bob = "did:plc:bob"
    private val carol = "did:plc:carol"
    private val keys = mapOf(alice to TestAccountKey(), bob to TestAccountKey(), carol to TestAccountKey())

    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val indexer = Indexer(db, { did -> did.value.substringAfterLast(':') + ".test" })
    private val config = AppViewConfig.fromEnv(emptyMap())
    private val verifier = ServiceAuthVerifier(service, keys = { keys[it.value]?.multibase })

    private val run = "at://$alice/${Nsid.FEED_ACTIVITY}/3krun"
    private val bobsComment = AtUri("at://$bob/${Nsid.FEED_COMMENT}/3kcb")
    private val carolsComment = AtUri("at://$carol/${Nsid.FEED_COMMENT}/3kcc")
    private val aliceInteractions = SpaceUri.of(Did(alice), Nsid.SPACE_ATHLETE, AthleteSpace.INTERACTIONS)

    private fun commit(did: String, collection: String, rkey: String, record: String?) = runBlocking {
        indexer.handle(
            JetstreamEvent.Commit(
                did = did,
                time = "2026-10-14T12:00:00.000000Z",
                operation = if (record == null) "delete" else "create",
                collection = collection,
                rkey = rkey,
                record = record?.let { AtJson.parseToJsonElement(it).jsonObject },
                cid = "bafy$rkey",
            )
        )
    }

    private fun hide(comment: AtUri) = """{"subject":"${comment.value}","createdAt":"2026-10-14T11:00:00Z"}"""

    private fun rkey(comment: AtUri) = runBlocking { commentHideRkey(comment) }

    private fun publicRun() =
        commit(
            alice,
            Nsid.FEED_ACTIVITY,
            "3krun",
            """{"sport":"run","startedAt":"2026-10-14T09:00:00Z","elapsedTime":1800,"createdAt":"2026-10-14T09:31:00Z"}""",
        )

    private fun comment(uri: AtUri, text: String) =
        commit(
            uri.authority,
            Nsid.FEED_COMMENT,
            uri.rkey!!,
            """{"subject":{"uri":"$run","cid":"bafy3krun"},"text":"$text","createdAt":"2026-10-14T10:00:00Z"}""",
        )

    private fun hideRows() = transaction(db) { CommentHides.selectAll().count() }

    private fun ApplicationTestBuilder.start() {
        application { appView(config, db, verifier) }
    }

    /** `getActivity`'s count and `getComments`' list for [viewer] (signed out when null): (count, listed comment rkey → hidden). */
    private suspend fun HttpClient.seen(viewer: String?): Pair<Int, Map<String, Boolean?>> {
        val count = query(GetActivity.method, viewer).activity.commentCount.toInt()
        return count to query(GetComments.method, viewer).comments.associate { it.uri.rkey!! to it.hidden }
    }

    private suspend fun <P, O> HttpClient.query(method: XrpcQuery<P, O>, viewer: String?): O {
        val response =
            get("/xrpc/${method.nsid}?uri=${run.encodeURLParameter()}") {
                viewer?.let { header(HttpHeaders.Authorization, "Bearer ${keys.getValue(it).serviceJwt(it, service, method.nsid)}") }
            }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return AtJson.decodeFromString(method.output, response.bodyAsText())
    }

    @Test
    fun aHiddenCommentIsListedOnlyForTheAthleteAndItsCommenterAndCountedForNoOne() = testApplication {
        start()
        publicRun()
        comment(bobsComment, "Spam")
        comment(carolsComment, "Nice!")
        commit(alice, Nsid.FEED_COMMENT_HIDE, rkey(bobsComment), hide(bobsComment))

        assertEquals(1 to mapOf("3kcb" to true, "3kcc" to null), client.seen(alice), "the athlete sees it marked")
        assertEquals(1 to mapOf("3kcb" to true, "3kcc" to null), client.seen(bob), "the commenter sees it marked")
        assertEquals(1 to mapOf("3kcc" to null), client.seen(carol))
        assertEquals(1 to mapOf("3kcc" to null), client.seen(null))

        // Unhiding (deleting the hide) brings it back for everyone.
        commit(alice, Nsid.FEED_COMMENT_HIDE, rkey(bobsComment), null)
        assertEquals(2 to mapOf("3kcb" to null, "3kcc" to null), client.seen(carol))
        assertEquals(2 to mapOf("3kcb" to null, "3kcc" to null), client.seen(alice))
    }

    @Test
    fun onlyTheAthletesHideUnderItsOwnKeyCounts() = testApplication {
        start()
        publicRun()
        comment(bobsComment, "Hi")

        commit(carol, Nsid.FEED_COMMENT_HIDE, rkey(bobsComment), hide(bobsComment))
        assertEquals(1 to mapOf("3kcb" to null), client.seen(null), "someone else's hide changes nothing")

        commit(alice, Nsid.FEED_COMMENT_HIDE, "3kother", hide(bobsComment))
        val run = AtUri(run)
        commit(alice, Nsid.FEED_COMMENT_HIDE, rkey(run), hide(run))
        assertEquals(1L, hideRows(), "a hide under another rkey, or of something other than a comment, isn't indexed")
        assertEquals(1 to mapOf("3kcb" to null), client.seen(null))
    }

    @Test
    fun aHideIndexedBeforeItsCommentStillHidesIt() = testApplication {
        start()
        publicRun()
        commit(alice, Nsid.FEED_COMMENT_HIDE, rkey(bobsComment), hide(bobsComment))
        comment(bobsComment, "Late")
        assertEquals(0 to emptyMap(), client.seen(carol))
        assertEquals(0 to mapOf("3kcb" to true), client.seen(alice))
    }

    @Test
    fun aHideInBothPlacesHidesUntilBothAreDeleted() = testApplication {
        start()
        publicRun()
        comment(bobsComment, "Hi")
        val rkey = rkey(bobsComment)
        commit(alice, Nsid.FEED_COMMENT_HIDE, rkey, hide(bobsComment))
        runBlocking {
            indexer.spaceWrite(
                aliceInteractions,
                Nsid.FEED_COMMENT_HIDE,
                rkey,
                "bafyspace",
                AtJson.parseToJsonElement(hide(bobsComment)).jsonObject,
            )
        }
        assertEquals(2L, hideRows())

        commit(alice, Nsid.FEED_COMMENT_HIDE, rkey, null)
        assertEquals(0 to emptyMap(), client.seen(carol), "the space's copy still hides it")

        runBlocking { indexer.spaceDelete(aliceInteractions, Nsid.FEED_COMMENT_HIDE, rkey) }
        assertEquals(1 to mapOf("3kcb" to null), client.seen(carol))
    }

    @Test
    fun deletedAccountsHidesGo() = testApplication {
        start()
        publicRun()
        comment(bobsComment, "Hi")
        commit(alice, Nsid.FEED_COMMENT_HIDE, rkey(bobsComment), hide(bobsComment))
        runBlocking {
            indexer.handle(
                JetstreamEvent.Account(
                    did = alice,
                    time = "2026-10-14T12:00:00.000000Z",
                    account = JetstreamEvent.Account.Body(alice, active = false, status = "deleted"),
                )
            )
        }
        assertEquals(0L, hideRows())
    }
}
