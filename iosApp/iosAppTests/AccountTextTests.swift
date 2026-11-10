import Foundation
import Testing

@testable import ActivityTracker

/// What the Account and Leave pages say and offer (ui/settings-layout, dev/shared-display-text, dev/ios-swift-interop). The rules are
/// Kotlin's, with their own tests; these hold that Swift reads each on the shared samples, and the catalog words each as the shared
/// pages do.
@MainActor
struct AccountTextTests {
    private func read(_ text: any AppText) -> String { AppStrings.string(text) }
    private func read(_ texts: [any AppText]) -> [String] { texts.map(read) }

    private func account(_ name: String) throws -> AccountState {
        try #require(Samples.account[name] ?? Samples.accountVariants[name])
    }

    private func leave(_ name: String) throws -> LeaveState { try #require(Samples.leave[name]) }

    @Test func everySampleReads() {
        for state in Array(Samples.account.values) + Array(Samples.accountVariants.values) {
            _ = read(AccountTexts.shared.signIn(grant: state.grant))
            _ = read(AccountTexts.shared.missingFeatures(grant: state.grant))
            _ = read(AccountTexts.shared.exportLines(export: state.export))
        }
        for state in Samples.leave.values {
            _ = read(LeaveTexts.shared.places(places: state.places))
            _ = read(LeaveTexts.shared.deviceLines(state: state))
            if let run = state.run { _ = read(LeaveTexts.shared.step(run: run)) }
        }
    }

    @Test func anOutdatedSignInListsWhatItIsMissingInOrder() throws {
        let state = try account("outdated")
        #expect(state.offersSignInAgain)
        #expect(
            read(AccountTexts.shared.signIn(grant: state.grant))
                == "You signed in before Activity Tracker asked for everything it now needs. Sign in again to use:")
        #expect(
            read(AccountTexts.shared.missingFeatures(grant: state.grant))
                == ["Likes and comments on private activities", "Segments, leaderboards and grouping"])
        #expect(try !account("loaded").offersSignInAgain)
        #expect(read(AccountTexts.shared.missingFeatures(grant: try account("loaded").grant)).isEmpty)
    }

    @Test func signingOutWithChangesPending() throws {
        let ending = try #require(try account("pendingUploads").confirming)
        #expect(read(AccountTexts.shared.endTitle(ending: ending)) == "Sign out?")
        #expect(
            read(AccountTexts.shared.endLines(ending: ending))
                == ["3 changes haven't uploaded yet. They stay on this device and upload when you sign in here again."])
        #expect(read(AccountTexts.shared.endAction(ending: ending)) == "Sign out anyway")
    }

    @Test func signingInAgainAsksItsOwnQuestion() throws {
        let ending = try #require(try account("signInAgainPending").confirming)
        #expect(read(AccountTexts.shared.endTitle(ending: ending)) == "Sign in again?")
        #expect(read(AccountTexts.shared.endAction(ending: ending)) == "Sign in again anyway")
    }

    @Test func theExportsLines() throws {
        let running = try #require(try account("exportRunning").export as? DataExportStatus.Running)
        #expect(read(AccountTexts.shared.running(export: running)) == "Preparing activities: 120 of 400")
        let paused = try account("exportPaused").export
        #expect(
            read(AccountTexts.shared.exportLines(export: paused))
                == ["The export is paused: there isn't enough free space on this device. Free up 1,200 MB and try again."])
        let problems = try account("exportReadyWithProblems").export
        #expect(read(AccountTexts.shared.exportLines(export: problems)).first == "Your export is ready.")
        #expect(read(AccountTexts.shared.exportLines(export: problems)).count == 4)
        #expect(try account("exportReady").export is DataExportStatus.Ready)
    }

    @Test func leavingNeedsTheHandleTyped() throws {
        let state = try leave("loaded")
        #expect(state.canLeave(typed: "@Alice.test"))
        #expect(!state.canLeave(typed: "alice"))
        #expect(try leave("confirmed").canLeave)
        #expect(try !leave("exportRunning").canLeave(typed: "alice.test"))
    }

    @Test func sharedPlacesAndTheDevice() throws {
        #expect(read(LeaveTexts.shared.places(places: try leave("noPlaces").places)) == ["You haven't created any segments or dive sites."])
        let busy = try leave("pendingWork")
        #expect(busy.showsDeviceWork)
        #expect(read(LeaveTexts.shared.deviceLines(state: busy)).count == 3)
        #expect(try !leave("loaded").showsDeviceWork)
    }

    @Test func aRunningLeave() throws {
        let spaces = try #require(try leave("deletingSpaces").run)
        #expect(read(LeaveTexts.shared.step(run: spaces)) == "Deleting your private data…")
        #expect(try !leave("deletingSpaces").canGoBack)
        let records = try #require(try leave("deletingRecords").run)
        #expect(LeaveTexts.shared.recordsDeleted(run: records).map(read) == "1,200 records deleted")
        let resuming = try #require(try leave("resuming").run)
        #expect(read(LeaveTexts.shared.step(run: resuming)) == "Leaving Activity Tracker…")
        #expect(try leave("loaded").canGoBack)
    }

    @Test func aPausedLeaveSaysWhenOnTheDevicesClock() throws {
        let run = try #require(try leave("rateLimited").run)
        let until = try #require(run.pausedUntil)
        let time = Date(timeIntervalSince1970: TimeInterval(until.epochSeconds)).formatted(date: .omitted, time: .shortened)
        #expect(
            LeaveTexts.shared.paused(run: run).map(read) == "Your server asked to slow down. Carrying on at \(time).")
    }
}
