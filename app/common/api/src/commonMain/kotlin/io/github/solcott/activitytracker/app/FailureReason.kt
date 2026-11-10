package io.github.solcott.activitytracker.app

import io.github.solcott.dataresult.DataError

/**
 * Why something failed, for the failures an athlete can act on or wait out (ui/error-messages). The one place that rule is written: every
 * UI, Compose or native, turns the reason into its own text for it, and says only what failed for a failure that has none.
 */
enum class FailureReason {
    /** The server couldn't be reached. */
    Unreachable,

    /** The server answered that it had a problem. */
    Server,

    /** The server's reply couldn't be read. */
    Unreadable;

    companion object {
        /** The reason [error] gives, or null when it gives none worth saying. */
        fun of(error: DataError): FailureReason? =
            when (error) {
                DataError.Network -> Unreachable
                is DataError.Http if error.code >= SERVER_ERROR -> Server
                is DataError.Api if error.code == INTERNAL_SERVER_ERROR -> Server
                DataError.Serialization -> Unreadable
                else -> null
            }

        /** The lowest HTTP status that means the server failed. */
        private const val SERVER_ERROR = 500

        /** The XRPC error name for a failure on the server. */
        private const val INTERNAL_SERVER_ERROR = "InternalServerError"
    }
}
