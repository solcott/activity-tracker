package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.detail.ITEM_GAP
import io.github.solcott.activitytracker.app.ui.ActivityDetailsForm
import io.github.solcott.activitytracker.app.ui.ActivityVisibilityForm
import io.github.solcott.activitytracker.app.ui.activityTypeLabel
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ScrollbarBox
import io.github.solcott.activitytracker.app.ui.adaptive.WindowWidth
import io.github.solcott.activitytracker.app.ui.adaptive.WithWindowWidth
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.sportLabel
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.app.ui.theme.UiPlatform
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_save
import io.github.solcott.activitytracker.composeApp.resources.action_saving
import io.github.solcott.activitytracker.composeApp.resources.default_title_afternoon
import io.github.solcott.activitytracker.composeApp.resources.default_title_evening
import io.github.solcott.activitytracker.composeApp.resources.default_title_morning
import io.github.solcott.activitytracker.composeApp.resources.default_title_night
import io.github.solcott.activitytracker.composeApp.resources.import_add_recording_title
import io.github.solcott.activitytracker.composeApp.resources.import_hint
import io.github.solcott.activitytracker.composeApp.resources.import_original_too_large
import io.github.solcott.activitytracker.composeApp.resources.import_pick
import io.github.solcott.activitytracker.composeApp.resources.import_pick_another
import io.github.solcott.activitytracker.composeApp.resources.import_reading
import io.github.solcott.activitytracker.composeApp.resources.import_threshold_change
import io.github.solcott.activitytracker.composeApp.resources.import_threshold_change_sport
import io.github.solcott.activitytracker.composeApp.resources.import_thresholds_heading
import io.github.solcott.activitytracker.composeApp.resources.import_title
import io.github.solcott.activitytracker.composeApp.resources.threshold_ftp
import io.github.solcott.activitytracker.composeApp.resources.threshold_max_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.threshold_resting_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.threshold_threshold_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.unit_bpm
import io.github.solcott.activitytracker.composeApp.resources.unit_watts
import io.github.solcott.activitytracker.composeApp.resources.value_missing
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.training.ThresholdChange
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.jetbrains.compose.resources.stringResource

@CircuitInject(ImportActivityScreen::class, SessionScope::class)
@Composable
fun ImportActivityUi(state: ImportActivityState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    // Unfiltered: Android and iOS have no MIME type or UTType for .fit, so an extension filter hides the files. The presenter checks it.
    val pick =
        rememberFilePickerLauncher(type = FileKitType.File()) { file ->
            file ?: return@rememberFilePickerLauncher
            scope.launch {
                runSuspendCatching { file.readBytes() }
                    .onSuccess { state.eventSink(ImportActivityEvent.FilePicked(file.name, ByteString(it))) }
            }
        }
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    val preview = state.preview
    val defaultTitle = state.defaultTitle?.let { defaultTitleLabel(it) }
    val save = { state.eventSink(ImportActivityEvent.Save(defaultTitle)) }
    val ios = LocalUiPlatform.current == UiPlatform.Ios
    WithWindowWidth(modifier) { width ->
        // Below 840dp the save panel follows the preview, so Save stays in reach: in the navigation bar on iOS, a bottom bar elsewhere.
        val wide = width == WindowWidth.Expanded
        val pinnedSave = preview != null && !wide
        AppScaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                AppTopBar(
                    title = stringResource(if (state.addsRecording) Res.string.import_add_recording_title else Res.string.import_title),
                    onBack = { state.eventSink(ImportActivityEvent.Back) },
                    scrollBehavior = scrollBehavior,
                    actions = {
                        if (pinnedSave && ios) {
                            AppTextButton(onClick = save, enabled = state.canSave(preview)) { Text(saveLabel(state)) }
                        }
                    },
                )
            },
            bottomBar = { if (pinnedSave && !ios) SaveBar(state, preview, save) },
        ) { padding ->
            if (wide && preview != null) WidePreview(state, preview, padding, save, pick::launch)
            else NarrowContent(state, padding, width, pick::launch)
        }
    }
}

/** Under 840dp: one scrolling column, the preview then the save panel, Save pinned by the scaffold. */
@Composable
private fun NarrowContent(state: ImportActivityState, padding: PaddingValues, width: WindowWidth, onPick: () -> Unit) {
    val scroll = rememberScrollState()
    ScrollbarBox(scroll, contentPadding = padding) {
        Column(
            Modifier.verticalScroll(scroll).padding(padding).readableWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
        ) {
            PickSection(state, onPick)
            val preview = state.preview
            if (preview != null) {
                PreviewContent(state, preview, medium = width == WindowWidth.Medium)
                SavePanel(state, save = null)
            }
            state.dives?.let { DiveBatchForm(state, it) }
        }
    }
}

