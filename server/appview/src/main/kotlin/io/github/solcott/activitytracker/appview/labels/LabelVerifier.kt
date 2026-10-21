package io.github.solcott.activitytracker.appview.labels

import io.github.solcott.activitytracker.appview.auth.Multikey
import io.github.solcott.activitytracker.appview.auth.SigningKey
import java.util.concurrent.ConcurrentHashMap

/**
 * Checks a label's signature against its labeler's `#atproto_label` key ([keys] reads it from the DID document, fresh when asked). A
 * failure refreshes the key once before the label is refused, which covers a rotation.
 */
class LabelVerifier(private val keys: suspend (did: String, fresh: Boolean) -> String?) {
    private val cache = ConcurrentHashMap<String, SigningKey>()

    /** Whether [label] came from [source] (the stream it arrived on) and is signed by its key. */
    suspend fun verify(label: Label, source: String): Boolean {
        val sig = label.sig
        val bytes = runCatching { label.signedBytes() }.getOrNull()
        if (sig == null || bytes == null || label.src != source) return false
        return listOf(false, true).any { fresh -> key(source, fresh)?.verifies(bytes, sig) == true }
    }

    private suspend fun key(did: String, fresh: Boolean): SigningKey? =
        cache[did]?.takeUnless { fresh }
            ?: keys(did, fresh)?.let { runCatching { Multikey.decode(it) }.getOrNull() }?.also { cache[did] = it }
}
