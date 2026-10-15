package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.Comments
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
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetComments
import io.github.solcott.activitytracker.lexicon.app.ThreadView
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** The fixture `getComments` tests share: an AppView over H2 fed through the real [Indexer], and helpers to read it over HTTP. */
open class CommentTestBase {
    protected val service = "did:web:localhost%3A8081"
    protected val alice = "did:plc:alice"
    protected val bob = "did:plc:bob"
    protected val carol = "did:plc:carol"
    protected val dave = "did:plc:dave"
    protected val erin = "did:plc:erin"
    protected val keys = listOf(alice, bob, carol, dave, erin).associateWith { TestAccountKey() }

    protected val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    protected val base = Instant.parse("2026-10-14T10:00:00Z")

    /**
     * When the indexer indexes each record: [comment] sets it to the comment's `createdAt`, as for an honest client, unless told otherwise.
     */
    protected var indexingAt = base
    protected val indexer =
        Indexer(
            db,
            { did -> did.value.substringAfterLast(':') + ".test" },
            clock =
                object : Clock {
                    override fun now() = indexingAt
                },
        )
    protected val config = AppViewConfig.fromEnv(emptyMap())
    protected val verifier = ServiceAuthVerifier(service, keys = { keys[it.value]?.multibase })

    protected fun uri(rkey: String) = "at://$alice/${Nsid.FEED_ACTIVITY}/$rkey"

