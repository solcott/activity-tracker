package io.github.solcott.activitytracker.app.ui

import io.github.solcott.activitytracker.app.activitylist.ActivityCardContent
import io.github.solcott.activitytracker.app.activitylist.ActivityCardSamples
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.moderation.NoticeText
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.moderation.BlurReason
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone

/** What an activity card shows, chosen once for both UIs (dev/shared-display-text), over the card samples. */
class ActivityCardContentTest {
    private fun card(activity: ActivityView) = ActivityCardContent.of(activity, ActivityCardSamples.viewer)

    private fun named(name: String, vararg args: AppText) = AppText.named(name, *args)

    @Test
    fun aFollowersRide() {
        val card = card(ActivityCardSamples.ride)
        assertFalse(card.isOwn)
        assertTrue(card.offersLike)
        assertFalse(card.liked)
        assertEquals(AppText.written("Bob"), card.author())
        assertNull(card.badge())
        assertEquals(AppText.written("Morning Ride"), card.title())
        assertEquals(named("sport_ride"), (card.typeDescription() as AppText.Named).copy(fallback = null))
        assertEquals(
            listOf("stat_distance", "stat_time", "stat_speed"),
            card.stats(UnitSystem.Metric).map { (it.label as AppText.Named).name },
        )
        assertTrue(card.hasRoute)
        assertEquals(
            named(
                "activity_card_counts",
                AppText.Plural("like_count", 3, persistentListOf(AppText.written("3"))),
                AppText.Plural("comments_count", 1, persistentListOf(AppText.written("1"))),
            ),
            card.counts(),
        )
        assertTrue(card.notices().isEmpty())
        assertTrue(card.locks().isEmpty())
    }

    @Test fun likedShowsGiven() = assertTrue(card(ActivityCardSamples.liked).liked)

    @Test
    fun anUntitledRunIsTitledByItsTypeAndItsIconIsntDescribed() {
        val card = card(ActivityCardSamples.untitled)
        assertEquals("sport_run", (card.title() as AppText.Named).name)
        assertNull(card.typeDescription())
        assertFalse(card.hasRoute)
        assertNull(card.routeShape(300.0, 160.0, 12.0))
    }

    @Test
    fun withThreeOthers() =
        assertEquals(
            AppText.Plural("card_with_others", 2, persistentListOf(AppText.written("Carol"), AppText.written("2"))),
            card(ActivityCardSamples.group).together(),
        )

    @Test
    fun blurredPhotos() {
        val card = card(ActivityCardSamples.blurred)
        assertEquals(BlurReason.GraphicMedia, card.blur)
        assertEquals(named("label_graphic_media"), card.blurLabel())
        assertNull(card.avatarBlur)
        assertEquals(2, card.media.size)
        // Someone else's blurred activity shows no note: the blur says it.
        assertTrue(card.notices().isEmpty())
    }

    @Test
    fun ownWithALock() {
        val card = card(ActivityCardSamples.own)
        assertTrue(card.isOwn)
        assertFalse(card.offersLike)
        assertEquals(named("activity_visibility_badge_followers"), card.badge())
        val lock = card.locks().single()
        assertEquals(
            named("detail_category_lock", named("detail_category_heartRate"), named("activity_visibility_badge_self")),
            lock.text.withoutFallbacks(),
        )
    }

    @Test fun warned() = assertEquals(listOf(NoticeText(named("label_spam"), isError = false)), card(ActivityCardSamples.warned).notices())

    @Test
    fun takenDown() {
        val card = card(ActivityCardSamples.takenDown)
        assertTrue(card.takenDown)
        assertEquals(listOf(NoticeText(named("label_taken_down"), isError = true)), card.notices())
    }

    @Test
    fun aDive() {
        val card = card(ActivityCardSamples.dive)
        assertEquals("Blue Hole", card.diveSite())
        assertEquals(
            listOf("stat_max_depth", "stat_dive_time", "stat_water_temp"),
            card.stats(UnitSystem.Metric).map { (it.label as AppText.Named).name },
        )
        assertEquals(AppText.written("45:00"), card.stats(UnitSystem.Metric)[1].value)
    }

    @Test
    fun aStrengthWorkoutShowsOnlyItsTime() =
        assertEquals(
            listOf("stat_time"),
            card(ActivityCardSamples.strength).stats(UnitSystem.Metric).map { (it.label as AppText.Named).name },
        )

    @Test
    fun aDateOnlyStartIsItsDay() {
        val start = card(ActivityCardSamples.dateOnly).start(Fixed.NOW, TimeZone.UTC)
        assertEquals(named("start_yesterday"), start)
    }

    @Test
    fun aStartReadsOnTheActivitysClock() {
        val start = card(ActivityCardSamples.ride).start(Fixed.NOW, TimeZone.UTC) as AppText.Named
        assertEquals("page_start_value", start.name)
        assertEquals(AppText.Time(Fixed.STARTED_AT.epochSeconds, -360), start.args[1])
    }

    @Test
    fun aBrokenRouteHasTwoPiecesInsideItsTile() {
        val shape = assertNotNull(card(ActivityCardSamples.broken).routeShape(300.0, 160.0, 12.0))
        assertEquals(2, shape.pieces.size)
        assertEquals(1, shape.breaks.size)
        val all = shape.pieces.flatMap { it.points } + assertNotNull(shape.start) + assertNotNull(shape.end)
        assertTrue(all.all { it.x in 11.9..288.1 && it.y in 11.9..148.1 }, "Outside the tile: $all")
    }

    private fun AppText.withoutFallbacks(): AppText =
        when (this) {
            is AppText.Named -> AppText.Named(name, args.map { it.withoutFallbacks() }.let { persistentListOf(*it.toTypedArray()) })
            else -> this
        }
}
