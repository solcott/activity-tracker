package io.github.solcott.activitytracker.app.trim

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.data.activity.trim.KeptSpan
import io.github.solcott.activitytracker.data.activity.trim.TrimSuggestion
import io.github.solcott.activitytracker.data.activity.trim.TrimTotals
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.TrackMath
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.uistate.ContentState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The trim screen's samples (feed/activity-trim): a 1:19 ride that starts with 6 minutes standing in a car park and ends with a stop and a
 * drive home, trimmed and untrimmed, by time and by distance; an indoor ride with no map; a manual entry; and the screen's other moments.
 */
@Suppress("MagicNumber") // A made-up ride's times, distances and climbs.
object TrimSamples {
    private const val ELAPSED = 4740
    private const val STANDING = 360
    private const val RIDE_END = 4140
    private const val SPEED = 8.0
    private const val POINTS = 80

    // A function, not a field: LocationSample isn't a stable type, and samples hold only stable state.
    private fun route(): List<LocationSample> =
        (0..POINTS)
            .map { i ->
                val angle = 2 * PI * i / POINTS
                LocationSample(
                    latitude = 44.26 + 0.03 * sin(angle),
                    longitude = -72.58 + 0.05 * cos(angle) * (1 + 0.2 * sin(3 * angle)),
                    altitude = null,
                    speed = null,
                    accuracy = null,
                    time = Fixed.NOW,
                )
            }
            .toImmutableList()

    private fun polylineOf(points: List<LocationSample>) = TrackMath.encodePolyline(points)

    private val profile: TrimProfile
        get() {
            val times = (0..ELAPSED step 30).toList()
            return TrimProfile(
                time = times.toImmutableList(),
                values = times.map { t -> 220.0 + 90 * sin(t / 900.0) + 30 * sin(t / 170.0) }.toImmutableList(),
                elevation = true,
                distance = times.map { metersAt(it) }.toImmutableList(),
            )
        }

    private fun metersAt(t: Int): Double =
        when {
            t <= STANDING -> 0.0
            t <= RIDE_END -> (t - STANDING) * SPEED
            t <= RIDE_END + 540 -> (RIDE_END - STANDING) * SPEED
            else -> (RIDE_END - STANDING) * SPEED + (t - RIDE_END - 540) * 13.6
        }

    private val before = TrimTotals(distanceMeters = 33_500.0, movingSeconds = 4_020, elapsedSeconds = ELAPSED.toLong(), gainMeters = 612.0)

    private val suggestion =
        TrimSuggestion(KeptSpan(STANDING, RIDE_END), TrimSuggestion.StartCut(STANDING), TrimSuggestion.EndCut.Travel(780, 3_264.0))

    private fun editor(from: Int = 0, to: Int = ELAPSED, byDistance: Boolean = false): TrimEditor {
        val route = route()
        val keptPoints = route.subList(route.size * from / ELAPSED, (route.size * to / ELAPSED).coerceAtLeast(2))
        return TrimEditor(
            elapsed = ELAPSED,
            from = from,
            to = to,
            byDistance = byDistance,
            offersDistance = true,
            manual = false,
            startedAtEpochSeconds = Fixed.NOW.epochSeconds - 7_200,
            utcOffsetMinutes = -240,
            fromMeters = metersAt(from),
            toMeters = metersAt(to),
            totalMeters = metersAt(ELAPSED),
            sport = "ride",
            before = before,
            after =
                if (from == 0 && to == ELAPSED) before
                else TrimTotals(metersAt(to) - metersAt(from), (to - from - 540).toLong().coerceAtLeast(60), (to - from).toLong(), 598.0),
            profile = profile,
            polyline = polylineOf(route),
            kept = if (from == 0 && to == ELAPSED) null else polylineOf(keptPoints),
            efforts = persistentListOf(TrimEffort("Ridgeline Climb", 3_900, 4_380)),
            suggestion = suggestion,
        )
    }

    private fun state(content: ContentState<Unit>, editor: TrimEditor?) =
        TrimState(content = content, editor = editor, draft = false, eventSink = {})

    /** The ride as it was recorded, nothing cut yet. */
    val loaded: TrimState
        get() = state(ContentStates.loaded(Unit), editor())

    /** The suggestion applied: the car park and the drive home cut. */
    val trimmed: TrimState
        get() = state(ContentStates.loaded(Unit), editor(STANDING, RIDE_END))

    /** Cut by distance, the start at 0.4 km, the end cutting into an effort. */
    val byDistance: TrimState
        get() = state(ContentStates.loaded(Unit), editor(STANDING + 50, 3_800, byDistance = true))

    /** A treadmill run: distance but no GPS, so no map. */
    val indoor: TrimState
        get() =
            state(
                ContentStates.loaded(Unit),
                editor(0, 2_400)
                    .copy(elapsed = 2_700, polyline = null, kept = null, efforts = persistentListOf(), suggestion = null, sport = "run"),
            )

    /** A manual entry: no samples, so only its times move, and Distance is off. */
    val manual: TrimState
        get() =
            state(
                ContentStates.loaded(Unit),
                editor(600, 3_600)
                    .copy(
                        elapsed = 3_600,
                        offersDistance = false,
                        manual = true,
                        fromMeters = null,
                        toMeters = null,
                        totalMeters = null,
                        profile = null,
                        polyline = null,
                        kept = null,
                        efforts = persistentListOf(),
                        suggestion = null,
                        before = TrimTotals(12_000.0, null, 3_600, null),
                        after = TrimTotals(12_000.0, null, 3_000, null),
                    ),
            )

    /** Asking before the trim is written. */
    val confirming: TrimState
        get() = trimmed.copy(confirming = true)

    /** The exact value of the start, open. */
    val exactValue: TrimState
        get() = trimmed.copy(exact = TrimExactValue(start = true, field = textFieldStateOf("6:00")))

    /** An unsaved recording, whose Done keeps the span for it. */
    val draft: TrimState
        get() = trimmed.copy(draft = true)

    /** The activity still has writes waiting to be sent. */
    val notSent: TrimState
        get() = state(ContentStates.loaded(Unit), null).copy(notSent = true)

    /** The save failed. */
    val saveFailed: TrimState
        get() = trimmed.copy(error = UiError(UiError.Kind.SaveTrim))

    val all: Map<String, TrimState>
        get() =
            mapOf(
                SampleKind.LOADING to state(ContentStates.loading(Unit), null),
                SampleKind.FAILED to state(ContentStates.failed(Unit), null),
                SampleKind.LOADED to loaded,
                "trimmed" to trimmed,
                "byDistance" to byDistance,
                "indoor" to indoor,
                "manual" to manual,
                "confirming" to confirming,
                "exactValue" to exactValue,
                "draft" to draft,
                "notSent" to notSent,
                "saveFailed" to saveFailed,
            )
}
