package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.BoardMeta
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry
import io.github.solcott.activitytracker.lexicon.app.SegmentBoard
import io.github.solcott.uistate.PagedState
import io.github.solcott.uistate.pagedStateOf

/**
 * The leaderboard of the active segment at [uri] (segments/segment-leaderboards), titled [name] (its current name, loaded, when null:
 * opened by link, ui/deep-links), opening on [board] (a [SegmentBoard] type), or the AppView's default when null.
 */
@CircuitSerializable(SessionScope::class)
data class SegmentLeaderboardScreen(val uri: String, val name: String? = null, val board: String? = null) : Screen

@Immutable
data class SegmentLeaderboardState(
    val name: String,
    /** The board's choices, whose events the presenter's filters handle. */
    val filters: BoardFilterFields = BoardFilterFields(BoardKind.Leaderboard),
    /**
     * The chosen board's entries, fastest first, a page at a time (ui/content-loading), with the viewer's own entry and the board types as
     * its meta. A new filter starts from nothing, so no other board's entries show under it.
     */
    val entries: PagedState<LeaderboardEntry, BoardMeta<LeaderboardEntry>> = pagedStateOf(),
    /** The viewer's own entry, shown apart from [entries] when it isn't among them; null when they aren't on the board. */
    val viewerEntry: LeaderboardEntry? = null,
    val eventSink: (SegmentLeaderboardEvent) -> Unit = {},
) : CircuitUiState {
    /** Whether [entry]'s rank is drawn in the accent colour: the first three, the podium. */
    fun accents(entry: LeaderboardEntry): Boolean = entry.rank <= PODIUM

    /** Whether [entry] is the viewer's own, marked as theirs wherever it shows. */
    fun isOwn(entry: LeaderboardEntry): Boolean = (entries.meta?.viewer ?: viewerEntry)?.athlete?.did == entry.athlete.did

    /**
     * How far the pinned [viewerEntry]'s time is behind the board's first, as filtered ("51 s behind 1st"); null while there is no pinned
     * entry, the first entry hasn't loaded, or the viewer is first.
     */
    val gap: AppText?
        get() {
            val own = viewerEntry?.takeIf { it.rank != 1 } ?: return null
            val first = entries.items.firstOrNull()?.takeIf { it.rank == 1 } ?: return null
            return BoardGapTexts.behindFirst(own.effort.elapsed - first.effort.elapsed)
        }
}

sealed interface SegmentLeaderboardEvent {
    data object Back : SegmentLeaderboardEvent

    /** Loads the next page; ignored while a page is loading or when there are no more. */
    data object LoadMore : SegmentLeaderboardEvent

    /** Tries the page that failed to load again. */
    data object Retry : SegmentLeaderboardEvent

    /** Opens the activity an entry's effort came from. */
    data class OpenActivity(val uri: AtUri) : SegmentLeaderboardEvent
}

/** The leaderboard ranks drawn in the accent colour. */
private const val PODIUM = 3
