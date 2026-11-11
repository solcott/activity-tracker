package io.github.solcott.activitytracker.app

import io.github.solcott.dataresult.DataError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What a list's notice says, and whether it offers Sign in again (ui/error-messages, dev/shared-display-text). */
class SignInAgainNoticeTest {
    private val failed = UiError(UiError.Kind.AnswerFollowRequest, DataError.Network)

    @Test fun neitherShowsNothing() = assertNull(SignInAgainNotice.of(null, needsSignIn = false))

    @Test
    fun aSessionThatMustSignInAgainSaysSo() =
        assertEquals(SignInAgainNotice(UiError(UiError.Kind.SignInAgain), true), SignInAgainNotice.of(null, needsSignIn = true))

    @Test
    fun aFailedActionShowsAloneWhenTheSessionCanDoIt() = assertEquals(SignInAgainNotice(failed, false), SignInAgainNotice.of(failed, false))

    @Test
    fun aFailedActionStillOffersSignInAgainWhenTheSessionNeedsIt() =
        assertEquals(SignInAgainNotice(failed, true), SignInAgainNotice.of(failed, needsSignIn = true))

    @Test
    fun aFailureThatIsSignInAgainOffersIt() {
        val signIn = UiError(UiError.Kind.SignInAgain)
        assertEquals(SignInAgainNotice(signIn, true), SignInAgainNotice.of(signIn, needsSignIn = false))
    }
}
