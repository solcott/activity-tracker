package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import app.cash.turbine.ReceiveTurbine
import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.app.detail.ActivityDetailPresenter
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.detail.ActivityDetailState
import io.github.solcott.activitytracker.app.detail.CombinePickerEvent
import io.github.solcott.activitytracker.app.detail.CombinePickerPresenter
import io.github.solcott.activitytracker.app.detail.CombinePickerScreen
import io.github.solcott.activitytracker.app.detail.CombinePickerState
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.data.activity.ActivityExporter
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.ActivityPlaces
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.activity.RouteTrimmer
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.UnitsRepository
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Combining from an activity's page (`feed/combined-activities`): the picker, and the combined activity's own actions. */
class CombinePresentersTest {
    private val alice = Did("did:plc:alice")
    private val aliceProfile = """{"did":"did:plc:alice","handle":"alice.test"}"""

    private fun uri(rkey: String) = "at://${alice.value}/${Nsid.FEED_ACTIVITY}/$rkey"

    /** Alice's repo: `rkey → feed.activity`, as the PDS holds it and the AppView serves it. */
    private val records = mutableMapOf<String, JsonObject>()

    /** Every write the PDS took, in order, as "put|delete collection/rkey". */
    private val writes = mutableListOf<String>()

    /** Holds each `putRecord` until completed. */
    private var gate = CompletableDeferred(Unit)
    private var failReads = false
    private var failByStart = false

    private fun ride(start: String, minutes: Int = 60, members: List<String> = emptyList()): JsonObject {
        val combined =
            members
                .takeIf { it.isNotEmpty() }
                ?.joinToString(",", prefix = ""","combined":{"members":[""", postfix = """],"sources":[]}""") { "\"${uri(it)}\"" }
                .orEmpty()
        return AtJson.parseToJsonElement(
                """{"sport":"ride","startedAt":"$start","elapsedTime":${minutes * 60},"createdAt":"$start"$combined}"""
            )
            .jsonObject
    }

    /** The AppView's view of [rkey]: its members, and the combined activity it's part of. */
    private fun view(rkey: String): String {
        val record = records.getValue(rkey)
        val combinedInto =
            records.entries.firstOrNull { (_, r) -> uri(rkey) in r.membersOf() }?.let { (key, _) -> ""","combinedInto":"${uri(key)}"""" }
        val members =
            record
                .membersOf()
                .takeIf { it.isNotEmpty() }
                ?.joinToString(",", prefix = ""","members":[""", postfix = "]") { m ->
                    val r = AtJson.decodeFromJsonElement(ActivityRecord.serializer(), records.getValue(m.substringAfterLast('/')))
                    """{"uri":"$m","startedAt":"${r.startedAt}","elapsedTime":${r.elapsedTime}}"""
                }
        return """{"uri":"${uri(rkey)}","cid":"bafy$rkey","author":$aliceProfile,"record":$record,""" +
            """"likeCount":0,"commentCount":0,"indexedAt":"2026-10-14T12:00:00Z","viewer":{}${combinedInto.orEmpty()}${members.orEmpty()}}"""
    }

    private fun JsonObject.membersOf(): List<String> =
        AtJson.decodeFromJsonElement(ActivityRecord.serializer(), this).combined?.members.orEmpty().map { it.value }

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                fun notFound() = respond("""{"error":"NotFound"}""", HttpStatusCode.BadRequest, json)
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)

                    "/xrpc/${Nsid.FEED_GET_ACTIVITY}" -> {
                        val rkey = request.url.parameters["uri"]!!.substringAfterLast('/')
                        if (rkey in records) respond("""{"activity":${view(rkey)},"comments":[]}""", headers = json) else notFound()
                    }

                    "/xrpc/${Nsid.FEED_GET_AUTHOR_ACTIVITIES_BY_START}" -> {
                        if (failByStart) {
                            respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        } else {
                            respond("""{"activities":[${records.keys.sorted().joinToString(",") { view(it) }}]}""", headers = json)
                        }
                    }

                    "/xrpc/com.atproto.repo.getRecord" -> {
                        if (failReads)
                            return@MockEngine respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        val rkey = request.url.parameters["rkey"]!!
                        val record = records[rkey]?.takeIf { request.url.parameters["collection"] == Nsid.FEED_ACTIVITY }
                        if (record == null) respond("""{"error":"RecordNotFound"}""", HttpStatusCode.BadRequest, json)
                        else respond("""{"uri":"${uri(rkey)}","cid":"bafy$rkey","value":$record}""", headers = json)
                    }

