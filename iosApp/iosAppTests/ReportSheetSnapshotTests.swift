import SwiftUI
import Testing

@testable import ActivityTracker

/// The native report and place-name sheets in each sample state, against their references (moderation/reports,
/// segments/segment-names, dev/ios-snapshot-tests): every step of the report (no reason, chosen, Other, sending, the failure, sent with
/// and without Block) and of the name sheet. Each is drawn on its own at a phone's size, since a sheet doesn't render in a snapshot of
/// the page that presents it.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct ReportSheetSnapshotTests {
    nonisolated private static let reportSamples = ["loaded", "chosen", "other", "sending", "failed", "sent", "sentNoBlock"]
    nonisolated private static let nameSamples = ["loaded", "edit", "invalid", "sharing", "sharingPublicOnly", "sending", "failed"]

    @Test(arguments: reportSamples)
    func reportSheet(sample: String) throws {
        let state = try #require(Samples.report[sample])
        assertAppSnapshot(of: ReportSheet(state: state), named: sample, precision: Snapshots.barButtons)
    }

    @Test(arguments: nameSamples)
    func placeNameSheet(sample: String) throws {
        let state = try #require(Samples.proposePlaceName[sample])
        assertAppSnapshot(of: ProposePlaceNameSheet(state: state), named: sample, precision: Snapshots.barButtons)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.report.keys) == Set(Self.reportSamples))
        #expect(Set(Samples.proposePlaceName.keys) == Set(Self.nameSamples))
    }
}
