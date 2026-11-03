package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.GroupChoice

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
) : CircuitUiState

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
