package io.github.solcott.activitytracker.app.trim

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.ActivityMap
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppBottomSheet
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppRangeSlider
import io.github.solcott.activitytracker.app.ui.adaptive.AppRepeatIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSegmentedControl
import io.github.solcott.activitytracker.app.ui.adaptive.AppSheetHeader
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.HighlightPlacement
import io.github.solcott.activitytracker.app.ui.adaptive.RangeSliderLabels
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableScrollColumn
import io.github.solcott.activitytracker.app.ui.adaptive.WindowWidth
import io.github.solcott.activitytracker.app.ui.adaptive.WithWindowWidth
import io.github.solcott.activitytracker.app.ui.adaptive.highlight
import io.github.solcott.activitytracker.app.ui.adaptive.highlightInset
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.app.ui.chart.LineChartUi
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.trim_confirm_title
import io.github.solcott.activitytracker.composeApp.resources.trim_distance_unavailable
import io.github.solcott.activitytracker.composeApp.resources.trim_done
import io.github.solcott.activitytracker.composeApp.resources.trim_end
import io.github.solcott.activitytracker.composeApp.resources.trim_end_handle
import io.github.solcott.activitytracker.composeApp.resources.trim_exact_earlier
import io.github.solcott.activitytracker.composeApp.resources.trim_exact_invalid
import io.github.solcott.activitytracker.composeApp.resources.trim_exact_later
import io.github.solcott.activitytracker.composeApp.resources.trim_exact_set
import io.github.solcott.activitytracker.composeApp.resources.trim_mode_distance
import io.github.solcott.activitytracker.composeApp.resources.trim_mode_time
import io.github.solcott.activitytracker.composeApp.resources.trim_not_sent
import io.github.solcott.activitytracker.composeApp.resources.trim_reset
import io.github.solcott.activitytracker.composeApp.resources.trim_save
import io.github.solcott.activitytracker.composeApp.resources.trim_scrub_hint
import io.github.solcott.activitytracker.composeApp.resources.trim_start
import io.github.solcott.activitytracker.composeApp.resources.trim_start_handle
import io.github.solcott.activitytracker.composeApp.resources.trim_suggest_apply
import io.github.solcott.activitytracker.composeApp.resources.trim_suggest_title
import io.github.solcott.activitytracker.composeApp.resources.trim_suggest_undo
import io.github.solcott.activitytracker.composeApp.resources.trim_title
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlin.math.roundToInt
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/**
 * The trim screen (feed/activity-trim): the map previewing the cut over a profile with two handles, the readouts of each end, the cut and
 * the totals before and after. One column on narrow windows; on wide ones the map fills the left and the rest sits in a panel beside it.
 */
@CircuitInject(TrimScreen::class, SessionScope::class)
@Composable
fun TrimUi(state: TrimState, modifier: Modifier = Modifier) {
    AppScaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.trim_title),
                onBack = { state.eventSink(TrimEvent.Back) },
                actions = {
                    AppTextButton(onClick = { state.eventSink(TrimEvent.Save) }, enabled = state.canSave) {
                        Text(stringResource(if (state.draft) Res.string.trim_done else Res.string.trim_save))
                    }
                },
            )
        },
    ) { padding ->
        ContentStateLayout(state.content, UiError.Kind.LoadTrim, { state.eventSink(TrimEvent.Retry) }, contentPadding = padding) {
            val editor = state.editor
            when {
                state.notSent -> NotSent(padding)
                editor != null -> Editor(state, editor, padding)
            }
        }
    }
    state.exact?.let { exact -> state.editor?.let { ExactSheet(exact, it, state.eventSink) } }
    if (state.confirming) {
        state.editor?.let { editor ->
            AppAlertDialog(
                title = stringResource(Res.string.trim_confirm_title),
                message = TrimTexts.confirm(editor).string(),
                actions =
                    persistentListOf(
                        AppDialogAction(stringResource(Res.string.trim_save), destructive = true) { state.eventSink(TrimEvent.Confirm) },
                    ),
                cancelLabel = stringResource(Res.string.action_cancel),
                onDismiss = { state.eventSink(TrimEvent.CancelConfirm) },
            )
        }
    }
}

