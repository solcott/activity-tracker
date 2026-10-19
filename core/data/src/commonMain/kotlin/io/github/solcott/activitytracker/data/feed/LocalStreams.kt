package io.github.solcott.activitytracker.data.feed

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Streams the athlete rebuilt this session (a combined activity's new sources or members), by activity URI. The AppView serves the old
 * streams until Jetstream brings it the new records, so its reads are laid over with these until it serves the same data, as
 * [LocalRewrites] does for records.
 */
class LocalStreams {
    private val streams = MutableStateFlow(emptyMap<String, GetActivityStreams.Output>())

    fun set(uri: AtUri, output: GetActivityStreams.Output) = streams.update { it + (uri.value to output) }

    /**
     * [served] (null when the AppView has none for [uri]), or the streams the athlete rebuilt [uri] with, until the AppView serves them.
     */
    fun applyTo(uri: AtUri, served: GetActivityStreams.Output?): GetActivityStreams.Output? {
        val mine = streams.value[uri.value] ?: return served
        val caughtUp = served != null && served.data == mine.data
        if (caughtUp) streams.update { it - uri.value }
        return if (caughtUp) served else mine
    }
}
