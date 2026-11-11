package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Immutable

/**
 * A list's notice over its rows (ui/error-messages), decided once for the shared and the native UIs: why its last action failed, or, when
 * nothing failed and the session can't do everything the list offers, that it must sign in again. [offersSignInAgain] says whether Sign in
 * again goes with it.
 */
@Immutable
data class SignInAgainNotice(val error: UiError, val offersSignInAgain: Boolean) {
    companion object {
        /** The notice for a list whose last action failed with [error], in a session that [needsSignIn] again; null for neither. */
        fun of(error: UiError?, needsSignIn: Boolean): SignInAgainNotice? {
            if (error == null && !needsSignIn) return null
            val shown = error ?: UiError(UiError.Kind.SignInAgain)
            return SignInAgainNotice(shown, offersSignInAgain = needsSignIn || shown.kind == UiError.Kind.SignInAgain)
        }
    }
}
