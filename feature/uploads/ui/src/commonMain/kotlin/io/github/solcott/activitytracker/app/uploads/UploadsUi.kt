package io.github.solcott.activitytracker.app.uploads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.EmptyListText
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.uploads_cancel_confirm
import io.github.solcott.activitytracker.composeApp.resources.uploads_cancel_message
import io.github.solcott.activitytracker.composeApp.resources.uploads_cancel_rest
import io.github.solcott.activitytracker.composeApp.resources.uploads_cancel_rest_title
import io.github.solcott.activitytracker.composeApp.resources.uploads_cancel_title
import io.github.solcott.activitytracker.composeApp.resources.uploads_clear
import io.github.solcott.activitytracker.composeApp.resources.uploads_clear_description
import io.github.solcott.activitytracker.composeApp.resources.uploads_empty
import io.github.solcott.activitytracker.composeApp.resources.uploads_finished
import io.github.solcott.activitytracker.composeApp.resources.uploads_imports_paused
import io.github.solcott.activitytracker.composeApp.resources.uploads_keep
import io.github.solcott.activitytracker.composeApp.resources.uploads_open_only
import io.github.solcott.activitytracker.composeApp.resources.uploads_pause_imports
import io.github.solcott.activitytracker.composeApp.resources.uploads_refused_other
import io.github.solcott.activitytracker.composeApp.resources.uploads_resume_imports
import io.github.solcott.activitytracker.composeApp.resources.uploads_title
import io.github.solcott.activitytracker.composeApp.resources.uploads_uploading_now
import io.github.solcott.activitytracker.composeApp.resources.uploads_yours
import io.github.solcott.activitytracker.data.activity.ImportSaveStatus
import io.github.solcott.activitytracker.data.activity.UploadImport
import io.github.solcott.activitytracker.data.activity.UploadQueueView
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The upload queue (data/upload-queue): pauses and time left, the item uploading now, the athlete's own items, each import's group, the
 * finished items and the refused other changes. Waiting and refused items can be cancelled, which asks first.
 */
@CircuitInject(UploadsScreen::class, SessionScope::class)
@Composable
fun UploadsUi(state: UploadsState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.uploads_title),
                onBack = { state.eventSink(UploadsEvent.Back) },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (state.showsClear) {
                        val description = stringResource(Res.string.uploads_clear_description)
                        AppTextButton(
                            onClick = { state.eventSink(UploadsEvent.ClearFinished) },
                            Modifier.semantics { contentDescription = description },
                            enabled = state.canClear,
                        ) {
                            Text(stringResource(Res.string.uploads_clear))
                        }
                    }
                },
            )
        },
    ) { padding ->
        ContentStateLayout(
            state.content,
            UiError.Kind.LoadUploads,
            onRetry = { state.eventSink(UploadsEvent.Retry) },
            contentPadding = padding,
        ) { loaded ->
            if (loaded != null) QueueList(state, loaded, padding)
        }
    }
    Confirmations(state)
}

