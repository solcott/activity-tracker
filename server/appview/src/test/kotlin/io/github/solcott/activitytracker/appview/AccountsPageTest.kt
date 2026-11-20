package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.admin.AccountsPage
import io.github.solcott.activitytracker.appview.api.PdsLookup
import io.github.solcott.activitytracker.appview.auth.AppViewSigningKey
import io.github.solcott.activitytracker.appview.db.AdminActions
import io.github.solcott.activitytracker.appview.db.Blocks
import io.github.solcott.activitytracker.appview.db.RepoReindexes
import io.github.solcott.activitytracker.appview.db.SpaceSync
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.appview.reindex.RepoReindexer
import io.github.solcott.activitytracker.appview.sync.BskyBlockBackfill
import io.github.solcott.activitytracker.appview.sync.SpaceSyncer
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.space.SignedDelegationTokens
import io.github.solcott.activitytracker.atproto.space.SpaceCredentials
import io.github.solcott.activitytracker.atproto.space.SpaceMember
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.decodeURLPart
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** admin/account-tools: the account page, its fragments and its four audited repairs. Alice is an operator, bob a viewer. */
class AccountsPageTest : AdminTestBase() {
    private val handles = mutableMapOf(carol to "carol.test")
    private val indexer = Indexer(db, { did -> handles[did.value] ?: "handle.invalid" }, clock)

