package io.github.solcott.activitytracker.data.auth

import androidx.compose.runtime.Immutable
import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.identity.HandleNotFoundException
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.toDataError
import io.github.solcott.dataresult.DataError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Opens the platform's browser for OAuth. The redirect comes back via [AuthCoordinator.handleRedirect]. */
fun interface AuthBrowser {
    fun open(url: String)
}

/**
 * Drives OAuth sign-in across the browser round-trip. Platform entry points (Android deep link, iOS ASWebAuthenticationSession callback,
 * web page load with `?code=`) call [handleRedirect].
 */
@SingleIn(AppScope::class)
@Inject
class AuthCoordinator(
    private val auth: AuthRepository,
    private val browser: AuthBrowser,
    logger: Logger,
) {
    @Immutable
    sealed interface Status {
        data object Idle : Status

        data object Starting : Status

        data object WaitingForBrowser : Status

        data object Completing : Status

        /** Sign-in failed at [stage], and why when the failure was classified ([reason]); the UI never shows the failure's own text. */
        data class Failed(val stage: Stage, val reason: DataError? = null) : Status

        enum class Stage {
            /** Resolving the handle and opening the browser. */
            Start,

            /** Exchanging the redirect for tokens. */
            Complete,

            /** No account was found for the handle: a lookup answered that it points at no DID. */
            FindAccount,
        }
    }

    private val log = logger.withTag("AuthCoordinator")

    val status: StateFlow<Status>
        field = MutableStateFlow<Status>(Status.Idle)

    suspend fun signIn(handle: String) {
        status.value = Status.Starting
        runSuspendCatching {
            browser.open(auth.beginSignIn(handle))
            status.value = Status.WaitingForBrowser
        }
            .onFailure {
                log.w(it) { "Could not start sign-in" }
                status.value =
                    if (it is HandleNotFoundException) Status.Failed(Status.Stage.FindAccount)
                    else Status.Failed(Status.Stage.Start, it.toDataError())
            }
    }

    suspend fun handleRedirect(params: Map<String, String>) {
        status.value = Status.Completing
        runSuspendCatching {
            auth.completeSignIn(params)
            status.value = Status.Idle
        }
            .onFailure {
                log.w(it) { "Sign-in failed" }
                status.value = Status.Failed(Status.Stage.Complete, it.toDataError())
            }
    }

    fun cancelled() {
        status.value = Status.Idle
    }
}

/** Parses the query string of an OAuth redirect URI. */
fun parseRedirectParams(url: String): Map<String, String> =
    url.substringAfter('?', "")
        .substringBefore('#')
        .split('&')
        .filter { '=' in it }
        .associate {
            val (k, v) = it.split('=', limit = 2)
            k.decodeUrlComponent() to v.decodeUrlComponent()
        }

private const val HEX_RADIX = 16
private const val PERCENT_HEX_DIGITS = 2

private fun String.decodeUrlComponent(): String {
    val bytes = ArrayList<Byte>(length)
    var i = 0
    while (i < length) {
        when (val c = this[i]) {
            '%' -> {
                // A stray or cut-off escape (`?x=%`, `%zz`) is kept as written rather than failing the whole redirect.
                val escape = substring(i + 1, minOf(length, i + 1 + PERCENT_HEX_DIGITS))
                val byte =
                    escape
                        .takeIf { it.length == PERCENT_HEX_DIGITS && it.all { c -> c.digitToIntOrNull(HEX_RADIX) != null } }
                        ?.toInt(HEX_RADIX)
                if (byte == null) {
                    bytes += '%'.code.toByte()
                } else {
                    bytes += byte.toByte()
                    i += PERCENT_HEX_DIGITS
                }
            }

            '+' -> {
                bytes += ' '.code.toByte()
            }

            else -> {
                c.toString().encodeToByteArray().forEach { bytes += it }
            }
        }
        i++
    }
    return bytes.toByteArray().decodeToString()
}
