package io.github.solcott.activitytracker.atproto

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException

/** Like [runCatching], but rethrows [CancellationException] so callers in coroutines stay cancellable. */
@Suppress("TooGenericExceptionCaught")
inline fun <R> runSuspendCatching(block: () -> R): Result<R> {
    return runCatching(block).throwIf { it is CancellationException }
}

@OptIn(ExperimentalContracts::class)
inline fun <V> Result<V>.throwIf(predicate: (Throwable) -> Boolean): Result<V> {
    contract { callsInPlace(predicate, InvocationKind.AT_MOST_ONCE) }
    val error = this.exceptionOrNull()
    return when {
        error != null && predicate(error) -> throw error
        else -> this
    }
}
