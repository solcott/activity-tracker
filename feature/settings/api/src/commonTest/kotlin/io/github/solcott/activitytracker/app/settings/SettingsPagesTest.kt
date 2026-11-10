package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.data.segments.GroupChoice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What Activity defaults and Segments & groups say and offer, which both UIs ask (ui/settings-layout, "Activity defaults is native on iOS",
 * "Segments & groups is native on iOS").
 */
class SettingsPagesTest {
    private val fields = VisibilityFields()
    private val defaults = ActivityDefaultsState(defaults = fields, saving = false, error = null, eventSink = {})
    private val segments =
        SegmentsAndGroupsState(
            error = null,
            segmentMatching = true,
            showOnLeaderboards = true,
            bracketBoards = false,
            groupActivities = GroupChoice.CONNECTED,
            eventSink = {},
        )

    @Test
    fun theDefaultsNoteSaysSavingBeforeWhyTheyAreLocked() {
        val locked = VisibilityFields(canChoose = false)
        assertEquals(AppText.named("settings_defaults_help"), SettingsTexts.defaultsNote(fields, saving = false))
        assertEquals(AppText.named("settings_saving"), SettingsTexts.defaultsNote(fields, saving = true))
        assertEquals(AppText.named("activity_visibility_sign_in_again"), SettingsTexts.defaultsNote(locked, saving = false))
        assertEquals(AppText.named("settings_saving"), SettingsTexts.defaultsNote(locked, saving = true))
        assertEquals(
            AppText.named("activity_visibility_unsupported"),
            SettingsTexts.defaultsNote(VisibilityFields(canChoose = false, spacesUnsupported = true), saving = false),
        )
    }

    @Test
    fun theDefaultsChangeOnlyLoadedChoosableAndNotSaving() {
        assertTrue(defaults.canChange)
        assertFalse(defaults.copy(defaults = null).canChange)
        assertFalse(defaults.copy(saving = true).canChange)
        assertFalse(defaults.copy(defaults = VisibilityFields(canChoose = false)).canChange)
    }

    @Test
    fun theSegmentsNoteSaysSignInAgainBeforeSaving() {
        assertEquals(AppText.named("settings_segments_help"), SettingsTexts.segmentsNote(segments))
        assertEquals(AppText.named("settings_saving"), SettingsTexts.segmentsNote(segments.copy(savingSegments = true)))
        assertEquals(AppText.named("settings_saving"), SettingsTexts.segmentsNote(segments.copy(savingLeaderboards = true)))
        assertEquals(AppText.named("settings_saving"), SettingsTexts.segmentsNote(segments.copy(savingBracketBoards = true)))
        assertEquals(
            AppText.named("settings_segments_sign_in_again"),
            SettingsTexts.segmentsNote(segments.copy(canUseSegments = false, savingSegments = true)),
        )
    }

    @Test
    fun theGroupsNoteSaysSignInAgainBeforeSaving() {
        assertEquals(AppText.named("settings_groups_help"), SettingsTexts.groupsNote(segments))
        assertEquals(AppText.named("settings_saving"), SettingsTexts.groupsNote(segments.copy(savingGroups = true)))
        assertEquals(
            AppText.named("settings_groups_sign_in_again"),
            SettingsTexts.groupsNote(segments.copy(canUseGroups = false, savingGroups = true)),
        )
    }

    @Test
    fun matchingChangesOnceReadOnASessionThatCanUseItAndNotSaving() {
        assertTrue(segments.canChangeMatching)
        assertFalse(segments.copy(segmentMatching = null).canChangeMatching)
        assertFalse(segments.copy(canUseSegments = false).canChangeMatching)
        assertFalse(segments.copy(savingSegments = true).canChangeMatching)
    }

    @Test
    fun leaderboardsFollowMatching() {
        assertTrue(segments.offersLeaderboards)
        assertFalse(segments.copy(segmentMatching = false).offersLeaderboards)
        assertFalse(segments.copy(showOnLeaderboards = null).offersLeaderboards)
        assertTrue(segments.canChangeLeaderboards)
        assertFalse(segments.copy(savingLeaderboards = true).canChangeLeaderboards)
        assertFalse(segments.copy(savingSegments = true).canChangeLeaderboards, "nor while matching itself saves")
    }

    @Test
    fun bracketBoardsFollowMatchingAndTheSession() {
        assertTrue(segments.offersBracketBoards)
        assertFalse(segments.copy(segmentMatching = false).offersBracketBoards)
        assertFalse(segments.copy(canUseBracketBoards = false).offersBracketBoards)
        assertFalse(segments.copy(bracketBoards = null).offersBracketBoards)
        assertTrue(segments.canChangeBracketBoards)
        assertFalse(segments.copy(savingBracketBoards = true).canChangeBracketBoards)
        assertFalse(segments.copy(canUseSegments = false).canChangeBracketBoards)
    }

    @Test
    fun aGroupingNeverChosenShowsAsOff() {
        assertEquals(GroupChoice.CONNECTED, segments.shownGroupChoice)
        assertEquals(GroupChoice.OFF, segments.copy(groupActivities = GroupChoice.NEVER_ASKED).shownGroupChoice)
        assertEquals(GroupChoice.OFF, segments.copy(groupActivities = null).shownGroupChoice)
    }

    @Test
    fun groupingChangesOnceReadOnASessionThatCanUseItAndNotSaving() {
        assertTrue(segments.canChangeGroups)
        assertFalse(segments.copy(groupActivities = null).canChangeGroups)
        assertFalse(segments.copy(canUseGroups = false).canChangeGroups)
        assertFalse(segments.copy(savingGroups = true).canChangeGroups)
    }

    @Test
    fun threeGroupingChoicesInOrder() {
        assertEquals(
            listOf(
                GroupOption(GroupChoice.OFF, AppText.named("settings_groups_off")),
                GroupOption(GroupChoice.CONNECTED, AppText.named("settings_groups_connected")),
                GroupOption(GroupChoice.ANYONE, AppText.named("settings_groups_anyone")),
            ),
            segments.groupOptions,
        )
    }
}
