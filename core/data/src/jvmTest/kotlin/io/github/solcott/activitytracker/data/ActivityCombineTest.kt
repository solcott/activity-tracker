package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.FakeAthletePds.Session
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityEdit
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.StreamChannel
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject

/** Combining, splitting and deleting in the data layer (`feed/combined-activities`), against the fake PDS. */
class ActivityCombineTest {
    private val pds = FakeAthletePds()
    private val did = pds.did
    private val repo = pds.repo
    private val stored = pds.stored

    private val ride = ActivityType(Sport.RIDE)

    private fun draft(
        title: String,
        start: String = "2026-10-14T09:00:00Z",
        minutes: Int = 60,
        streams: ActivityStreamData? = null,
        choice: VisibilityChoice = VisibilityChoice.PUBLIC,
    ) =
        ActivityDraft(
                type = ride,
                title = title,
                description = null,
                startedAt = Instant.parse(start),
                elapsed = minutes.minutes,
                streams = streams,
            )
            .withVisibility(choice)

    private fun samples(every: Int = 1, minutes: Int = 60, block: (ActivityStreamData) -> ActivityStreamData) =
        block(ActivityStreamData(time = (0..minutes * 60 step every).toList()))

    private fun place(visibility: String) = if (visibility == Visibility.PUBLIC) repo else visibility

    private fun record(uri: AtUri, visibility: String = Visibility.PUBLIC): JsonObject? =
        stored[place(visibility)]?.get("${Nsid.FEED_ACTIVITY}/${uri.rkey}")

    /** The athlete's own [uri] as the AppView would serve it, from what's stored. */
    private fun view(uri: AtUri, visibility: String = Visibility.PUBLIC, combinedInto: AtUri? = null) =
        ActivityView(
            uri = uri,
            cid = Cid("bafyrec"),
            author = ProfileViewBasic(did, Handle("alice.test")),
            record = AtJson.decodeFromJsonElement(ActivityRecord.serializer(), requireNotNull(record(uri, visibility))),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Clock.System.now(),
            visibility = visibility,
            combinedInto = combinedInto,
        )

    private suspend fun Session.two(intoChoice: VisibilityChoice = VisibilityChoice.PUBLIC): Pair<AtUri, AtUri> {
        val watch =
            activities.publish(draft("Lunch loop", streams = samples(2) { it.copy(heartRate = it.time.map { 120 }) }, choice = intoChoice))
        val edge =
            activities.publish(
                draft("Morning Ride", streams = samples { it.copy(heartRate = it.time.map { 140 }, power = it.time.map { 210 }) })
            )
        return watch to edge
    }

    private fun streamsOf(uri: AtUri, visibility: String = Visibility.PUBLIC): ActivityStreamData? {
        val record = stored[place(visibility)]?.get("${Nsid.FEED_ACTIVITY_STREAMS}/${uri.rkey}") ?: return null
        val cid = record["data"].toString().substringAfter("\"\$link\":\"").substringBefore('"')
        return AtJson.decodeFromString(ActivityStreamData.serializer(), pds.blobs.getValue(cid).decodeToString())
    }

    @Test
    fun combiningWritesANewActivityAndLeavesTheMembersAsTheyWere() = runTest {
        val s = pds.session()
        val (watch, edge) = s.two()
        val before = listOf(watch, edge).associateWith { record(it) to streamsOf(it) }

        val combined = s.manager.combine(view(watch), view(edge))

        val made = view(combined).record
        assertEquals(listOf(watch, edge).toSet(), made.combined?.members?.toSet())
        assertEquals("Lunch loop", made.title, "it starts from the activity combined into")
        val streams = streamsOf(combined)!!
        assertTrue(streams.power!!.all { it == 210 })
        assertTrue(streams.heartRate!!.filterNotNull().all { it == 140 }, "the bike computer has more heart rate")
        assertEquals(140, made.avgHeartRate)
        for ((uri, kept) in before) assertEquals(kept, record(uri) to streamsOf(uri), "member $uri is untouched")
    }

    @Test
    fun aThirdActivityJoinsTheCombinedOne() = runTest {
        val s = pds.session()
        val (watch, edge) = s.two()
        val combined = s.manager.combine(view(watch), view(edge))
        val phone = s.activities.publish(draft("Phone", start = "2026-10-14T09:05:00Z", minutes = 50))

        assertEquals(combined, s.manager.combine(view(combined), view(phone)))
        assertEquals(setOf(watch, edge, phone), view(combined).record.combined?.members?.toSet())
        assertEquals("Lunch loop", view(combined).record.title)
    }

    @Test
    fun combiningRefusesWhatCantBeAMember() = runTest {
        val s = pds.session()
        val (watch, edge) = s.two()
        val combined = s.manager.combine(view(watch), view(edge))
        val other = s.activities.publish(draft("Other"))

        assertFailsWith<IllegalArgumentException>("a combined activity") { s.manager.combine(view(other), view(combined)) }
        assertFailsWith<IllegalArgumentException>("another's member") {
            s.manager.combine(view(other), view(watch, combinedInto = combined))
        }
        val many = (1..9).map { s.activities.publish(draft("Ride $it")) }
        var into = view(combined)
        many.dropLast(1).forEach { into = view(s.manager.combine(into, view(it))) }
        assertEquals(10, into.record.combined?.members?.size)
        assertFailsWith<IllegalArgumentException>("an eleventh") { s.manager.combine(into, view(many.last())) }
    }

