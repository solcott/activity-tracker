import Testing

@testable import ActivityTracker

/// What sign-in says when it fails (auth/sign-in, ui/error-messages): the step that failed, and why only when that helps.
@MainActor
struct LoginTextTests {
    @Test func eachStageSaysWhatFailed() {
        #expect(LoginText.failure(stage: .Start, reason: nil) == "Couldn't start sign-in")
        #expect(LoginText.failure(stage: .Complete, reason: nil) == "Sign-in failed")
        #expect(LoginText.failure(stage: .FindAccount, reason: nil) == "No account found for that handle")
    }

    @Test func aReasonWorthSayingFollowsWhatFailed() {
        #expect(LoginText.failure(stage: .Start, reason: .Unreachable) == "Couldn't start sign-in. Couldn't reach the server.")
        #expect(LoginText.failure(stage: .Complete, reason: .Server) == "Sign-in failed. The server had a problem.")
        #expect(
            LoginText.failure(stage: .FindAccount, reason: .Unreadable)
                == "No account found for that handle. The app couldn't read the server's reply.")
    }

    @Test func theLeftMessageNamesTheAccount() {
        #expect(
            LoginText.left(handle: "alice.test")
                == "You've left Activity Tracker. Your atproto account alice.test and its other apps are unchanged.")
    }

    @Test func aBlankHandleCannotBeSubmitted() {
        #expect(!LoginText.canSubmit(""))
        #expect(!LoginText.canSubmit("   "))
        #expect(LoginText.canSubmit("alice.test"))
    }
}
