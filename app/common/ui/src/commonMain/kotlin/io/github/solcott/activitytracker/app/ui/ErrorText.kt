package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.ErrorTexts
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_retry
import io.github.solcott.dataresult.DataError
import org.jetbrains.compose.resources.stringResource

/**
 * An error to show: what failed and, when it's one the athlete can act on or wait out, why. Never the failure's own text, status code or
 * error name.
 */
@Composable fun errorText(error: UiError): String = ErrorTexts.of(error).string()

/** [error]'s [errorText], in the error colour. */
@Composable
fun ErrorText(error: UiError, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
    Text(errorText(error), modifier, color = MaterialTheme.colorScheme.error, style = style)
}

/** [error] beside a Retry button that runs [onRetry]: a failure that keeps whatever loaded above or around it. */
@Composable
fun ErrorRetryRow(error: UiError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ErrorText(error, Modifier.weight(1f))
        AppTextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
    }
}

/** [what] failed, followed by why when [reason] is one the athlete can act on or wait out. */
@Composable fun failureText(what: String, reason: DataError?): String = ErrorTexts.failure(AppText.written(what), reason).string()
