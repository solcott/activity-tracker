import SwiftUI
import Testing

@testable import ActivityTracker

/// All data against its references (feed/activity-page-layout, dev/ios-snapshot-tests): the page in each loading state, opened at the
/// laps from their Show all, and as a wide activity page's side pane, under its title and close.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct ActivityDataSnapshotTests {
    nonisolated private static let samples = ["loading", "failed", "loaded", "refreshing", "refreshFailed"]
    private static let tall = CGSize(width: 393, height: 2_200)

    private func page(_ state: ActivityDataState) -> some View {
        NavigationStack { ActivityDataView(state: state) }.environment(\.clock, Samples.clock)
    }

    @Test(arguments: samples)
    func allData(sample: String) throws {
        let state = try #require(Samples.activityData[sample])
        assertAppSnapshot(of: page(state), size: Self.tall, named: sample, precision: Snapshots.barButtons)
    }

    @Test func openedAtTheLaps() {
        assertAppSnapshot(of: page(Samples.activityDataAtLaps), named: "atLaps", precision: Snapshots.barButtons)
    }

    @Test func inThePane() throws {
        let data = try #require(Samples.activityDataAtLaps.content.data as? ActivityData)
        let pane = ActivityDataList(data: data, openGear: { _ in }) {
            EmptyView()
        } header: {
            PageBar(
                title: Text(ActivityDataTexts.shared.title),
                actions: AnyView(AppIconButton(.close, label: ActivityDataTexts.shared.close) {}))
        }
        .environment(\.clock, Samples.clock)
        assertAppSnapshot(of: pane, size: CGSize(width: 400, height: 1_000), named: "pane")
    }
}
