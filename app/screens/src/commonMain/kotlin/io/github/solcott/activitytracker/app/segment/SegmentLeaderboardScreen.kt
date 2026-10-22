package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.LeaderboardAgeGroup
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry
import io.github.solcott.activitytracker.lexicon.app.LeaderboardGender
import io.github.solcott.activitytracker.lexicon.app.LeaderboardPeriod
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.LeaderboardWeightClass
import io.github.solcott.uistate.PagedState
import io.github.solcott.uistate.pagedStateOf

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
    /** A [LeaderboardAgeGroup] value: only athletes in it (segments/leaderboard-brackets); null for every age. */
    val ageGroup: String? = null,
    /** A [LeaderboardWeightClass] value, in kilograms; null for every weight. */
    val weightClass: String? = null,
    /** A [LeaderboardGender] value; null for every gender. */
    val gender: String? = null,
    /**
     * The chosen board's entries, fastest first, a page at a time (ui/content-loading), with the viewer's own entry as its meta. A new
     * filter starts from nothing, so no other board's entries show under it.
     */
    val entries: PagedState<LeaderboardEntry, LeaderboardEntry?> = pagedStateOf(),
    /** The viewer's own entry, shown apart from [entries] when it isn't among them; null when they aren't on the board. */
    val viewerEntry: LeaderboardEntry? = null,
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

    /** Shows only athletes in [ageGroup], a [LeaderboardAgeGroup] value, or every age when null, from its first page. */
    data class AgeGroupChanged(val ageGroup: String?) : SegmentLeaderboardEvent

    /** Shows only athletes in [weightClass], a [LeaderboardWeightClass] value, or every weight when null, from its first page. */
    data class WeightClassChanged(val weightClass: String?) : SegmentLeaderboardEvent

    /** Shows only athletes of [gender], a [LeaderboardGender] value, or every gender when null, from its first page. */
    data class GenderChanged(val gender: String?) : SegmentLeaderboardEvent

    /** Loads the next page; ignored while a page is loading or when there are no more. */
    data object LoadMore : SegmentLeaderboardEvent

    /** Tries the page that failed to load again. */
    data object Retry : SegmentLeaderboardEvent

    /** Opens the activity an entry's effort came from. */
    data class OpenActivity(val uri: AtUri) : SegmentLeaderboardEvent
}
