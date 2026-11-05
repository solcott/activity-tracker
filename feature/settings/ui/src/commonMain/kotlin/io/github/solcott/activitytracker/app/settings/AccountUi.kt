package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppListRow
import io.github.solcott.activitytracker.app.ui.adaptive.AppListSection
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableScrollColumn
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.account_did
import io.github.solcott.activitytracker.composeApp.resources.account_did_copied
import io.github.solcott.activitytracker.composeApp.resources.account_did_copy
import io.github.solcott.activitytracker.composeApp.resources.account_end_changes
import io.github.solcott.activitytracker.composeApp.resources.account_end_import
import io.github.solcott.activitytracker.composeApp.resources.account_end_sign_in_again_anyway
import io.github.solcott.activitytracker.composeApp.resources.account_end_sign_out_anyway
import io.github.solcott.activitytracker.composeApp.resources.account_end_title
import io.github.solcott.activitytracker.composeApp.resources.account_end_title_sign_in_again
import io.github.solcott.activitytracker.composeApp.resources.account_feature_blocks_and_mutes
import io.github.solcott.activitytracker.composeApp.resources.account_feature_gear
import io.github.solcott.activitytracker.composeApp.resources.account_feature_privacy_zones
import io.github.solcott.activitytracker.composeApp.resources.account_feature_private_activities
import io.github.solcott.activitytracker.composeApp.resources.account_feature_private_interactions
import io.github.solcott.activitytracker.composeApp.resources.account_feature_private_profile
import io.github.solcott.activitytracker.composeApp.resources.account_feature_segments_and_groups
import io.github.solcott.activitytracker.composeApp.resources.account_handle
import io.github.solcott.activitytracker.composeApp.resources.account_managed_by_server
import io.github.solcott.activitytracker.composeApp.resources.account_section_identity
import io.github.solcott.activitytracker.composeApp.resources.account_section_managed
import io.github.solcott.activitytracker.composeApp.resources.account_section_sign_in
import io.github.solcott.activitytracker.composeApp.resources.account_server
import io.github.solcott.activitytracker.composeApp.resources.account_sign_in_current
import io.github.solcott.activitytracker.composeApp.resources.account_sign_in_outdated
import io.github.solcott.activitytracker.composeApp.resources.account_sign_in_spaces_unsupported
import io.github.solcott.activitytracker.composeApp.resources.account_title
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.action_sign_in_again
import io.github.solcott.activitytracker.composeApp.resources.profile_sign_out
import io.github.solcott.activitytracker.data.auth.GrantFeature
import io.github.solcott.activitytracker.data.auth.GrantStatus
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
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
            AppListSection(null) {
                AppListRow(
                    stringResource(Res.string.profile_sign_out),
                    onClick = { state.eventSink(AccountEvent.SignOut) },
                    enabled = !state.ending,
                )
            }
        }
    }
    state.confirming?.let { EndDialog(it, state.eventSink) }
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
                    }
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
        when (val grant = state.grant) {
            GrantStatus.Current -> Text(stringResource(Res.string.account_sign_in_current), style = MaterialTheme.typography.bodyMedium)
            GrantStatus.SpacesUnsupported ->
                Text(stringResource(Res.string.account_sign_in_spaces_unsupported), style = MaterialTheme.typography.bodyMedium)
            is GrantStatus.Outdated -> {
                Text(stringResource(Res.string.account_sign_in_outdated), style = MaterialTheme.typography.bodyMedium)
                GrantFeature.entries
                    .filter { it in grant.missing }
                    .forEach { Text("• " + stringResource(it.label), style = MaterialTheme.typography.bodyMedium) }
                AppSecondaryButton(onClick = { state.eventSink(AccountEvent.SignInAgain) }, enabled = !state.ending) {
                    Text(stringResource(Res.string.action_sign_in_again))
                }
            }
        }
    }
}

@Composable
private fun EndDialog(ending: EndingSession, eventSink: (AccountEvent) -> Unit) {
    val changes = ending.pending.changes.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.account_end_changes, it, it) }
    val import = if (ending.pending.importSaving) stringResource(Res.string.account_end_import) else null
    AppAlertDialog(
        title = stringResource(if (ending.signInAgain) Res.string.account_end_title_sign_in_again else Res.string.account_end_title),
        message = listOfNotNull(changes, import).joinToString("\n\n"),
        actions =
            persistentListOf(
                AppDialogAction(
                    stringResource(
                        if (ending.signInAgain) Res.string.account_end_sign_in_again_anyway else Res.string.account_end_sign_out_anyway
                    ),
                    destructive = true,
                ) {
                    eventSink(AccountEvent.Confirm(ending.signInAgain))
                }
            ),
        cancelLabel = stringResource(Res.string.action_cancel),
        onDismiss = { eventSink(AccountEvent.Dismiss) },
    )
}

private val GrantFeature.label: StringResource
    get() =
        when (this) {
            GrantFeature.PrivateActivities -> Res.string.account_feature_private_activities
            GrantFeature.PrivacyZones -> Res.string.account_feature_privacy_zones
            GrantFeature.PrivateProfile -> Res.string.account_feature_private_profile
            GrantFeature.PrivateInteractions -> Res.string.account_feature_private_interactions
            GrantFeature.BlocksAndMutes -> Res.string.account_feature_blocks_and_mutes
            GrantFeature.Gear -> Res.string.account_feature_gear
            GrantFeature.SegmentsAndGroups -> Res.string.account_feature_segments_and_groups
        }
