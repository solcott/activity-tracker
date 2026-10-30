package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_retry
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.errorOrNull
import io.github.solcott.uistate.hasLoaded
import io.github.solcott.uistate.isLoading
import org.jetbrains.compose.resources.stringResource

/**
 * Content loaded into [state] (ui/content-loading). Until it first loads: a spinner, or why it failed ([errorKind] says what) with
 * [onRetry]. Once it has: [content], kept through every refresh and failed refresh after. [content] shows those with [RefreshStatus], or a
 * pull to refresh's own spinner.
 */
@Composable
fun <T> ContentStateLayout(
    state: ContentState<T>,
    errorKind: UiError.Kind,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable (T) -> Unit,
) {
    val error = state.errorOrNull
    when {
        state.hasLoaded -> content(state.data)
        error != null ->
            Column(
                modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(errorText(UiError(errorKind, error)), Modifier.testTag(TestTags.LOAD_ERROR), color = MaterialTheme.colorScheme.error)
                AppTextButton(onClick = onRetry, Modifier.testTag(TestTags.RETRY)) { Text(stringResource(Res.string.action_retry)) }
            }
        else ->
            Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
                AppProgressIndicator(Modifier.testTag(TestTags.LOADING))
            }
    }
}

/**
 * Over loaded content: a spinner while [state] refreshes (unless [showProgress] is off, for a pull to refresh that shows its own), or why
 * the last refresh failed with [onRetry]. Nothing once it settles.
 */
@Composable
fun RefreshStatus(
    state: ContentState<*>,
    errorKind: UiError.Kind,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showProgress: Boolean = true,
) {
    val error = state.errorOrNull
    when {
        error != null ->
            Row(
                modifier.fillMaxWidth().testTag(TestTags.REFRESH_ERROR),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(errorText(UiError(errorKind, error)), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                AppTextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
            }
        state.isLoading && showProgress ->
            Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AppProgressIndicator(Modifier.testTag(TestTags.REFRESHING), small = true)
            }
    }
}
