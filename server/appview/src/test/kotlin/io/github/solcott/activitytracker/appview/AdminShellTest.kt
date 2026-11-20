package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.admin.AdminPage
import io.github.solcott.activitytracker.appview.admin.AdminRoutes
import io.github.solcott.activitytracker.appview.admin.Summaries
import io.github.solcott.activitytracker.appview.admin.Tile
import io.github.solcott.activitytracker.appview.admin.TileStatus
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.html.p

/** The redesigned shell (admin/admin-site): navigation, page summaries, the triage Home and the nav flags. */
class AdminShellTest : AdminTestBase() {
    /** A page whose summary is [tiles], or throws when [fails], counting its reads. */
    private class FakePage(
        override val path: String,
        override val title: String,
        private val tiles: List<Tile>? = emptyList(),
        private val fails: Boolean = false,
        private val slow: Boolean = false,
    ) : AdminPage {
        var reads = 0

        override fun AdminRoutes.install() {
            get(path) { page(title) { p { +"$title body" } } }
        }

        override suspend fun summary(now: Instant): List<Tile> {
            reads++
            if (fails) error("summary broke")
            if (slow) delay(10.seconds)
            return checkNotNull(tiles)
        }
    }

    private fun tile(label: String, status: TileStatus?, page: String) = Tile(label, "1", status, "/admin/$page")

    private val health =
        FakePage("health", "Health", listOf(tile("H ok", TileStatus.Ok, "health"), tile("H warn", TileStatus.Warn, "health")))
    private val queues = FakePage("queues", "Queues", listOf(tile("Q info", null, "queues"), tile("Q bad", TileStatus.Bad, "queues")))
    private val quiet = FakePage("quiet", "Quiet", listOf(tile("All fine", TileStatus.Ok, "quiet")))

    private suspend fun HttpClient.page(path: String, cookie: String) = get(path) { session(cookie) }.bodyAsText()

    @Test
    fun theCurrentPageIsMarkedAndEveryPageListed() =
        adminTest({ site(pages = listOf(health, queues)) }) { http ->
            val cookie = http.signIn("bob.test")!!
            val html = http.page("/admin/queues", cookie)
            assertEquals(1, Regex("aria-current=\"page\"").findAll(html).count(), "exactly one page is current")
            assertTrue(Regex("""<a href="/admin/queues" aria-current="page">""").containsMatchIn(html), html)
            for (href in listOf("\"/admin\"", "\"/admin/health\"", "\"/admin/queues\"", "\"/admin/audit\"")) assertTrue(href in html, href)
            assertTrue(bob in html && "viewer" in html && "Sign out" in html)
            assertTrue("hx-get=\"/admin/nav?current=queues\"" in html, "flags load after the page")
            assertFalse("class=\"flag" in html, "no flag is drawn with the page")
        }

    @Test
    fun navFlagsMarkPagesWithProblems() =
        adminTest({ site(pages = listOf(health, queues, quiet)) }) { http ->
            val cookie = http.signIn("bob.test")!!
            val nav = http.page("/admin/nav?current=quiet", cookie)
            assertTrue(Regex("""href="/admin/health">\s*<span>Health</span>\s*<span class="flag warn"""").containsMatchIn(nav), nav)
            assertTrue(Regex("""href="/admin/queues">\s*<span>Queues</span>\s*<span class="flag bad"""").containsMatchIn(nav), nav)
            assertFalse(
                Regex("""href="/admin/quiet" aria-current="page">\s*<span>Quiet</span>\s*<span class="flag""").containsMatchIn(nav),
                nav,
            )
            assertTrue("aria-current=\"page\"" in nav)
            assertTrue("needs attention" in nav, "flags have text for assistive technology")
            val unknown = http.page("/admin/nav?current=%3Cscript%3E", cookie)
            assertFalse("aria-current" in unknown, "an unknown page marks nothing")
            assertFalse("script" in unknown, "and isn't echoed")
        }

    @Test
    fun homeShowsProblemsFirstThenTheRest() =
        adminTest({ site(pages = listOf(health, queues, quiet)) }) { http ->
            val html = http.page("/admin", http.signIn("bob.test")!!)
            val order = listOf("Q bad", "H warn", "H ok", "Q info", "All fine").map { html.indexOf(">$it<") }
            assertTrue(order.all { it >= 0 } && order == order.sorted(), "bad, then warn, then the rest in navigation order: $order")
            assertTrue(html.indexOf("Needs attention") < html.indexOf("Q bad") && html.indexOf("Everything else") < html.indexOf("H ok"))
            assertFalse("Nothing needs attention" in html)
            assertTrue("""hx-trigger="every 30s"""" in html)
            assertTrue("href=\"/admin/queues\"" in html, "a tile links to its detail")
        }

    @Test
    fun homeSaysAllClearAndViewersSeeTheSameTiles() =
        adminTest({ site(pages = listOf(quiet)) }) { http ->
            val viewer = http.page("/admin", http.signIn("bob.test")!!)
            val operator = http.page("/admin", http.signIn("alice.test")!!)
            assertTrue("Nothing needs attention." in viewer)
            assertTrue(">All fine<" in viewer)
            fun tiles(html: String) = html.substringAfter("id=\"home-tiles\"").substringBefore("</main>")
            assertEquals(tiles(viewer), tiles(operator))
            val fragment = http.page("/admin/tiles", http.signIn("bob.test")!!)
            assertTrue("id=\"home-tiles\"" in fragment && ">All fine<" in fragment && "<html" !in fragment)
        }

    @Test
    fun aBrokenSummaryIsUnavailableAndTheRestStillDraw() {
        val broken = FakePage("broken", "Broken", fails = true)
        adminTest({ site(pages = listOf(broken, health)) }) { http ->
            val html = http.page("/admin", http.signIn("bob.test")!!)
            assertTrue(">Unavailable<" in html && ">H warn<" in html, html)
            assertFalse("Nothing needs attention" in html, "an unreadable summary needs attention")
            assertTrue(
                html.indexOf("Needs attention") < html.indexOf(">Unavailable<") &&
                    html.indexOf(">Unavailable<") < html.indexOf("Everything else"),
            )
            val nav = http.page("/admin/nav", http.signIn("bob.test")!!)
            assertTrue(Regex("""href="/admin/broken">\s*<span>Broken</span>\s*<span class="flag warn"""").containsMatchIn(nav), nav)
        }
    }

    @Test
    fun summariesTimeOutPerPageAndAreCachedForTenSeconds() = runBlocking {
        val slow = FakePage("slow", "Slow", slow = true)
        val counted = FakePage("counted", "Counted", listOf(tile("c", TileStatus.Ok, "counted")))
        val summaries = Summaries({ listOf(slow, counted) }, clock, timeout = 50.milliseconds)
        val read = summaries.read()
        assertNull(read.single { it.page == slow }.tiles, "a slow page times out")
        assertNotNull(read.single { it.page == counted }.tiles, "without holding up the others")
        summaries.read()
        assertEquals(1, counted.reads, "read again within 10 s from the cache")
        now += 11.seconds
        summaries.read()
        assertEquals(2, counted.reads)
    }

    @Test
    fun aPageWithNoSummaryIsNeverFlagged() =
        adminTest({ site(pages = listOf(FakePage("plain", "Plain"))) }) { http ->
            assertFalse("class=\"flag" in http.page("/admin/nav", http.signIn("bob.test")!!))
        }
}
