package io.github.solcott.activitytracker.app.names

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.lexicon.AtUri

/**
 * A place's names as its page shows them (segments/segment-names): a segment's and a dive site's alike. The holder that owns it
 * (`rememberPlaceNames`) fills [eventSink], so the host declares no name events of its own.
 */
@Immutable
data class PlaceNameFields(
    /** Whether this session can vote on names: false on a PDS without Spaces or a grant from before votes. */
    val canVoteHere: Boolean = true,
    /** The sheet proposing a name, or editing the viewer's own open proposal, while it's open. */
    val sheet: ProposePlaceNameScreen? = null,
    /** The viewer's own open proposal whose deletion is being confirmed. */
    val confirmDelete: AtUri? = null,
    /** Why the last vote or deletion failed. */
    val error: UiError? = null,
    /** The signed-in viewer's DID: the proposals they made are offered Edit and Delete, not votes. */
    val self: String? = null,
    val eventSink: (PlaceNameEvent) -> Unit = {},
)

sealed interface PlaceNameEvent {
    /** Opens the sheet proposing a name, when the viewer may propose. */
    data object ProposeName : PlaceNameEvent

    /** Opens the sheet editing the viewer's own open proposal [proposal]. */
    data class EditProposal(val proposal: AtUri) : PlaceNameEvent

    /** Asks to delete the viewer's own open proposal [proposal]; [ConfirmDelete] answers. */
    data class DeleteProposal(val proposal: AtUri) : PlaceNameEvent

    /** The answer to [DeleteProposal]: delete it when [confirmed]. */
    data class ConfirmDelete(val confirmed: Boolean) : PlaceNameEvent

    /** Votes [direction] (a `SegmentNameVoteDirection` value) on [proposal], or takes the viewer's vote back when null. */
    data class Vote(val proposal: AtUri, val direction: String?) : PlaceNameEvent

    /** What the name sheet reported. */
    data class Sheet(val event: ProposePlaceNameOuterEvent) : PlaceNameEvent
}
