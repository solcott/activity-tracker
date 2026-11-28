package io.github.solcott.activitytracker.app.trim

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.formContent
import io.github.solcott.activitytracker.app.launchAction
import io.github.solcott.activitytracker.app.rememberReload
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.text.StartText
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.PendingWritesException
import io.github.solcott.activitytracker.data.activity.TrimSource
import io.github.solcott.activitytracker.data.activity.trim.ActivityTrimmer
import io.github.solcott.activitytracker.data.activity.trim.KeptSpan
import io.github.solcott.activitytracker.data.activity.trim.TrimDrafts
import io.github.solcott.activitytracker.data.activity.trim.TrimInput
import io.github.solcott.activitytracker.data.activity.trim.TrimPreview
import io.github.solcott.activitytracker.data.activity.trim.TrimSuggestion
import io.github.solcott.activitytracker.data.activity.trim.TrimTotals
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.outcomeFlow
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.TrackMath
import io.github.solcott.activitytracker.textfield.setTextAndPlaceCursorAtEnd
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@CircuitInject(TrimScreen::class, SessionScope::class)
@Composable
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod") // Injected collaborators; one screen's events.
fun TrimPresenter(
    screen: TrimScreen,
    navigator: Navigator,
    feed: FeedRepository,
    manager: ActivityManager,
    segments: SegmentRepository,
    drafts: TrimDrafts,
): TrimState {
    val reload = rememberReload()
    val loaded =
        produceRetainedContentState<TrimLoad?>(null, reload.key) { outcomeFlow { TrimLoad.of(screen, feed, manager, segments, drafts) } }
    val model = (loaded.data as? TrimLoad.Ready)?.model
    var chosen by rememberRetained { mutableStateOf<KeptSpan?>(null) }
    var byDistance by rememberRetained { mutableStateOf(false) }
    var exact by remember { mutableStateOf<TrimExactValue?>(null) }
    var confirming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()

    val span = model?.let { chosen ?: it.initial }
    val distanceMode = byDistance && model?.preview?.distance != null
    val kept = remember(model, span) { if (model != null && span != null) model.keptPolyline(span) else null }
    val editor = if (model != null && span != null) model.editor(span, distanceMode, kept) else null

    // Events read the span and mode as they are now, not as the state they came from showed them: a sink kept from an earlier state
    // (a native view's, or a fast second event) still acts on the latest.
    fun currentSpan(): KeptSpan? = model?.let { chosen ?: it.initial }

    fun currentMode(): Boolean = byDistance && model?.preview?.distance != null

    fun currentEditor(): TrimEditor? = model?.let { m -> currentSpan()?.let { m.editor(it, currentMode(), kept = null) } }

    fun move(from: Int, to: Int) {
        val m = model ?: return
        val current = currentSpan() ?: return
        chosen = m.clamp(current, from, to, currentMode())
    }

    fun seedExact(start: Boolean, units: UnitSystem) {
        val e = currentEditor() ?: return
        val field = exact?.takeIf { it.start == start }?.field ?: textFieldStateOf("")
        field.setTextAndPlaceCursorAtEnd(TrimValues.format(e, start, units))
        exact = TrimExactValue(start, field)
    }

    return TrimState(
        content = loaded.formContent(seeded = loaded.data != null),
        editor = editor,
        draft = screen.draftId != null,
        notSent = loaded.data is TrimLoad.NotSent,
        exact = exact,
        confirming = confirming,
        saving = flights.isRunning(TrimEvent.Confirm),
        error = error,
    ) { event ->
        when (event) {
            TrimEvent.Back -> navigator.pop()
            TrimEvent.Retry -> reload.retry()
            is TrimEvent.Moved -> move(event.from, event.to)
            is TrimEvent.ModeChanged -> byDistance = event.byDistance && model?.preview?.distance != null
            TrimEvent.ApplySuggestion -> model?.suggestion?.let { chosen = it.span }
            TrimEvent.Reset -> chosen = model?.let { KeptSpan.whole(it.preview.elapsed) }
            is TrimEvent.Nudge -> {
                val e = currentEditor()
                if (exact == null && e != null && model != null) {
                    val target = TrimValues.nudge(e, model.preview, event)
                    if (event.start) move(target, e.to) else move(e.from, target)
                }
            }
            is TrimEvent.EditExact -> seedExact(event.start, event.units)
            is TrimEvent.StepExact -> {
                val open = exact
                val e = currentEditor()
                if (open != null && e != null && model != null) {
                    val target = TrimValues.step(e, model.preview, open.start, event.later, event.units)
                    if (open.start) move(target, e.to) else move(e.from, target)
                    currentEditor()?.let { open.field.setTextAndPlaceCursorAtEnd(TrimValues.format(it, open.start, event.units)) }
                    exact = open.copy(invalid = false)
                }
            }
            is TrimEvent.ApplyExact -> {
                val open = exact
                val e = currentEditor()
                if (open != null && e != null && model != null) {
                    val target = TrimValues.parse(open.field.text.toString(), e, model.preview, open.start, event.units)
                    if (target == null) {
                        exact = open.copy(invalid = true)
                    } else {
                        if (open.start) move(target, e.to) else move(e.from, target)
                        exact = null
                    }
                }
            }
            TrimEvent.CancelExact -> exact = null
            TrimEvent.Save -> {
                val e = currentEditor()
                val draftId = screen.draftId
                when {
                    e == null -> Unit
                    draftId != null -> {
                        drafts.apply(draftId, e.span)
                        navigator.pop()
                    }
                    e.changed -> confirming = true
                }
            }
            TrimEvent.CancelConfirm -> confirming = false
            TrimEvent.Confirm -> {
                val source = model?.source
                val e = currentEditor()
                if (source != null && e != null) {
                    confirming = false
                    flights.launchAction(TrimEvent.Confirm, UiError.Kind.SaveTrim, { error = it }) {
                        error = null
                        manager.trim(source, e.span)
                        navigator.pop()
                    }
                }
            }
        }
    }
}

