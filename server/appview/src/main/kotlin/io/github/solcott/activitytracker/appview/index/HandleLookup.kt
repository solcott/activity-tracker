package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.lexicon.Did
import java.util.concurrent.ConcurrentHashMap

/** DID → handle, via the DID document's `alsoKnownAs`, cached for the process lifetime. */
fun interface HandleLookup {
    suspend fun handleFor(did: Did): String

    fun invalidate(did: Did) {}
}

class ResolvingHandleLookup(private val resolver: IdentityResolver) : HandleLookup {
    private val cache = ConcurrentHashMap<Did, String>()

    override suspend fun handleFor(did: Did): String =
        cache[did]
            ?: run {
                val handle = runSuspendCatching { resolver.resolveDid(did).handle }.getOrNull() ?: IdentityResolver.INVALID_HANDLE.value
                cache[did] = handle
                handle
            }

    override fun invalidate(did: Did) {
        cache.remove(did)
    }
}
