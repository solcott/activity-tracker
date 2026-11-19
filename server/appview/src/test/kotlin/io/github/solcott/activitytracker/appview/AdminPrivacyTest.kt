package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.admin.AdminPage
import io.github.solcott.activitytracker.appview.admin.AdminRoutes
import io.github.solcott.activitytracker.appview.admin.AdminSite
import io.github.solcott.activitytracker.appview.admin.HealthPage
import io.github.solcott.activitytracker.appview.admin.HealthSources
import io.github.solcott.activitytracker.appview.admin.JobQueuesPage
import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.SegmentJobFailures
import io.github.solcott.activitytracker.appview.db.SegmentMatchJobs
import io.github.solcott.activitytracker.appview.db.SpaceSync
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.ops.WorkerRegistry
import io.github.solcott.activitytracker.appview.ops.WorkerSupervisor
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.appview.segments.SegmentQueue
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.html.p
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

/**
 * Admin pages reveal no private content (admin/admin-site): carol's non-public activity and comment carry a marker, and no registered page,
 * rendered for her or her records, may print it. A later change adds its pages to [pagesUnderTest].
 */
class AdminPrivacyTest : AdminTestBase() {
    private val marker = "PRIVATE-MARKER-7f3a"
    private val activityUri = "at://$carol/${Nsid.FEED_ACTIVITY}/3kprivate"
    private val trackUri = "at://$carol/${Nsid.FEED_SEGMENT_TRACK}/3kprivate"

    /** A voter and a rider whose DIDs no page may show. */
    private val voter = "did:plc:voter-$marker"
    private val rider = "did:plc:rider-$marker"
    /** The elapsed seconds of carol's Only me effort, which no page may show. */
    private val elapsed = 87_654L
    private val segments = SegmentAdminFixtures(db, clock)
    private val segmentUri = segments.segmentUri("3kseg", carol)

    @AfterTest fun forgetLabels() = segments.forgetLabels()

    /** The operator pages the AppView registers. */
    private val pagesUnderTest: List<AdminPage> =
        listOf(
            HealthPage(
                HealthSources(
                    WorkerSupervisor(CoroutineScope(SupervisorJob()), WorkerRegistry(clock)),
                    StartedWorkers(null, emptyList()),
                    db,
                    24.hours,
                    syncing = true,
                    clock = clock,
                ),
            ),
            JobQueuesPage(SegmentQueue(db, SegmentJobs(), clock), clock),
            segments.page,
        )

    /** What no page may print: the marker (record content), the voter's and rider's DIDs, and the effort's elapsed time. */
    private val secrets = listOf(marker, voter, rider, "$elapsed")

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
        // Carol's Only me space failing to sync, for the health page (admin/ingest-health).
        db.tx {
            SpaceSync.insert {
                it[space] = self.value
                it[authorityDid] = carol
                it[failures] = 3
                it[lastError] = "IOException: host unreachable"
                it[lastErrorAt] = 1
                it[nextAttemptAt] = 2
                it[createdAt] = 0
            }
        }
        // Carol's Only me activity's segment track, retrying and, from an earlier run, given up (admin/job-queues).
        val track =
            """{"activity":"$activityUri","data":{"${'$'}type":"blob","ref":{"${'$'}link":"bafkdata"},""" +
                """"mimeType":"application/json","size":10},"createdAt":"2026-10-01T08:00:00Z"}"""
        indexer.spaceWrite(self, Nsid.FEED_SEGMENT_TRACK, "3kprivate", "bafytrack", AtJson.parseToJsonElement(track).jsonObject)
        db.tx {
            SegmentMatchJobs.upsert {
                it[kind] = SegmentJobs.TRACK
                it[subjectUri] = trackUri
                it[enqueuedAt] = 0
                it[attempts] = 2
                it[lastError] = "IllegalStateException: the PDS is down"
                it[firstFailedAt] = 0
            }
            SegmentJobFailures.insert {
                it[kind] = SegmentJobs.TRACK
                it[subjectUri] = trackUri
                it[attempts] = 9
                it[lastError] = "SerializationException"
                it[firstFailedAt] = 0
                it[failedAt] = 0
            }
        }
        // A segment carol has an Only me effort on, with an open proposal a voter voted on; a taken-down segment named with the marker; a
        // staged candidate with one rider, below the threshold.
        segments.segment("3kseg", did = carol)
        segments.efforts(carol, segmentUri, visibility = "self", elapsed = elapsed)
        val proposal = segments.proposal("3kprop", segmentUri, carol, "Carols Hill", 0)
        segments.vote(proposal, voter)
        val hidden = segments.segment("3khide", did = carol, east = 5000.0, name = marker)
        segments.label(hidden, "!takedown")
        segments.stage("us-vermont", "r1", "vt-1")
        segments.ride("vt-1", rider)
        // The seed must hold the marker, or the test proves nothing.
        assertTrue(db.tx { Activities.selectAll().any { marker in it[Activities.record] } })
        assertTrue(db.tx { SegmentJobFailures.selectAll().count() == 1L })
    }

    /** Every probe of every page in [site], for carol and her records, that printed the marker. */
    private suspend fun HttpClient.leaks(site: AdminSite, cookie: String): List<String> =
        site.pages
            .flatMap { page -> listOf(carol, activityUri, segmentUri).flatMap { page.privacyProbes(it) } }
            .distinct()
            .filter { probe ->
                val body = get("/admin/$probe".trimEnd('/')) { session(cookie) }.bodyAsText()
                secrets.any { it in body }
            }

    @Test
    fun noPagePrintsPrivateContent() {
        seed()
        adminTest({ site(pages = pagesUnderTest) }) { http ->
            val cookie = http.signIn("alice.test")!!
            assertEquals(emptyList(), http.leaks(site(pages = pagesUnderTest), cookie))
            // The queues page names the track, and nothing more of it.
            assertTrue(trackUri in http.get("/admin/queues/failed?kind=track") { session(cookie) }.bodyAsText())
            // The candidate's one rider shows nowhere: its run counts it as not ridden.
            val importer = http.get("/admin/segments") { session(cookie) }.bodyAsText()
            assertTrue("<td>r1</td><td>1</td><td>0</td><td>0</td>" in importer.replace(Regex(">\\s+<"), "><"))
        }
    }

    /** The harness catches a page that prints a voter's DID, an effort's time or a taken-down name. */
    @Test
    fun catchesAPageThatPrintsAVoterOrAnEffort() {
        seed()
        for (leak in listOf(voter, "$elapsed s", marker)) {
            val leaky =
                object : AdminPage {
                    override val path = "leaky"
                    override val title = "Leaky"

                    override fun AdminRoutes.install() {
                        get(path) { page(title) { p { +leak } } }
                    }
                }
            adminTest({ site(pages = listOf(leaky)) }) { http ->
                val cookie = http.signIn("alice.test")!!
                assertEquals(listOf("leaky"), http.leaks(site(pages = listOf(leaky)), cookie), leak)
            }
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