/** From 840dp: the preview, scrolling on its own, with the save panel and Save beside it. */
@Composable
private fun WidePreview(state: ImportActivityState, preview: ImportPreview, padding: PaddingValues, save: () -> Unit, onPick: () -> Unit) {
    val previewScroll = rememberScrollState()
    val panelScroll = rememberScrollState()
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
        ScrollbarBox(previewScroll, Modifier.weight(1f, fill = false).widthIn(max = PREVIEW_MAX_WIDTH), contentPadding = padding) {
            Column(
                Modifier.verticalScroll(previewScroll).padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
            ) {
                PickSection(state, onPick)
                PreviewContent(state, preview, medium = true)
            }
        }
        ScrollbarBox(panelScroll, Modifier.width(SAVE_PANEL_WIDTH), contentPadding = padding) {
            Column(Modifier.verticalScroll(panelScroll).padding(padding).padding(16.dp)) { SavePanel(state, save) }
        }
    }
}

/** Picking a file, and how reading it went. */
@Composable
private fun PickSection(state: ImportActivityState, onPick: () -> Unit) {
    val picked = state.preview != null || state.dives != null
    Column(verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
        if (!picked) Text(stringResource(Res.string.import_hint))
        AppSecondaryButton(onClick = onPick, enabled = !state.reading && !state.saving) {
            Text(stringResource(if (picked) Res.string.import_pick_another else Res.string.import_pick))
        }
        if (state.reading) {
            AppProgressIndicator()
            Text(stringResource(Res.string.import_reading))
        }
        state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
        if (picked && state.originalTooLarge) {
            Text(stringResource(Res.string.import_original_too_large), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The choices saving keeps: the type, the title and details, threshold changes and who may see it; and Save, when [save] is given. */
@Composable
private fun SavePanel(state: ImportActivityState, save: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
        ActivityTypePicker(state.type, enabled = !state.saving)
        val defaultTitle = state.defaultTitle?.let { defaultTitleLabel(it) }
        // Present whenever a single activity is: the title, description, dive site and photos.
        state.details?.let { ActivityDetailsForm(state.title, titlePlaceholder = defaultTitle, fields = it, enabled = !state.saving) }
        if (state.thresholdChanges.isNotEmpty()) ThresholdChanges(state.thresholdChanges)
        // A recording added to an activity takes its visibility.
        if (!state.addsRecording) ActivityVisibilityForm(state.visibility, enabled = !state.saving)
        val preview = state.preview
        if (save != null && preview != null) {
            AppButton(onClick = save, Modifier.fillMaxWidth(), enabled = state.canSave(preview)) { Text(saveLabel(state)) }
        }
    }
}

/** Save, pinned under the preview on Android, desktop and web, so it stays in reach while the preview scrolls. */
@Composable
private fun SaveBar(state: ImportActivityState, preview: ImportPreview, save: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            AppButton(onClick = save, Modifier.readableWidth(), enabled = state.canSave(preview)) { Text(saveLabel(state)) }
        }
    }
}

@Composable
private fun saveLabel(state: ImportActivityState): String =
    stringResource(if (state.saving) Res.string.action_saving else Res.string.action_save)

private val SAVE_PANEL_WIDTH = 380.dp
private val PREVIEW_MAX_WIDTH = 900.dp

/** The thresholds saving updates from the device's settings: "FTP: 231 → 240 W", or "Run · FTP: …" for a sport's override. */
@Composable
private fun ThresholdChanges(changes: ImmutableList<ThresholdChange>, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(Res.string.import_thresholds_heading),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        changes.forEach { change ->
            val from = thresholdValue(change.threshold, change.from)
            val to = thresholdValue(change.threshold, change.to)
            Text(
                change.sport?.let { sport ->
                    stringResource(Res.string.import_threshold_change_sport, sportLabel(sport), thresholdLabel(change.threshold), from, to)
                } ?: stringResource(Res.string.import_threshold_change, thresholdLabel(change.threshold), from, to),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** A threshold's value in watts or bpm, or a dash when it wasn't set. */
@Composable
private fun thresholdValue(threshold: String, value: Long?): String =
    when {
        value == null -> stringResource(Res.string.value_missing)
        threshold == Threshold.FUNCTIONAL_THRESHOLD_POWER -> stringResource(Res.string.unit_watts, value.toString())
        else -> stringResource(Res.string.unit_bpm, value.toString())
    }

@Composable
private fun thresholdLabel(threshold: String): String =
    when (threshold) {
        Threshold.FUNCTIONAL_THRESHOLD_POWER -> stringResource(Res.string.threshold_ftp)
        Threshold.MAX_HEART_RATE -> stringResource(Res.string.threshold_max_heart_rate)
        Threshold.THRESHOLD_HEART_RATE -> stringResource(Res.string.threshold_threshold_heart_rate)
        Threshold.RESTING_HEART_RATE -> stringResource(Res.string.threshold_resting_heart_rate)
        else -> threshold
    }

/** "Morning Mountain Bike Ride". */
@Composable
internal fun defaultTitleLabel(title: DefaultTitle): String =
    stringResource(
        when (title.partOfDay) {
            PartOfDay.Morning -> Res.string.default_title_morning
            PartOfDay.Afternoon -> Res.string.default_title_afternoon
            PartOfDay.Evening -> Res.string.default_title_evening
            PartOfDay.Night -> Res.string.default_title_night
        },
        activityTypeLabel(title.type),
    )
