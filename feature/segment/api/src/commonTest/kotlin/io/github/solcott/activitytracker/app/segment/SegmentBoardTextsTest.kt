package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.LeaderboardPeriod
import io.github.solcott.activitytracker.lexicon.app.MayorPeriod
import io.github.solcott.activitytracker.lexicon.app.SegmentBoard
import io.github.solcott.uistate.pagedStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * What a segment's boards and starred list offer and say (segments/segment-leaderboards, segments/segment-mayors, dev/shared-display-text).
 */
class SegmentBoardTextsTest {
    @Test
    fun theLeaderboardsPeriodsAreAllTimeThisYearAndThisMonth() {
        val choice = SegmentBoardTexts.leaderboardPeriod(LeaderboardPeriod.MONTH)
        assertEquals(LeaderboardPeriod.all, choice.options.map { it.value })
        assertEquals(
            listOf("leaderboard_period_all_time", "leaderboard_period_year", "leaderboard_period_month").map(AppText::named),
            choice.options.map { it.label },
        )
        assertEquals(AppText.named("leaderboard_period_month"), choice.label)
    }

    @Test
    fun theMayorsWindowsAreEveryWindowInOrder() {
        val choice = SegmentBoardTexts.mayorPeriod(MayorPeriod.LAST_90_DAYS)
        assertEquals(MayorPeriod.all, choice.options.map { it.value })
        assertEquals(AppText.named("mayor_period_last_90_days"), choice.label)
        assertEquals(AppText.named("mayor_period_this_week"), SegmentBoardTexts.mayorPeriod(MayorPeriod.THIS_WEEK).label)
        assertTrue(choice.offersMenu)
    }

    @Test
    fun oneTypeIsALabelAndSeveralAMenu() {
        assertNull(SegmentBoardTexts.type(null, listOf("run")))
        val one = SegmentBoardTexts.type("run", listOf("run"))!!
        assertFalse(one.offersMenu)
        assertEquals(LabelText.sport("run"), one.label)
        val none = SegmentBoardTexts.type("run", emptyList())!!
        assertFalse(none.offersMenu)
        val two = SegmentBoardTexts.type("ride", listOf("ride", SegmentBoard.E_BIKE))!!
        assertTrue(two.offersMenu)
        assertEquals(listOf(LabelText.sport("ride"), AppText.named("leaderboard_board_ebike")), two.options.map { it.label })
    }

    @Test
    fun ageGroupsReadAsTheirBounds() {
        val choice = LeaderboardBracketTexts.ageGroup(null)
        assertEquals(AppText.named("leaderboard_age_all"), choice.label)
        assertFalse(choice.narrows)
        assertEquals(AppText.named("leaderboard_age_under", AppText.written("19")), choice.options[1].label)
        assertEquals(AppText.named("leaderboard_age_range", AppText.written("45"), AppText.written("54")), choice.options[5].label)
        assertEquals(AppText.named("leaderboard_age_over", AppText.written("75")), choice.options.last().label)
        assertTrue(LeaderboardBracketTexts.ageGroup("45-54").narrows)
    }

    @Test
    fun weightClassesReadInTheViewersUnits() {
        val kg = LeaderboardBracketTexts.weightClass("55-64", UnitSystem.Metric)
        assertEquals(AppText.named("leaderboard_weight_range_kg", AppText.written("55"), AppText.written("64")), kg.label)
        val lb = LeaderboardBracketTexts.weightClass("55-64", UnitSystem.Imperial)
        assertEquals(AppText.named("leaderboard_weight_range_lb", AppText.written("121"), AppText.written("142")), lb.label)
        assertTrue(lb.narrows)
        assertEquals(AppText.named("leaderboard_weight_under_lb", AppText.written("121")), lb.options[1].label)
        assertEquals(AppText.named("leaderboard_weight_over_lb", AppText.written("254")), lb.options.last().label)
    }

    @Test
    fun gendersAreNamed() {
        assertEquals(
            listOf("leaderboard_gender_all", "leaderboard_gender_female", "leaderboard_gender_male", "leaderboard_gender_non_binary")
                .map(AppText::named),
            LeaderboardBracketTexts.gender(null).options.map { it.label },
        )
    }

    @Test
    fun aCountOfEffortsIsGrouped() {
        assertEquals(
            AppText.Plural("mayor_efforts", 1204, persistentListOf(AppText.written(formatWhole(1204)))),
            SegmentBoardTexts.efforts(1204),
        )
    }

    @Test
    fun theStatesOfferTheirChoices() {
        val board =
            SegmentLeaderboardState("Ridge", board = "ride", boards = persistentListOf("ride", SegmentBoard.E_BIKE), ageGroup = "45-54")
        assertTrue(board.typeChoice!!.offersMenu)
        assertTrue(board.ageGroupChoice.narrows)
        assertFalse(board.genderChoice.narrows)
        assertFalse(board.scopeChoice.narrows)
        assertEquals(AppText.named("leaderboard_period_all_time"), board.periodChoice.label)
        assertEquals(AppText.named("mayor_period_last_90_days"), SegmentMayorsState("Ridge").periodChoice.label)
    }

    @Test
    fun aStarOpensWhileItsSegmentIsThereAndUnstarsOnce() {
        val gone = StarredSegment(AtUri("at://did:plc:alice/app.activitytracker.feed.segment/gone"), segment = null)
        val other = StarredSegment(AtUri("at://did:plc:alice/app.activitytracker.feed.segment/other"), segment = null)
        val state = StarredSegmentsState(pagedStateOf(), removing = persistentSetOf(gone.uri), eventSink = {})
        assertFalse(state.canOpen(gone))
        assertTrue(state.isRemoving(gone))
        assertFalse(state.isRemoving(other))
        assertNull(state.signInError)
        assertTrue(state.showsEmpty)
    }

    @Test
    fun aSessionThatMustSignInAgainSaysSoAndNothingElse() {
        val state = StarredSegmentsState(pagedStateOf(), needsSignIn = true, eventSink = {})
        assertEquals(UiError(UiError.Kind.SignInAgain), state.signInError)
        assertFalse(state.showsEmpty)
    }
}
