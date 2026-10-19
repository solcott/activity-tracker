package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * While an activity this device [written] moments ago (a combined one, say) [failed] to load, calls [retry] every so often, a few times:
 * the AppView serves it only once it has indexed it.
 */
@Composable
internal fun AwaitIndexed(failed: Boolean, written: Boolean, retry: () -> Unit) {
    var waits by remember { mutableIntStateOf(0) }
    val again by rememberUpdatedState(retry)
    val waiting = failed && written
    LaunchedEffect(waiting, waits) {
        if (waiting && waits < MAX_INDEX_WAITS) {
            delay(INDEX_WAIT_MILLIS)
            waits++
            again()
        }
    }
}

/** How often, and how long apart, a just-written activity is asked for again before its load error stands. */
private const val MAX_INDEX_WAITS = 10
private const val INDEX_WAIT_MILLIS = 1_500L
