package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.retained.rememberRetained
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.segments.TrailContributions
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.TrailDetailVoteDirection
import kotlinx.collections.immutable.toImmutableList

/**
 * [segment]'s trail detail contributions as its page shows them (segments/trail-detail-suggestions), with the sink for every [TrailEvent]:
 * a detail's sheet, confirming it, voting on suggestions, the suggest picker and the quick check after a recent ride. The viewer's own
 * changes show through [contributions]' overlay on the host's read, so [segment] is already the segment as they changed it.
 */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod") // An event per action on a detail, its suggestions and the quick check.
fun rememberTrailFields(segment: SegmentView?, contributions: TrailContributions): TrailFields {
    val flights = rememberSingleFlight()
    var open by rememberRetained { mutableStateOf<String?>(null) }
    var picking by rememberRetained { mutableStateOf<String?>(null) }
    var selected by rememberRetained { mutableStateOf<Int?>(null) }
    var error by rememberRetained { mutableStateOf<UiError?>(null) }
    var quickDue by rememberRetained { mutableStateOf(false) }
    val self = contributions.self.value
    val canVote = contributions.canVote && segment?.viewer?.canVoteOnTrailDetails == true
    val recent = segment?.viewer?.recentEffort == true

    // Shown once per segment per season on this device, to a recent rider (segments/trail-detail-suggestions).
    LaunchedEffect(segment?.uri, recent, canVote) {
        quickDue = segment != null && recent && canVote && contributions.quickCheckDue(segment.uri)
    }
    val quickCheck = segment?.takeIf { quickDue }?.let(TrailContributionTexts::quickCheck)
    // Once every listed detail is answered, the card is done for the season.
    LaunchedEffect(quickCheck?.rows?.all { it.answer != null }) {
        if (segment != null && quickCheck != null && quickCheck.rows.all { it.answer != null }) {
            contributions.quickCheckDone(segment.uri)
            quickDue = false
        }
    }

    fun confirm(view: SegmentView, key: String, still: Boolean) {
        val trail = view.trail ?: return
        val current = view.trailDetails.firstOrNull { it.detail == key }?.viewerConfirmation
        val wanted = if (still) TrailDetailVoteDirection.STILL else TrailDetailVoteDirection.CHANGED
        val direction = wanted.takeIf { current?.direction != it }
        error = null
        flights.launch("confirm:$key") {
            runSuspendCatching { contributions.confirm(view.uri, key, trail, direction, current) }
                .onFailure { error = UiError.of(UiError.Kind.VoteTrailDetail, it) }
        }
    }

    fun vote(view: SegmentView, uri: String, up: Boolean) {
        val suggestion = view.trailSuggestions.firstOrNull { it.uri.value == uri } ?: return
        if (suggestion.author.did.value == self) return
        val wanted = if (up) TrailDetailVoteDirection.UP else TrailDetailVoteDirection.DOWN
        val direction = wanted.takeIf { suggestion.viewerVote?.direction != it }
        error = null
        flights.launch(uri) {
            runSuspendCatching { contributions.vote(view.uri, suggestion, direction, suggestion.viewerVote) }
                .onFailure { error = UiError.of(UiError.Kind.VoteTrailDetail, it) }
        }
    }

    fun mayOpenPicker(view: SegmentView, key: String) =
        view.viewer?.canSuggestTrailDetails == true && key !in view.viewer?.closedTrailDetails.orEmpty()

    return TrailFields(
        canVoteHere = contributions.canVote,
        missing = TrailContributionTexts.missing(segment).toImmutableList(),
        sheet = segment?.let { view -> open?.let { TrailContributionTexts.sheet(view, it, self) } },
        picker = segment?.let { view -> picking?.let { TrailContributionTexts.picker(view, it).copy(selected = selected) } },
        quickCheck = quickCheck,
        error = error,
    ) { event ->
        val view = segment ?: return@TrailFields
        when (event) {
            is TrailEvent.Open -> if (canVote) open = event.key
            TrailEvent.Close -> open = null
            is TrailEvent.Confirm -> {
                val key = open ?: return@TrailFields
                if (!canVote) return@TrailFields
                confirm(view, key, event.still)
                // A regular who says it changed goes on to say what to (segments/trail-detail-suggestions).
                if (!event.still && mayOpenPicker(view, key)) {
                    picking = key
                    selected = null
                }
            }
            is TrailEvent.Vote -> if (canVote) vote(view, event.uri, event.up)
            is TrailEvent.Suggest ->
                if (mayOpenPicker(view, event.key)) {
                    picking = event.key
                    selected = null
                }
            is TrailEvent.Pick -> {
                val key = picking ?: return@TrailFields
                val option = TrailContributionTexts.picker(view, key).options.getOrNull(event.index)
                if (option != null && !option.current) selected = event.index
            }
            TrailEvent.Send -> {
                val key = picking ?: return@TrailFields
                val index = selected ?: return@TrailFields
                val value = TrailContributionTexts.optionValue(key, index) ?: return@TrailFields
                val option = TrailContributionTexts.picker(view, key).options.getOrNull(index)
                picking = null
                selected = null
                if (option?.suggested == true) {
                    // Choosing a value already suggested agrees with it rather than suggesting it again.
                    view.trailSuggestions
                        .firstOrNull { it.detail == key && TrailContributionTexts.valueText(key, it.value) == option.text }
                        ?.let { vote(view, it.uri.value, up = true) }
                } else {
                    error = null
                    flights.launch("suggest:$key") {
                        runSuspendCatching { contributions.suggest(view.uri, key, value) }
                            .onFailure { error = UiError.of(UiError.Kind.SuggestTrailDetail, it) }
                    }
                }
            }
            TrailEvent.ClosePicker -> {
                picking = null
                selected = null
            }
            is TrailEvent.Answer -> {
                if (!canVote) return@TrailFields
                confirm(view, event.key, event.still)
                if (!event.still) open = event.key
            }
            TrailEvent.AllStillRight -> {
                quickCheck?.rows?.filter { it.answer == null }?.forEach { confirm(view, it.key, still = true) }
                flights.launch(TrailEvent.AllStillRight) { contributions.quickCheckDone(view.uri) }
                quickDue = false
            }
            TrailEvent.NotNow -> {
                flights.launch(TrailEvent.NotNow) { contributions.quickCheckDone(view.uri) }
                quickDue = false
            }
            TrailEvent.DismissError -> error = null
        }
    }
}
