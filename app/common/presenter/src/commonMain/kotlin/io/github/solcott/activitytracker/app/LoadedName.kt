package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.slack.circuit.retained.produceRetainedState
import io.github.solcott.activitytracker.atproto.runSuspendCatching

/**
 * The name a page is titled with (ui/deep-links): [given] when the screen carries one, as it does when opened inside the app, else what
 * [load] answers once it has, keyed by [key], and empty until then or when it fails, since the page's own content says why.
 */
@Composable
fun rememberShownName(given: String?, key: Any, load: suspend () -> String?): String {
    val latestLoad by rememberUpdatedState(load)
    val loaded by
        produceRetainedState<String?>(initialValue = null, given, key) {
            if (given == null) value = runSuspendCatching { latestLoad() }.getOrNull()
        }
    return given ?: loaded.orEmpty()
}
