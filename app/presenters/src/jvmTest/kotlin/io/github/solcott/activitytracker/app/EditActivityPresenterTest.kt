package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withCompositionLocals
import app.cash.turbine.ReceiveTurbine
import co.touchlab.kermit.Logger
import com.slack.circuit.retained.LocalRetainedStateRegistry
import com.slack.circuit.retained.RetainedStateRegistry
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.edit.DetailsEvent
import io.github.solcott.activitytracker.app.edit.EditActivityEvent
import io.github.solcott.activitytracker.app.edit.EditActivityPresenter
import io.github.solcott.activitytracker.app.edit.EditActivityScreen
import io.github.solcott.activitytracker.app.edit.EditActivityState
import io.github.solcott.activitytracker.app.edit.EditMedia
import io.github.solcott.activitytracker.app.edit.PickedPhoto
import io.github.solcott.activitytracker.app.visibility.VisibilityEvent
import io.github.solcott.activitytracker.data.activity.ActivityDefaults
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.ActivityPlaces
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.activity.RouteTrimmer
import io.github.solcott.activitytracker.data.activity.cid
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.errorOrNull
import io.github.solcott.uistate.hasLoaded
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class EditActivityPresenterTest {
    private val alice = Did("did:plc:alice")
    private val uri = "at://${alice.value}/${Nsid.FEED_ACTIVITY}/3kdive"

    private val record =
        """{"sport":"dive","startedAt":"2026-09-30T10:00:00Z","elapsedTime":1800,"createdAt":"2026-09-30T10:31:00Z",""" +
            """"title":"Blue Hole","description":"Sharks","dive":{"maxDepth":3000,"site":"Dahab"},""" +
            """"media":[${photo("bafkone")},${photo("bafktwo")}]}"""

    private fun photo(cid: String) =
        """{"${'$'}type":"app.activitytracker.feed.activity#image","image":{"${'$'}type":"blob","ref":{"${'$'}link":"$cid"},""" +
            """"mimeType":"image/webp","size":10},"aspectRatio":{"width":4,"height":3}}"""

    /** Where the activity is stored, as the AppView serves it. */
    private var visibility = Visibility.PUBLIC

    /** What the AppView appends to each photo URL: a non-public activity's are signed for the viewer. */
    private var photoQuery = ""

    private val view
        get() =
            """{"uri":"$uri","cid":"bafyact","author":{"did":"${alice.value}","handle":"alice.test"},"record":$record,""" +
                """"visibility":"$visibility",""" +
                """"likeCount":0,"commentCount":0,"indexedAt":"2026-09-30T10:31:01Z","viewer":{},"media":[""" +
                listOf("bafkone", "bafktwo").joinToString(",") {
                    """{"${'$'}type":"app.activitytracker.feed.defs#imageView",""" +
                        """"fullsize":"https://appview/img/media/${alice.value}/$it$photoQuery"}"""
                } +
                "]}"

    /** From before detail categories: one streams record, in the `self` space, holding the depth and temperature charts. */
    private var oldStreams = false
    private val oldData = """{"time":[0,1],"depth":[100,200],"temperature":[200,210]}"""

    /** Public streams the AppView serves with these channels, when set. */
    private var servedChannels: List<String>? = null

    /** The places holding the activity record: where [visibility] says, until a save moves it. */
    private val held = mutableSetOf<String>()
    private var heldSeeded = false

    private fun held(): MutableSet<String> {
        if (!heldSeeded) held += if (visibility == Visibility.PUBLIC) "repo" else visibility
        heldSeeded = true
        return held
    }

    private fun MockRequestHandleScope.activityAt(place: String, collection: String?) =
        if (collection == Nsid.FEED_ACTIVITY && place in held()) {
            respond("""{"uri":"$uri","cid":"bafyact","value":$record}""", headers = headersOf("Content-Type", "application/json"))
        } else {
            respond("""{"error":"RecordNotFound"}""", HttpStatusCode.BadRequest, headersOf("Content-Type", "application/json"))
        }

    /** Segment matching is on, and the activity has a segment track in `self`, excluded from leaderboards when [trackExcluded]. */
    private var segmentTrack = false
    private var trackExcluded = false

    private lateinit var db: AppDatabase

    /** Every upload and write the PDS took, in order. */
    private val writes = mutableListOf<String>()
    private val putRecords = mutableListOf<ActivityRecord>()
    private var failUpload = false

    /** `getActivity` fails as a dropped connection would, while set. */
    private var activityOffline = false

    /** How many times the AppView was asked for the activity. */
    private var activityReads = 0

    /** Holds each `putRecord` until completed, so the test sees the save in flight. */
    private var gate = CompletableDeferred(Unit)

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)

                    "/xrpc/${Nsid.FEED_GET_ACTIVITY}" -> {
                        if (activityOffline) throw IOException("Connection refused")
                        activityReads++
                        respond("""{"activity":$view,"comments":[]}""", headers = json)
                    }

                    "/xrpc/com.atproto.repo.getRecord" -> activityAt("repo", request.url.parameters["collection"])

                    "/xrpc/com.atproto.repo.uploadBlob" ->
                        if (failUpload) {
                            respond("""{"error":"PayloadTooLarge"}""", HttpStatusCode.PayloadTooLarge, json)
                        } else {
                            writes += "upload"
                            respond(
                                """{"blob":{"${'$'}type":"blob","ref":{"${'$'}link":"bafknew"},"mimeType":"image/webp","size":3}}""",
                                headers = json,
                            )
                        }

                    "/xrpc/com.atproto.space.getRecord" -> {
                        val place = request.url.parameters["space"].orEmpty().substringAfterLast('/')
                        val collection = request.url.parameters["collection"]
                        if (segmentTrack && place == Visibility.SELF && collection == Nsid.ACTOR_SEGMENT_PREFS) {
                            respond(
                                """{"uri":"$uri","cid":"bafyprefs","value":{"matching":true,"updatedAt":"2026-10-01T00:00:00Z"}}""",
                                headers = json,
                            )
                        } else if (segmentTrack && place == Visibility.SELF && collection == Nsid.FEED_SEGMENT_TRACK) {
                            val track =
                                """{"activity":"$uri","createdAt":"2026-09-30T10:31:00Z",""" +
                                    (if (trackExcluded) """"excludeFromLeaderboards":true,""" else "") +
                                    """"data":{"${'$'}type":"blob","ref":{"${'$'}link":"bafktrack"},"mimeType":"application/json","size":9}}"""
                            respond("""{"uri":"$uri","cid":"bafytrk","value":$track}""", headers = json)
                        } else if (
                            oldStreams && place == Visibility.SELF && request.url.parameters["collection"] == Nsid.FEED_ACTIVITY_STREAMS
                        ) {
                            val streams =
                                """{"activity":"$uri","channels":["depth","temperature"],"createdAt":"2026-09-30T10:31:00Z",""" +
                                    """"data":{"${'$'}type":"blob","ref":{"${'$'}link":"bafkstreams"},"mimeType":"application/json","size":9}}"""
                            respond("""{"uri":"$uri","cid":"bafystr","value":$streams}""", headers = json)
                        } else {
                            activityAt(place, request.url.parameters["collection"])
                        }
                    }

                    "/xrpc/com.atproto.space.getBlob" ->
                        if (oldStreams && request.url.parameters["cid"] == "bafkstreams") respond(oldData, headers = json)
                        else respond("""{"error":"BlobNotFound"}""", HttpStatusCode.BadRequest, json)

                    "/xrpc/${Nsid.FEED_GET_ACTIVITY_STREAMS}" ->
                        if (servedChannels != null) {
                            val channels = servedChannels.orEmpty()
                            val data = channels.joinToString(",", prefix = """{"time":[0,1],""", postfix = "}") { """"$it":[1,2]""" }
                            respond(
                                """{"uri":"$uri","visibility":"public","channels":${channels.map { "\"$it\"" }},"data":$data}""",
                                headers = json,
                            )
                        } else if (oldStreams) {
                            respond(
                                """{"uri":"$uri","visibility":"self","channels":["depth","temperature"],"data":$oldData}""",
                                headers = json,
                            )
                        } else {
                            respond("""{"error":"NotFound"}""", HttpStatusCode.BadRequest, json)
                        }

                    "/.well-known/did.json" ->
                        respond(
                            """{"id":"did:web:appview.test","service":[
                               {"id":"#activitytracker_space","type":"AtprotoSpaceService","serviceEndpoint":"http://appview.test"}]}""",
                            headers = json,
                        )

                    "/xrpc/com.atproto.simplespace.createSpace" -> {
                        val tier = AtJson.parseToJsonElement(body(request)).jsonObject.getValue("skey").jsonPrimitive.content
                        respond("""{"uri":"at://${alice.value}/space/${Nsid.SPACE_ATHLETE}/$tier"}""", headers = json)
                    }

                    "/xrpc/${Nsid.SPACE_NOTIFY_SPACE}" -> respond("{}", headers = json)

                    "/xrpc/com.atproto.space.putRecord",
                    "/xrpc/com.atproto.space.deleteRecord",
                    "/xrpc/com.atproto.repo.deleteRecord" -> {
                        val input = AtJson.parseToJsonElement(body(request)).jsonObject
                        val place = input["space"]?.jsonPrimitive?.content?.substringAfterLast('/') ?: "repo"
                        if (input["collection"]?.jsonPrimitive?.content == Nsid.FEED_ACTIVITY) {
                            if (input["record"] != null) held() += place else held() -= place
                        }
                        writes += (if (input["record"] != null) "put " else "delete ") + place
                        respond("""{"uri":"$uri","cid":"bafyrec"}""", headers = json)
                    }

                    "/xrpc/com.atproto.repo.putRecord" -> {
                        gate.await()
                        val body = AtJson.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                        val collection = body.jsonObject["collection"]?.jsonPrimitive?.content
                        if (collection == Nsid.FEED_ACTIVITY) {
                            putRecords += AtJson.decodeFromJsonElement(ActivityRecord.serializer(), body.jsonObject.getValue("record"))
                            held() += "repo"
                            writes += "put"
                        } else {
                            writes += "put repo $collection"
                        }
                        respond("""{"uri":"$uri","cid":"bafyrec"}""", headers = json)
                    }

                    else -> respond("""{"error":"NotFound"}""", HttpStatusCode.NotFound, json)
                }
            }
        ) {
            expectSuccess = false
        }

    private fun body(request: io.ktor.client.request.HttpRequestData) =
        (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private val picked = PickedPhoto(ByteString(1, 2, 3), "image/webp", width = 3, height = 4)

    private suspend fun presenter(navigator: FakeNavigator): @Composable () -> EditActivityState {
        val (db, _, clients) = signedInTestSession(http, did = alice)
        this.db = db
        val feed = FeedRepository(db, clients, LocalLikes(), Logger)
        val zones = TestZones(db, clients)
        val privacy = PrivacyZones(clients, Clock.System, Logger)
        val places = ActivityPlaces(clients, Clock.System, Logger)
        val activities =
            ActivityRepository(
                db,
                clients,
                zones.outbox,
                zones.writer,
                zones.streams,
                zones.tracks,
                zones.sources,
                zones.devices,
                zones.store,
                places,
                privacy,
                RouteTrimmer(db, clients, zones.writer, zones.outbox, privacy, zones.tracks, feed, Clock.System, Logger),
                feed,
                LocalLikes(),
                RecentActivityTypes(db, clients, feed),
                Clock.System,
                testSegments(db, clients, zones.writer, zones.outbox, privacy, zones.tracks).tracks,
                testSegments(db, clients, zones.writer, zones.outbox, privacy, zones.tracks).sweeper,
            )
        val manager =
            ActivityManager(
                db,
                clients,
                zones.outbox,
                zones.writer,
                zones.streams,
                zones.tracks,
                zones.sources,
                zones.devices,
                zones.store,
                places,
                feed,
                privacy,
                Clock.System,
                activities,
                testSegments(db, clients, zones.writer, zones.outbox, privacy, zones.tracks).tracks,
            )
        val defaults = ActivityDefaults(clients)
        val profiles = ProfileRepository(clients, Clock.System, Logger, zones.store)
        val segments = testSegments(db, clients, zones.writer, zones.outbox, privacy, zones.tracks)
        return {
            EditActivityPresenter(EditActivityScreen(uri), navigator, feed, manager, defaults, profiles, segments.prefs, segments.store)
        }
    }

    /** A stand-in for the screen while it's being recreated. */
    private val hidden =
        EditActivityState(
            content = ContentState(Unit),
            type = null,
            title = TextFieldState(),
            details = null,
            saving = false,
            error = null,
            confirmDiscard = false,
            eventSink = {},
        )

    private suspend fun ReceiveTurbine<EditActivityState>.awaitLoaded() = awaitUntil { it.details != null }

    @Test
    fun seedsTheFieldsAndSavesWhatChangedKeepingTheRest() = runTest {
        gate = CompletableDeferred()
        val navigator = FakeNavigator(EditActivityScreen(uri))
        presenterTestOf(presenter(navigator)) {
            val loaded = awaitLoaded()
            val details = assertNotNull(loaded.details)
            assertEquals("Blue Hole", loaded.title.text.toString())
            assertEquals("Sharks", details.description.text.toString())
            assertEquals("Dahab", details.diveSite?.text.toString())
            assertEquals(listOf("bafkone", "bafktwo"), details.media.map { (it as EditMedia.Existing).cid })

            details.eventSink(DetailsEvent.RemoveMedia(0))
            details.eventSink(DetailsEvent.PhotosPicked(persistentListOf(picked)))
            details.description.setTextAndPlaceCursorAtEnd("Sharks and turtles")
            val edited = awaitUntil { it.details?.media?.lastOrNull() is EditMedia.Picked }
            edited.eventSink(EditActivityEvent.Save)
            awaitUntil { it.saving }
            edited.eventSink(EditActivityEvent.Save)

            gate.complete(Unit)
            navigator.awaitPop()
            assertEquals(listOf("upload", "put"), writes, "a second tap while saving does nothing")
            val saved = putRecords.single()
            assertEquals("Sharks and turtles", saved.description)
            assertEquals("Dahab", saved.dive?.site)
            assertEquals(3000, saved.dive?.maxDepth)
            assertEquals(listOf("bafktwo", "bafknew"), saved.media.orEmpty().map { it.cid })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedLoadSaysWhyAndRetryFillsTheForm() = runTest {
        activityOffline = true
        presenterTestOf(presenter(FakeNavigator(EditActivityScreen(uri)))) {
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertEquals(DataError.Network, failed.content.errorOrNull)
            assertNull(failed.details)
            assertNull(failed.error, "a load failure isn't the save's")

            activityOffline = false
            failed.eventSink(EditActivityEvent.Retry)
            val loaded = awaitLoaded()
            assertTrue(loaded.content.hasLoaded)
            assertEquals("Blue Hole", loaded.title.text.toString())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun streamsThatCantBeReadStillLoadTheForm() = runTest {
        // With no channels served, getActivityStreams answers NotFound.
        presenterTestOf(presenter(FakeNavigator(EditActivityScreen(uri)))) {
            val loaded = awaitLoaded()
            assertTrue(loaded.content.hasLoaded)
            assertNull(loaded.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRecreatedScreenKeepsTheTypedTitleThroughItsReload() = runTest {
        val retained = RetainedStateRegistry()
        var saveable by mutableStateOf(SaveableStateRegistry(null) { true })
        var shown by mutableStateOf(true)
        val present = presenter(FakeNavigator(EditActivityScreen(uri)))
        presenterTestOf({
            withCompositionLocals(LocalRetainedStateRegistry provides retained, LocalSaveableStateRegistry provides saveable) {
                if (shown) present() else hidden
            }
        }) {
            val loaded = awaitLoaded()
            loaded.title.setTextAndPlaceCursorAtEnd("Blue Hole at dawn")
            val reads = activityReads

            // What an Android configuration change does: saved state is restored into a new registry, retained state is kept.
            val saved = saveable.performSave()
            retained.saveAll()
            shown = false
            awaitUntil { it === hidden }
            saveable = SaveableStateRegistry(saved) { true }
            shown = true

            val back = awaitLoaded()
            assertEquals("Blue Hole at dawn", back.title.text.toString())
            assertEquals("Sharks", back.details?.description?.text.toString(), "the details are filled again from the activity as loaded")
            // Coming back restarts the load; its answer leaves the form alone.
            awaitSettled { activityReads > reads }
            assertEquals("Blue Hole at dawn", mostRecentItemOr(back).title.text.toString())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedUploadKeepsTheScreenOpenWithTheError() = runTest {
        failUpload = true
        val navigator = FakeNavigator(EditActivityScreen(uri))
        presenterTestOf(presenter(navigator)) {
            val details = assertNotNull(awaitLoaded().details)
            details.eventSink(DetailsEvent.PhotosPicked(persistentListOf(picked)))
            awaitUntil { it.details?.media?.size == 3 }.eventSink(EditActivityEvent.Save)
            val failed = awaitUntil { it.error != null && !it.saving }
            assertEquals(UiError.Kind.SaveActivity, failed.error?.kind)
            assertTrue(putRecords.isEmpty())
            navigator.assertPopIsEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun backAsksBeforeDroppingChanges() = runTest {
        val navigator = FakeNavigator(EditActivityScreen(uri))
        presenterTestOf(presenter(navigator)) {
            val loaded = awaitLoaded()
            loaded.title.setTextAndPlaceCursorAtEnd("Renamed")
            loaded.eventSink(EditActivityEvent.Back)
            val asking = awaitUntil { it.confirmDiscard }
            navigator.assertPopIsEmpty()
            asking.eventSink(EditActivityEvent.Discard)
            assertIs<FakeNavigator.PopEvent>(navigator.awaitPop())
            assertNull(putRecords.firstOrNull())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun narrowingAPublicActivityWarnsBeforeSaving() = runTest {
        val navigator = FakeNavigator(EditActivityScreen(uri))
        presenterTestOf(presenter(navigator)) {
            val loaded = awaitUntil { it.visibility != null }
            assertEquals(Visibility.PUBLIC, loaded.visibility?.activity)
            loaded.visibility!!.eventSink(VisibilityEvent.ActivityChanged(Visibility.SELF))
            awaitUntil { it.visibility?.activity == Visibility.SELF }.eventSink(EditActivityEvent.Save)
            val warned = awaitUntil { it.confirmNarrowing }
            assertEquals(emptyList(), writes, "nothing saved before the athlete confirms")

            warned.eventSink(EditActivityEvent.CancelNarrowing)
            val back = awaitUntil { !it.confirmNarrowing }
            assertEquals(Visibility.SELF, back.visibility?.activity, "the choice stays")
            back.eventSink(EditActivityEvent.Save)
            awaitUntil { it.confirmNarrowing }.eventSink(EditActivityEvent.ConfirmNarrowing)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        // The edit, then the move: into the self space before out of the repo.
        assertEquals(listOf("put", "put self", "delete repo"), writes)
    }

    @Test
    fun cadenceWithoutRunningFormIsJustCadence() = runTest {
        servedChannels = listOf("cadence")
        presenterTestOf(presenter(FakeNavigator(EditActivityScreen(uri)))) {
            val loaded = awaitUntil { it.visibility != null }
            assertEquals(Visibility.PUBLIC, loaded.visibility?.tierOf(DetailCategory.CADENCE))
            assertEquals(false, loaded.visibility?.runningForm)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stanceTimeIsRunningForm() = runTest {
        servedChannels = listOf("cadence", "stanceTime")
        presenterTestOf(presenter(FakeNavigator(EditActivityScreen(uri)))) {
            assertEquals(true, awaitUntil { it.visibility != null }.visibility?.runningForm)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theFirstEditOfAnOldActivityWarnsBeforeWideningItsCoreCharts() = runTest {
        oldStreams = true
        val navigator = FakeNavigator(EditActivityScreen(uri))
        presenterTestOf(presenter(navigator)) {
            val loaded = awaitUntil { it.visibility != null }
            // Each category shows where its data is served now: the dive's channels Only me, the rest Public.
            assertEquals(Visibility.SELF, loaded.visibility?.tierOf(DetailCategory.DIVE))
            assertEquals(Visibility.PUBLIC, loaded.visibility?.tierOf(DetailCategory.START_TIME))
            assertEquals(null, loaded.visibility?.tierOf(DetailCategory.HEART_RATE), "no data, not offered")

            loaded.visibility!!.eventSink(VisibilityEvent.CategoryChanged(DetailCategory.START_TIME, Visibility.SELF))
            awaitUntil { it.visibility?.tierOf(DetailCategory.START_TIME) == Visibility.SELF }.eventSink(EditActivityEvent.Save)
            val warned = awaitUntil { it.confirmCoreCharts != null }
            assertEquals(Visibility.PUBLIC, warned.confirmCoreCharts)
            assertEquals(emptyList(), writes, "nothing saved before the athlete confirms")

            warned.eventSink(EditActivityEvent.CancelCoreCharts)
            awaitUntil { it.confirmCoreCharts == null }.eventSink(EditActivityEvent.Save)
            awaitUntil { it.confirmCoreCharts != null }.eventSink(EditActivityEvent.ConfirmCoreCharts)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        // The temperature chart went with the activity to the repo, the dive's stayed in self, with its exact start.
        assertTrue("put repo ${Nsid.FEED_ACTIVITY_STREAMS}" in writes, writes.toString())
        assertTrue("put self" in writes, writes.toString())
    }

    @Test
    fun movingBetweenNonPublicTiersNeedsNoWarning() = runTest {
        visibility = Visibility.FOLLOWERS
        val navigator = FakeNavigator(EditActivityScreen(uri))
        presenterTestOf(presenter(navigator)) {
            awaitUntil { it.visibility != null }.visibility!!.eventSink(VisibilityEvent.ActivityChanged(Visibility.MUTUALS))
            val mutuals = awaitUntil { it.visibility?.activity == Visibility.MUTUALS }
            assertEquals(
                persistentListOf(Visibility.MUTUALS, Visibility.SELF),
                mutuals.visibility?.categoryOptions,
                "wider categories not offered",
            )
            mutuals.eventSink(EditActivityEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("put followers", "put mutuals", "delete followers"), writes)
    }

    @Test
    fun aNonPublicActivityKeepsItsPhotosWhoseUrlsAreSigned() = runTest {
        visibility = Visibility.FOLLOWERS
        photoQuery = "?viewer=did%3Aplc%3Aalice&exp=1&sig=abc"
        val navigator = FakeNavigator(EditActivityScreen(uri))
        presenterTestOf(presenter(navigator)) {
            val details = assertNotNull(awaitLoaded().details)
            assertEquals(listOf("bafkone", "bafktwo"), details.media.map { (it as EditMedia.Existing).cid })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anUnchangedVisibilityMovesNothing() = runTest {
        val navigator = FakeNavigator(EditActivityScreen(uri))
        presenterTestOf(presenter(navigator)) {
            awaitUntil { it.visibility != null }.eventSink(EditActivityEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("put"), writes)
    }

    /** The segment-track writes still queued, as `excludeFromLeaderboards` values (null when absent). */
    private suspend fun queuedExclusions(): List<String?> =
        db.outbox()
            .pending(alice.value)
            .filter { it.collection == Nsid.FEED_SEGMENT_TRACK }
            .map { AtJson.parseToJsonElement(it.recordJson!!).jsonObject["excludeFromLeaderboards"]?.jsonPrimitive?.content }

    @Test
    fun anActivityWithoutASegmentTrackIsntOfferedTheExclusion() = runTest {
        presenterTestOf(presenter(FakeNavigator(EditActivityScreen(uri)))) {
            val loaded = awaitLoaded()
            assertNull(loaded.leaderboardExclusion)
            loaded.eventSink(EditActivityEvent.LeaderboardExclusionChanged(true))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theExclusionIsShownAndOnlyAChangeIsWritten() = runTest {
        segmentTrack = true
        val navigator = FakeNavigator(EditActivityScreen(uri))
        presenterTestOf(presenter(navigator)) {
            val loaded = awaitUntil { it.details != null && it.leaderboardExclusion != null }
            assertEquals(false, loaded.leaderboardExclusion)
            loaded.eventSink(EditActivityEvent.LeaderboardExclusionChanged(true))
            awaitUntil { it.leaderboardExclusion == true }.eventSink(EditActivityEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf<String?>("true"), queuedExclusions())
    }

    @Test
    fun anUnchangedExclusionWritesNothing() = runTest {
        segmentTrack = true
        trackExcluded = true
        val navigator = FakeNavigator(EditActivityScreen(uri))
        presenterTestOf(presenter(navigator)) {
            val loaded = awaitUntil { it.details != null && it.leaderboardExclusion != null }
            assertEquals(true, loaded.leaderboardExclusion)
            loaded.eventSink(EditActivityEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(emptyList(), queuedExclusions())
    }
}
