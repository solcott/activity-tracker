package io.github.solcott.activitytracker.app.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.ActivityCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.handleLabel
import io.github.solcott.activitytracker.app.ui.visibilityLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.moderation_help
import io.github.solcott.activitytracker.composeApp.resources.moderation_not_under_review
import io.github.solcott.activitytracker.composeApp.resources.moderation_open
import io.github.solcott.activitytracker.composeApp.resources.moderation_title
import io.github.solcott.activitytracker.composeApp.resources.moderation_uri
import io.github.solcott.activitytracker.composeApp.resources.moderation_uri_placeholder
import io.github.solcott.activitytracker.composeApp.resources.moderation_visibility
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.stringResource

@CircuitInject(ModerationScreen::class, SessionScope::class)
@Composable
fun ModerationUi(state: ModerationState, modifier: Modifier = Modifier) {
    AppScaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = stringResource(Res.string.moderation_title), onBack = { state.eventSink(ModerationEvent.Back) }) },
    ) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(padding).readableWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.moderation_help), style = MaterialTheme.typography.bodyMedium)
            AppTextField(
                state = state.uri,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(Res.string.moderation_uri),
                placeholder = stringResource(Res.string.moderation_uri_placeholder),
                lineLimits = TextFieldLineLimits.SingleLine,
                onKeyboardAction = { state.eventSink(ModerationEvent.Open) },
            )
            AppButton(onClick = { state.eventSink(ModerationEvent.Open) }, enabled = !state.loading) {
                Text(stringResource(Res.string.moderation_open))
            }
            if (state.loading) AppProgressIndicator()
            state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
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
                val author = subject.comment.author
                Text(author.displayName ?: handleLabel(author.handle.value), style = MaterialTheme.typography.labelLarge)
                Text(subject.comment.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Visibility(visibility: String) {
    Text(stringResource(Res.string.moderation_visibility, visibilityLabel(visibility)), style = MaterialTheme.typography.labelMedium)
}
