import SwiftUI
import Testing

@testable import ActivityTracker

/// The Account and Leave pages in each of their sample states, alone and beside Settings' list as a wide window lays them out, against
/// their references (ui/settings-layout, dev/ios-snapshot-tests). Alerts are presented over the page, not in it, so they aren't here;
/// their words are in `AccountTextTests`.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct AccountSnapshotTests {
    /// A 13-inch iPad's width, upright: wide enough for the list beside its page.
    private let wide = CGSize(width: 1_032, height: 1_000)
    /// A phone's width and the whole of each page.
    private let wholePage = CGSize(width: Snapshots.screen.width, height: 1_700)

    nonisolated private static let accountSamples = [
        "loaded", "outdated", "noSpaceScope", "spacesUnsupported", "pendingUploads", "pendingImport", "exportRunning", "exportGathering",
        "exportReady", "exportReadyWithProblems", "exportPaused", "exportFailed", "exportSharedAsk", "pendingExport", "signInAgainPending",
    ]
    nonisolated private static let accountVariants = ["ending", "exportSaveFailed"]
    nonisolated private static let leaveSamples = [
        "loaded", "confirmed", "wrongHandle", "placesLoading", "placesFailed", "noPlaces", "pendingWork", "exportRunning", "exportReady",
        "signInAgain", "unreachable", "starting", "resuming", "deletingSpaces", "deletingRecords", "rateLimited", "stopped",
    ]

    @Test(arguments: accountSamples + accountVariants)
    func account(sample: String) throws {
        let state = try #require(Samples.account[sample] ?? Samples.accountVariants[sample])
        assertAppSnapshot(of: NavigationStack { AccountView(state: state) }, size: wholePage, named: sample)
    }

    @Test(arguments: leaveSamples)
    func leave(sample: String) throws {
        let state = try #require(Samples.leave[sample])
        assertAppSnapshot(of: NavigationStack { LeaveView(state: state) }, size: wholePage, named: sample)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.account.keys) == Set(Self.accountSamples))
        #expect(Set(Samples.accountVariants.keys) == Set(Self.accountVariants))
        #expect(Set(Samples.leave.keys) == Set(Self.leaveSamples))
    }

    @Test func accountBesideTheList() throws {
        let page = AccountView(state: try #require(Samples.account["exportRunning"]))
        assertAppSnapshot(of: beside(page, paneRoot: true), size: wide, named: "exportRunning")
    }

    /// Leave opened from Account: deeper than the column's first page, so its header has a back button.
    @Test func leaveBesideTheList() throws {
        let page = LeaveView(state: try #require(Samples.leave["confirmed"]))
        assertAppSnapshot(of: beside(page, paneRoot: false), size: wide, named: "confirmed")
    }

    /// A running leave beside the list: no back button.
    @Test func runningLeaveBesideTheList() throws {
        let page = LeaveView(state: try #require(Samples.leave["deletingRecords"]))
        assertAppSnapshot(of: beside(page, paneRoot: false), size: wide, named: "deletingRecords")
    }

    /// The list with Account marked, and `page` in the column beside it, as the shell draws them.
    private func beside(_ page: some View, paneRoot: Bool) -> some View {
        NavigationStack {
            SettingsColumns(
                list: {
                    SettingsView(state: Samples.settings["loaded"]!).environment(\.settingsLayout, .beside(SettingsRows.account))
                },
                page: page.environment(\.inPane, true).environment(\.paneRoot, paneRoot))
        }
    }
}
