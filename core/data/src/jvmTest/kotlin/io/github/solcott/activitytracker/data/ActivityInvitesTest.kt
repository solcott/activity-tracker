package io.github.solcott.activitytracker.data

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.activity.PrivacySettings
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.data.db.UploadItemEntity
import io.github.solcott.activitytracker.data.invites.ActivityInvitesRepository
import io.github.solcott.activitytracker.data.invites.Incoming
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityInviteDeclineRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityInviteRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GetActivityInvite
import io.github.solcott.activitytracker.lexicon.app.InviteView
import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.PrivacyZone
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.TrackMath
import io.github.solcott.dataresult.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive

/**
 * Activity invites on the client (feed/activity-invites): adding and cancelling, the inbox and its declines, and the copy accepting makes.
 */
class ActivityInvitesTest {
    private val pds = FakeAthletePds()
    private val bob = Did("did:plc:bob")
    private val carol = Did("did:plc:carol")
    private val ride = AtUri.of(pds.did, Nsid.FEED_ACTIVITY, "3l2abcdefgh22")
    private val start = Instant.parse("2026-10-14T07:00:00Z")

    /** Carol's ride, as Bob's invite to it serves it: a route along the equator, with what isn't granted already left out. */
    private val carolsRide = AtUri.of(carol, Nsid.FEED_ACTIVITY, "3l2carolride2")
    private val carolsInvite = AtUri.of(carol, Nsid.FEED_ACTIVITY_INVITE, ActivityInviteRecord.rkey("3l2carolride2", pds.did))
    private val route = (0..30).map { 0.0 to it * 0.001 }

    private fun inviteView(uri: AtUri = carolsInvite) =
        InviteView(
            uri = uri,
            activity = carolsRide,
            inviter = ProfileViewBasic(carol, Handle("carol.test")),
            record =
                ActivityRecord(
                    sport = Sport.RIDE,
                    startedAt = start,
                    elapsedTime = 3600,
                    createdAt = start,
                    title = "Morning ride",
                    distance = 3336,
                    elevationGain = 12,
                    maxSpeed = 9000,
                    utcOffset = 0,
                    polyline = TrackMath.encodePolyline(route.map { (lat, lon) -> sample(lat, lon) }),
                ),
            createdAt = start,
        )

    /**
     * The first inbox that loaded and matches [predicate]. The inbox `combine`s the read with this session's answers, which keeps only the
     * latest of each, so its loading state may never show.
     */
    private suspend fun ReceiveTurbine<Outcome<Incoming>>.awaitIncoming(predicate: (Incoming) -> Boolean = { true }): Incoming =
        (awaitUntil { it is Outcome.Data && predicate(it.data) } as Outcome.Data).data

    private fun sample(lat: Double, lon: Double) = LocationSample(lat, lon, null, null, null, start)

    private fun repository(s: FakeAthletePds.Session) = ActivityInvitesRepository(s.clients, s.activities, s.feed, Clock.System, Logger)

    @Test
    fun addingPutsAnInviteInSelfUnderItsKeyAndCancellingDeletesIt() = runTest {
        val s = pds.session()
        val invites = repository(s)
        invites.add(ride, ProfileViewBasic(bob, Handle("bob.test")))
        val key = "${Nsid.FEED_ACTIVITY_INVITE}/${ActivityInviteRecord.rkey("3l2abcdefgh22", bob)}"
        val record = pds.stored.getValue(Visibility.SELF).getValue(key)
        assertEquals(bob.value, record.getValue("invitee").jsonPrimitive.content)
        assertEquals(setOf(Visibility.SELF), pds.placesOf(Nsid.FEED_ACTIVITY_INVITE, ActivityInviteRecord.rkey("3l2abcdefgh22", bob)))

        invites.cancel(AtUri.of(pds.did, Nsid.FEED_ACTIVITY_INVITE, ActivityInviteRecord.rkey("3l2abcdefgh22", bob)))
        assertNull(pds.stored[Visibility.SELF]?.get(key))
    }

