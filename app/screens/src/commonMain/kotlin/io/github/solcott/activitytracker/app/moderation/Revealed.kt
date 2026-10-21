package io.github.solcott.activitytracker.app.moderation

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * What the viewer chose to see on this screen despite its labels (`moderation/labels`): an activity's blurred photos (its URI), an
 * athlete's blurred avatar (their DID), or a hidden subject's content ([hiddenKey]). It lasts only as long as the screen: the presenter
 * keeps it in plain state, so leaving the screen blurs and collapses everything again. Its sink reveals a key.
 */
@Immutable
data class Revealed(val keys: ImmutableSet<String> = persistentSetOf(), val eventSink: (String) -> Unit = {}) {
    operator fun contains(key: String): Boolean = key in keys

    companion object {
        /** The key for opening [uri]'s content when it's hidden, apart from revealing its photos. */
        fun hiddenKey(uri: String): String = "hidden:$uri"
    }
}
