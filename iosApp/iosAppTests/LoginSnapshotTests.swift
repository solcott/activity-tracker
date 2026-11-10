import SwiftUI
import Testing

@testable import ActivityTracker

/// Sign-in in each of its sample states, against its references (dev/ios-snapshot-tests).
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct LoginSnapshotTests {
    @Test(arguments: ["loaded", "starting", "failed", "expired", "expiredRecording", "signInAgain", "left"])
    func signIn(sample: String) throws {
        let state = try #require(Samples.login[sample])
        assertAppSnapshot(of: LoginView(state: state), named: sample)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.login.keys) == ["loaded", "starting", "failed", "expired", "expiredRecording", "signInAgain", "left"])
    }
}
