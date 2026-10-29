package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.ActivityMap
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppRangeSlider
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ScrollbarBox
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.segment_create_all_hidden
import io.github.solcott.activitytracker.composeApp.resources.segment_create_crosses_gap
import io.github.solcott.activitytracker.composeApp.resources.segment_create_duplicates
import io.github.solcott.activitytracker.composeApp.resources.segment_create_length
import io.github.solcott.activitytracker.composeApp.resources.segment_create_limit
import io.github.solcott.activitytracker.composeApp.resources.segment_create_links_activity
import io.github.solcott.activitytracker.composeApp.resources.segment_create_name
import io.github.solcott.activitytracker.composeApp.resources.segment_create_no_route
import io.github.solcott.activitytracker.composeApp.resources.segment_create_no_sport
import io.github.solcott.activitytracker.composeApp.resources.segment_create_public
import io.github.solcott.activitytracker.composeApp.resources.segment_create_publish
import io.github.solcott.activitytracker.composeApp.resources.segment_create_range
import io.github.solcott.activitytracker.composeApp.resources.segment_create_title
import io.github.solcott.activitytracker.composeApp.resources.segment_create_too_long
import io.github.solcott.activitytracker.composeApp.resources.segment_create_too_short
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * A new segment from the athlete's own activity: the route as their privacy zones leave it with the chosen stretch highlighted, a slider
 * choosing the stretch, its length or why it can't be a segment, a name, the public warning, and Publish. Duplicates found by the check are
 * listed, each opening the existing segment.
 */
@CircuitInject(CreateSegmentScreen::class, SessionScope::class)
@Composable
fun CreateSegmentUi(state: CreateSegmentState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.segment_create_title),
                onBack = { state.eventSink(CreateSegmentEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ContentStateLayout(
            state.content,
            state.loadErrorKind,
            onRetry = { state.eventSink(CreateSegmentEvent.Retry) },
            contentPadding = padding,
        ) {
            val unavailable = state.unavailable
            val route = state.route
            when {
                unavailable != null -> Message(unavailableText(unavailable), Modifier.padding(padding))
                route != null -> Form(state, route, padding)
            }
        }
    }
}

@Composable
private fun Form(state: CreateSegmentState, route: String, padding: PaddingValues) {
    val scroll = rememberScrollState()
    ScrollbarBox(scroll, Modifier.fillMaxSize(), padding) {
        Column(
            Modifier.verticalScroll(scroll).padding(padding).readableWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActivityMap(route, Modifier.fillMaxWidth(), gaps = state.routeGaps, highlight = state.highlight)
            Text(stringResource(Res.string.segment_create_range), style = MaterialTheme.typography.titleSmall)
            AppRangeSlider(
                value = state.start..state.end,
                onValueChange = { range -> state.eventSink(CreateSegmentEvent.SelectionChanged(range.start, range.endInclusive)) },
                valueRange = 0f..state.lengthMeters.coerceAtLeast(1f),
                enabled = !state.publishing,
                modifier = Modifier.fillMaxWidth(),
            )
            SelectionText(state)
            AppTextField(
                state.name,
                label = stringResource(Res.string.segment_create_name),
                enabled = !state.publishing,
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(Res.string.segment_create_public) +
                    if (state.namesActivity) " " + stringResource(Res.string.segment_create_links_activity) else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.duplicates.isNotEmpty()) Duplicates(state.duplicates) { state.eventSink(CreateSegmentEvent.OpenSegment(it.uri)) }
            if (state.limitReached) Text(stringResource(Res.string.segment_create_limit), color = MaterialTheme.colorScheme.error)
            state.actionError?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
            AppButton(onClick = { state.eventSink(CreateSegmentEvent.Publish) }, enabled = state.canPublish) {
                Text(stringResource(Res.string.segment_create_publish))
            }
            if (state.publishing) AppProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/** The stretch's length and sport group, or why it can't be a segment. */
@Composable
private fun SelectionText(state: CreateSegmentState) {
    val sport = state.sport?.let { segmentSportLabel(it) }.orEmpty()
    when (val selection = state.selection) {
        is SelectionCheck.Ok ->
            Text(
                stringResource(Res.string.segment_create_length, Format.distance(selection.meters), sport),
                style = MaterialTheme.typography.bodyLarge,
            )
        is SelectionCheck.TooShort ->
            ErrorLine(stringResource(Res.string.segment_create_too_short, sport, Format.distance(selection.minMeters.toDouble())))
        is SelectionCheck.TooLong ->
            ErrorLine(stringResource(Res.string.segment_create_too_long, Format.distance(selection.maxMeters.toDouble())))
        SelectionCheck.CrossesGap -> ErrorLine(stringResource(Res.string.segment_create_crosses_gap))
    }
}

@Composable
private fun ErrorLine(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)

@Composable
private fun Duplicates(duplicates: ImmutableList<SegmentView>, onOpen: (SegmentView) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.segment_create_duplicates), style = MaterialTheme.typography.bodyMedium)
        duplicates.forEach { segment ->
            AppTextButton(onClick = { onOpen(segment) }) {
                Text("${segment.currentName} · ${Format.distance(segment.distanceMeters.toDouble())}")
            }
        }
    }
}

@Composable
private fun Message(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun unavailableText(unavailable: CreateSegmentUnavailable): String =
    stringResource(
        when (unavailable) {
            CreateSegmentUnavailable.NoRoute -> Res.string.segment_create_no_route
            CreateSegmentUnavailable.NoSportGroup -> Res.string.segment_create_no_sport
            CreateSegmentUnavailable.AllHidden -> Res.string.segment_create_all_hidden
        }
    )
