package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.atproto.identity.HandleNotFoundException
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.identity.isResolvableDid
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** The outcome of checking a DID's handle both ways (identity/handle-verification). */
sealed interface HandleCheck {
    /** The DID document claims [handle], and [handle] resolves back to the DID. */
    data class Verified(val handle: String) : HandleCheck

    /** No claimed handle, or one that resolves to another DID or to none: shown as `handle.invalid`. */
    data object Invalid : HandleCheck

    /** The DID document or every handle lookup couldn't be reached: the last verified handle stands, and the check is retried sooner. */
    data object Unreachable : HandleCheck
}

/** DID → verified handle. Tests pass a lambda giving each DID's handle, which counts as verified (or invalid for `handle.invalid`). */
fun interface HandleLookup {
    suspend fun handleFor(did: Did): String

    suspend fun check(did: Did): HandleCheck =
        handleFor(did).let { if (it == IdentityResolver.INVALID_HANDLE.value) HandleCheck.Invalid else HandleCheck.Verified(it) }
}

/** Checks the handle a DID document claims (`alsoKnownAs`) by resolving it back. Nothing is cached: every call asks again. */
class HandleVerifier(private val resolver: IdentityResolver) : HandleLookup {
    override suspend fun handleFor(did: Did): String =
        (check(did) as? HandleCheck.Verified)?.handle ?: IdentityResolver.INVALID_HANDLE.value

    override suspend fun check(did: Did): HandleCheck {
        // A malformed DID is refused before any request, so it can never be verified.
        val document = runSuspendCatching {
            resolver.resolveDid(did)
        }
            .getOrElse {
                return if (isResolvableDid(did.value)) HandleCheck.Unreachable else HandleCheck.Invalid
            }
        val claimed = document.handle?.lowercase()?.takeIf(HANDLE::matches)
        return claimed?.let { resolvesBack(did, it) } ?: HandleCheck.Invalid
    }

    private suspend fun resolvesBack(did: Did, claimed: String): HandleCheck = runSuspendCatching {
        resolver.resolveHandle(Handle(claimed))
    }
        .fold(
            { resolved -> if (resolved == did) HandleCheck.Verified(claimed) else HandleCheck.Invalid },
            { if (it is HandleNotFoundException) HandleCheck.Invalid else HandleCheck.Unreachable },
        )
}

/** The handle a check stores: none for [HandleCheck.Unreachable], which keeps whatever is stored. */
internal fun handleOf(check: HandleCheck): String? =
    when (check) {
        is HandleCheck.Verified -> check.handle
        HandleCheck.Invalid -> IdentityResolver.INVALID_HANDLE.value
        HandleCheck.Unreachable -> null
    }

/** When a handle is checked again after [check]: a day after an answer, an hour after lookups that couldn't be reached. */
internal fun nextHandleCheck(check: HandleCheck, now: Instant): Long =
    (now + if (check == HandleCheck.Unreachable) HANDLE_RETRY else HANDLE_RECHECK).toEpochMilliseconds()

private val HANDLE_RECHECK = 24.hours
private val HANDLE_RETRY = 1.hours

/** A domain name of at least two labels: anything else can't be a handle, and would only be sent to the lookups to fail. */
private val HANDLE = Regex("""[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+""")