    @Test
    fun anOfflineAddFailsAndInvitesNoOne() = runTest {
        val s = pds.session()
        pds.offline = true
        assertTrue(runCatching { repository(s).add(ride, ProfileViewBasic(bob, Handle("bob.test"))) }.isFailure)
        pds.offline = false
        assertTrue(pds.stored[Visibility.SELF].orEmpty().keys.none { it.startsWith(Nsid.FEED_ACTIVITY_INVITE) })
    }

    @Test
    fun aGrantWithoutInvitesCantInvite() = runTest {
        val scope = "atproto transition:generic space:${Nsid.SPACE_ATHLETE}?collection=${Nsid.FEED_ACTIVITY}"
        val invites = repository(pds.session(scope = scope))
        assertFalse(invites.canInvite)
        assertFalse(invites.canDecline)
        assertTrue(repository(pds.session()).canInvite)
    }

    @Test
    fun aDeclineMovesTheInviteToDeclinedPrivately() = runTest {
        val s = pds.session()
        val invites = repository(s)
        pds.invitesServed = listOf(inviteView())
        assertEquals(listOf(carolsInvite), invites.incoming().pending.map { it.uri })

        invites.decline(carolsInvite)
        val incoming = invites.incoming()
        assertEquals(emptyList(), incoming.pending)
        assertEquals(listOf(carolsInvite), incoming.declined.map { it.uri })
        val declines = pds.stored.getValue(Visibility.SELF).filterKeys { it.startsWith(Nsid.FEED_ACTIVITY_INVITE_DECLINE) }
        assertEquals(carolsInvite, AtJson.decodeFromJsonElement(ActivityInviteDeclineRecord.serializer(), declines.values.single()).subject)
        assertTrue(pds.stored[pds.repo].orEmpty().keys.none { it.startsWith(Nsid.FEED_ACTIVITY_INVITE_DECLINE) })
    }

    @Test
    fun theCopyHoldsOnlyWhatTheInviteGrantsAndNamesTheOriginal() = runTest {
        val s = pds.session()
        val invite =
            GetActivityInvite.Output(
                inviteView(),
                data =
                    ActivityStreamData(
                        time = listOf(0, 60),
                        distance = listOf(0, 300),
                        speed = listOf(5000, 5100),
                        laps = listOf(Lap(start = 0, elapsed = 60)),
                    ),
            )
        val uri = repository(s).accept(invite, VisibilityChoice(Visibility.PUBLIC), "Ride with Carol")
        s.drain()

        val record =
            AtJson.decodeFromJsonElement(
                ActivityRecord.serializer(),
                pds.stored.getValue(pds.repo).getValue("${Nsid.FEED_ACTIVITY}/${uri.rkey}"),
            )
        assertEquals(carolsRide, record.copyOf)
        assertEquals("Ride with Carol", record.title)
        assertEquals(3336L, record.distance)
        assertEquals(start, record.startedAt)
        assertNotNull(record.polyline)
        assertNull(record.avgHeartRate)
        assertNull(record.description)
        assertNull(record.media)
        assertNull(record.gear)
        val self = pds.stored[Visibility.SELF].orEmpty().keys
        assertTrue(self.none { it.startsWith(Nsid.FEED_ACTIVITY_TRACK) || it.startsWith(Nsid.FEED_SEGMENT_TRACK) }, "no track: $self")
    }

