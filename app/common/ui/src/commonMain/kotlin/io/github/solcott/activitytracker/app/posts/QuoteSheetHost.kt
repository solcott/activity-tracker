package io.github.solcott.activitytracker.app.posts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.TestTags
import io.github.solcott.activitytracker.app.ui.VisibilityPicker
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppBottomSheet
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppSheetHeader
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.post_share_public_only_sign_in
import io.github.solcott.activitytracker.composeApp.resources.quote_delete_confirm
import io.github.solcott.activitytracker.composeApp.resources.quote_delete_message
import io.github.solcott.activitytracker.composeApp.resources.quote_delete_title
import io.github.solcott.activitytracker.composeApp.resources.quote_sheet_post
import io.github.solcott.activitytracker.composeApp.resources.quote_sheet_title
import io.github.solcott.activitytracker.composeApp.resources.quote_text_field
import io.github.solcott.activitytracker.composeApp.resources.quote_text_too_long
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/**
 * A host's quote sheet and delete confirmation (feed/quote-posts), from its [QuoteFields]: while it's quoting a post, a sheet with the post
 * being quoted, the athlete's words, and who may see the quote; while Delete asks, its confirmation.
 */
@Composable
fun QuoteSheetHost(fields: QuoteFields) {
    fields.quoting?.let { post ->
        AppBottomSheet(onDismiss = { fields.eventSink(QuoteEvent.Cancel) }, sheetState = rememberAppSheetState()) {
            QuoteSheet(fields, post.author.shownName, post.text)
        }
    }
    fields.confirmDelete?.let {
        AppAlertDialog(
            title = stringResource(Res.string.quote_delete_title),
            message = stringResource(Res.string.quote_delete_message),
            actions =
                persistentListOf(
                    AppDialogAction(stringResource(Res.string.quote_delete_confirm), destructive = true) {
                        fields.eventSink(QuoteEvent.ConfirmDelete(true))
                    }
                ),
            cancelLabel = stringResource(Res.string.action_cancel),
            onDismiss = { fields.eventSink(QuoteEvent.ConfirmDelete(false)) },
        )
    }
}

/** The quote sheet's content: the post being quoted, the athlete's words, the audience, and why a quote couldn't be posted. */
@Composable
fun QuoteSheet(fields: QuoteFields, quotedAuthor: String, quotedText: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        AppSheetHeader(
            title = stringResource(Res.string.quote_sheet_title),
            cancelLabel = stringResource(Res.string.action_cancel),
            onCancel = { fields.eventSink(QuoteEvent.Cancel) },
            confirmLabel = stringResource(Res.string.quote_sheet_post),
            onConfirm = { if (fields.canPost) fields.eventSink(QuoteEvent.Post) },
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(quotedAuthor, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                quotedText?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3) }
            }
            AppTextField(
                state = fields.text,
                modifier = Modifier.fillMaxWidth().testTag(TestTags.QUOTE_TEXT),
                label = stringResource(Res.string.quote_text_field),
                enabled = !fields.posting,
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 6),
            )
            if (!fields.textValid) {
                Text(
                    stringResource(Res.string.quote_text_too_long, QuoteFields.MAX_TEXT_LENGTH),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            VisibilityPicker(
                selected = fields.audience,
                onSelect = { fields.eventSink(QuoteEvent.AudienceChanged(it)) },
                approvesFollowers = false,
                enabled = !fields.posting,
                options = fields.audiences,
            )
            if (fields.publicOnly) {
                Text(
                    stringResource(Res.string.post_share_public_only_sign_in),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            fields.error?.let { ErrorText(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
