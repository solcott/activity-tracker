package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.boards.RankBoards
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.BoardMeta
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import io.github.solcott.activitytracker.lexicon.app.SegmentBoard
import io.github.solcott.uistate.PagedState
import io.github.solcott.uistate.pagedStateOf

/**
 * The Mayors board of the active segment at [uri] (segments/segment-mayors), titled [name] (its current name, loaded, when null: opened by
 * link, ui/deep-links): athletes by how many efforts they have on it. It opens on [board] (a [SegmentBoard] type), or the AppView's default
 * when null.
 */
@CircuitSerializable(SessionScope::class)
data class SegmentMayorsScreen(val uri: String, val name: String? = null, val board: String? = null) : Screen

@Immutable
data class SegmentMayorsState(
    val name: String,
    /** The board's choices, whose events the presenter's filters handle. */
    val filters: BoardFilterFields = BoardFilterFields(BoardKind.Mayors),
    /**
     * The chosen board's entries, most efforts first, a page at a time (ui/content-loading), with the viewer's own entry and the board
     * types as its meta. A new choice starts from nothing, so no other board's entries show under it.
     */
    val entries: PagedState<MayorEntry, BoardMeta<MayorEntry>> = pagedStateOf(),
    /** The viewer's own entry, shown apart from [entries] when it isn't among them; null when they aren't on the board. */
    val viewerEntry: MayorEntry? = null,
    val eventSink: (SegmentMayorsEvent) -> Unit = {},
) : CircuitUiState {
    /** Whether [entry]'s rank is drawn in the accent colour: the Mayor alone. */
    fun accents(entry: MayorEntry): Boolean = entry.rank == 1

    /** Whether [entry] is the board's one Mayor, whose rank is drawn as a crown (segments/segment-mayors). */
    fun crowned(entry: MayorEntry): Boolean = entry.rank == 1

    /** Whether [entry] is the viewer's own, marked as theirs wherever it shows. */
    fun isOwn(entry: MayorEntry): Boolean = RankBoards.isOwn((entries.meta?.viewer ?: viewerEntry)?.athlete, entry.athlete)

    /**
     * What the pinned [viewerEntry] needs to be Mayor ("8 more efforts to be Mayor"): the first entry's count less theirs, plus one, since
     * a level count reached later ranks below. Null while there is no pinned entry, the first entry hasn't loaded, or the viewer is first.
     */
    val gap: AppText?
        get() {
            val own = viewerEntry ?: return null
            val first = entries.items.firstOrNull()?.takeIf { RankBoards.showsGap(own.rank, it.rank) } ?: return null
            return BoardGapTexts.moreToBeMayor(first.efforts - own.efforts + 1)
        }
}

sealed interface SegmentMayorsEvent {
    data object Back : SegmentMayorsEvent

    /** Loads the next page; ignored while a page is loading or when there are no more. */
    data object LoadMore : SegmentMayorsEvent

    /** Tries the page that failed to load again. */
    data object Retry : SegmentMayorsEvent

    /** Opens an entry's athlete's profile. */
    data class OpenAthlete(val did: Did) : SegmentMayorsEvent
}
