package io.github.solcott.activitytracker.app.ui.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.slack.circuit.subcircuit.LocalSubCircuit
import com.slack.circuit.subcircuit.SubCircuit
import com.slack.circuit.subcircuit.SubCircuitOuterEvent
import com.slack.circuit.subcircuit.SubCircuitUiState
import com.slack.circuit.subcircuit.SubPresenter
import com.slack.circuit.subcircuit.SubScreen
import com.slack.circuit.subcircuit.SubUi
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterScreen
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterState
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterSubUi
import io.github.solcott.activitytracker.app.report.ReportScreen
import io.github.solcott.activitytracker.app.report.ReportState
import io.github.solcott.activitytracker.app.report.ReportSubUi
import io.github.solcott.activitytracker.app.ui.LocalClock
import io.github.solcott.activitytracker.app.ui.LocalHourClock
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.adaptive.LocalIsNavRoot
import io.github.solcott.activitytracker.app.ui.adaptive.WindowWidth
import io.github.solcott.activitytracker.app.ui.theme.AppTheme
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.app.ui.theme.UiPlatform
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/** The harness's default current time, `Fixed.NOW` in `:app-fixtures`. */
val PreviewNow: Instant = Instant.parse("2026-06-01T12:00:00Z")

/**
 * Renders [content], a screen's UI or part of one, in isolation for tests, previews and screenshot tools (dev/ui-test-harness): with no
 * session, network or local stack, the same on any machine and date. It chooses the [platform] look, [darkTheme], [units], the window
 * [width] (null fills what it's given), the current time ([now], [timeZone]) and the clock style ([use24Hour]), and whether the screen is a
 * tab root ([isNavRoot]). Under its inspection mode maps draw `RoutePreview` and images a flat placeholder. Links open in [uriHandler].
 * Sub-screens render their real UIs from [subState], or a default state, and an unknown one renders nothing.
 *
 * Production code: `:androidApp`'s screenshot tests and Compose previews need it. Nothing in the app calls it.
 */
@Composable
fun AppPreview(
    modifier: Modifier = Modifier,
    platform: UiPlatform = UiPlatform.Android,
    darkTheme: Boolean = false,
    units: UnitSystem = UnitSystem.Metric,
    width: WindowWidth? = WindowWidth.Compact,
    now: Instant = PreviewNow,
    timeZone: TimeZone = TimeZone.UTC,
    use24Hour: Boolean = true,
    isNavRoot: Boolean = false,
    uriHandler: RecordingUriHandler = remember { RecordingUriHandler() },
    subState: (SubScreen<*>) -> SubCircuitUiState? = { null },
    content: @Composable () -> Unit,
) {
    val clock = remember(now) { FixedClock(now) }
    val subCircuit = remember(subState) { previewSubCircuit(subState) }
    @OptIn(ExperimentalCoilApi::class)
    CompositionLocalProvider(
        LocalInspectionMode provides true,
        LocalUiPlatform provides platform,
        LocalUnitSystem provides units,
        LocalClock provides clock,
        LocalTimeZone provides timeZone,
        LocalHourClock provides use24Hour,
        LocalIsNavRoot provides isNavRoot,
        LocalUriHandler provides uriHandler,
        LocalAsyncImagePreviewHandler provides PlaceholderImages,
        LocalSubCircuit provides subCircuit,
    ) {
        PreviewResources()
        AppTheme(darkTheme = darkTheme) {
            // Required, so the screen gets its width class even in a narrower test window or preview pane.
            val sized = width?.let { modifier.requiredWidth(it.previewWidth) } ?: modifier
            // Screens fill the height themselves; a component keeps its own, so a preview can wrap it.
            Box(sized.fillMaxWidth()) { content() }
        }
    }
}

/** Where [AppPreview] lays a window of each width out: inside each [WindowWidth]'s range. */
val WindowWidth.previewWidth: Dp
    get() =
        when (this) {
            WindowWidth.Compact -> 400.dp
            WindowWidth.Medium -> 700.dp
            WindowWidth.Expanded -> 1000.dp
        }

/** A [UriHandler] that opens nothing and keeps each URI asked for, in order. */
@Stable
class RecordingUriHandler : UriHandler {
    val opened: MutableList<String> = mutableListOf()

    override fun openUri(uri: String) {
        opened += uri
    }
}

private class FixedClock(private val now: Instant) : Clock {
    override fun now(): Instant = now
}

/** A flat grey image for every request, its shade from the URL so different images differ. */
@OptIn(ExperimentalCoilApi::class)
private val PlaceholderImages = AsyncImagePreviewHandler { request ->
    val shade = (request.data.hashCode() and SHADE_MASK) or SHADE_FLOOR
    ColorImage(Color(shade, shade, shade).toArgb())
}

private const val SHADE_MASK = 0x3F
private const val SHADE_FLOOR = 0x9F

/** The session's sub-screens with their real UIs and fixed states; anything else renders nothing. */
private fun previewSubCircuit(subState: (SubScreen<*>) -> SubCircuitUiState?): SubCircuit =
    SubCircuit.builder()
        .addPresenterFactory { screen ->
            (subState(screen) ?: defaultSubState(screen))?.let(::FixedSubPresenter) ?: FixedSubPresenter(UnknownSubState)
        }
        .addUiFactory { screen ->
            when (screen) {
                is FeedFilterScreen -> FeedFilterSubUi()
                is ReportScreen -> ReportSubUi()
                else -> SubUi<SubCircuitUiState> { _, _ -> }
            }
        }
        .build()

private fun defaultSubState(screen: SubScreen<*>): SubCircuitUiState? =
    when (screen) {
        is FeedFilterScreen -> FeedFilterState(screen.initial, typePicker = null, customRange = null, eventSink = {})
        is ReportScreen ->
            ReportState(
                authorName = screen.authorName,
                reason = null,
                note = TextFieldState(),
                sending = false,
                sent = false,
                offerBlock = screen.offerBlock,
                error = null,
                eventSink = {},
            )
        else -> null
    }

private class FixedSubPresenter(private val state: SubCircuitUiState) : SubPresenter<SubCircuitOuterEvent, SubCircuitUiState> {
    @Composable override fun present(outerEventSink: (SubCircuitOuterEvent) -> Unit): SubCircuitUiState = state
}

/** The state of a sub-screen the harness doesn't know, which renders nothing. */
private data object UnknownSubState : SubCircuitUiState
