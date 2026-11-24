package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.SignInAgainNotice
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_sign_in_again
import org.jetbrains.compose.resources.stringResource

/** A small explanatory line in a list, inset like its rows. */
@Composable
fun ListNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** What a list says when it lists nothing, centred where its rows would be. */
@Composable
fun EmptyListText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text(text, color = color) }
}

/** Why a list's action failed, as an item inset like its rows (as [ListNote] is), under [key]. Nothing when there's no [error]. */
fun LazyListScope.errorItem(error: UiError?, key: String = "error") {
    if (error == null) return
    item(key = key) { ErrorText(error, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) }
}

/**
 * Why a list's action failed, or that the session must sign in again ([needsSignIn]) when there's no [error], with Sign in again
 * ([onSignInAgain]) where [SignInAgainNotice] offers it. Nothing when there's neither.
 */
fun LazyListScope.signInAgainItem(error: UiError?, needsSignIn: Boolean, onSignInAgain: () -> Unit) {
    val notice = SignInAgainNotice.of(error, needsSignIn) ?: return
    item(key = "error") {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ErrorText(notice.error)
            if (notice.offersSignInAgain) {
                AppSecondaryButton(onClick = onSignInAgain) { Text(stringResource(Res.string.action_sign_in_again)) }
            }
        }
    }
}
