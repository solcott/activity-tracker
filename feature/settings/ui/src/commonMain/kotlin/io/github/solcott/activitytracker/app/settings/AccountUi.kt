package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.ArchiveHandOffResult
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppListRow
import io.github.solcott.activitytracker.app.ui.adaptive.AppListSection
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableScrollColumn
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.rememberArchiveHandOff
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.app.ui.theme.UiPlatform
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.account_did
import io.github.solcott.activitytracker.composeApp.resources.account_did_copied
import io.github.solcott.activitytracker.composeApp.resources.account_did_copy
import io.github.solcott.activitytracker.composeApp.resources.account_handle
import io.github.solcott.activitytracker.composeApp.resources.account_leave_summary
import io.github.solcott.activitytracker.composeApp.resources.account_managed_by_server
import io.github.solcott.activitytracker.composeApp.resources.account_section_export
import io.github.solcott.activitytracker.composeApp.resources.account_section_identity
import io.github.solcott.activitytracker.composeApp.resources.account_section_leave
import io.github.solcott.activitytracker.composeApp.resources.account_section_managed
import io.github.solcott.activitytracker.composeApp.resources.account_section_sign_in
import io.github.solcott.activitytracker.composeApp.resources.account_server
import io.github.solcott.activitytracker.composeApp.resources.account_title
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.action_sign_in_again
import io.github.solcott.activitytracker.composeApp.resources.export_data
import io.github.solcott.activitytracker.composeApp.resources.export_data_cancel
import io.github.solcott.activitytracker.composeApp.resources.export_data_delete
import io.github.solcott.activitytracker.composeApp.resources.export_data_keep_open
import io.github.solcott.activitytracker.composeApp.resources.export_data_save
import io.github.solcott.activitytracker.composeApp.resources.export_data_shared_delete
import io.github.solcott.activitytracker.composeApp.resources.export_data_shared_keep
import io.github.solcott.activitytracker.composeApp.resources.export_data_shared_message
import io.github.solcott.activitytracker.composeApp.resources.export_data_shared_title
import io.github.solcott.activitytracker.composeApp.resources.export_data_try_again
import io.github.solcott.activitytracker.composeApp.resources.leave_title
import io.github.solcott.activitytracker.composeApp.resources.profile_sign_out
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/**
 * The Account page (auth/account-page): the account and the server hosting it, whether the sign-in is up to date, where the password and
 * deleting the account live, and sign out, which asks first when work is still pending on this device.
 */
