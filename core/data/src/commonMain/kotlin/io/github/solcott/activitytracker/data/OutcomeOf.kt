package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.atproto.oauth.OAuthServerException
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.dataresult.DataError
import io.github.solcott.dataresult.Origin
import io.github.solcott.dataresult.Outcome
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/**
 * Runs [block] and answers with its value, or with what its failure means to a reader: the one place a read's exceptions become a
 * [DataError]. [origin] is where both came from. Rethrows [CancellationException], like `runSuspendCatching`.
 */
@Suppress("TooGenericExceptionCaught")
suspend fun <T> outcomeOf(origin: Origin = Origin.Network, block: suspend () -> T): Outcome<T> =
    try {
        Outcome.Data(block(), origin)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Outcome.Error(e.toDataError(), origin)
    }

/** [outcomeOf] as a stream: [Outcome.Loading], then the answer. Each collection runs [block] again, which is how a retry re-fetches. */
fun <T> outcomeFlow(origin: Origin = Origin.Network, block: suspend () -> T): Flow<Outcome<T>> = flow {
    emit(Outcome.Loading)
    emit(outcomeOf(origin, block))
}

/**
 * A cached read ([cache], a Room flow) and the [refresh] that rewrites it, as one stream: [Outcome.Loading], then each cached value as
 * [Outcome.Data], from [Origin.Cache] until the refresh succeeds and [Origin.Network] after, and an [Outcome.Error] if it fails.
 *
 * An [isEmpty] value is a cache miss, not an answer, until the refresh succeeds: it's held back while the refresh runs and after it fails,
 * so a first visit shows loading and then the failure, never an empty list. A failure stays the last word: a cached value read after it is
 * followed by the failure again, so a cache that's slow to read can't settle a feed whose refresh already failed.
 */
fun <T> cachedOutcomes(cache: Flow<T>, isEmpty: (T) -> Boolean, refresh: suspend () -> Unit): Flow<Outcome<T>> = flow {
    emit(Outcome.Loading)
    var refreshed: Outcome<Unit>? = null
    merge<CacheEvent<T>>(
            cache.map { Cached(it) },
            flow {
                // Read again once it's written: Room's own re-read may come later, and none comes for a write that changed no rows.
                val outcome = outcomeOf { refresh() }
                emit(Refreshed(outcome, if (outcome is Outcome.Error) null else Latest(cache.first())))
            },
        )
        .collect { event ->
            when (event) {
                is Cached -> {
                    when (val answer = refreshed) {
                        null -> if (!isEmpty(event.value)) emit(Outcome.Data(event.value, Origin.Cache))
                        is Outcome.Error ->
                            if (!isEmpty(event.value)) {
                                emit(Outcome.Data(event.value, Origin.Cache))
                                emit(answer)
                            }
                        else -> emit(Outcome.Data(event.value, Origin.Network))
                    }
                }
                is Refreshed -> {
                    refreshed = event.outcome
                    when (val answer = event.outcome) {
                        is Outcome.Error -> emit(answer)
                        else -> event.fresh?.let { emit(Outcome.Data(it.value, Origin.Network)) }
                    }
                }
            }
        }
}

private sealed interface CacheEvent<out T>

private class Cached<T>(val value: T) : CacheEvent<T>

private class Refreshed<T>(val outcome: Outcome<Unit>, val fresh: Latest<T>?) : CacheEvent<T>

/** A value read, which may itself be null. */
private class Latest<T>(val value: T)

/**
 * What [this] failure means to a reader. A non-XRPC error body ([XrpcException.error] `HttpError`) says only its status. Ktor's timeouts
 * are [IOException]s on every engine, so they count as [DataError.Network] too. A token refresh the authorization server failed without
 * ending the session ([OAuthServerException]) says only its status, like a non-XRPC body.
 */
fun Throwable.toDataError(): DataError =
    when {
        this is XrpcException -> if (error == HTTP_ERROR) DataError.Http(status) else DataError.Api(listOf(message ?: error), code = error)
        this is OAuthServerException -> DataError.Http(status)
        this is IOException || isPlatformNetworkFailure() -> DataError.Network
        this is SerializationException -> DataError.Serialization
        else -> DataError.Unknown(this, message)
    }

/**
 * Whether [this] is a transport failure the platform's HTTP engine doesn't report as an [IOException]: on the web, a `fetch` that never got
 * a response.
 */
internal expect fun Throwable.isPlatformNetworkFailure(): Boolean

/** [XrpcException.error] for a failed response whose body isn't an XRPC error. */
private const val HTTP_ERROR = "HttpError"
