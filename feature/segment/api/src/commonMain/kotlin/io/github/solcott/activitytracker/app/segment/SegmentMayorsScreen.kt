package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.BoardMeta
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.LeaderboardAgeGroup
import io.github.solcott.activitytracker.lexicon.app.LeaderboardGender
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.LeaderboardWeightClass
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import io.github.solcott.activitytracker.lexicon.app.MayorPeriod
import io.github.solcott.activitytracker.lexicon.app.SegmentBoard
import io.github.solcott.uistate.PagedState
import io.github.solcott.uistate.pagedStateOf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The Mayors board of the active segment at [uri] (segments/segment-mayors), titled [name]: athletes by how many efforts they have on it.
 * It opens on [board] (a [SegmentBoard] type), or the AppView's default when null.
 */
@CircuitSerializable(SessionScope::class)
data class SegmentMayorsScreen(val uri: String, val name: String, val board: String? = null) : Screen

@Immutable
data class SegmentMayorsState(
    val name: String,
    /** A [MayorPeriod] value: the window the board counts. */
    val period: String = MayorPeriod.LAST_90_DAYS,
    /** A [LeaderboardScope] value. */
    val scope: String = LeaderboardScope.EVERYONE,
    /** The board type shown (a [SegmentBoard] value), as the AppView served it; null until it answers, or when the segment has none. */
    val board: String? = null,
    /** The types the viewer may choose, most efforts first; a menu when there are two or more. */
    val boards: ImmutableList<String> = persistentListOf(),
    /** A [LeaderboardAgeGroup] value: only athletes in it (segments/leaderboard-brackets); null for every age. */
    val ageGroup: String? = null,
    /** A [LeaderboardWeightClass] value, in kilograms; null for every weight. */
    val weightClass: String? = null,
    /** A [LeaderboardGender] value; null for every gender. */
    val gender: String? = null,
    /**
     * The chosen board's entries, most efforts first, a page at a time (ui/content-loading), with the viewer's own entry and the board
     * types as its meta. A new choice starts from nothing, so no other board's entries show under it.
     */
    val entries: PagedState<MayorEntry, BoardMeta<MayorEntry>> = pagedStateOf(),
    /** The viewer's own entry, shown apart from [entries] when it isn't among them; null when they aren't on the board. */
    val viewerEntry: MayorEntry? = null,
    val eventSink: (SegmentMayorsEvent) -> Unit = {},
) : CircuitUiState

sealed interface SegmentMayorsEvent {
    data object Back : SegmentMayorsEvent

    /** Shows the board for [period], a [MayorPeriod] value, from its first page. */
    data class PeriodChanged(val period: String) : SegmentMayorsEvent

    /** Shows the board for [scope], a [LeaderboardScope] value, from its first page. */
    data class ScopeChanged(val scope: String) : SegmentMayorsEvent

    /** Shows the board of [board], a listed [SegmentBoard] type, from its first page; ignored for the type already shown. */
    data class BoardChanged(val board: String) : SegmentMayorsEvent

    /** Shows only athletes in [ageGroup], a [LeaderboardAgeGroup] value, or every age when null, from its first page. */
    data class AgeGroupChanged(val ageGroup: String?) : SegmentMayorsEvent

    /** Shows only athletes in [weightClass], a [LeaderboardWeightClass] value, or every weight when null, from its first page. */
    data class WeightClassChanged(val weightClass: String?) : SegmentMayorsEvent

    /** Shows only athletes of [gender], a [LeaderboardGender] value, or every gender when null, from its first page. */
    data class GenderChanged(val gender: String?) : SegmentMayorsEvent

    /** Loads the next page; ignored while a page is loading or when there are no more. */
    data object LoadMore : SegmentMayorsEvent

    /** Tries the page that failed to load again. */
    data object Retry : SegmentMayorsEvent

    /** Opens an entry's athlete's profile. */
    data class OpenAthlete(val did: Did) : SegmentMayorsEvent
}
