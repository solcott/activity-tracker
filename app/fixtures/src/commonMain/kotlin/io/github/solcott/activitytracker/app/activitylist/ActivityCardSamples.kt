package io.github.solcott.activitytracker.app.activitylist

import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.fakeActivity
import io.github.solcott.activitytracker.app.fixtures.fakeActivityRecord
import io.github.solcott.activitytracker.app.fixtures.fakeGroupPartner
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityMediaView
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ActivityViewerState
import io.github.solcott.activitytracker.lexicon.app.AspectRatio
import io.github.solcott.activitytracker.lexicon.app.DetailsView
import io.github.solcott.activitytracker.lexicon.app.LabelView
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.time.Instant

/**
 * One activity card in each case it draws differently (`ActivityCardContent`), for the viewer [viewer] (Alice): her follower Bob's
 * activities and her own. Not a screen's states: the native card's previews, snapshots and text tests read them, and a page listing
 * activities builds its own samples from them.
 */
object ActivityCardSamples {
    /** Who views every card: Alice, whose own activities carry no Like. */
    val viewer: String = Fixed.ALICE.value

    private val bob = fakeProfileBasic(Fixed.BOB)
    private val dan = fakeProfileBasic(Did("did:plc:dan"), displayName = "Dan")
    private val labeler = Did("did:plc:labeler")

    private fun photo(n: Int, width: Int = 4, height: Int = 3) =
        ActivityMediaView.Image("https://cdn.example/photo$n.webp", alt = "Photo $n", aspectRatio = AspectRatio(width, height))

    private fun label(value: String) = LabelView(labeler, value)

    /** A ride whose recording stopped once part way, so its line breaks between its fourth and fifth points. */
    private val brokenRoute: String =
        TrackMath.encodePolyline(
            listOf(
                    40.015 to -105.270,
                    40.020 to -105.262,
                    40.027 to -105.258,
                    40.031 to -105.249,
                    40.036 to -105.236,
                    40.041 to -105.229,
                    40.044 to -105.218,
                )
                .map { (lat, lng) -> LocationSample(lat, lng, altitude = null, speed = null, accuracy = null, time = Fixed.STARTED_AT) },
        )

    /** Bob's titled public ride with a route, 3 likes and a comment. */
    val ride: ActivityView = fakeActivity(rkey = "3kcardride", author = bob)

    /** The same ride, liked by the viewer. */
    val liked: ActivityView =
        ride.copy(viewer = ActivityViewerState(like = AtUri("at://did:plc:alice/app.activitytracker.feed.like/3klike")))

    /** Bob's untitled run with no route served: the title is its type. */
    val untitled: ActivityView =
        fakeActivity(
            rkey = "3kcarduntitled",
            author = bob,
            record = fakeActivityRecord(sport = Sport.RUN, title = null, distance = 8_200, movingTime = 2_460, polyline = null),
            likeCount = 0,
            commentCount = 0,
        )

    /** Bob's ride done with Carol, Alice and Dan (feed/group-activities). */
    val group: ActivityView =
        ride.copy(
            uri = Fixed.uri(Fixed.BOB, Nsid.FEED_ACTIVITY, "3kcardgroup"),
            group =
                listOf(
                    fakeGroupPartner(),
                    fakeGroupPartner(fakeProfileBasic(Fixed.ALICE), rkey = "3kpartner2"),
                    fakeGroupPartner(dan, rkey = "3kpartner3"),
                ),
        )

    /** Bob's ride with its route and two photos, one upright, and two lines of description. */
    val photos: ActivityView =
        fakeActivity(
                rkey = "3kcardphotos",
                author = bob,
                record =
                    fakeActivityRecord(
                        description =
                            "Up the canyon and back with a long stop at the top for coffee. Windy on the way down, " +
                                "so the last ten miles took longer than they should have.",
                    ),
            )
            .copy(media = listOf(photo(1), photo(2, width = 3, height = 4)))

