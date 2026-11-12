import SwiftUI
import Testing

@testable import ActivityTracker

/// Threshold history in each of its sample states, against their references (ui/settings-layout, dev/ios-snapshot-tests): the shared
/// samples, then charts expanded, every group collapsed to its tiles, a pace in minutes per mile, a removal running and one that failed;
/// and the page beside Settings' list as an iPad lays it out, the column's second page with its back button.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct ThresholdHistorySnapshotTests {
    nonisolated private static let samples = ["loading", "failed", "loaded", "refreshing", "refreshFailed"]
    nonisolated private static let variants = ["allRows", "charted", "collapsed", "pace", "removing", "removeFailed"]

    private static let size = CGSize(width: 393, height: 1_700)

    @Test(arguments: samples + variants)
    func thresholdHistory(sample: String) throws {
        let state = try #require(Samples.thresholdHistory[sample] ?? Samples.thresholdHistoryVariants[sample])
        assertAppSnapshot(
            of: NavigationStack { ThresholdHistoryView(state: state) }, size: Self.size, named: sample, precision: Snapshots.barButtons)
    }

    /// Beside the list, as Training zones' second page: a back button and Collapse all in its header.
    @Test func besideTheList() throws {
        let state = try #require(Samples.thresholdHistoryVariants["charted"])
        let page = ThresholdHistoryView(state: state).environment(\.inPane, true).environment(\.paneRoot, false)
        assertAppSnapshot(
            of: NavigationStack {
                SettingsColumns(
                    list: {
                        SettingsView(state: Samples.settings["loaded"]!).environment(\.settingsLayout, .beside(SettingsRows.trainingZones))
                    },
                    page: page)
            },
            size: CGSize(width: 1_024, height: 1_366), named: "beside", precision: Snapshots.barButtons)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.thresholdHistory.keys) == Set(Self.samples))
        #expect(Set(Samples.thresholdHistoryVariants.keys) == Set(Self.variants))
    }
}
