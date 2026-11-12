import SwiftUI
import Testing

@testable import ActivityTracker

/// The activity page in each of its sample states against their references (feed/activity-page-layout, dev/ios-snapshot-tests), on
/// the samples' clock: the shared samples at a phone's size, the states beyond loading tall enough for every card, and the owner's
/// ride at an iPad's width, its pane beside the cards. Snapshots run outside a session, so the map draws the line preview and loads no
/// tiles (`\.liveMaps`).
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct ActivityPageSnapshotTests {
    nonisolated private static let samples = ["loading", "failed", "loaded", "refreshing", "refreshFailed"]
    nonisolated private static let variants = [
        "ownRide", "run", "missingZones", "likesShown", "combineSuggestion", "hidden", "segments",
    ]
    /// Tall enough for most of a page with cards; taller renders blank (past the snapshot renderer's limit).
    private static let tall = CGSize(width: 393, height: 2_600)
    /// An 11-inch iPad on its side: wide enough for the pane beside the cards.
    private static let iPad = CGSize(width: 1_180, height: 1_600)

    private func page(_ state: ActivityDetailState) -> some View {
        NavigationStack { ActivityPage(state: state) }.environment(\.clock, Samples.clock)
    }

    @Test(arguments: samples)
    func activity(sample: String) throws {
        let state = try #require(Samples.activityDetail[sample])
        assertAppSnapshot(of: page(state), size: CGSize(width: 393, height: 1_400), named: sample, precision: Snapshots.barButtons)
    }

    @Test(arguments: variants)
    func activityVariant(sample: String) throws {
        let state = try #require(Samples.activityDetailVariants[sample])
        assertAppSnapshot(of: page(state), size: Self.tall, named: sample, precision: Snapshots.barButtons)
    }

    @Test func ownRideOnAnIPad() throws {
        let state = try #require(Samples.activityDetailVariants["ownRide"])
        assertAppSnapshot(
            of: page(state).environment(\.horizontalSizeClass, .regular), size: Self.iPad, named: "iPad", precision: Snapshots.barButtons)
    }

    /// The cards the tall images cut off: elevation with grade titled under it, and the laps table.
    @Test func lapsAndElevationCards() {
        let cards = List {
            MetricCardSection(card: Samples.rideElevation, charts: Samples.rideCharts, missingZones: false, setThresholds: {})
            MetricCardSection(card: Samples.rideLaps, charts: Samples.rideCharts, missingZones: false, setThresholds: {})
        }
        .listStyle(.insetGrouped)
        assertAppSnapshot(of: cards, size: CGSize(width: 393, height: 900), named: "cards")
    }

    @Test func activityCharts() {
        assertAppSnapshot(of: DesignGallery.activityCharts, size: CGSize(width: 393, height: 520), named: "activityCharts")
    }
}

/// How a figure's value splits for its unit to read smaller (feed/activity-page-layout "Readable figures").
struct FigurePartsTests {
    @Test func aNumberAndItsUnitSplit() {
        #expect(FigureParts.split("7.83 mi") == [.init(text: "7.83", isNumber: true), .init(text: " mi", isNumber: false)])
    }

    @Test func aTimeIsOneNumber() {
        #expect(FigureParts.split("1:02:03").count == 1)
    }

    @Test func textWithoutANumberIsOnePart() {
        #expect(FigureParts.split("Edge 840").first?.isNumber == false)
        #expect(FigureParts.split("Road bike").count == 1)
    }

    @Test func theOwnersMenuHasAnIconForEveryAction() {
        for action in [ActivityMenuAction.Edit, .Split, .Export, .Delete] {
            _ = ActivityPageMenu.icon(action)
        }
        #expect(ActivityPageMenu.icon(.Delete) == .delete)
    }
}