@CircuitInject(AccountScreen::class, SessionScope::class)
@Composable
fun AccountUi(state: AccountState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.account_title),
                onBack = { state.eventSink(AccountEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ReadableScrollColumn(padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Identity(state)
            AppListSection(stringResource(Res.string.account_section_sign_in)) { Grant(state) }
            AppListSection(stringResource(Res.string.account_section_managed)) {
                Text(
                    stringResource(Res.string.account_managed_by_server, state.pdsHost),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            AppListSection(stringResource(Res.string.account_section_export)) { ExportSection(state) }
            AppListSection(null) {
                AppListRow(
                    stringResource(Res.string.profile_sign_out),
                    onClick = { state.eventSink(AccountEvent.SignOut) },
                    enabled = !state.ending,
                )
            }
            // Apart from Sign out and from the server's page, where the account itself is deleted (auth/leaving).
            AppListSection(stringResource(Res.string.account_section_leave)) {
                AppListRow(
                    stringResource(Res.string.leave_title),
                    summary = stringResource(Res.string.account_leave_summary, state.handle),
                    onClick = { state.eventSink(AccountEvent.Leave) },
                    enabled = !state.ending,
                )
            }
        }
    }
    state.confirming?.let { EndDialog(it, state.eventSink) }
    if (state.askingKeptShared) KeptSharedDialog(state.eventSink)
}

/** The data export (auth/data-export): what it holds, then its progress, or the finished archive to save. */
@Composable
private fun ExportSection(state: AccountState) {
    val handOff = rememberArchiveHandOff { result ->
        when (result) {
            ArchiveHandOffResult.Saved -> state.eventSink(AccountEvent.ExportSaved)
            ArchiveHandOffResult.Shared -> state.eventSink(AccountEvent.ExportShared)
            ArchiveHandOffResult.Failed -> state.eventSink(AccountEvent.ExportSaveFailed)
            ArchiveHandOffResult.Dismissed -> Unit
        }
    }
    val body = MaterialTheme.typography.bodyMedium
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val export = state.export
        val lines = AccountTexts.exportLines(export)
        when (export) {
            DataExportStatus.None -> {
                lines.forEach { Text(it.string(), style = body) }
                AppButton(onClick = { state.eventSink(AccountEvent.Export) }) { Text(stringResource(Res.string.export_data)) }
            }
            is DataExportStatus.Running -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppProgressIndicator(small = true)
                    Text(AccountTexts.running(export).string(), style = body)
                }
                if (LocalUiPlatform.current == UiPlatform.Web) Text(stringResource(Res.string.export_data_keep_open), style = body)
                AppSecondaryButton(onClick = { state.eventSink(AccountEvent.CancelExport) }) {
                    Text(stringResource(Res.string.export_data_cancel))
                }
            }
            is DataExportStatus.Ready -> {
                lines.forEach { Text(it.string(), style = body) }
                AppButton(onClick = { handOff(export.archive) }) { Text(stringResource(Res.string.export_data_save)) }
                AppSecondaryButton(onClick = { state.eventSink(AccountEvent.CancelExport) }) {
                    Text(stringResource(Res.string.export_data_delete))
                }
            }
            is DataExportStatus.Paused,
            DataExportStatus.Failed -> {
                lines.forEach { Text(it.string(), style = body) }
                RetryOrCancel(state)
            }
        }
        state.exportError?.let { Text(errorText(it), style = body, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun RetryOrCancel(state: AccountState) {
    AppButton(onClick = { state.eventSink(AccountEvent.Export) }) { Text(stringResource(Res.string.export_data_try_again)) }
    AppSecondaryButton(onClick = { state.eventSink(AccountEvent.CancelExport) }) { Text(stringResource(Res.string.export_data_cancel)) }
}

/** iOS's share sheet doesn't say whether the zip was kept, so the athlete says whether the app's copy can go. */
@Composable
private fun KeptSharedDialog(eventSink: (AccountEvent) -> Unit) {
    AppAlertDialog(
        title = stringResource(Res.string.export_data_shared_title),
        message = stringResource(Res.string.export_data_shared_message),
        actions =
            persistentListOf(
                AppDialogAction(stringResource(Res.string.export_data_shared_delete), destructive = true) {
                    eventSink(AccountEvent.KeptShared(kept = true))
                },
            ),
        cancelLabel = stringResource(Res.string.export_data_shared_keep),
        onDismiss = { eventSink(AccountEvent.KeptShared(kept = false)) },
    )
}

@Composable
private fun Identity(state: AccountState) {
    // Compose's newer clipboard API has no common way to build a plain-text entry yet; this one still works on every target.
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    AppListSection(stringResource(Res.string.account_section_identity)) {
        AppListRow(stringResource(Res.string.account_handle), summary = state.handle)
        AppListRow(
            stringResource(Res.string.account_did),
            summary = state.did,
            trailing = {
                AppSecondaryButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(state.did))
                        copied = true
                    },
                ) {
                    Text(stringResource(if (copied) Res.string.account_did_copied else Res.string.account_did_copy))
                }
            },
        )
        AppListRow(stringResource(Res.string.account_server), summary = state.pdsHost)
    }
}

@Composable
private fun Grant(state: AccountState) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(AccountTexts.signIn(state.grant).string(), style = MaterialTheme.typography.bodyMedium)
        AccountTexts.missingFeatures(state.grant).forEach { Text("• " + it.string(), style = MaterialTheme.typography.bodyMedium) }
        if (state.offersSignInAgain) {
            AppSecondaryButton(onClick = { state.eventSink(AccountEvent.SignInAgain) }, enabled = !state.ending) {
                Text(stringResource(Res.string.action_sign_in_again))
            }
        }
    }
}

@Composable
private fun EndDialog(ending: EndingSession, eventSink: (AccountEvent) -> Unit) {
    AppAlertDialog(
        title = AccountTexts.endTitle(ending).string(),
        message = AccountTexts.endLines(ending).map { it.string() }.joinToString("\n\n"),
        actions =
            persistentListOf(
                AppDialogAction(AccountTexts.endAction(ending).string(), destructive = true) {
                    eventSink(AccountEvent.Confirm(ending.signInAgain))
                },
            ),
        cancelLabel = stringResource(Res.string.action_cancel),
        onDismiss = { eventSink(AccountEvent.Dismiss) },
    )
}
