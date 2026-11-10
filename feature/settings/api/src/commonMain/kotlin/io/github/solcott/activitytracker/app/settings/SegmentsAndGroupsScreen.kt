package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.GroupChoice
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Segment matching, the leaderboard choices and activity grouping: a page of Settings. Each shares a copy of the athlete's routes, without
 * their privacy zones, with the AppView while it's on.
 */
@CircuitSerializable(SessionScope::class) data object SegmentsAndGroupsScreen : SettingsPage

@Immutable
data class SegmentsAndGroupsState(
    val error: UiError?,
    /** Whether segment matching is on; null while it's unknown (not read yet, or unreadable). */
    val segmentMatching: Boolean? = null,
    /** Whether this session can use segment matching: false for one signed in before its `space:` grant listed it. */
    val canUseSegments: Boolean = true,
    val savingSegments: Boolean = false,
    /** Whether the athlete shows on segment leaderboards; offered only while [segmentMatching] is on, and null while unknown. */
    val showOnLeaderboards: Boolean? = null,
    val savingLeaderboards: Boolean = false,
    /**
     * Whether the athlete shows on age, weight and gender leaderboards (segments/leaderboard-brackets); offered only while
     * [segmentMatching] is on and [canUseBracketBoards], and null while unknown.
     */
    val bracketBoards: Boolean? = null,
    /** Whether this session can use bracket boards: false for one signed in before its `space:` grant listed the brackets record. */
    val canUseBracketBoards: Boolean = true,
    val savingBracketBoards: Boolean = false,
    /**
     * Whose activities the athlete's may be grouped with (feed/group-activities); [GroupChoice.NEVER_ASKED] shows as off, and null while
     * it's unknown (not read yet, or unreadable).
     */
    val groupActivities: GroupChoice? = null,
    /** Whether this session can use grouping: false for one signed in before its `space:` grant listed it. */
    val canUseGroups: Boolean = true,
    val savingGroups: Boolean = false,
    val eventSink: (SegmentsAndGroupsEvent) -> Unit,
) : CircuitUiState {
    /** Whether segment matching can be turned on or off now: a session that can use it, the setting read, and no save of it running. */
    val canChangeMatching: Boolean
        get() = canUseSegments && segmentMatching != null && !savingSegments

    /** Whether the leaderboard choice is offered: only while matching is on and the choice has been read. */
    val offersLeaderboards: Boolean
        get() = segmentMatching == true && showOnLeaderboards != null

    val canChangeLeaderboards: Boolean
        get() = canChangeMatching && !savingLeaderboards

    /** Whether the bracket-board choice is offered: only while matching is on, for a session that can use it, once read. */
    val offersBracketBoards: Boolean
        get() = segmentMatching == true && canUseBracketBoards && bracketBoards != null

    val canChangeBracketBoards: Boolean
        get() = canChangeMatching && !savingBracketBoards

    /** The grouping choice shown as chosen: a choice never made, or one not read yet, shows as [GroupChoice.OFF]. */
    val shownGroupChoice: GroupChoice
        get() = groupActivities.takeUnless { it == GroupChoice.NEVER_ASKED } ?: GroupChoice.OFF

    val canChangeGroups: Boolean
        get() = canUseGroups && groupActivities != null && !savingGroups

    /** The grouping choices offered, in order: off, people the athlete follows or who follow them, anyone. */
    val groupOptions: ImmutableList<GroupOption>
        get() = GROUP_OPTIONS
}

/**
 * One grouping choice a control offers, with its label. A list of an enum's values can't reach Swift, so a choice among them is a list of
 * these, each holding its value as a property, which Swift reads as its own enum (dev/ios-swift-interop).
 */
@Immutable data class GroupOption(val choice: GroupChoice, val label: AppText)

private val GROUP_OPTIONS: ImmutableList<GroupOption> =
    persistentListOf(
        GroupOption(GroupChoice.OFF, AppText.named("settings_groups_off")),
        GroupOption(GroupChoice.CONNECTED, AppText.named("settings_groups_connected")),
        GroupOption(GroupChoice.ANYONE, AppText.named("settings_groups_anyone")),
    )

sealed interface SegmentsAndGroupsEvent {
    data object Back : SegmentsAndGroupsEvent

    /** Turns segment matching [on] or off: a copy of each route without the privacy zones is shared with the AppView while it's on. */
    data class SegmentMatchingChanged(val on: Boolean) : SegmentSettingEvent

    /** Shows the athlete on segment leaderboards, or hides them from every one (segments/segment-leaderboards). */
    data class LeaderboardsChanged(val shown: Boolean) : SegmentSettingEvent

    /**
     * Shows the athlete on age, weight and gender leaderboards, sharing only those brackets, or takes them off
     * (segments/leaderboard-brackets).
     */
    data class BracketBoardsChanged(val on: Boolean) : SegmentSettingEvent

    /**
     * Chooses whose activities the athlete's may be grouped with: a copy of each route without the privacy zones is shared with the AppView
     * while it isn't off (feed/group-activities).
     */
    data class GroupActivitiesChanged(val choice: GroupChoice) : SegmentsAndGroupsEvent

    /** The segment settings' events, which their holder handles. */
    sealed interface SegmentSettingEvent : SegmentsAndGroupsEvent
}