    @Test
    fun aPublicActivityCombinedIntoAFollowersOneBecomesFollowers() = runTest {
        val s = pds.session()
        val (watch, edge) = s.two(VisibilityChoice(Visibility.FOLLOWERS))
        val combined = s.manager.combine(view(watch, Visibility.FOLLOWERS), view(edge))

        assertEquals(setOf(Visibility.FOLLOWERS), pds.placesOf(Nsid.FEED_ACTIVITY, edge.rkey!!))
        assertEquals(setOf(Visibility.FOLLOWERS), pds.placesOf(Nsid.FEED_ACTIVITY, combined.rkey!!))
    }

    @Test
    fun theCombinedActivityKeepsEachMembersNarrowestCategory() = runTest {
        val s = pds.session()
        val watch =
            s.activities.publish(
                draft(
                    "Watch",
                    streams = samples { it.copy(heartRate = it.time.map { 120 }) },
                    choice = VisibilityChoice(Visibility.PUBLIC, mapOf(DetailCategory.HEART_RATE to Visibility.SELF)),
                )
            )
        val edge = s.activities.publish(draft("Edge", streams = samples(2) { it.copy(heartRate = it.time.map { 140 }) }))
        val combined = s.manager.combine(view(edge), view(watch))

        assertNull(streamsOf(combined)?.heartRate, "heart rate isn't in the public streams")
        assertTrue(streamsOf(combined, Visibility.SELF)?.heartRate != null, "it's in self, as the watch had it")
    }

    @Test
    fun splittingDeletesTheCombinedActivityOnly() = runTest {
        val s = pds.session()
        val (watch, edge) = s.two()
        val combined = s.manager.combine(view(watch), view(edge))
        val members = listOf(watch, edge).associateWith { record(it) to streamsOf(it) }

        s.manager.split(view(combined))

        assertNull(record(combined))
        assertNull(streamsOf(combined))
        assertEquals(emptySet(), pds.placesOf(Nsid.FEED_ACTIVITY_TRACK, combined.rkey!!))
        for ((uri, kept) in members) assertEquals(kept, record(uri) to streamsOf(uri))
    }

    @Test
    fun removingAMemberRebuildsOrSplits() = runTest {
        val s = pds.session()
        val (watch, edge) = s.two()
        val phone = s.activities.publish(draft("Phone", minutes = 59))
        val combined = s.manager.combine(view(watch), view(edge))
        s.manager.combine(view(combined), view(phone))

        s.manager.removeMember(view(combined), edge)
        assertEquals(setOf(watch, phone), view(combined).record.combined?.members?.toSet())
        assertNull(streamsOf(combined)?.power, "rebuilt without the bike computer's power")

        s.manager.removeMember(view(combined), phone)
        assertNull(record(combined), "one member left: split")
        assertTrue(record(watch) != null && record(phone) != null && record(edge) != null)
    }

    @Test
    fun deletingWithMembersDeletesThemAllAndWithoutLeavesThem() = runTest {
        val s = pds.session()
        val (watch, edge) = s.two()
        val combined = s.manager.combine(view(watch), view(edge))
        s.manager.delete(view(combined), withRecordings = false)
        assertNull(record(combined))
        assertTrue(record(watch) != null && record(edge) != null)

        val again = s.manager.combine(view(watch), view(edge))
        s.manager.delete(view(again), withRecordings = true)
        assertNull(record(again))
        assertNull(record(watch))
        assertNull(record(edge))
        assertNull(streamsOf(edge))
    }

    @Test
    fun deletingOneOfTwoMembersSplitsTheCombinedActivity() = runTest {
        val s = pds.session()
        val (watch, edge) = s.two()
        val combined = s.manager.combine(view(watch), view(edge))
        pds.served = GetActivity.Output(view(combined))

        s.manager.delete(view(edge, combinedInto = combined), withRecordings = false)

        assertNull(record(edge))
        assertNull(record(combined))
        assertTrue(record(watch) != null)
    }

    @Test
    fun narrowingACombinedActivityMovesItsMembersAndAnEditKeepsTheMembership() = runTest {
        val s = pds.session()
        val (watch, edge) = s.two()
        val combined = s.manager.combine(view(watch), view(edge))

        s.manager.setVisibility(view(combined), VisibilityChoice(Visibility.FOLLOWERS))
        for (uri in listOf(combined, watch, edge)) assertEquals(setOf(Visibility.FOLLOWERS), pds.placesOf(Nsid.FEED_ACTIVITY, uri.rkey!!))

        s.manager.edit(
            view(combined, Visibility.FOLLOWERS),
            ActivityEdit(title = "Big day", description = "", diveSite = null, media = emptyList()),
        )
        val edited = view(combined, Visibility.FOLLOWERS).record
        assertEquals("Big day", edited.title)
        assertEquals(setOf(watch, edge), edited.combined?.members?.toSet())
        assertTrue(edited.combined?.sourcesOf(StreamChannel.POWER) == listOf(edge))
    }
}