                    "/xrpc/com.atproto.repo.putRecord" -> {
                        gate.await()
                        val body =
                            AtJson.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                                .jsonObject
                        val collection = body.getValue("collection").jsonPrimitive.content
                        val rkey = body.getValue("rkey").jsonPrimitive.content
                        writes += "put $collection/$rkey"
                        if (collection == Nsid.FEED_ACTIVITY) records[rkey] = body.getValue("record").jsonObject
                        respond("""{"uri":"${uri(rkey)}","cid":"bafyrec"}""", headers = json)
                    }

                    "/xrpc/com.atproto.repo.deleteRecord" -> {
                        val body =
                            AtJson.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                                .jsonObject
                        val collection = body.getValue("collection").jsonPrimitive.content
                        val rkey = body.getValue("rkey").jsonPrimitive.content
                        writes += "delete $collection/$rkey"
                        if (collection == Nsid.FEED_ACTIVITY) records -= rkey
                        respond("{}", headers = json)
                    }

                    else -> notFound()
                }
            }
        ) {
            expectSuccess = false
        }

    private val localLikes = LocalLikes()

    private class Graph(
        val feed: FeedRepository,
        val activities: ActivityRepository,
        val profiles: ProfileRepository,
        val manager: ActivityManager,
        val exporter: ActivityExporter,
    )

    /** A session that can write to the repo only, so the PDS here is just Alice's repo. */
    private suspend fun graph(): Graph {
        val (db, _, clients) = signedInTestSession(http, did = alice, scope = "atproto transition:generic")
        val feed = FeedRepository(db, clients, localLikes, Logger)
        val zones = TestZones(db, clients)
        val (outbox, writer, streams) = Triple(zones.outbox, zones.writer, zones.streams)
        val tracks = zones.tracks
        val privacy = PrivacyZones(clients, Clock.System, Logger)
        val places = ActivityPlaces(clients, Clock.System, Logger)
        val activities =
            ActivityRepository(
                db,
                clients,
                outbox,
                writer,
                streams,
                tracks,
                zones.sources,
                zones.devices,
                zones.store,
                places,
                privacy,
                RouteTrimmer(db, clients, writer, outbox, privacy, tracks, feed, Clock.System, Logger),
                feed,
                localLikes,
                RecentActivityTypes(db, clients, feed),
                Clock.System,
            )
        val manager =
            ActivityManager(
                db,
                clients,
                outbox,
                writer,
                streams,
                tracks,
                zones.sources,
                zones.devices,
                zones.store,
                places,
                feed,
                privacy,
                Clock.System,
                activities,
            )
        return Graph(
            feed,
            activities,
            ProfileRepository(clients, Clock.System, Logger, zones.store),
            manager,
            ActivityExporter(db, clients, tracks, zones.sources, feed),
        )
    }

    private suspend fun picker(uri: String, navigator: FakeNavigator): @Composable () -> CombinePickerState {
        val g = graph()
        return { CombinePickerPresenter(CombinePickerScreen(uri), navigator, g.feed, g.manager) }
    }

    private suspend fun detail(uri: String, navigator: FakeNavigator): @Composable () -> ActivityDetailState {
        val g = graph()
        val units = UnitsRepository(g.profiles, Clock.System, Logger, UnitSystem.Metric)
        return {
            ActivityDetailPresenter(
                ActivityDetailScreen(uri),
                navigator,
                g.feed,
                g.activities,
                g.profiles,
                localLikes,
                g.manager,
                g.exporter,
                units,
            )
        }
    }

    private fun day() {
        records["3kwatch"] = ride("2026-10-14T09:00:00Z")
        records["3kedge"] = ride("2026-10-14T08:58:00Z", minutes = 63)
        records["3kevening"] = ride("2026-10-14T18:00:00Z")
        records["3kother"] = ride("2026-10-14T06:00:00Z")
        records["3kday"] = ride("2026-10-14T06:00:00Z", members = listOf("3kother", "3kmorning"))
        records["3kmorning"] = ride("2026-10-14T06:30:00Z")
    }

    @Test
    fun thePickerOffersOnlyWhatCanBeCombined() = runTest {
        day()
        presenterTestOf(picker(uri("3kwatch"), FakeNavigator(CombinePickerScreen(uri("3kwatch"))))) {
            val state = awaitUntil { it.candidates != null }
            // Not itself, not a combined activity, not another one's member; oldest first.
            assertEquals(listOf("3kedge", "3kevening"), state.candidates?.map { it.uri.rkey })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedLoadShowsAnErrorAndRetries() = runTest {
        day()
        failByStart = true
        presenterTestOf(picker(uri("3kwatch"), FakeNavigator(CombinePickerScreen(uri("3kwatch"))))) {
            val failed = awaitUntil { it.error != null }
            assertEquals(UiError.Kind.LoadCombineCandidates, failed.error?.kind)
            failByStart = false
            failed.eventSink(CombinePickerEvent.Retry)
            assertEquals(2, awaitUntil { it.candidates != null && it.error == null }.candidates?.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun pickingCombinesOnceAndOpensTheCombinedActivity() = runTest {
        day()
        gate = CompletableDeferred()
        val navigator = FakeNavigator(ActivityDetailScreen(uri("3kwatch")), CombinePickerScreen(uri("3kwatch")))
        presenterTestOf(picker(uri("3kwatch"), navigator)) {
            val loaded = awaitUntil { it.candidates != null }
            loaded.eventSink(CombinePickerEvent.Pick(AtUri(uri("3kedge"))))
            awaitUntil { it.combining != null }
            loaded.eventSink(CombinePickerEvent.Pick(AtUri(uri("3kedge"))))
            gate.complete(Unit)

            navigator.awaitPop()
            navigator.awaitPop()
            val opened = assertIs<ActivityDetailScreen>(navigator.awaitNextScreen())
            val combined = opened.uri.substringAfterLast('/')
            assertEquals(setOf(uri("3kwatch"), uri("3kedge")), records.getValue(combined).membersOf().toSet())
            assertEquals(1, writes.count { it == "put ${Nsid.FEED_ACTIVITY}/$combined" }, "a second tap does nothing")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedCombineShowsAnError() = runTest {
        day()
        // The members can't be read from the PDS to build it from.
        failReads = true
        presenterTestOf(picker(uri("3kwatch"), FakeNavigator(CombinePickerScreen(uri("3kwatch"))))) {
            awaitUntil { it.candidates != null }.eventSink(CombinePickerEvent.Pick(AtUri(uri("3kedge"))))
            assertEquals(UiError.Kind.CombineActivity, awaitUntil { it.error != null }.error?.kind)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theCombineActionOpensThePicker() = runTest {
        day()
        val navigator = FakeNavigator(ActivityDetailScreen(uri("3kwatch")))
        presenterTestOf(detail(uri("3kwatch"), navigator)) {
            val loaded = awaitLoaded()
            assertTrue(loaded.manage?.canCombine == true)
            loaded.eventSink(ActivityDetailEvent.Combine)
            assertEquals(CombinePickerScreen(uri("3kwatch")), navigator.awaitNextScreen())
            loaded.eventSink(ActivityDetailEvent.AddRecording)
            assertEquals(ImportActivityScreen(combineInto = uri("3kwatch")), navigator.awaitNextScreen(), "a file to combine with it")
            cancelAndIgnoreRemainingEvents()
        }
        presenterTestOf(detail(uri("3kother"), FakeNavigator(ActivityDetailScreen(uri("3kother"))))) {
            assertFalse(awaitLoaded().manage?.canCombine == true, "a member is combined through its combined activity")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun splittingLeavesThePage() = runTest {
        day()
        val navigator = FakeNavigator(ActivityDetailScreen(uri("3kday")))
        presenterTestOf(detail(uri("3kday"), navigator)) {
            val loaded = awaitLoaded()
            assertEquals(2, loaded.manage?.members)
            loaded.eventSink(ActivityDetailEvent.Split)
            navigator.awaitPop()
            assertTrue("delete ${Nsid.FEED_ACTIVITY}/3kday" in writes)
            assertTrue("3kother" in records && "3kmorning" in records, "members stay")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun removingAMemberRebuildsOrSplits() = runTest {
        day()
        records["3knoon"] = ride("2026-10-14T06:10:00Z", minutes = 40)
        records["3kday"] = ride("2026-10-14T06:00:00Z", members = listOf("3kother", "3kmorning", "3knoon"))
        val navigator = FakeNavigator(ActivityDetailScreen(uri("3kday")))
        presenterTestOf(detail(uri("3kday"), navigator)) {
            awaitLoaded().eventSink(ActivityDetailEvent.RemoveMember(AtUri(uri("3knoon"))))
            val rebuilt = awaitUntil { (it as? ActivityDetailState.Loaded)?.manage?.members == 2 }
            assertEquals(listOf(uri("3kother"), uri("3kmorning")), records.getValue("3kday").membersOf())

            rebuilt.eventSink(ActivityDetailEvent.RemoveMember(AtUri(uri("3kmorning"))))
            navigator.awaitPop()
            assertFalse("3kday" in records, "one member left: split")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun ReceiveTurbine<ActivityDetailState>.awaitLoaded() =
        awaitUntil { it is ActivityDetailState.Loaded } as ActivityDetailState.Loaded
}
