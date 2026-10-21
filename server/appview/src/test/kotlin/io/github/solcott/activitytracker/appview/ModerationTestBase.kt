package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.PrivateMediaTokens
import io.github.solcott.activitytracker.appview.api.SpaceBlobs
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.appview.labels.Label
import io.github.solcott.activitytracker.appview.labels.LabelStore
import io.github.solcott.activitytracker.appview.moderation.OzoneClient
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetComments
import io.github.solcott.activitytracker.lexicon.app.GetLikes
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.testing.ApplicationTestBuilder
import java.util.UUID
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * Alice, bob and carol, indexed as Jetstream and the syncer index them, with the app's labeler configured, and read back through the API:
 * the tests of what moderation (`moderation/labels`, `moderation/account-status`) does to what's served.
 */
open class ModerationTestBase {
    protected val service = "did:web:localhost%3A8081"
    protected val labeler = "did:plc:labeler"
    protected val alice = "did:plc:alice"
    protected val bob = "did:plc:bob"
    protected val carol = "did:plc:carol"
    private val keys = listOf(alice, bob, carol).associateWith { TestAccountKey() }

    protected val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    protected val indexer = Indexer(db, { did -> did.value.substringAfterLast(':') + ".test" })
    protected val config = AppViewConfig.fromEnv(emptyMap()).copy(labeler = AppViewConfig.Labeler(labeler, "http://labeler.test"))
    private val verifier = ServiceAuthVerifier(service, keys = { keys[it.value]?.multibase })
    private val labels = LabelStore(db, labeler, null)
    private var seq = 0L

    /** With [ozone], the moderator routes ask it; with [spaceBlobs] and [mediaTokens], non-public photos are served. */
    protected fun ApplicationTestBuilder.start(
        ozone: OzoneClient? = null,
        spaceBlobs: SpaceBlobs? = null,
        mediaTokens: PrivateMediaTokens? = null,
    ) {
        val signing = if (mediaTokens != null) config.copy(signingKey = TEST_SIGNING_KEY) else config
        application { appView(signing, db, verifier, spaceBlobs = spaceBlobs, mediaTokens = mediaTokens, ozone = ozone) }
    }

    protected fun commit(did: String, collection: String, rkey: String, record: String?, cid: String = "bafy$rkey") = runBlocking {
        indexer.handle(
            JetstreamEvent.Commit(
                did = did,
                time = "2026-10-21T12:00:00.000000Z",
                operation = if (record == null) "delete" else "create",
                collection = collection,
                rkey = rkey,
                record = record?.let { AtJson.parseToJsonElement(it).jsonObject },
                cid = cid,
            )
        )
    }

    protected fun account(did: String, active: Boolean, status: String? = null) = runBlocking {
        indexer.handle(
            JetstreamEvent.Account(did, "2026-10-21T12:00:00.000000Z", account = JetstreamEvent.Account.Body(did, active, status))
        )
    }

    /** The labeler labels [subject] [value], or negates it; each call is newer than the last. */
    protected fun label(subject: String, value: String, neg: Boolean = false, cid: String? = null) = runBlocking {
        seq++
        labels.apply(
            labeler,
            seq,
            listOf(testLabel(labeler, subject, value, "2026-10-21T10:00:${seq.toString().padStart(2, '0')}Z", labelExtras(neg, cid))),
        )
    }

    /** Stores [label] as the labeler's stream would after verifying it. */
    protected fun label(label: Label) = runBlocking {
        seq++
        labels.apply(labeler, seq, listOf(label))
    }

    protected fun profile(did: String) = commit(did, Nsid.ACTOR_PROFILE, "self", """{"displayName":"${did.last()}"}""")

    protected fun follow(author: String, subject: String) =
        commit(
            author,
            Nsid.GRAPH_FOLLOW,
            "3kf${author.last()}${subject.last()}",
            """{"subject":"$subject","createdAt":"2026-10-01T09:00:00Z"}""",
        )

    protected fun activityUri(author: String, rkey: String) = "at://$author/${Nsid.FEED_ACTIVITY}/$rkey"

