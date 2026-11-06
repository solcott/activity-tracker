package io.github.solcott.activitytracker.app.names

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.subcircuit.SubCircuitInject
import com.slack.circuit.subcircuit.SubPresenter
import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.PostShare
import io.github.solcott.activitytracker.data.segments.SegmentNames
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentNameProposalView
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.time.Clock
import kotlinx.collections.immutable.persistentListOf

/**
 * The name sheet (segments/segment-names): proposes a new name for the place, or edits the athlete's own open proposal, queues it through
 * [SegmentNames] and closes. A blank, over-long or unchanged name isn't sent. It also shares the proposal to the feed (feed/posts): off by
 * default for a new one, and for an edit as the proposal is shared now, read when the sheet opens; an edit that changes only the sharing
 * doesn't rewrite the proposal, so it keeps its votes.
 */
@SubCircuitInject(ProposePlaceNameScreen::class, SessionScope::class)
@Inject
class ProposePlaceNamePresenter(private val screen: ProposePlaceNameScreen, private val names: SegmentNames) :
    SubPresenter<ProposePlaceNameOuterEvent, ProposePlaceNameState> {
    @Composable
    override fun present(outerEventSink: (ProposePlaceNameOuterEvent) -> Unit): ProposePlaceNameState {
        val name = rememberRetained { TextFieldState(screen.proposalName.orEmpty()) }
        val shareText = rememberRetained { TextFieldState() }
        var error by rememberRetained { mutableStateOf<UiError?>(null) }
        var sharing by rememberRetained { mutableStateOf(false) }
        var audience by rememberRetained { mutableStateOf(Visibility.PUBLIC) }
        // For an edit: how the proposal is shared now, once read. A failed read leaves it unknown, and sharing can't be changed.
        var initial by rememberRetained { mutableStateOf<PostShare?>(null) }
        var shareLoaded by rememberRetained { mutableStateOf(screen.proposal == null) }
        val flights = rememberSingleFlight()
        val proposal = screen.proposal
        if (proposal != null) {
            LaunchedEffect(proposal) {
                if (shareLoaded) return@LaunchedEffect
                runSuspendCatching { names.share(AtUri(proposal)) }
                    .onSuccess { share ->
                        initial = share
                        sharing = share != null
                        audience = share?.visibility ?: Visibility.PUBLIC
                        shareText.setTextAndPlaceCursorAtEnd(share?.text.orEmpty())
                        shareLoaded = true
                    }
                    .onFailure { error = UiError.of(UiError.Kind.LoadPostShare, it) }
            }
        }
        val limit =
            when {
                names.canShareBeyondPublic -> ShareLimit.None
                names.spacesUnsupported -> ShareLimit.SpacesUnsupported
                else -> ShareLimit.SignInAgain
            }
        val state =
            ProposePlaceNameState(
                name = name,
                editing = proposal != null,
                unchangedName = screen.proposalName ?: screen.currentName,
                sending = flights.isRunning(ProposePlaceNameEvent.Send),
                error = error,
                sharing = sharing,
                audience = audience,
                shareText = shareText,
                audiences = if (limit == ShareLimit.None) ALL_AUDIENCES else PUBLIC_ONLY,
                shareLimit = limit,
                shareLoading = !shareLoaded,
                initialSharing = initial != null,
                initialAudience = initial?.visibility ?: Visibility.PUBLIC,
                initialShareText = initial?.text.orEmpty(),
            ) {}
        return state.copy { event ->
            when (event) {
                ProposePlaceNameEvent.Send -> {
                    if (state.canSend) {
                        error = null
                        val text = name.text.trim().toString()
                        val share = PostShare(audience, shareText.text.trim().toString().ifEmpty { null }).takeIf { sharing }
                        flights.launch(ProposePlaceNameEvent.Send) {
                            runSuspendCatching { send(state, text, share, initial) }
                                .onSuccess { outerEventSink(ProposePlaceNameOuterEvent.Closed) }
                                .onFailure { error = UiError.of(UiError.Kind.ProposeSegmentName, it) }
                        }
                    }
                }
                ProposePlaceNameEvent.Cancel -> outerEventSink(ProposePlaceNameOuterEvent.Closed)
                is ProposePlaceNameEvent.SharingChanged -> sharing = event.sharing
                is ProposePlaceNameEvent.AudienceChanged -> if (event.audience in state.audiences) audience = event.audience
            }
        }
    }

    /** Queues the proposal (or its rename) and its sharing, as [state] asks. */
    private suspend fun send(state: ProposePlaceNameState, text: String, share: PostShare?, initial: PostShare?) {
        val place = AtUri(screen.place)
        val proposal = screen.proposal
        if (proposal == null) {
            names.propose(place, text, share, screen.sharedPlace)
            return
        }
        val uri = AtUri(proposal)
        if (state.valid) names.edit(place, uri, text)
        if (state.shareChanged) {
            val listed = screen.proposalView ?: fallbackView(uri)
            val shown = if (state.valid) listed.copy(name = text, up = 0, down = 0, openedAt = Clock.System.now()) else listed
            names.setShare(place, shown, initial, share, screen.sharedPlace)
        }
    }

    /** The athlete's proposal as a post shows it, when the host didn't pass its listed view. */
    private fun fallbackView(uri: AtUri) =
        SegmentNameProposalView(
            uri = uri,
            cid = Cid("pending"),
            name = screen.proposalName.orEmpty(),
            proposer = ProfileViewBasic(names.self, Handle("handle.invalid")),
            openedAt = Clock.System.now(),
            up = 0,
            down = 0,
        )

    private companion object {
        val ALL_AUDIENCES = persistentListOf(Visibility.PUBLIC, Visibility.FOLLOWERS, Visibility.MUTUALS)
        val PUBLIC_ONLY = persistentListOf(Visibility.PUBLIC)
    }
}