    @Test
    fun theCopysRouteIsTrimmedByTheInviteesOwnZones() = runTest {
        val s = pds.session()
        s.privacy.save(PrivacySettings(listOf(PrivacyZone(latitudeE7 = 0, longitudeE7 = 0, radiusMeters = 1000, label = "Home"))))
        val uri = repository(s).accept(GetActivityInvite.Output(inviteView()), VisibilityChoice(Visibility.PUBLIC), null)
        s.drain()

        val record =
            AtJson.decodeFromJsonElement(
                ActivityRecord.serializer(),
                pds.stored.getValue(pds.repo).getValue("${Nsid.FEED_ACTIVITY}/${uri.rkey}"),
            )
        val first = TrackMath.decodePolyline(assertNotNull(record.polyline)).first()
        // The stored center moves up to half the radius, so at least 500 m of the start is cut.
        assertTrue(first.lng > 0.004, "the start near home is cut: $first")
    }

    @Test
    fun acceptingFromDeclinedForgetsTheDecline() = runTest {
        val s = pds.session()
        val invites = repository(s)
        pds.invitesServed = listOf(inviteView())
        invites.decline(carolsInvite)
        invites.accept(GetActivityInvite.Output(inviteView()), VisibilityChoice(Visibility.PUBLIC), null)
        assertTrue(pds.stored.getValue(Visibility.SELF).keys.none { it.startsWith(Nsid.FEED_ACTIVITY_INVITE_DECLINE) })
    }

    @Test
    fun theInboxDropsAnAcceptedInviteBeforeTheAppViewIndexesTheCopy() = runTest {
        val s = pds.session()
        val invites = repository(s)
        pds.invitesServed = listOf(inviteView())
        invites.incomingOutcome().test(FLOW_TEST_TIMEOUT) {
            assertEquals(listOf(carolsInvite), awaitIncoming().pending.map { it.uri })

            invites.accept(GetActivityInvite.Output(inviteView()), VisibilityChoice(Visibility.PUBLIC), null)
            // The AppView still lists it, but the open inbox and every later read leave it out.
            val incoming = awaitIncoming { it.pending.isEmpty() }
            assertEquals(emptyList(), incoming.pending + incoming.declined)
        }
        assertEquals(emptyList(), invites.incoming().pending)
    }

    @Test
    fun theOpenInboxMovesAnInviteDeclinedOnAnotherScreen() = runTest {
        val s = pds.session()
        val invites = repository(s)
        pds.invitesServed = listOf(inviteView())
        invites.incomingOutcome().test(FLOW_TEST_TIMEOUT) {
            assertEquals(listOf(carolsInvite), awaitIncoming().pending.map { it.uri })

            invites.decline(carolsInvite)
            val incoming = awaitIncoming { it.declined.isNotEmpty() }
            assertEquals(emptyList(), incoming.pending)
            assertEquals(listOf(carolsInvite), incoming.declined.map { it.uri })
        }
    }

    @Test
    fun leavingTheGroupOnACopyClearsCopyOfThroughTheOutbox() = runTest {
        val s = pds.session()
        val uri = repository(s).accept(GetActivityInvite.Output(inviteView()), VisibilityChoice(Visibility.PUBLIC), "Ride")
        s.drain()
        val view =
            ActivityView(
                uri = uri,
                cid = Cid("bafyrec"),
                author = ProfileViewBasic(pds.did, Handle("alice.test")),
                record = ActivityRecord(sport = Sport.RIDE, startedAt = start, elapsedTime = 3600, createdAt = start, copyOf = carolsRide),
                likeCount = 0,
                commentCount = 0,
                indexedAt = start,
            )
        s.manager.leaveCopyGroup(view)
        val record =
            AtJson.decodeFromJsonElement(
                ActivityRecord.serializer(),
                pds.stored.getValue(pds.repo).getValue("${Nsid.FEED_ACTIVITY}/${uri.rkey}"),
            )
        assertNull(record.copyOf)
        assertEquals("Ride", record.title, "the rest of the record stays")
        val items = s.db.uploads().items(pds.did.value).first().filter { it.rkey == uri.rkey }
        assertEquals(UploadItemEntity.KIND_EDIT, items.last().kind, "listed in the upload queue like any edit")
    }

    private suspend fun FakeAthletePds.Session.drain() = outbox.drain()
}