    protected fun post(author: String, rkey: String, tier: String = Visibility.PUBLIC) {
        val json = """{"sport":"run","startedAt":"2026-10-02T07:00:00Z","elapsedTime":1800,"createdAt":"2026-10-02T07:31:00Z"}"""
        if (tier == Visibility.PUBLIC) {
            commit(author, Nsid.FEED_ACTIVITY, rkey, json)
        } else {
            runBlocking {
                indexer.spaceWrite(
                    SpaceUri.of(Did(author), Nsid.SPACE_ATHLETE, tier),
                    Nsid.FEED_ACTIVITY,
                    rkey,
                    "bafy$rkey",
                    AtJson.parseToJsonElement(json).jsonObject,
                )
            }
        }
    }

    protected fun commentUri(author: String, owner: String) = "at://$author/${Nsid.FEED_COMMENT}/3kc${author.last()}${owner.last()}"

    /** [author] comments, publicly, on [owner]'s [rkey]. */
    protected fun comment(author: String, owner: String, rkey: String) =
        commit(
            author,
            Nsid.FEED_COMMENT,
            "3kc${author.last()}${owner.last()}",
            """{"subject":{"uri":"${activityUri(owner, rkey)}","cid":"bafy$rkey"},"text":"Nice","createdAt":"2026-10-03T09:00:00Z"}""",
        )

    protected fun like(author: String, owner: String, rkey: String) =
        commit(
            author,
            Nsid.FEED_LIKE,
            "3kl${author.last()}${owner.last()}",
            """{"subject":{"uri":"${activityUri(owner, rkey)}","cid":"bafy$rkey"},"createdAt":"2026-10-03T09:00:00Z"}""",
        )

    /** How `getActivity` and `getProfile` answer for something that isn't there (or isn't served): an XRPC 400. */
    protected val notFound = HttpStatusCode.BadRequest

    protected suspend fun HttpClient.raw(nsid: String, viewer: String?, query: String): HttpResponse =
        get("/xrpc/$nsid$query") {
            viewer?.let { header(HttpHeaders.Authorization, "Bearer ${keys.getValue(it).serviceJwt(it, service, nsid)}") }
        }

    protected suspend fun <P, O> HttpClient.query(method: XrpcQuery<P, O>, viewer: String?, query: String): O {
        val response = raw(method.nsid, viewer, query)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return AtJson.decodeFromString(method.output, response.bodyAsText())
    }

    protected suspend fun HttpClient.timeline(viewer: String): List<String> =
        query(GetTimeline.method, viewer, "?limit=50").activities.map { it.uri.value }

    protected suspend fun HttpClient.activityStatus(uri: String, viewer: String?): HttpStatusCode =
        raw(GetActivity.method.nsid, viewer, "?uri=${uri.encodeURLParameter()}").status

    protected suspend fun HttpClient.profileStatus(actor: String, viewer: String?): HttpStatusCode =
        raw(Nsid.ACTOR_GET_PROFILE, viewer, "?actor=$actor").status

    /** The commenters [viewer] is shown on [uri], after checking that the view's count matches them. */
    protected suspend fun HttpClient.commenters(uri: String, viewer: String?): List<String> {
        val count = query(GetActivity.method, viewer, "?uri=${uri.encodeURLParameter()}").activity.commentCount
        val comments = query(GetComments.method, viewer, "?uri=${uri.encodeURLParameter()}&limit=100").comments
        assertEquals(count, comments.count { it.hidden != true }.toLong(), "the count matches the list")
        return comments.map { it.author.did.value }.sorted()
    }

    /** The likers [viewer] is shown on [uri], after checking that the view's count matches them. */
    protected suspend fun HttpClient.likers(uri: String, viewer: String?): List<String> {
        val count = query(GetActivity.method, viewer, "?uri=${uri.encodeURLParameter()}").activity.likeCount
        val likes = query(GetLikes.method, viewer, "?uri=${uri.encodeURLParameter()}").likes.map { it.did.value }.sorted()
        assertEquals(count, likes.size.toLong(), "the count matches the list")
        return likes
    }
}