@Composable
private fun NotSent(padding: PaddingValues) {
    Text(
        stringResource(Res.string.trim_not_sent),
        Modifier.fillMaxWidth().padding(padding).padding(24.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun Editor(state: TrimState, editor: TrimEditor, padding: PaddingValues) {
    val polyline = editor.polyline
    WithWindowWidth(Modifier.fillMaxSize()) { width ->
        if (width == WindowWidth.Expanded && polyline != null) {
            Row(Modifier.fillMaxSize().padding(padding)) {
                ActivityMap(
                    polyline,
                    Modifier.weight(1f).fillMaxHeight(),
                    height = 2000.dp,
                    highlight = editor.kept,
                    interactive = true,
                    cut = editor.kept != null,
                )
                Column(
                    Modifier.width(PANEL_WIDTH).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Panel(state, editor)
                }
            }
        } else {
            ReadableScrollColumn(padding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                editor.polyline?.let { ActivityMap(it, highlight = editor.kept, cut = editor.kept != null) }
                Panel(state, editor)
            }
        }
    }
}

/** The profile with its handles, the readouts, the suggestion, the totals and the notes. */
@Composable
private fun Panel(state: TrimState, editor: TrimEditor) {
    val units = LocalUnitSystem.current
    val sink = state.eventSink
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppSegmentedControl(
                labels = persistentListOf(stringResource(Res.string.trim_mode_time), stringResource(Res.string.trim_mode_distance)),
                selected = if (editor.byDistance) 1 else 0,
                onSelect = { sink(TrimEvent.ModeChanged(it == 1)) },
                enabled = editor.offersDistance,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!editor.offersDistance) {
                Text(stringResource(Res.string.trim_distance_unavailable), style = MaterialTheme.typography.bodySmall)
            }
            val chart = remember(editor.profile, editor.byDistance, units, editor.elapsed) { TrimTexts.profile(editor, units) }
            chart?.let { profile ->
                val spans = TrimTexts.cutSpans(editor)
                LineChartUi(remember(profile, spans) { profile.withSpans(spans) }, interactive = false)
            }
            var scrub by remember { mutableStateOf<Float?>(null) }
            AppRangeSlider(
                value = editor.from.toFloat()..editor.to.toFloat(),
                onValueChange = { sink(TrimEvent.Moved(it.start.roundToInt(), it.endInclusive.roundToInt())) },
                valueRange = 0f..editor.elapsed.toFloat().coerceAtLeast(1f),
                enabled = !state.saving,
                labels =
                    RangeSliderLabels(
                        start = stringResource(Res.string.trim_start_handle),
                        startValue = TrimTexts.handleValue(editor, start = true, units).string(),
                        end = stringResource(Res.string.trim_end_handle),
                        endValue = TrimTexts.handleValue(editor, start = false, units).string(),
                    ),
                precision = { TrimTexts.scrubFactor(it.value.toDouble()).toFloat() },
                onPrecisionChange = { scrub = it },
                modifier = Modifier.fillMaxWidth(),
            )
            // One line either way, so the panel never moves while the handle slows: the hint, or how fast the handle follows.
            val speed = scrub?.let { TrimTexts.scrubLabel(it.toDouble()) }
            Text(
                speed?.string() ?: stringResource(Res.string.trim_scrub_hint),
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = if (speed != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                End(editor, start = true, enabled = !state.saving, sink, Modifier.weight(1f))
                End(editor, start = false, enabled = !state.saving, sink, Modifier.weight(1f))
            }
            if (editor.changed) {
                AppTextButton(onClick = { sink(TrimEvent.Reset) }) { Text(stringResource(Res.string.trim_reset)) }
            }
        }
    }
    editor.suggestion?.let { suggestion ->
        TrimTexts.suggestion(suggestion, units)?.let { text ->
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.trim_suggest_title), style = MaterialTheme.typography.titleSmall)
                    Text(text.string(), style = MaterialTheme.typography.bodyMedium)
                    if (editor.suggestionApplied) {
                        AppSecondaryButton(onClick = { sink(TrimEvent.Reset) }) { Text(stringResource(Res.string.trim_suggest_undo)) }
                    } else {
                        AppButton(onClick = { sink(TrimEvent.ApplySuggestion) }) { Text(stringResource(Res.string.trim_suggest_apply)) }
                    }
                }
            }
        }
    }
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TrimTexts.totals(editor, units).forEach { TotalRow(it) }
        }
    }
    TrimTexts.effortNotes(editor).forEach {
        Text(it.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
    state.error?.let { ErrorText(it) }
}

/** One end: its readout, which opens its exact value, over the buttons that nudge it earlier or later. */
@Composable
private fun End(editor: TrimEditor, start: Boolean, enabled: Boolean, sink: (TrimEvent) -> Unit, modifier: Modifier = Modifier) {
    val units = LocalUnitSystem.current
    Column(modifier, horizontalAlignment = if (start) Alignment.Start else Alignment.End) {
        Readout(editor, start, onClick = { sink(TrimEvent.EditExact(start, units)) })
        // The icons' own edges line up with the readout's text, past the buttons' touch padding.
        Row(Modifier.offset(x = if (start) -NUDGE_INSET else NUDGE_INSET)) {
            listOf(false, true).forEach { later ->
                AppRepeatIconButton(
                    if (later) AppIcon.Chevron else AppIcon.ChevronBack,
                    TrimTexts.nudgeLabel(start, later).string(),
                    onStep = { sink(TrimEvent.Nudge(start, later, it, units)) },
                    enabled = enabled,
                )
            }
        }
    }
}

/** One end's readout: where it lands, its clock time and what it cuts; a tap opens its exact value. */
@Composable
private fun Readout(editor: TrimEditor, start: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val units = LocalUnitSystem.current
    Column(
        modifier.highlight(placement = HighlightPlacement.Bleed).clickable(role = Role.Button, onClick = onClick).highlightInset(),
        horizontalAlignment = if (start) Alignment.Start else Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(stringResource(if (start) Res.string.trim_start else Res.string.trim_end), style = MaterialTheme.typography.labelMedium)
        Text(TrimTexts.position(editor, start, units).string(), style = MaterialTheme.typography.titleMedium)
        Text(TrimTexts.clock(editor, start).string(), style = MaterialTheme.typography.bodySmall)
        Text(
            TrimTexts.cut(editor, start, units).string(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TotalRow(row: TrimTotalRow) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(row.label.string(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Column(horizontalAlignment = Alignment.End) {
            Text(row.value.string(), style = MaterialTheme.typography.titleMedium)
            row.was?.let {
                Text(it.string(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The exact value of one end: typed, or stepped earlier or later. */
@Composable
private fun ExactSheet(exact: TrimExactValue, editor: TrimEditor, sink: (TrimEvent) -> Unit) {
    val units = LocalUnitSystem.current
    AppBottomSheet(onDismiss = { sink(TrimEvent.CancelExact) }, sheetState = rememberAppSheetState()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppSheetHeader(
                title = stringResource(if (exact.start) Res.string.trim_start else Res.string.trim_end),
                cancelLabel = stringResource(Res.string.action_cancel),
                onCancel = { sink(TrimEvent.CancelExact) },
                confirmLabel = stringResource(Res.string.trim_exact_set),
                onConfirm = { sink(TrimEvent.ApplyExact(units)) },
            )
            AppTextField(
                exact.field,
                Modifier.fillMaxWidth(),
                label = TrimTexts.exactLabel(editor, units).string(),
                isError = exact.invalid,
                supportingText = if (exact.invalid) stringResource(Res.string.trim_exact_invalid) else null,
                lineLimits = TextFieldLineLimits.SingleLine,
                onKeyboardAction = { sink(TrimEvent.ApplyExact(units)) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppSecondaryButton(onClick = { sink(TrimEvent.StepExact(later = false, units)) }, Modifier.weight(1f)) {
                    Text(stringResource(Res.string.trim_exact_earlier))
                }
                AppSecondaryButton(onClick = { sink(TrimEvent.StepExact(later = true, units)) }, Modifier.weight(1f)) {
                    Text(stringResource(Res.string.trim_exact_later))
                }
            }
        }
    }
}

private val PANEL_WIDTH = 400.dp

/** The space between an icon button's edge and its icon. */
private val NUDGE_INSET = 12.dp
