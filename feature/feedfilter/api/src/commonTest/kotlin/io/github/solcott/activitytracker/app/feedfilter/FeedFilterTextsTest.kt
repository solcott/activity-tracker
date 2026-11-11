package io.github.solcott.activitytracker.app.feedfilter

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.datetime.LocalDate

/** The feed filter bar's chips (dev/shared-display-text, "The feed filter bar's chips read the same in both UIs"). */
class FeedFilterTextsTest {
    private val ride = ActivityType(Sport.RIDE)
    private val trail = ActivityType(Sport.RUN, "trail")
    private val track = ActivityType(Sport.RUN, "track")

    @Test
    fun noTypeIsAllTypes() {
        assertEquals(AppText.named("feed_filter_all_types"), FeedFilterTexts.types(FeedFilter.Default))
    }

    @Test
    fun oneTypeIsThatType() {
        assertEquals(LabelText.activityType(trail), FeedFilterTexts.types(FeedFilter(persistentSetOf(trail))))
    }

    @Test
    fun severalTypesAreTheFirstBySportAndHowManyMore() {
        val text = FeedFilterTexts.types(FeedFilter(persistentSetOf(track, trail, ride)))

        assertEquals(
            AppText.Plural("feed_filter_types_more", 2, persistentListOf(LabelText.activityType(ride), AppText.written("2"))),
            text,
        )
    }

    @Test
    fun eachRangeHasItsWords() {
        assertEquals(AppText.named("feed_filter_any_time"), FeedFilterTexts.range(null))
        assertEquals(
            AppText.Plural("feed_filter_last_days", 30, persistentListOf(AppText.written("30"))),
            FeedFilterTexts.range(DateRange.LastDays(30)),
        )
        assertEquals(AppText.named("feed_filter_this_year"), FeedFilterTexts.range(DateRange.ThisYear))
        assertEquals(
            AppText.named("feed_filter_custom_range", AppText.written("2026-03-01"), AppText.written("2026-03-15")),
            FeedFilterTexts.range(DateRange.Custom(LocalDate(2026, 3, 1), LocalDate(2026, 3, 15))),
        )
    }

    @Test
    fun theDateMenuOffersAnyTimeThenThePresets() {
        assertEquals(listOf(null) + DateRange.Presets, FeedFilterTexts.rangeChoices().map { it.range })
    }

    @Test
    fun aPickedTypeOrItsSportIsAllAndASportWithSomeSubSportsIsPart() {
        val picker = TypePickerState(persistentSetOf(trail, ride), persistentListOf(), persistentListOf())

        assertEquals(Selection.All, picker.selectionOf(trail))
        assertEquals(Selection.All, picker.selectionOf(ActivityType(Sport.RIDE, "gravel")))
        assertEquals(Selection.Partial, picker.selectionOf(ActivityType(Sport.RUN)))
        assertEquals(Selection.None, picker.selectionOf(track))
    }

    @Test
    fun theChosenRangeIsItsPlaceInTheMenuAndCustomFollowsThePresets() {
        assertEquals(0, FeedFilterTexts.chosenRange(null))
        assertEquals(2, FeedFilterTexts.chosenRange(DateRange.LastDays(30)))
        assertEquals(3, FeedFilterTexts.chosenRange(DateRange.ThisYear))
        assertEquals(4, FeedFilterTexts.chosenRange(DateRange.Custom(LocalDate(2026, 3, 1), LocalDate(2026, 3, 2))))
    }
}
