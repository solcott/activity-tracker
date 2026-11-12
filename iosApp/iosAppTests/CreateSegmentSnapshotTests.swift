import SwiftUI
import Testing

@testable import ActivityTracker

/// Create segment in each of its sample states, and the range slider it chooses a stretch with, against their references
/// (segments/segment-creation, ui/ios-design-system, dev/ios-snapshot-tests). The form is recorded tall enough for all of it; outside a
/// session its map is the line preview, with the chosen stretch highlighted.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct CreateSegmentSnapshotTests {
    nonisolated private static let samples = ["loading", "failed", "loaded", "invalid"]
    nonisolated private static let variants = [
        "unnamed", "tooLong", "crossesGap", "namesActivity", "duplicate", "limitReached", "publishing", "checkFailed", "noRoute",
        "noSportGroup", "allHidden",
    ]

    private static let size = CGSize(width: 393, height: 1_000)

    @Test(arguments: samples + variants)
    func createSegment(sample: String) throws {
        let state = try #require(Samples.createSegment[sample] ?? Samples.createSegmentVariants[sample])
        assertAppSnapshot(
            of: NavigationStack { CreateSegmentView(state: state) }, size: Self.size, named: sample, precision: Snapshots.barButtons)
    }

    @Test func rangeSlider() {
        let slider = { (lower: Double, upper: Double) in
            RangeSlider(
                lower: lower, upper: upper, bounds: 0...2_400, lowerLabel: Text(verbatim: "Start"), upperLabel: Text(verbatim: "End"),
                value: { Text(verbatim: "\(Int($0)) m") }, onChange: { _, _ in })
        }
        let view = Form {
            slider(480, 1_440)
            slider(0, 2_400)
            slider(1_200, 1_200)
            slider(480, 1_440).disabled(true)
        }
        assertAppSnapshot(of: view, size: CGSize(width: 393, height: 400), named: "states")
    }

    @Test func theSuiteCoversEverySample() {
        #expect(Set(Samples.createSegment.keys) == Set(Self.samples))
        #expect(Set(Samples.createSegmentVariants.keys) == Set(Self.variants))
    }
}