    /** Bob's ride with photos under a Graphic media label: blurred until revealed. */
    val blurred: ActivityView =
        photos.copy(uri = Fixed.uri(Fixed.BOB, Nsid.FEED_ACTIVITY, "3kcardblurred"), labels = listOf(label("graphic-media")))

    /** Alice's own Followers ride whose heart rate is Only me: a badge and a lock, and no Like. */
    val own: ActivityView =
        fakeActivity(rkey = "3kcardown", visibility = Visibility.FOLLOWERS, likeCount = 2, commentCount = 0)
            .copy(
                details =
                    listOf(
                        DetailsView(
                            Visibility.SELF,
                            ActivityDetailsRecord(
                                activity = Fixed.uri(Fixed.ALICE, Nsid.FEED_ACTIVITY, "3kcardown"),
                                createdAt = Fixed.CREATED_AT,
                                avgHeartRate = 142,
                                maxHeartRate = 171,
                            ),
                        ),
                    ),
            )

    /** Bob's ride labelled Spam: shown with the warning. */
    val warned: ActivityView = ride.copy(uri = Fixed.uri(Fixed.BOB, Nsid.FEED_ACTIVITY, "3kcardwarned"), labels = listOf(label("spam")))

    /** Alice's own ride, taken down: the notice, and placeholders for its photos. */
    val takenDown: ActivityView = fakeActivity(rkey = "3kcardtakendown").copy(media = listOf(photo(3)), labels = listOf(label("!takedown")))

    /** Bob's dive at Blue Hole: its depth, dive time and water temperature. */
    val dive: ActivityView =
        fakeActivity(
            rkey = "3kcarddive",
            author = bob,
            record =
                fakeActivityRecord(sport = Sport.DIVE, title = "Morning dive", elapsedTime = 2_700, distance = null, polyline = null)
                    .copy(dive = ActivityDive(maxDepth = 1_820, minTemperature = 265, site = "Blue Hole")),
        )

    /** Bob's strength workout with no distance: its time alone. */
    val strength: ActivityView =
        fakeActivity(
            rkey = "3kcardstrength",
            author = bob,
            record = fakeActivityRecord(sport = Sport.STRENGTH, title = "Leg day", distance = null, elevationGain = null, polyline = null),
            likeCount = 1,
            commentCount = 0,
        )

    /** Bob's run whose start time the viewer wasn't served: the record holds its local midnight, so only its day shows. */
    val dateOnly: ActivityView =
        fakeActivity(
            rkey = "3kcarddateonly",
            author = bob,
            // Local midnight at the samples' offset (UTC-6) on Fixed.STARTED_AT's day.
            record = fakeActivityRecord(sport = Sport.RUN, title = "Easy run", startedAt = Instant.parse("2026-05-31T06:00:00Z")),
        )

    /** Bob's ride whose line breaks once: two pieces, a dashed connector and four markers. */
    val broken: ActivityView =
        fakeActivity(
            rkey = "3kcardbroken",
            author = bob,
            record = fakeActivityRecord(polyline = brokenRoute).copy(polylineGaps = listOf(4)),
        )

    /** Bob's ride with a title long enough to wrap. */
    val longTitle: ActivityView =
        fakeActivity(
            rkey = "3kcardlong",
            author = bob,
            record = fakeActivityRecord(title = "Long gravel loop over Sunshine Canyon, Lee Hill and back through Lefthand"),
        )

    /** Every card, by name. */
    val all: Map<String, ActivityView>
        get() =
            mapOf(
                "ride" to ride,
                "liked" to liked,
                "untitled" to untitled,
                "group" to group,
                "photos" to photos,
                "blurred" to blurred,
                "own" to own,
                "warned" to warned,
                "takenDown" to takenDown,
                "dive" to dive,
                "strength" to strength,
                "dateOnly" to dateOnly,
                "broken" to broken,
                "longTitle" to longTitle,
            )
}
