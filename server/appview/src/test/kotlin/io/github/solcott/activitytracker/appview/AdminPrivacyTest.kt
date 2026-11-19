package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.admin.AdminPage
import io.github.solcott.activitytracker.appview.admin.AdminRoutes
import io.github.solcott.activitytracker.appview.admin.AdminSite
import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.html.p
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * Admin pages reveal no private content (admin/admin-site): carol's non-public activity and comment carry a marker, and no registered page,
 * rendered for her or her records, may print it. A later change adds its pages to [pagesUnderTest].
 */
class AdminPrivacyTest : AdminTestBase() {
    private val marker = "PRIVATE-MARKER-7f3a"
    private val activityUri = "at://$carol/${Nsid.FEED_ACTIVITY}/3kprivate"

    /** The operator pages the AppView registers; empty until a change adds one. */
    private val pagesUnderTest: List<AdminPage> = emptyList()

    private fun seed() = runBlocking {
        val indexer = Indexer(db, { did -> did.value.substringAfterLast(':') + ".test" })
        val self = SpaceUri.of(Did(carol), Nsid.SPACE_ATHLETE, AthleteSpace.SELF)
        val activity =
            """{"sport":"run","title":"$marker","description":"$marker","startedAt":"2026-10-01T07:00:00Z","elapsedTime":1800,""" +
                """"createdAt":"2026-10-01T07:31:00Z"}"""
        indexer.spaceWrite(self, Nsid.FEED_ACTIVITY, "3kprivate", "bafyprivate", AtJson.parseToJsonElement(activity).jsonObject)
        val interactions = SpaceUri.of(Did(carol), Nsid.SPACE_ATHLETE, AthleteSpace.INTERACTIONS)
        val comment =
            """{"subject":{"uri":"$activityUri","cid":"bafyprivate"},"text":"$marker","audience":"self",""" +
                """"createdAt":"2026-10-01T08:00:00Z"}"""
        indexer.spaceWrite(interactions, Nsid.FEED_COMMENT, "3kcomment", "bafycomment", AtJson.parseToJsonElement(comment).jsonObject)
        // The seed must hold the marker, or the test proves nothing.
        assertTrue(db.tx { Activities.selectAll().any { marker in it[Activities.record] } })
    }

    /** Every probe of every page in [site], for carol and her records, that printed the marker. */
    private suspend fun HttpClient.leaks(site: AdminSite, cookie: String): List<String> =
        site.pages
            .flatMap { page -> listOf(carol, activityUri).flatMap { page.privacyProbes(it) } }
            .distinct()
            .filter { probe -> marker in get("/admin/$probe".trimEnd('/')) { session(cookie) }.bodyAsText() }

    @Test
    fun noPagePrintsPrivateContent() {
        seed()
        adminTest({ site(pages = pagesUnderTest) }) { http ->
            val cookie = http.signIn("alice.test")!!
            assertEquals(emptyList(), http.leaks(site(pages = pagesUnderTest), cookie))
        }
    }

    /** The harness itself: a page that prints a record's content is caught. */
    @Test
    fun catchesAPageThatPrintsARecord() {
        seed()
        val leaky =
            object : AdminPage {
                override val path = "leaky"
                override val title = "Leaky"

                override fun AdminRoutes.install() {
                    get(path) {
                        val records = db.tx { Activities.selectAll().map { it[Activities.record] } }
                        page(title) { records.forEach { p { +it } } }
                    }
                }
            }
        adminTest({ site(pages = listOf(leaky)) }) { http ->
            val cookie = http.signIn("alice.test")!!
            assertEquals(listOf("leaky"), http.leaks(site(pages = listOf(leaky)), cookie))
        }
    }
}
