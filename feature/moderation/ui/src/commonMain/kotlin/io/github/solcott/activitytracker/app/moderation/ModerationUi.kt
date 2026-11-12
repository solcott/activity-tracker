package io.github.solcott.activitytracker.app.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.ActivityCard
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableScrollColumn
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.moderation_help
import io.github.solcott.activitytracker.composeApp.resources.moderation_not_under_review
import io.github.solcott.activitytracker.composeApp.resources.moderation_open
import io.github.solcott.activitytracker.composeApp.resources.moderation_title
import io.github.solcott.activitytracker.composeApp.resources.moderation_uri
import io.github.solcott.activitytracker.composeApp.resources.moderation_uri_placeholder
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.stringResource

@CircuitInject(ModerationScreen::class, SessionScope::class)
@Composable
fun ModerationUi(state: ModerationState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.moderation_title),
                onBack = { state.eventSink(ModerationEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ReadableScrollColumn(padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.moderation_help), style = MaterialTheme.typography.bodyMedium)
            AppTextField(
                state = state.uri,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(Res.string.moderation_uri),
                placeholder = stringResource(Res.string.moderation_uri_placeholder),
                lineLimits = TextFieldLineLimits.SingleLine,
                onKeyboardAction = { state.eventSink(ModerationEvent.Open) },
            )
            AppButton(onClick = { state.eventSink(ModerationEvent.Open) }, enabled = ModerationSubjectTexts.canOpen(state)) {
                Text(stringResource(Res.string.moderation_open))
            }
            if (state.loading) AppProgressIndicator()
            state.error?.let { ErrorText(it) }
            state.subject?.let { Subject(it) }
        }
    }
}

@Composable
private fun Subject(subject: ModerationSubject, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (subject) {
            ModerationSubject.NotUnderReview -> Text(stringResource(Res.string.moderation_not_under_review))
            is ModerationSubject.Activity -> {
                Visibility(subject.visibility)
                ActivityCard(subject.activity, onClick = {}, onAuthorClick = null, onLike = null, onOpenMedia = {})
            }
            is ModerationSubject.Comment -> {
                Visibility(subject.visibility)
                Text(ModerationSubjectTexts.commentAuthor(subject.comment).string(), style = MaterialTheme.typography.labelLarge)
                Text(subject.comment.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Visibility(visibility: String) {
    Text(ModerationSubjectTexts.visibility(visibility).string(), style = MaterialTheme.typography.labelMedium)
}
