package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry
import io.github.solcott.activitytracker.lexicon.app.LeaderboardPeriod
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The leaderboard of the active segment at [uri] (segments/segment-leaderboards), titled [name]. [sport] is its sport group: a `ride`
 * segment also offers its e-bike board.
 */
@CircuitSerializable(SessionScope::class) data class SegmentLeaderboardScreen(val uri: String, val name: String, val sport: String) : Screen

@Immutable
data class SegmentLeaderboardState(
    val name: String,
    /** A [LeaderboardPeriod] value. */
    val period: String = LeaderboardPeriod.ALL_TIME,
    /** A [LeaderboardScope] value. */
    val scope: String = LeaderboardScope.EVERYONE,
    /** Whether the e-bike board is shown; only offered when [canChooseEBike]. */
    val eBike: Boolean = false,
    val canChooseEBike: Boolean = false,
    /** The loaded entries, fastest first. */
    val entries: ImmutableList<LeaderboardEntry> = persistentListOf(),
    /** The viewer's own entry, shown apart from [entries] when it isn't among them; null when they aren't on the board. */
    val viewerEntry: LeaderboardEntry? = null,
    val isLoading: Boolean = true,
    val canLoadMore: Boolean = false,
    val error: UiError? = null,
    val eventSink: (SegmentLeaderboardEvent) -> Unit = {},
) : CircuitUiState

sealed interface SegmentLeaderboardEvent {
    data object Back : SegmentLeaderboardEvent

    /** Shows the board for [period], a [LeaderboardPeriod] value, from its first page. */
    data class PeriodChanged(val period: String) : SegmentLeaderboardEvent

    /** Shows the board for [scope], a [LeaderboardScope] value, from its first page. */
    data class ScopeChanged(val scope: String) : SegmentLeaderboardEvent

    /** Shows the e-bike board, or the standard one, from its first page. */
    data class EBikeChanged(val eBike: Boolean) : SegmentLeaderboardEvent

    /** Loads the next page; ignored while a page is loading or when there are no more. */
    data object LoadMore : SegmentLeaderboardEvent

    /** Reloads from the first page after an error. */
    data object Retry : SegmentLeaderboardEvent

    /** Opens the activity an entry's effort came from. */
    data class OpenActivity(val uri: AtUri) : SegmentLeaderboardEvent
}
