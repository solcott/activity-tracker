package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.dataresult.DataError
import io.github.solcott.dataresult.Outcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile

/**
 * [this] read of an activity, asked again every so often, a few times, while the AppView answers that it has none and the activity was
 * [justWritten] on this device moments ago (a combined one, or one just saved): the AppView serves it only once it has indexed it. Until
 * the last try, that answer isn't passed on, so the page stays loading rather than failing; any other failure passes at once. Once a try
 * after the first answers, [indexed] is called: what was read alongside the first try, before the AppView had it, can be read again.
 */
internal fun <T> Flow<Outcome<T>>.awaitIndexed(indexed: () -> Unit = {}, justWritten: suspend () -> Boolean): Flow<Outcome<T>> = flow {
    repeat(MAX_INDEX_WAITS + 1) { attempt ->
        var again = false
        this@awaitIndexed.transformWhile { outcome ->
                again = attempt < MAX_INDEX_WAITS && outcome.isNotIndexed() && justWritten()
                // A try after the first is still the first load: its own Loading would say nothing new.
                if (!again && !(attempt > 0 && outcome is Outcome.Loading)) emit(outcome)
                if (attempt > 0 && outcome is Outcome.Data) indexed()
                !again
            }
            .collect { emit(it) }
        if (!again) return@flow
        delay(INDEX_WAIT_MILLIS)
    }
}

/** The page's activity was written on this device moments ago: just saved ([ActivityDetailScreen.suggestOnOpen]), combined or edited. */
internal suspend fun justWritten(screen: ActivityDetailScreen, feed: FeedRepository): Boolean =
    screen.suggestOnOpen || feed.rewrites.observe(AtUri(screen.uri)).first() != null

private fun Outcome<*>.isNotIndexed(): Boolean = this is Outcome.Error && (cause as? DataError.Api)?.code == NOT_FOUND

/** The AppView's answer for an activity it doesn't serve. */
private const val NOT_FOUND = "NotFound"

/** How often, and how long apart, a just-written activity is asked for again before its load error stands. */
internal const val MAX_INDEX_WAITS = 10
internal const val INDEX_WAIT_MILLIS = 1_500L