@Composable
private fun QueueList(state: UploadsState, view: UploadQueueView, padding: PaddingValues) {
    val listState = rememberLazyListState()
    ReadableList(listState, Modifier.fillMaxSize(), padding) { listPadding ->
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding + PaddingValues(vertical = 8.dp)) {
            item(key = "refresh") {
                RefreshStatus(state.content, UiError.Kind.LoadUploads, onRetry = { state.eventSink(UploadsEvent.Retry) })
            }
            banners(state)
            view.current?.let { current ->
                header("current", Res.string.uploads_uploading_now)
                item(key = "current:${current.id}") { ItemRow(current, state) }
            }
            if (view.own.isNotEmpty()) {
                header("own", Res.string.uploads_yours)
                items(view.own, key = { "own:${it.id}" }) { ItemRow(it, state) }
            }
            view.imports.forEach { group -> importGroup(group, state) }
            if (view.finished.isNotEmpty()) {
                header("finished", Res.string.uploads_finished)
                items(view.finished, key = { "finished:${it.id}" }) { ItemRow(it, state) }
            }
            if (view.refusedOther.isNotEmpty()) {
                header("refusedOther", Res.string.uploads_refused_other)
                items(view.refusedOther, key = { "refused:${it.rkey}" }) { change -> RefusedChangeRow(change, state) }
            }
            if (view.isEmpty) {
                item(key = "empty") {
                    EmptyListText(stringResource(Res.string.uploads_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The pauses, the time left, where uploads go on, and why the last action failed. */
private fun LazyListScope.banners(state: UploadsState) {
    state.error?.let { error -> item(key = "error") { ErrorText(error, Modifier.padding(vertical = 8.dp)) } }
    if (state.importsPaused) {
        item(key = "paused") {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.uploads_imports_paused), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                AppSecondaryButton(onClick = { state.eventSink(UploadsEvent.ResumeImports) }, enabled = !state.changingPause) {
                    Text(stringResource(Res.string.uploads_resume_imports))
                }
            }
        }
    } else if (state.showsPauseImports) {
        item(key = "pause") {
            AppSecondaryButton(
                onClick = { state.eventSink(UploadsEvent.PauseImports) },
                Modifier.padding(vertical = 4.dp),
                enabled = !state.changingPause,
            ) {
                Text(stringResource(Res.string.uploads_pause_imports))
            }
        }
    }
    if (state.serverPausedUntil != null) item(key = "serverPaused") { serverPaused(state)?.let { Note(it) } }
    UploadsTexts.timeLeft(state)?.let { left -> item(key = "timeLeft") { Note(left.string()) } }
    if (state.isOpenOnly) item(key = "openOnly") { Note(stringResource(Res.string.uploads_open_only)) }
}

private fun LazyListScope.header(key: String, title: StringResource) {
    item(key = "header:$key") { SectionTitle(stringResource(title)) }
}

/** An import's header, its live items (the first [UploadsState.IMPORT_PREVIEW] until Show all), then its finished ones. */
private fun LazyListScope.importGroup(group: UploadImport, state: UploadsState) {
    item(key = "import:${group.id}") { ImportHeader(group, state) }
    items(state.shownLive(group), key = { "import:${group.id}:${it.id}" }) { ItemRow(it, state) }
    if (state.showsShowAll(group)) {
        item(key = "import:${group.id}:showAll") {
            AppTextButton(onClick = { state.eventSink(UploadsEvent.ShowAll(group.id)) }) { Text(UploadImportTexts.showAll(group).string()) }
        }
    }
    items(group.finished, key = { "import:${group.id}:finished:${it.id}" }) { ItemRow(it, state) }
}

@Composable
private fun ImportHeader(group: UploadImport, state: UploadsState) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(UploadImportTexts.source(group.source).string(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (state.offersCancelRest(group)) {
                val description = UploadImportTexts.cancelRest(group).string()
                AppTextButton(
                    onClick = { state.eventSink(UploadsEvent.CancelRest(group.id)) },
                    Modifier.semantics { contentDescription = description },
                    enabled = !state.isBusy(group.id),
                ) {
                    Text(stringResource(Res.string.uploads_cancel_rest))
                }
            }
        }
        Detail(saved(group))
        UploadImportTexts.saveStatus(group)?.let { save ->
            Text(
                save.string(),
                color = if (group.save is ImportSaveStatus.Stopped) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        }
        Text(UploadImportTexts.progress(group).string())
        UploadImportTexts.counts(group)?.let { Detail(it.string()) }
    }
}

@Composable
private fun Confirmations(state: UploadsState) {
    state.confirmingCancel?.let { rkey ->
        AppAlertDialog(
            title = stringResource(Res.string.uploads_cancel_title),
            message = stringResource(Res.string.uploads_cancel_message),
            actions =
                persistentListOf(
                    AppDialogAction(stringResource(Res.string.uploads_cancel_confirm), destructive = true) {
                        state.eventSink(UploadsEvent.ConfirmCancel(rkey))
                    },
                ),
            cancelLabel = stringResource(Res.string.uploads_keep),
            onDismiss = { state.eventSink(UploadsEvent.Dismiss) },
        )
    }
    state.confirmingCancelRest?.let { batchId ->
        AppAlertDialog(
            title = stringResource(Res.string.uploads_cancel_rest_title),
            message = UploadsTexts.cancelRestMessage(state).string(),
            actions =
                persistentListOf(
                    AppDialogAction(stringResource(Res.string.uploads_cancel_rest), destructive = true) {
                        state.eventSink(UploadsEvent.ConfirmCancelRest(batchId))
                    },
                ),
            cancelLabel = stringResource(Res.string.uploads_keep),
            onDismiss = { state.eventSink(UploadsEvent.Dismiss) },
        )
    }
}