    /** Carol's PDS holds one Bluesky block. */
    private val pdsHttp =
        HttpClient(
            MockEngine {
                val body =
                    """{"records":[{"uri":"at://$carol/${Nsid.BSKY_GRAPH_BLOCK}/3kb1","cid":"bafyb1","value":""" +
                        """{"${'$'}type":"${Nsid.BSKY_GRAPH_BLOCK}","subject":"did:plc:dave","createdAt":"2025-01-01T00:00:00Z"}}]}"""
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
    private val backfill = BskyBlockBackfill(db, indexer, pdsHttp, PdsLookup { "https://pds.test" }, clock)

    /** Never started, so a requested run stays queued. */
    private val reindexer = RepoReindexer(db, indexer, pdsHttp, { error("not resolved in these tests") }, null, clock = clock)

    private val unreachable = HttpClient(MockEngine { respond("down", HttpStatusCode.BadGateway) })
    private val syncer =
        SpaceSyncer(
            db,
            indexer,
            SpaceMember(
                SpaceCredentials(
                    SignedDelegationTokens(
                        Did("did:web:appview.test"),
                        AppViewSigningKey(TEST_SIGNING_KEY).jwtAlg,
                        AppViewSigningKey(TEST_SIGNING_KEY)::sign,
                    ),
                    unreachable,
                    IdentityResolver(unreachable, "http://plc.test", null),
                ),
                unreachable,
            ),
            clock,
        )

    private fun page(withSyncer: Boolean = true) = AccountsPage(db, indexer, backfill, reindexer, if (withSyncer) syncer else null)

    private fun seedCarol() = runBlocking {
        indexer.handle(
            JetstreamEvent.Commit(
                did = carol,
                time = "2026-11-18T12:00:00.000000Z",
                operation = "create",
                collection = Nsid.ACTOR_PROFILE,
                rkey = "self",
                record = AtJson.parseToJsonElement("""{"displayName":"Carol"}""").jsonObject,
                cid = "bafyself",
            ),
        )
    }

    private data class Audit(val admin: String, val action: String, val target: String?, val params: String, val outcome: String)

    private fun audits() = runBlocking {
        db.tx {
            AdminActions.selectAll().orderBy(AdminActions.id).map {
                Audit(
                    it[AdminActions.adminDid],
                    it[AdminActions.action],
                    it[AdminActions.target],
                    it[AdminActions.params],
                    it[AdminActions.outcome],
                )
            }
        }
    }

    private fun runs() = runBlocking { db.tx { RepoReindexes.selectAll().count() } }

    private suspend fun HttpClient.html(path: String, cookie: String) = get(path) { session(cookie) }.bodyAsText()

    private suspend fun HttpClient.post(path: String, cookie: String, csrf: String?, vararg fields: Pair<String, String>): HttpResponse =
        submitForm(path, Parameters.build { fields.forEach { (k, v) -> append(k, v) } }) {
            session(cookie)
            csrf?.let { header("X-CSRF-Token", it) }
        }

    private fun test(withSyncer: Boolean = true, block: suspend (HttpClient) -> Unit) =
        adminTest({ site(pages = listOf(page(withSyncer))) }) { http -> block(http) }

    @Test
    fun aViewerSeesEverySectionWithoutControls() = test { http ->
        seedCarol()
        val cookie = http.signIn("bob.test")!!
        val html = http.html("/admin/accounts/$carol", cookie)
        listOf("Account", "carol.test", "Indexed records", "Spaces", "Reindex runs", "Segment matching", "Grouping").forEach {
            assertTrue(it in html, it)
        }
        assertFalse("hx-post" in html)
        assertFalse("Reindex repo" in html)
    }

    @Test
    fun anOperatorSeesTheRepairs() = test { http ->
        seedCarol()
        val cookie = http.signIn("alice.test")!!
        val html = http.html("/admin/accounts/$carol", cookie)
        listOf("Re-verify handle now", "Backfill Bluesky blocks now", "Reindex repo", "Resync spaces").forEach {
            assertTrue(it in html, it)
        }
    }

    @Test
    fun withoutASyncerThereIsNoResync() =
        test(withSyncer = false) { http ->
            seedCarol()
            val cookie = http.signIn("alice.test")!!
            val html = http.html("/admin/accounts/$carol", cookie)
            assertTrue("Reindex repo" in html)
            assertFalse("Resync spaces" in html)
            assertFalse("Also resync spaces" in html)
            val csrf = csrfOf(html)
            assertEquals(HttpStatusCode.NotFound, http.post("/admin/accounts/resync-spaces", cookie, csrf, "target" to carol).status)
        }

    @Test
    fun anUnknownDidShowsAsNotIndexed() = test { http ->
        val cookie = http.signIn("bob.test")!!
        assertTrue("not indexed" in http.html("/admin/accounts/did:plc:nobody", cookie))
    }

    @Test
    fun lookups() = test { http ->
        seedCarol()
        val cookie = http.signIn("bob.test")!!
        val found = http.get("/admin/accounts?q=%40Carol.test") { session(cookie) }
        assertEquals(HttpStatusCode.Found, found.status)
        assertEquals("/admin/accounts/$carol", found.headers[HttpHeaders.Location]!!.decodeURLPart())
        assertTrue("No indexed account" in http.html("/admin/accounts?q=handle.invalid", cookie))
        assertTrue("neither a DID nor a handle" in http.html("/admin/accounts?q=not+a+handle", cookie))
    }

    @Test
    fun theRunsListPollsOnlyWhileARunIsActive() = test { http ->
        seedCarol()
        val cookie = http.signIn("alice.test")!!
        assertFalse("every 3s" in http.html("/admin/accounts/$carol/runs", cookie))
        val csrf = csrfOf(http.html("/admin/accounts/$carol", cookie))
        http.post("/admin/accounts/reindex", cookie, csrf, "target" to carol, "resyncSpaces" to "true", "resyncSpaces" to "false")
        val queued = http.html("/admin/accounts/$carol/runs", cookie)
        assertTrue("every 3s" in queued)
        assertTrue("queued" in queued)
        runBlocking { db.tx { RepoReindexes.update { it[state] = "succeeded" } } }
        val done = http.html("/admin/accounts/$carol/runs", cookie)
        assertTrue("succeeded" in done)
        assertFalse("every 3s" in done)
    }

    @Test
    fun recheckHandleRunsAndIsAudited() = test { http ->
        seedCarol()
        handles[carol] = "carol.example.com"
        val cookie = http.signIn("alice.test")!!
        val csrf = csrfOf(http.html("/admin/accounts/$carol", cookie))
        val response = http.post("/admin/accounts/recheck-handle", cookie, csrf, "target" to carol)
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue("Verified carol.example.com" in response.bodyAsText())
        assertTrue("carol.example.com" in http.html("/admin/accounts/$carol", cookie))
        assertEquals(listOf(Audit(alice, "account.recheckHandle", carol, "{}", "ok")), audits())
        val unknown = http.post("/admin/accounts/recheck-handle", cookie, csrf, "target" to "did:plc:nobody")
        assertEquals(HttpStatusCode.UnprocessableEntity, unknown.status)
        assertTrue("Not indexed" in unknown.bodyAsText())
        assertEquals("refused: not-indexed", audits().last().outcome)
    }

    @Test
    fun backfillBlocksRunsAndIsAudited() = test { http ->
        seedCarol()
        val cookie = http.signIn("alice.test")!!
        val csrf = csrfOf(http.html("/admin/accounts/$carol", cookie))
        val response = http.post("/admin/accounts/backfill-blocks", cookie, csrf, "target" to carol)
        assertTrue("1 Bluesky blocks indexed" in response.bodyAsText())
        assertEquals(1, runBlocking { db.tx { Blocks.selectAll().count() } })
        assertEquals(listOf(Audit(alice, "account.backfillBlocks", carol, "{}", "ok: 1 blocks")), audits())
    }

    @Test
    fun aSecondReindexReturnsTheQueuedRun() = test { http ->
        seedCarol()
        val cookie = http.signIn("alice.test")!!
        val csrf = csrfOf(http.html("/admin/accounts/$carol", cookie))
        val first = http.post("/admin/accounts/reindex", cookie, csrf, "target" to carol, "resyncSpaces" to "false").bodyAsText()
        val second = http.post("/admin/accounts/reindex", cookie, csrf, "target" to carol, "resyncSpaces" to "false").bodyAsText()
        val id = Regex("Run (\\d+)").find(first)!!.groupValues[1]
        assertTrue("Run $id is already queued" in second, second)
        assertEquals(1, runs())
        val run = runBlocking { db.tx { RepoReindexes.selectAll().single() } }
        assertEquals(alice, run[RepoReindexes.requestedBy])
        assertFalse(run[RepoReindexes.resyncSpaces])
        assertEquals(Audit(alice, "account.reindex", carol, """{"resyncSpaces":"false"}""", "ok: run $id"), audits().first())
    }

    @Test
    fun resyncSpacesQueuesTheAccountsSpaces() = test { http ->
        seedCarol()
        val cookie = http.signIn("alice.test")!!
        val csrf = csrfOf(http.html("/admin/accounts/$carol", cookie))
        val response = http.post("/admin/accounts/resync-spaces", cookie, csrf, "target" to carol)
        assertTrue("4 spaces queued" in response.bodyAsText())
        assertEquals(4, runBlocking { db.tx { SpaceSync.selectAll().where { SpaceSync.authorityDid eq carol }.count() } })
        assertEquals(listOf(Audit(alice, "account.resyncSpaces", carol, "{}", "ok: 4 spaces")), audits())
    }

    @Test
    fun viewersAndForgedPostsAreRefusedAndAudited() = test { http ->
        seedCarol()
        val viewer = http.signIn("bob.test")!!
        val viewerCsrf = csrfOf(http.html("/admin/accounts/$carol", viewer))
        for (action in listOf("recheck-handle", "backfill-blocks", "reindex", "resync-spaces")) {
            assertEquals(
                HttpStatusCode.Forbidden,
                http.post("/admin/accounts/$action", viewer, viewerCsrf, "target" to carol).status,
                action,
            )
        }
        assertEquals(
            listOf("account.recheckHandle", "account.backfillBlocks", "account.reindex", "account.resyncSpaces").map {
                Audit(bob, it, carol, "{}", "refused: viewer")
            },
            audits(),
        )
        val operator = http.signIn("alice.test")!!
        for (action in listOf("recheck-handle", "backfill-blocks", "reindex", "resync-spaces")) {
            assertEquals(HttpStatusCode.Forbidden, http.post("/admin/accounts/$action", operator, null, "target" to carol).status, action)
        }
        assertTrue(audits().drop(4).all { it.outcome == "refused: csrf" })
        assertEquals(0, runs())
        assertEquals(0, runBlocking { db.tx { SpaceSync.selectAll().count() + Blocks.selectAll().count() } })
    }

    @Test
    fun aTargetThatIsntADidIsRefused() = test { http ->
        val cookie = http.signIn("alice.test")!!
        val csrf = csrfOf(http.html("/admin/accounts", cookie))
        val response = http.post("/admin/accounts/reindex", cookie, csrf, "target" to "carol.test")
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue("That is not a DID." in response.bodyAsText())
        assertEquals("refused: invalid-target", audits().single().outcome)
        assertEquals(0, runs())
    }
}