    protected fun commit(did: String, collection: String, rkey: String, record: String?) = runBlocking {
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

    /** Alice posts [rkey] at [tier]: in her repo when public, in her tier space otherwise. */
    protected fun post(rkey: String, tier: String = Visibility.PUBLIC) {
        val json = """{"sport":"run","startedAt":"2026-10-14T07:00:00Z","elapsedTime":1800,"createdAt":"2026-10-14T07:31:00Z"}"""
        if (tier == Visibility.PUBLIC) commit(alice, Nsid.FEED_ACTIVITY, rkey, json)
        else
            runBlocking {
                indexer.spaceWrite(
                    SpaceUri.of(Did(alice), Nsid.SPACE_ATHLETE, tier),
                    Nsid.FEED_ACTIVITY,
                    rkey,
                    "bafy$rkey",
                    AtJson.parseToJsonElement(json).jsonObject,
                )
            }
    }

    protected fun follow(author: String, subject: String) =
        commit(
            author,
            Nsid.GRAPH_FOLLOW,
            "3kf${author.last()}${subject.last()}",
            """{"subject":"$subject","createdAt":"2026-10-01T09:00:00Z"}""",
        )

    protected fun unfollow(author: String, subject: String) =
        commit(author, Nsid.GRAPH_FOLLOW, "3kf${author.last()}${subject.last()}", null)

    /**
     * [author] comments [rkey] on alice's [activity], dated [minute] minutes after [base]: in their repo with no [audience], else in their
     * interactions space written for [audience]. With [parent], it's a reply to [parent] in [root]'s thread. It's indexed [indexedMinute]
     * minutes after [base], by default when it says it was written.
     */
    @Suppress("LongParameterList") // The record's fields.
    protected fun comment(
        author: String,
        rkey: String,
        minute: Int,
        activity: String = "3krun",
        audience: String? = null,
        parent: AtUri? = null,
        root: AtUri? = parent,
        indexedMinute: Int = minute,
    ): AtUri {
        val createdAt = Instant.fromEpochMilliseconds(base.toEpochMilliseconds() + minute * 60_000L)
        indexingAt = Instant.fromEpochMilliseconds(base.toEpochMilliseconds() + indexedMinute * 60_000L)
        fun ref(uri: AtUri) = """{"uri":"${uri.value}","cid":"bafy${uri.rkey}"}"""
        val json =
            """{"subject":{"uri":"${uri(activity)}","cid":"bafy$activity"},"text":"c$rkey","createdAt":"$createdAt"""" +
                audience?.let { ""","audience":"$it"""" }.orEmpty() +
                parent?.let { ""","reply":{"root":${ref(root!!)},"parent":${ref(it)}}""" }.orEmpty() +
                "}"
        if (audience == null) commit(author, Nsid.FEED_COMMENT, rkey, json)
        else
            runBlocking {
                indexer.spaceWrite(
                    SpaceUri.of(Did(author), Nsid.SPACE_ATHLETE, AthleteSpace.INTERACTIONS),
                    Nsid.FEED_COMMENT,
                    rkey,
                    "bafy$rkey",
                    AtJson.parseToJsonElement(json).jsonObject,
                )
            }
        return AtUri("at://$author/${Nsid.FEED_COMMENT}/$rkey")
    }

    protected fun hide(comment: AtUri) {
        val rkey = runBlocking { commentHideRkey(comment) }
        commit(alice, Nsid.FEED_COMMENT_HIDE, rkey, """{"subject":"${comment.value}","createdAt":"2026-10-14T11:00:00Z"}""")
    }

    /** Bob's comments `3kc000`… on alice's public run, one a minute apart, oldest first. */
    protected fun bobComments(count: Int): List<String> =
        (0 until count)
            .map { i -> "3kc" + i.toString().padStart(3, '0') }
            .onEach { rkey -> comment(bob, rkey, minute = rkey.drop(3).toInt()) }

    protected fun ApplicationTestBuilder.start() {
        application { appView(config, db, verifier) }
    }

    protected suspend fun HttpClient.raw(nsid: String, viewer: String?, query: String): HttpResponse =
        get("/xrpc/$nsid$query") {
            viewer?.let { header(HttpHeaders.Authorization, "Bearer ${keys.getValue(it).serviceJwt(it, service, nsid)}") }
        }

    protected suspend fun <P, O> HttpClient.query(method: XrpcQuery<P, O>, viewer: String?, query: String): O {
        val response = raw(method.nsid, viewer, query)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return AtJson.decodeFromString(method.output, response.bodyAsText())
    }

    protected fun pageQuery(activity: String, limit: Int?, cursor: String?) =
        "?uri=${uri(activity).encodeURLParameter()}" +
            limit?.let { "&limit=$it" }.orEmpty() +
            cursor?.let { "&cursor=${it.encodeURLParameter()}" }.orEmpty()

    protected suspend fun HttpClient.page(viewer: String?, limit: Int? = null, cursor: String? = null, activity: String = "3krun") =
        query(GetComments.method, viewer, pageQuery(activity, limit, cursor))

    protected suspend fun HttpClient.lookup(viewer: String?, uris: List<AtUri>, activity: String = "3krun"): List<CommentView> =
        query(
                GetComments.method,
                viewer,
                "?uri=${uri(activity).encodeURLParameter()}" + uris.joinToString("") { "&uris=${it.value.encodeURLParameter()}" },
            )
            .also { assertNull(it.cursor, "a lookup has no cursor") }
            .comments

    /** Every comment [viewer] is listed on [activity], following cursors in pages of [limit]. */
    protected suspend fun HttpClient.allPages(viewer: String?, limit: Int, activity: String = "3krun"): List<CommentView> {
        val all = mutableListOf<CommentView>()
        var cursor: String? = null
        do {
            val page = page(viewer, limit, cursor, activity)
            assertTrue(page.comments.size <= limit)
            all += page.comments
            cursor = page.cursor
        } while (cursor != null)
        return all
    }

    protected suspend fun HttpClient.threads(viewer: String?, limit: Int? = null, cursor: String? = null, activity: String = "3krun") =
        page(viewer, limit, cursor, activity).also { assertNotNull(it.threads, "a page lists threads") }

    /** Every thread listed for [viewer] on [activity], following cursors in pages of [limit]. */
    protected suspend fun HttpClient.allThreads(viewer: String?, limit: Int, activity: String = "3krun"): List<ThreadView> {
        val all = mutableListOf<ThreadView>()
        var cursor: String? = null
        do {
            val page = threads(viewer, limit, cursor, activity)
            assertTrue(page.threads!!.size <= limit)
            all += page.threads!!
            cursor = page.cursor
        } while (cursor != null)
        return all
    }

    protected suspend fun HttpClient.rawReplies(viewer: String?, root: AtUri, cursor: String? = null, limit: Int? = null) =
        raw(GetComments.method.nsid, viewer, pageQuery("3krun", limit, cursor) + "&root=${root.value.encodeURLParameter()}")

    protected suspend fun HttpClient.replies(viewer: String?, root: AtUri, cursor: String? = null, limit: Int? = null) =
        query(GetComments.method, viewer, pageQuery("3krun", limit, cursor) + "&root=${root.value.encodeURLParameter()}").also {
            assertNull(it.threads, "thread mode lists replies, not threads")
        }

    /**
     * Every comment and reply listed for [viewer], as a client reaches them: each page of threads, and the rest of each thread's replies a
     * page at a time from its repliesCursor.
     */
    protected suspend fun HttpClient.everything(viewer: String?, threadLimit: Int = 30, replyLimit: Int = 30): List<CommentView> =
        allThreads(viewer, threadLimit).flatMap { thread ->
            val all = listOfNotNull(thread.root) + thread.replies.toMutableList()
            val rest = mutableListOf<CommentView>()
            var cursor = thread.repliesCursor
            while (cursor != null) {
                val page = replies(viewer, thread.uri, cursor, replyLimit)
                rest += page.comments
                cursor = page.cursor
            }
            assertEquals(thread.replyCount, thread.replies.size + rest.size, "${thread.uri}'s reply count")
            all + rest
        }

    /** A thread as (root rkey, or "?" for the placeholder) to its first replies' rkeys. */
    protected fun ThreadView.shape() = (root?.rkey ?: "?") to replies.map { it.rkey }

    protected fun deleteSpaceComment(author: String, rkey: String) = runBlocking {
        indexer.spaceDelete(SpaceUri.of(Did(author), Nsid.SPACE_ATHLETE, AthleteSpace.INTERACTIONS), Nsid.FEED_COMMENT, rkey)
    }

    protected suspend fun HttpClient.count(viewer: String?, activity: String = "3krun"): Long =
        query(GetActivity.method, viewer, "?uri=${uri(activity).encodeURLParameter()}").activity.commentCount

    protected val CommentView.rkey
        get() = uri.rkey!!

    /** The indexed (thread_uri, parent_uri) of [comment], or null when it wasn't indexed. */
    protected fun threadOf(comment: AtUri): Pair<String, String?>? =
        transaction(db) {
            Comments.selectAll()
                .where { Comments.uri eq comment.value }
                .singleOrNull()
                ?.let { it[Comments.threadUri] to it[Comments.parentUri] }
        }
}
