package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.GroupPrefsRepository
import io.github.solcott.activitytracker.data.segments.LeaderboardBracketsRepository
import io.github.solcott.activitytracker.data.segments.SegmentPrefsRepository

@CircuitInject(SegmentsAndGroupsScreen::class, SessionScope::class)
@Composable
fun SegmentsAndGroupsPresenter(
    navigator: Navigator,
    segmentPrefs: SegmentPrefsRepository,
    leaderboardBrackets: LeaderboardBracketsRepository,
    groupPrefs: GroupPrefsRepository,
): SegmentsAndGroupsState {
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    val segments = rememberSegmentMatchingSetting(segmentPrefs, leaderboardBrackets, flights) { error = it }
    val groups = rememberGroupActivitiesSetting(groupPrefs, flights) { error = it }
    return SegmentsAndGroupsState(
        error = error,
        segmentMatching = segments.matching,
        canUseSegments = segments.canUse,
        savingSegments = segments.saving,
        showOnLeaderboards = segments.shownOnLeaderboards,
        savingLeaderboards = segments.savingLeaderboards,
        bracketBoards = segments.bracketBoards,
        canUseBracketBoards = segments.canUseBracketBoards,
        savingBracketBoards = segments.savingBracketBoards,
        groupActivities = groups.choice,
        canUseGroups = groups.canUse,
        savingGroups = groups.saving,
    ) { event ->
        when (event) {
            SegmentsAndGroupsEvent.Back -> navigator.pop()
            is SegmentsAndGroupsEvent.SegmentSettingEvent -> segments.onEvent(event)
            is SegmentsAndGroupsEvent.GroupActivitiesChanged -> groups.set(event.choice)
        }
    }
}
