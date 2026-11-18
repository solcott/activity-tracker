package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.retained.rememberRetained
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job

/** What the AppView's check of a new place found: the live places of type [T] it would duplicate, and how many more may be made today. */
class PublishCheck<T>(val duplicates: List<T>, val remaining: Int)

/** What [checkThenPublish] last found: the duplicates, whether today's limit is reached, and the last failure (null for none). */
@Stable
class PublishChecks<T> {
    var duplicates: ImmutableList<T> by mutableStateOf(persistentListOf())
        private set

    var limitReached: Boolean by mutableStateOf(false)
        private set

    var error: UiError? by mutableStateOf(null)
        private set

    /** Forgets duplicates found for an earlier choice, so no state shows them with the new one. */
    fun clearDuplicates() {
        duplicates = persistentListOf()
    }

    internal fun checked(check: PublishCheck<T>) {
        duplicates = check.duplicates.toImmutableList()
        limitReached = check.remaining <= 0
    }

    internal fun failed(error: UiError?) {
        this.error = error
    }
}

/** A [PublishChecks] retained with the screen, so coming back from a duplicate shows what was found. */
@Composable fun <T> rememberPublishChecks(): PublishChecks<T> = rememberRetained { PublishChecks() }

/**
 * Under [key], at most once at a time: [check]s a new place, records what it found in [checks], and only when it duplicates nothing and
 * today's limit isn't reached [publish]es it and calls [onPublished]. A failed check is [checks]' error as [checkError], a failed publish
 * as [publishError]; each try clears the last one. Returns null when a try is already running.
 */
@Suppress("LongParameterList") // The two steps, each with its failure, and what follows a publish.
fun <T> SingleFlight.checkThenPublish(
    key: Any,
    checks: PublishChecks<T>,
    check: suspend () -> PublishCheck<T>,
    checkError: UiError.Kind,
    publish: suspend () -> Unit,
    publishError: UiError.Kind,
    onPublished: () -> Unit,
): Job? =
    launch(key) {
        checks.failed(null)
        val checked = runSuspendCatching { check() }.onFailure { checks.failed(UiError.of(checkError, it)) }.getOrNull() ?: return@launch
        checks.checked(checked)
        if (checks.duplicates.isNotEmpty() || checks.limitReached) return@launch
        runSuspendCatching { publish() }.onSuccess { onPublished() }.onFailure { checks.failed(UiError.of(publishError, it)) }
    }
