package io.github.solcott.activitytracker.app.names

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.retained.rememberRetained
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.segments.SegmentNames
import io.github.solcott.activitytracker.data.segments.SharedPlace
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.SegmentNameProposalView
import kotlinx.collections.immutable.ImmutableList

/**
 * The names of [place] (a segment or a dive site) as its page shows them, with the sink for every [PlaceNameEvent]: the name sheet,
 * deleting the viewer's own open proposal after asking, and voting on others'. [currentName], [proposals], [canPropose] and [canVote] come
 * from the place as last loaded (empty and false before it loads); the viewer's own changes show through [names]' overlay on the host's
 * read.
 */
@Composable
fun rememberPlaceNames(
    place: AtUri,
    currentName: String,
    proposals: ImmutableList<SegmentNameProposalView>,
    canPropose: Boolean,
    canVote: Boolean,
    /** The place as a post sharing a proposal names it ([sharedPlaceOf]); null before it loads. */
    sharedPlace: SharedPlace?,
    names: SegmentNames,
): PlaceNameFields {
    val flights = rememberSingleFlight()
    var sheet by rememberRetained { mutableStateOf<ProposePlaceNameScreen?>(null) }
    var confirmDelete by rememberRetained { mutableStateOf<AtUri?>(null) }
    var error by rememberRetained { mutableStateOf<UiError?>(null) }
    /** The viewer's own open proposal [proposal], as listed. */
    fun own(proposal: AtUri) = proposals.firstOrNull { it.uri == proposal && it.proposer.did == names.self }

    return PlaceNameFields(
        canVoteHere = names.canVote,
        sheet = sheet,
        confirmDelete = confirmDelete,
        error = error,
        self = names.self.value,
    ) { event ->
        when (event) {
            PlaceNameEvent.ProposeName ->
                if (canPropose) sheet = ProposePlaceNameScreen(place.value, currentName, sharedPlace = sharedPlace)
            is PlaceNameEvent.EditProposal ->
                own(event.proposal)?.let {
                    sheet =
                        ProposePlaceNameScreen(
                            place.value,
                            currentName,
                            it.uri.value,
                            it.name,
                            proposalView = it,
                            sharedPlace = sharedPlace,
                        )
                }
            is PlaceNameEvent.DeleteProposal -> if (own(event.proposal) != null) confirmDelete = event.proposal
            is PlaceNameEvent.ConfirmDelete -> {
                val proposal = confirmDelete
                confirmDelete = null
                if (event.confirmed && proposal != null) {
                    error = null
                    flights.launch(proposal) {
                        runSuspendCatching { names.delete(place, proposal) }
                            .onFailure { error = UiError.of(UiError.Kind.DeleteSegmentName, it) }
                    }
                }
            }
            is PlaceNameEvent.Vote -> {
                val proposal = proposals.firstOrNull { it.uri == event.proposal }
                if (proposal != null && canVote && proposal.proposer.did != names.self) {
                    error = null
                    flights.launch(proposal.uri) {
                        runSuspendCatching { names.vote(place, proposal, event.direction, proposal.viewerVote) }
                            .onFailure { error = UiError.of(UiError.Kind.VoteSegmentName, it) }
                    }
                }
            }
            is PlaceNameEvent.Sheet ->
                when (event.event) {
                    ProposePlaceNameOuterEvent.Closed -> sheet = null
                }
        }
    }
}
