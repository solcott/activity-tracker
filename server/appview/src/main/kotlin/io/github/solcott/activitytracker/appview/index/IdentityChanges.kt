package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.lexicon.Did
import java.util.concurrent.CopyOnWriteArrayList
import org.slf4j.LoggerFactory

/**
 * Who hears that an account's DID document may have changed (identity/outbound-requests): the indexer reports each Jetstream `#identity`
 * event here, and every cache of identities (DID documents, signing keys, PDS URLs, `#atproto_kem` keys) and the handle sweep listen, so
 * the next use asks again. Listeners must be quick and never fetch: a burst of events (a key rollout) reaches them one after another.
 */
class IdentityChanges {
    private val log = LoggerFactory.getLogger(IdentityChanges::class.java)
    private val listeners = CopyOnWriteArrayList<suspend (Did) -> Unit>()

    fun onChange(listener: suspend (Did) -> Unit) {
        listeners += listener
    }

    /** Tells every listener; one that fails is logged and the rest still hear. */
    suspend fun changed(did: Did) {
        for (listener in listeners) {
            @Suppress("TooGenericExceptionCaught") // A cache's failure mustn't stop indexing.
            try {
                listener(did)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Identity change listener failed for {}: {}", did.value, e.message)
            }
        }
    }
}
