package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
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
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database

/** `getComments`: an activity's comments a page at a time, or looked up by URI, by the same rule `getActivity`'s count uses. */
class CommentPagingTest {
    private val service = "did:web:localhost%3A8081"
    private val alice = "did:plc:alice"
    private val bob = "did:plc:bob"
    private val carol = "did:plc:carol"
    private val dave = "did:plc:dave"
    private val erin = "did:plc:erin"
    private val keys = listOf(alice, bob, carol, dave, erin).associateWith { TestAccountKey() }

    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val indexer = Indexer(db, { did -> did.value.substringAfterLast(':') + ".test" })
    private val config = AppViewConfig.fromEnv(emptyMap())
    private val verifier = ServiceAuthVerifier(service, keys = { keys[it.value]?.multibase })

    private val base = Instant.parse("2026-10-14T10:00:00Z")

    private fun uri(rkey: String) = "at://$alice/${Nsid.FEED_ACTIVITY}/$rkey"

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

    /** Alice posts [rkey] at [tier]: in her repo when public, in her tier space otherwise. */
    private fun post(rkey: String, tier: String = Visibility.PUBLIC) {
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

    private fun follow(author: String, subject: String) =
        commit(
            author,
            Nsid.GRAPH_FOLLOW,
            "3kf${author.last()}${subject.last()}",
            """{"subject":"$subject","createdAt":"2026-10-01T09:00:00Z"}""",
        )

    private fun unfollow(author: String, subject: String) = commit(author, Nsid.GRAPH_FOLLOW, "3kf${author.last()}${subject.last()}", null)

    /**
     * [author] comments [rkey] on alice's [activity], dated [minute] minutes after [base]: in their repo with no [audience], else in their
     * interactions space written for [audience].
     */
    private fun comment(author: String, rkey: String, minute: Int, activity: String = "3krun", audience: String? = null): AtUri {
        val createdAt = Instant.fromEpochMilliseconds(base.toEpochMilliseconds() + minute * 60_000L)
        val json =
            """{"subject":{"uri":"${uri(activity)}","cid":"bafy$activity"},"text":"c$rkey","createdAt":"$createdAt"""" +
                audience?.let { ""","audience":"$it"""" }.orEmpty() +
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

    private fun hide(comment: AtUri) {
        val rkey = runBlocking { commentHideRkey(comment) }
        commit(alice, Nsid.FEED_COMMENT_HIDE, rkey, """{"subject":"${comment.value}","createdAt":"2026-10-14T11:00:00Z"}""")
    }

    /** Bob's comments `3kc000`… on alice's public run, one a minute apart, oldest first. */
    private fun bobComments(count: Int): List<String> =
        (0 until count)
            .map { i -> "3kc" + i.toString().padStart(3, '0') }
            .onEach { rkey -> comment(bob, rkey, minute = rkey.drop(3).toInt()) }

    private fun ApplicationTestBuilder.start() {
        application { appView(config, db, verifier) }
    }

    private suspend fun HttpClient.raw(nsid: String, viewer: String?, query: String): HttpResponse =
        get("/xrpc/$nsid$query") {
            viewer?.let { header(HttpHeaders.Authorization, "Bearer ${keys.getValue(it).serviceJwt(it, service, nsid)}") }
        }

    private suspend fun <P, O> HttpClient.query(method: XrpcQuery<P, O>, viewer: String?, query: String): O {
        val response = raw(method.nsid, viewer, query)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return AtJson.decodeFromString(method.output, response.bodyAsText())
    }

    private fun pageQuery(activity: String, limit: Int?, cursor: String?) =
        "?uri=${uri(activity).encodeURLParameter()}" +
            limit?.let { "&limit=$it" }.orEmpty() +
            cursor?.let { "&cursor=${it.encodeURLParameter()}" }.orEmpty()

    private suspend fun HttpClient.page(viewer: String?, limit: Int? = null, cursor: String? = null, activity: String = "3krun") =
        query(GetComments.method, viewer, pageQuery(activity, limit, cursor))

    private suspend fun HttpClient.lookup(viewer: String?, uris: List<AtUri>, activity: String = "3krun"): List<CommentView> =
        query(
                GetComments.method,
                viewer,
                "?uri=${uri(activity).encodeURLParameter()}" + uris.joinToString("") { "&uris=${it.value.encodeURLParameter()}" },
            )
            .also { assertNull(it.cursor, "a lookup has no cursor") }
            .comments

    /** Every comment [viewer] is listed on [activity], following cursors in pages of [limit]. */
    private suspend fun HttpClient.allPages(viewer: String?, limit: Int, activity: String = "3krun"): List<CommentView> {
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

    private suspend fun HttpClient.count(viewer: String?, activity: String = "3krun"): Long =
        query(GetActivity.method, viewer, "?uri=${uri(activity).encodeURLParameter()}").activity.commentCount

    private val CommentView.rkey
        get() = uri.rkey!!

    @Test
    fun pagesListCommentsOldestFirst() = testApplication {
        start()
        post("3krun")
        val rkeys = bobComments(45)

        val first = client.page(null)
        assertEquals(rkeys.take(30), first.comments.map { it.rkey }, "the 30 oldest, oldest first")
        val cursor = assertNotNull(first.cursor)

        val last = client.page(null, cursor = cursor)
        assertEquals(rkeys.drop(30), last.comments.map { it.rkey })
        assertNull(last.cursor, "no cursor after the last page")

        assertEquals(rkeys.take(10), client.page(null, limit = 10).comments.map { it.rkey })
        assertEquals(rkeys, client.allPages(null, limit = 7).map { it.rkey }, "no comment repeated or skipped")
    }

    @Test
    fun aPageHoldsAtMostOneHundred() = testApplication {
        start()
        post("3krun")
        bobComments(105)
        val page = client.page(null, limit = 500)
        assertEquals(100, page.comments.size)
        assertNotNull(page.cursor)
    }

    @Test
    fun aCommentIndexedBetweenPagesIsListedOnceOnThePageThatReachesIt() = testApplication {
        start()
        post("3krun")
        val rkeys = bobComments(45)
        val first = client.page(null)
        comment(carol, "3knew", minute = 100)
        val second = client.page(null, cursor = first.cursor)
        assertEquals(rkeys.drop(30) + "3knew", second.comments.map { it.rkey })
        assertTrue(first.comments.none { it in second.comments })
    }

    @Test
    fun commentsAtTheSameMomentAreNeitherRepeatedNorSkipped() = testApplication {
        start()
        post("3krun")
        val same = listOf("3kca", "3kcb", "3kcc", "3kcd").onEach { comment(bob, it, minute = 1) }
        assertEquals(same, client.allPages(null, limit = 1).map { it.rkey })
        assertEquals(same, client.allPages(null, limit = 3).map { it.rkey })
    }

    @Test
    fun aMalformedCursorReadsAsTheFirstPage() = testApplication {
        start()
        post("3krun")
        val rkeys = bobComments(3)
        assertEquals(rkeys, client.page(null, cursor = "nonsense").comments.map { it.rkey })
        assertEquals(rkeys, client.page(null, cursor = "abc::at://x").comments.map { it.rkey })
    }

    @Test
    fun anActivityTheViewerMayNotSeeIsNotFound() = testApplication {
        start()
        post("3krun", Visibility.FOLLOWERS)
        follow(bob, alice)
        val bobs = comment(bob, "3kcb", minute = 1, audience = Visibility.FOLLOWERS)
        for (viewer in listOf(dave, null)) {
            val page = client.raw(GetComments.method.nsid, viewer, pageQuery("3krun", null, null))
            assertEquals(HttpStatusCode.BadRequest, page.status)
            assertTrue("NotFound" in page.bodyAsText())
            assertTrue("3kcb" !in page.bodyAsText())
            val lookup =
                client.raw(
                    GetComments.method.nsid,
                    viewer,
                    "?uri=${uri("3krun").encodeURLParameter()}&uris=${bobs.value.encodeURLParameter()}",
                )
            assertEquals(HttpStatusCode.BadRequest, lookup.status)
            assertTrue("NotFound" in lookup.bodyAsText())
        }
    }

    @Test
    fun pagesApplyTheRuleForWhoSeesEachComment() = testApplication {
        start()
        post("3krun", Visibility.FOLLOWERS)
        follow(bob, alice)
        follow(carol, alice)
        follow(alice, bob) // bob is a mutual, carol isn't.
        follow(erin, alice)
        comment(bob, "3kc1", minute = 1, audience = Visibility.FOLLOWERS)
        comment(carol, "3kc2", minute = 2, audience = Visibility.FOLLOWERS)
        comment(bob, "3kc3", minute = 3, audience = Visibility.MUTUALS)
        val hidden = comment(carol, "3kc4", minute = 4, audience = Visibility.FOLLOWERS)
        comment(erin, "3kc5", minute = 5, audience = Visibility.FOLLOWERS)
        comment(dave, "3kc6", minute = 6) // dave can't see the activity, so his comment is shown to no one.
        hide(hidden)
        unfollow(erin, alice)

        fun List<CommentView>.seen() = map { it.rkey to it.hidden }

        assertEquals(
            listOf("3kc1" to null, "3kc2" to null, "3kc3" to null, "3kc4" to true),
            client.allPages(alice, limit = 2).seen(),
            "the athlete sees the hidden one marked",
        )
        assertEquals(listOf("3kc1" to null, "3kc2" to null, "3kc3" to null), client.allPages(bob, limit = 2).seen())
        assertEquals(
            listOf("3kc1" to null, "3kc2" to null, "3kc4" to true),
            client.allPages(carol, limit = 2).seen(),
            "not the mutuals one",
        )
        assertEquals(3, client.count(alice))
        assertEquals(3, client.count(bob))
        assertEquals(2, client.count(carol))
    }

    @Test
    fun aLookupReturnsJustTheListedOnesOfTheGivenComments() = testApplication {
        start()
        post("3krun", Visibility.FOLLOWERS)
        post("3kride", Visibility.FOLLOWERS)
        follow(bob, alice)
        follow(carol, alice)
        follow(alice, bob)
        val hidden = comment(bob, "3kc1", minute = 1, audience = Visibility.FOLLOWERS)
        val shown = comment(carol, "3kc2", minute = 2, audience = Visibility.FOLLOWERS)
        val mutuals = comment(bob, "3kc3", minute = 3, audience = Visibility.MUTUALS)
        val deleted = comment(carol, "3kc4", minute = 4, audience = Visibility.FOLLOWERS)
        val elsewhere = comment(carol, "3kc5", minute = 5, activity = "3kride", audience = Visibility.FOLLOWERS)
        val never = AtUri("at://$carol/${Nsid.FEED_COMMENT}/3kc9")
        hide(hidden)
        runBlocking {
            indexer.spaceDelete(SpaceUri.of(Did(carol), Nsid.SPACE_ATHLETE, AthleteSpace.INTERACTIONS), Nsid.FEED_COMMENT, "3kc4")
        }

        assertEquals(
            listOf("3kc1" to true, "3kc2" to null),
            client.lookup(alice, listOf(shown, hidden)).map { it.rkey to it.hidden },
            "oldest first, the hidden one marked",
        )
        assertEquals(emptyList(), client.lookup(alice, listOf(deleted, never)), "deleted and unknown comments are left out")
        assertEquals(emptyList(), client.lookup(carol, listOf(mutuals)), "carol isn't one of alice's mutuals")
        assertEquals(listOf("3kc3"), client.lookup(bob, listOf(mutuals)).map { it.rkey })
        assertEquals(emptyList(), client.lookup(alice, listOf(elsewhere)), "a comment on another activity")
        assertEquals(listOf("3kc5"), client.lookup(alice, listOf(elsewhere), activity = "3kride").map { it.rkey })
    }

    @Test
    fun aLookupOfMoreThanTwentyFiveIsInvalid() = testApplication {
        start()
        post("3krun")
        val uris = (0..25).map { AtUri("at://$bob/${Nsid.FEED_COMMENT}/3kc$it") }
        val response =
            client.raw(
                GetComments.method.nsid,
                null,
                "?uri=${uri("3krun").encodeURLParameter()}" + uris.joinToString("") { "&uris=${it.value.encodeURLParameter()}" },
            )
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue("InvalidRequest" in response.bodyAsText())
        assertEquals(emptyList(), client.lookup(null, uris.take(25)))
    }

    @Test
    fun theCountMatchesThePages() = testApplication {
        start()
        // A Public run whose comments were written while it had other visibilities, so their audiences differ.
        post("3krun")
        follow(bob, alice)
        follow(carol, alice)
        follow(dave, alice)
        follow(alice, bob)
        val commenters = listOf(bob, carol, dave, erin)
        val audiences = listOf(null, Visibility.FOLLOWERS, Visibility.MUTUALS, Visibility.SELF)
        val comments =
            (0 until 70).map { i ->
                comment(commenters[i % 4], "3kc" + i.toString().padStart(3, '0'), minute = i, audience = audiences[(i / 4) % 4])
            }
        comments.filter { it.authority == carol }.take(5).forEach(::hide)
        unfollow(dave, alice)

        // Alice, carol (whose comments she hid), bob, erin (who never followed) and someone signed out.
        for (viewer in listOf(alice, carol, bob, erin, null)) {
            val listed = client.allPages(viewer, limit = 30)
            assertEquals(listed.count { it.hidden != true }.toLong(), client.count(viewer), "the count matches the pages for $viewer")
            assertEquals(listed.map { it.uri }.distinct(), listed.map { it.uri }, "no comment listed twice for $viewer")
        }
        assertEquals(5, client.allPages(alice, limit = 30).count { it.hidden == true })
        // Two of carol's hidden ones were written for Mutuals and Only me, which don't admit her, hidden or not.
        assertEquals(3, client.allPages(carol, limit = 30).count { it.hidden == true })
        assertEquals(0, client.allPages(bob, limit = 30).count { it.hidden == true })

        val activity = client.query(GetActivity.method, alice, "?uri=${uri("3krun").encodeURLParameter()}")
        assertEquals(emptyList(), activity.comments, "getActivity no longer lists comments")
    }
}
