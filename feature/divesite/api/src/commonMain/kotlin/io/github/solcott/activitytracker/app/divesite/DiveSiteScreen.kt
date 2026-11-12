package io.github.solcott.activitytracker.app.divesite

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.moderation.Revealed
import io.github.solcott.activitytracker.app.names.PlaceNameFields
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DiveSiteConditions
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRegularEntry
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
import io.github.solcott.activitytracker.lexicon.app.SegmentNameAdoption
import io.github.solcott.activitytracker.lexicon.app.SegmentNameProposalView
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.PagedState
import io.github.solcott.uistate.pagedStateOf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The dive site at [uri] (dive-sites): its centre and radius on a map, its names, its conditions in the viewer's units, its top Regular and
 * the dives at it the viewer may see, newest first.
 */
@CircuitSerializable(SessionScope::class) data class DiveSiteScreen(val uri: String) : Screen

@Immutable
data class DiveSiteState(
    /** The site, with whether it's loading or failed to; null until it first loads. */
    val content: ContentState<DiveSiteView?> = ContentState(null),
    /** The site's radius as a closed precision-5 polyline around its centre, as the maps draw routes; null until loaded. */
    val outline: String? = null,
    /** The site's conditions (dive-sites/site-conditions); null while loading, when it has none, or when they failed to load. */
    val conditions: DiveSiteConditions? = null,
    /**
     * The all-time leader of the Regulars board and the viewer's own entry (dive-sites/site-regulars); null until it loads, or for a site
     * that isn't active.
     */
    val regulars: RegularsSummary? = null,
    /** The dives at the site the viewer may see, newest first, a page at a time (ui/content-loading). */
    val dives: PagedState<ActivityView, Unit> = pagedStateOf(),
    /** The site's names: the sheet, a deletion being confirmed, the last failure, and their events. */
    val names: PlaceNameFields = PlaceNameFields(),
    /** The signed-in viewer's DID, whose own dives' cards show their locks. */
    val viewerDid: String = "",
    /** Labeled photos the viewer revealed on this screen. */
    val revealed: Revealed = Revealed(),
    val eventSink: (DiveSiteEvent) -> Unit = {},
) : CircuitUiState {
    /** [content]'s site: the last loaded, or null before the first. */
    val site: DiveSiteView?
        get() = content.data

    /** The site's original name (its record's) when a community name replaced it, else null. */
    val originalName: String?
        get() = site?.let { it.record.name.takeIf { original -> original != it.currentName } }

    /** Propose a name is offered: the AppView says the viewer may propose now. */
    val canPropose: Boolean
        get() = site?.viewer?.canProposeName == true

    /** The viewer may vote, having visited the site; [PlaceNameFields.canVoteHere] says whether this session can send it. */
    val canVote: Boolean
        get() = site?.viewer?.canVoteOnNames == true

    /** The site's open name proposals, for a UI that can't read the lexicon view's list. */
    val proposals: ImmutableList<SegmentNameProposalView>
        get() = site?.nameProposals.orEmpty().toImmutableList()

    /** The site's adopted names, newest first. */
    val history: ImmutableList<SegmentNameAdoption>
        get() = site?.nameHistory.orEmpty().toImmutableList()
}

/** An active site's Regulars board in brief: its [leader], null when it has none yet, and the [viewer]'s own entry when on it. */
@Immutable data class RegularsSummary(val leader: DiveSiteRegularEntry?, val viewer: DiveSiteRegularEntry?)

sealed interface DiveSiteEvent {
    data object Back : DiveSiteEvent

    /** Loads the site again after it failed to load ([DiveSiteState.content]). */
    data object Retry : DiveSiteEvent

    /** Opens a dive at the site. */
    data class OpenDive(val uri: AtUri) : DiveSiteEvent

    /** Loads the next page of dives; ignored while a page is loading or when there are no more. */
    data object LoadMoreDives : DiveSiteEvent

    /** Tries the page of dives that failed to load again. */
    data object RetryDives : DiveSiteEvent

    /** Opens the earlier site a duplicate duplicates. */
    data object OpenOriginal : DiveSiteEvent

    /** Opens the site's Regulars board; offered only for an active site. */
    data object OpenRegulars : DiveSiteEvent
}
