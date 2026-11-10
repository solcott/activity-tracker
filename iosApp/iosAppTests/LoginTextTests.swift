import Testing

@testable import ActivityTracker

/// What sign-in says (auth/sign-in, ui/error-messages). The wording is chosen in Kotlin (`LoginTexts`, with its own tests); these hold
/// that the catalog reads each choice as the shared UI does.
@MainActor
struct LoginTextTests {
    private func failure(_ stage: AuthStatus.Stage, _ reason: DataError? = nil) -> String {
        AppStrings.string(LoginTexts.shared.failure(status: AuthStatus.Failed(stage: stage, reason: reason)))
    }

    @Test func eachStageSaysWhatFailed() {
        #expect(failure(.Start) == "Couldn't start sign-in")
        #expect(failure(.Complete) == "Sign-in failed")
        #expect(failure(.FindAccount) == "No account found for that handle")
    }

    @Test func aReasonWorthSayingFollowsWhatFailed() {
        #expect(failure(.Start, DataError.Network.shared) == "Couldn't start sign-in. Couldn't reach the server.")
        #expect(failure(.Complete, DataError.Serialization.shared) == "Sign-in failed. The app couldn't read the server's reply.")
    }

    @Test func theLeftMessageNamesTheAccount() {
        #expect(
            AppStrings.string(LoginTexts.shared.left(handle: "alice.test"))
                == "You've left Activity Tracker. Your atproto account alice.test and its other apps are unchanged.")
    }

    @Test func aBlankHandleCannotBeSubmitted() {
        #expect(!LoginText.canSubmit(""))
        #expect(!LoginText.canSubmit("   "))
        #expect(LoginText.canSubmit("alice.test"))
    }
}
