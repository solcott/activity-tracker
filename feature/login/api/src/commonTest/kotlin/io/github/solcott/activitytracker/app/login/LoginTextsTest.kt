package io.github.solcott.activitytracker.app.login

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.auth.AuthCoordinator.Status
import io.github.solcott.dataresult.DataError
import kotlin.test.Test
import kotlin.test.assertEquals

/** What sign-in says when it fails (auth/sign-in, ui/error-messages): the step that failed, and why only when that helps. */
class LoginTextsTest {
    @Test
    fun eachStageSaysWhatFailed() {
        assertEquals(AppText.named("error_sign_in_start"), LoginTexts.failure(Status.Failed(Status.Stage.Start)))
        assertEquals(AppText.named("error_sign_in_complete"), LoginTexts.failure(Status.Failed(Status.Stage.Complete)))
        assertEquals(AppText.named("error_sign_in_find_account"), LoginTexts.failure(Status.Failed(Status.Stage.FindAccount)))
    }

    @Test
    fun aReasonWorthSayingFollowsWhatFailed() =
        assertEquals(
            AppText.named("error_with_reason", AppText.named("error_sign_in_start"), AppText.named("error_reason_unreachable")),
            LoginTexts.failure(Status.Failed(Status.Stage.Start, DataError.Network)),
        )

    @Test
    fun aReasonThatDoesNotHelpIsLeftOut() =
        assertEquals(
            AppText.named("error_sign_in_complete"),
            LoginTexts.failure(Status.Failed(Status.Stage.Complete, DataError.Http(404))),
        )

    @Test
    fun theLeftMessageNamesTheAccount() =
        assertEquals(AppText.named("login_left", AppText.written("alice.test")), LoginTexts.left("alice.test"))
}
