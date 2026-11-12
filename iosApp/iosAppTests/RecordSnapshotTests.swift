import SwiftUI
import Testing

@testable import ActivityTracker

/// Record in each of its sample states, with location refused, and a run in miles, against their references (record/gps-recording,
/// dev/ios-snapshot-tests). Each is a phone's screen: the controls bar sits at its bottom over the form, as it does mid-ride; the full
/// form is also recorded tall enough for all of it.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct RecordSnapshotTests {
    nonisolated private static let samples = ["loaded", "recording", "paused", "recovered", "saveFailed"]
    nonisolated private static let variants = ["noPermission", "full", "saving", "finishFailed", "run"]

    private static let tall = CGSize(width: 393, height: 1_500)

    private func screen(_ state: RecordState, locationRefused: Bool = false) -> some View {
        NavigationStack { RecordView(state: state, locationRefused: locationRefused) }
    }

    @Test(arguments: samples + variants)
    func record(sample: String) throws {
        let state = try #require(Samples.record[sample] ?? Samples.recordVariants[sample])
        assertAppSnapshot(of: screen(state), named: sample, precision: Snapshots.barButtons)
    }

    @Test func locationRefused() throws {
        let state = try #require(Samples.recordVariants["noPermission"])
        assertAppSnapshot(of: screen(state, locationRefused: true), named: "locationRefused", precision: Snapshots.barButtons)
    }

    @Test func runInMiles() throws {
        let state = try #require(Samples.recordVariants["run"])
        assertAppSnapshot(
            of: screen(state).environment(\.stats, Stats(units: .Imperial)), named: "imperial", precision: Snapshots.barButtons)
    }

    @Test func wholeForm() throws {
        let state = try #require(Samples.recordVariants["full"])
        assertAppSnapshot(of: screen(state), size: Self.tall, named: "fullTall", precision: Snapshots.barButtons)
    }

    @Test func theSuiteCoversEverySample() {
        #expect(Set(Samples.record.keys) == Set(Self.samples))
        #expect(Set(Samples.recordVariants.keys) == Set(Self.variants))
    }
}