/** What the screen loaded: an activity to trim, or one that still has writes waiting to be sent. */
internal sealed interface TrimLoad {
    data object NotSent : TrimLoad

    class Ready(val model: TrimModel) : TrimLoad

    companion object {
        @Suppress("ReturnCount") // A draft, a saved activity, or one still being sent.
        suspend fun of(
            screen: TrimScreen,
            feed: FeedRepository,
            manager: ActivityManager,
            segments: SegmentRepository,
            drafts: TrimDrafts,
        ): TrimLoad {
            val draftId = screen.draftId
            if (draftId != null) {
                val draft = checkNotNull(drafts.draft(draftId)) { "No draft is offered under $draftId" }
                return Ready(
                    TrimModel(
                        input = draft.input,
                        before = draft.totals,
                        sport = draft.sport,
                        utcOffsetMinutes = draft.utcOffsetMinutes,
                        suggestion = draft.suggestion,
                        initial = draft.span,
                        efforts = persistentListOf(),
                        source = null,
                    ),
                )
            }
            val uri = AtUri(checkNotNull(screen.uri) { "A trim names an activity or a draft" })
            val view = feed.activity(uri)
            val source =
                try {
                    manager.loadForTrim(view)
                } catch (_: PendingWritesException) {
                    return NotSent
                }
            // Efforts are only notes: without them (or when they can't load) the trim still opens.
            val efforts = runSuspendCatching {
                segments.activitySegments(uri)
            }
                .getOrNull()
                .orEmpty()
                .map { TrimEffort(it.segment.name, it.startOffset.toInt(), (it.startOffset + it.elapsed).toInt()) }
            val record = source.record
            return Ready(
                TrimModel(
                    input = source.input,
                    before =
                        TrimTotals(
                            distanceMeters = record.distance?.toDouble(),
                            movingSeconds = record.movingTime,
                            elapsedSeconds = record.elapsedTime,
                            gainMeters = record.elevationGain?.toDouble(),
                        ),
                    sport = record.sport,
                    utcOffsetMinutes = StartText.startValue(record).utcOffsetMinutes,
                    suggestion = TrimSuggestion.of(source.input),
                    initial = null,
                    efforts = efforts.toImmutableList(),
                    source = source,
                ),
            )
        }
    }
}

