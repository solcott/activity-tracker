package io.github.solcott.activitytracker.app.login

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.ErrorTexts
import io.github.solcott.activitytracker.data.auth.AuthCoordinator.Status

/** Sign-in's text that follows a rule, chosen here for the shared and the native UI (dev/shared-display-text). */
object LoginTexts {
    /** The step of signing in that failed and, when the reason is one worth saying, why (ui/error-messages). */
    fun failure(status: Status.Failed): AppText =
        ErrorTexts.failure(
            AppText.named(
                when (status.stage) {
                    Status.Stage.Start -> "error_sign_in_start"
                    Status.Stage.Complete -> "error_sign_in_complete"
                    Status.Stage.FindAccount -> "error_sign_in_find_account"
                },
            ),
            status.reason,
        )

    /** The message after leaving Activity Tracker, naming the account that is unchanged (auth/leaving). */
    fun left(handle: String): AppText = AppText.named("login_left", AppText.written(handle))
}
