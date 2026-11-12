package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.detail.ITEM_GAP
import io.github.solcott.activitytracker.app.ui.ActivityDetailsForm
import io.github.solcott.activitytracker.app.ui.ActivityVisibilityForm
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableScrollColumn
import io.github.solcott.activitytracker.app.ui.adaptive.ScrollbarBox
import io.github.solcott.activitytracker.app.ui.adaptive.WindowWidth
import io.github.solcott.activitytracker.app.ui.adaptive.WithWindowWidth
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.app.ui.theme.UiPlatform
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.training.ThresholdChange
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@CircuitInject(ImportActivityScreen::class, SessionScope::class)
@Composable
fun ImportActivityUi(state: ImportActivityState, modifier: Modifier = Modifier) {
    // Unfiltered: Android and iOS have no MIME type or UTType for .fit, so an extension filter hides the files. The presenter checks them.
    // Each picked file is read a range at a time, never whole: an account export can be gigabytes.
    val pick =
        rememberFilePickerLauncher(type = FileKitType.File(), mode = FileKitMode.Multiple()) { files ->
            if (!files.isNullOrEmpty()) {
                state.eventSink(ImportActivityEvent.FilesPicked(files.map { it.toImportSource() }.toImmutableList()))
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
        // Once saved, the after-save panel replaces the form, and Save with it.
        val pinnedSave = preview != null && !wide && state.afterImport == null
        AppScaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                AppTopBar(
                    title = ImportActivityTexts.title(state.addsRecording).string(),
                    onBack = { state.eventSink(ImportActivityEvent.Back) },
                    scrollBehavior = scrollBehavior,
                    actions = {
                        if (pinnedSave && ios) {
                            AppTextButton(onClick = save, enabled = state.canSave) { Text(saveLabel(state)) }
                        }
                    },
                )
            },
            bottomBar = { if (pinnedSave && !ios) SaveBar(state, save) },
        ) { padding ->
            val saved = state.savedSuggestions
            val after = state.afterImport
            val batch = state.batch
            when {
                after != null -> AfterImportPanel(after, state.eventSink, padding)
                saved != null || state.batchSaved != null -> SavedSuggestions(state.batchSaved, saved, state.eventSink, padding)
                batch != null -> BatchContent(state, batch, padding, pick::launch)
                wide && preview != null -> WidePreview(state, preview, padding, save, pick::launch)
                else -> NarrowContent(state, padding, width, pick::launch)
            }
        }
    }
}

/** Under 840dp: one scrolling column, the preview then the save panel, Save pinned by the scaffold. */
@Composable
private fun NarrowContent(state: ImportActivityState, padding: PaddingValues, width: WindowWidth, onPick: () -> Unit) {
    ReadableScrollColumn(padding, verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
        PickSection(state, onPick)
        val preview = state.preview
        if (preview != null) {
            PreviewContent(state, preview, medium = width == WindowWidth.Medium)
            SavePanel(state, save = null)
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

/** Picking files, and how reading them went: progress and Cancel while they're read. */
@Composable
internal fun PickSection(state: ImportActivityState, onPick: () -> Unit, modifier: Modifier = Modifier) {
    val picked = state.picked
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
        if (!picked) Text(ImportActivityTexts.hint.string())
        AppSecondaryButton(onClick = onPick, enabled = !state.reading && !state.saving) { Text(ImportActivityTexts.pick(picked).string()) }
        if (state.reading) {
            AppProgressIndicator()
            Text(ImportActivityTexts.reading(state.readProgress).string())
            AppTextButton(onClick = { state.eventSink(ImportActivityEvent.CancelReading) }) { Text(ImportActivityTexts.cancel.string()) }
        }
        // Once a file is read, errors are saving's, shown by Save.
        if (!picked) state.error?.let { ErrorText(it) }
        if (!picked) {
            state.savingImport?.let { SavingImportCard(it) { state.eventSink(ImportActivityEvent.Saved.ViewUploads) } }
            state.stoppedImports.forEach { stopped ->
                StoppedImportNotice(stopped) { state.eventSink(ImportActivityEvent.StoppedImportDismissed(stopped.batchId)) }
            }
        }
        if (picked && state.originalTooLarge) {
            Text(ImportActivityTexts.originalTooLarge.string(), style = MaterialTheme.typography.bodySmall)
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
        state.gear?.let {
            GearPicker(it, enabled = !state.saving)
            GearConflicts(it.conflicts)
        }
        NewDevicesForm(state.newDevices, enabled = !state.saving)
        // A recording added to an activity takes its visibility.
        if (!state.addsRecording) ActivityVisibilityForm(state.visibility, enabled = !state.saving)
        val preview = state.preview
        if (save != null && preview != null) {
            state.error?.let { ErrorText(it) }
            AppButton(onClick = save, Modifier.fillMaxWidth(), enabled = state.canSave) { Text(saveLabel(state)) }
        }
    }
}

/** Save, pinned under the preview on Android, desktop and web, so it stays in reach while the preview scrolls. */
@Composable
private fun SaveBar(state: ImportActivityState, save: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.error?.let { ErrorText(it, Modifier.readableWidth()) }
            AppButton(onClick = save, Modifier.readableWidth(), enabled = state.canSave) { Text(saveLabel(state)) }
        }
    }
}

@Composable private fun saveLabel(state: ImportActivityState): String = ImportActivityTexts.save(state.saving).string()

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
            ImportActivityTexts.thresholdsHeading.string(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        changes.forEach { change ->
            Text(
                ImportActivityTexts.thresholdChange(change).string(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** "Morning Mountain Bike Ride". */
@Composable internal fun defaultTitleLabel(title: DefaultTitle): String = ImportActivityTexts.defaultTitle(title).string()