/** An activity loaded for trimming, read once: what every handle move is worked out from. */
@Suppress("LongParameterList") // What the screen shows of an activity.
internal class TrimModel(
    val input: TrimInput,
    val before: TrimTotals,
    val sport: String,
    val utcOffsetMinutes: Int?,
    val suggestion: TrimSuggestion?,
    initial: KeptSpan?,
    val efforts: ImmutableList<TrimEffort>,
    /** The saved activity, null for a draft. */
    val source: TrimSource?,
) {
    val preview = TrimPreview.of(input)
    val initial: KeptSpan = initial ?: KeptSpan.whole(preview.elapsed)
    private val route: List<LocationSample> = input.route.takeIf { it.size >= 2 }.orEmpty()
    private val polyline: String? =
        route.takeIf { it.isNotEmpty() }?.let { TrackMath.encodePolyline(TrackMath.simplify(it, OWN_TOLERANCE_M)) }
    private val profile: TrimProfile? = profileOf(preview)

    /** The kept part of the route, encoded; null without GPS, or while nothing is cut. */
    fun keptPolyline(span: KeptSpan): String? {
        if (route.isEmpty() || span.isWhole(preview.elapsed)) return null
        val kept = ActivityTrimmer.route(route, input.startedAt, span)
        return kept.takeIf { it.size >= 2 }?.let { TrackMath.encodePolyline(TrackMath.simplify(it, OWN_TOLERANCE_M)) }
    }

    fun editor(span: KeptSpan, byDistance: Boolean, kept: String?): TrimEditor {
        val distance = preview.distance
        return TrimEditor(
            elapsed = preview.elapsed,
            from = span.from,
            to = span.to,
            byDistance = byDistance,
            offersDistance = distance != null,
            manual = preview.time.isEmpty(),
            startedAtEpochSeconds = input.startedAt.epochSeconds,
            utcOffsetMinutes = utcOffsetMinutes,
            fromMeters = distance?.at(span.from),
            toMeters = distance?.at(span.to),
            totalMeters = distance?.total,
            sport = sport,
            before = before,
            after = preview.totals(before, span),
            profile = profile,
            polyline = polyline,
            kept = kept,
            efforts = efforts,
            suggestion = suggestion,
        )
    }

    /**
     * The span the handles moved to: [from] and [to] within the activity, a minute apart (the end that moved stops at the other), and by
     * distance a start snapped to the last sample at its distance and an end to the first, so standing there is cut too.
     */
    fun clamp(current: KeptSpan, from: Int, to: Int, byDistance: Boolean): KeptSpan {
        val end = preview.elapsed
        val gap = minOf(KeptSpan.MIN_SECONDS, end)
        val startMoved = from != current.from
        val endMoved = to != current.to
        var f = from.coerceIn(0, (end - gap).coerceAtLeast(0))
        var t = to.coerceIn(gap, end.coerceAtLeast(gap))
        val distance = preview.distance
        if (byDistance && distance != null) {
            if (startMoved) f = distance.startAt(distance.at(f))
            if (endMoved) t = distance.endAt(distance.at(t))
        }
        if (t - f < gap) {
            if (startMoved && !endMoved) f = (t - gap).coerceAtLeast(0) else t = (f + gap).coerceAtMost(end)
            if (t - f < gap) f = (t - gap).coerceAtLeast(0)
        }
        return KeptSpan(f, t)
    }

    private companion object {
        const val OWN_TOLERANCE_M = 1.0
        const val PROFILE_POINTS = 600

        /** [preview]'s elevation, else its speed, thinned to [PROFILE_POINTS]; null for a manual entry or no values. */
        @Suppress("ReturnCount") // No samples, or no values to draw.
        fun profileOf(preview: TrimPreview): TrimProfile? {
            val time = preview.time
            if (time.size < 2) return null
            val altitude = preview.altitude
            val values = altitude ?: preview.speed ?: return null
            val stride = (time.size + PROFILE_POINTS - 1) / PROFILE_POINTS
            val picked =
                (time.indices step stride.coerceAtLeast(1)).toMutableList().apply { if (last() != time.lastIndex) add(time.lastIndex) }
            val distance = preview.distance
            return TrimProfile(
                time = picked.map { time[it] }.toImmutableList(),
                values = picked.map { values[it] }.toImmutableList(),
                elevation = altitude != null,
                distance = distance?.let { d -> picked.map { d.at(time[it]) }.toImmutableList() },
            )
        }
    }
}
