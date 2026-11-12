package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
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
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppRangeSlider
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableScrollColumn
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.segment_create_duplicates
import io.github.solcott.activitytracker.composeApp.resources.segment_create_limit
import io.github.solcott.activitytracker.composeApp.resources.segment_create_name
import io.github.solcott.activitytracker.composeApp.resources.segment_create_publish
import io.github.solcott.activitytracker.composeApp.resources.segment_create_title
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.toImmutableList
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
                unavailable != null -> CreatePlaceMessage(unavailableText(unavailable), Modifier.padding(padding))
                route != null -> Form(state, route, padding)
            }
        }
    }
}

@Composable
private fun Form(state: CreateSegmentState, route: String, padding: PaddingValues) {
    val units = LocalUnitSystem.current
    ReadableScrollColumn(padding, Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActivityMap(route, Modifier.fillMaxWidth(), gaps = state.routeGaps, highlight = state.highlight)
        Text(CreateSegmentTexts.range.string(), style = MaterialTheme.typography.titleSmall)
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
            CreateSegmentTexts.publicNote(state.namesActivity).map { it.string() }.joinToString(" "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.duplicates.isNotEmpty()) {
            val labels = state.duplicates.map { CreateSegmentTexts.duplicate(it, units).string() }.toImmutableList()
            PlaceDuplicates(
                stringResource(Res.string.segment_create_duplicates),
                labels,
                onOpen = { state.eventSink(CreateSegmentEvent.OpenSegment(state.duplicates[it].uri)) },
            )
        }
        if (state.limitReached) Text(stringResource(Res.string.segment_create_limit), color = MaterialTheme.colorScheme.error)
        state.actionError?.let { ErrorText(it) }
        AppButton(onClick = { state.eventSink(CreateSegmentEvent.Publish) }, enabled = state.canPublish) {
            Text(stringResource(Res.string.segment_create_publish))
        }
        if (state.publishing) AppProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
    }
}

/** The stretch's length and sport group, or why it can't be a segment (`CreateSegmentTexts`). */
@Composable
private fun SelectionText(state: CreateSegmentState) {
    val text = CreateSegmentTexts.selection(state, LocalUnitSystem.current).string()
    if (CreateSegmentTexts.selectionFails(state)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    } else {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun unavailableText(unavailable: CreateSegmentUnavailable): String = CreateSegmentTexts.unavailable(unavailable).string()
