import SwiftUI
import Testing

@testable import ActivityTracker

/// The gear item page in each of its sample states, and its mount sheet and unmount calendar, against their references (gear/gear-usage,
/// gear/gear-components, dev/ios-snapshot-tests), on the samples' clock. The samples hold no picture URLs, so pictures are their
/// placeholders. The sheets present over the page, so the page's samples show the page under them, and the sheets are recorded on their
/// own; their words are `GearItemTextTests`'.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct GearItemSnapshotTests {
    nonisolated private static let samples = ["loading", "failed", "loaded", "refreshing", "refreshFailed", "empty", "linked"]
    nonisolated private static let variants = [
        "linked", "loadingMore", "follower", "components", "componentsFollower", "chain", "replacing", "mountingNone", "unmounting",
        "mountFailed", "mountSaving", "notFound",
    ]
    /// Tall enough for the owner's whole page down to its cards.
    private static let tall = CGSize(width: 393, height: 2_000)

    private func page(_ state: GearState) -> some View {
        NavigationStack { GearItemView(state: state) }.environment(\.clock, Samples.clock)
    }

    @Test(arguments: samples)
    func gear(sample: String) throws {
        let state = try #require(Samples.gear[sample])
        assertAppSnapshot(of: page(state), size: Self.tall, named: sample, precision: Snapshots.barButtons)
    }

    @Test(arguments: variants.filter { $0 != "linked" })
    func gearVariant(sample: String) throws {
        let state = try #require(Samples.gearVariants[sample])
        assertAppSnapshot(of: page(state), size: Self.tall, named: sample, precision: Snapshots.barButtons)
    }

    @Test(arguments: ["replacing", "mountingNone"])
    func mountSheet(sample: String) throws {
        let picker = try #require(Samples.gearVariants[sample]?.picker)
        assertAppSnapshot(of: MountSheet(picker: picker, send: { _ in }), size: CGSize(width: 393, height: 420), named: sample)
    }

    @Test func unmountSheet() throws {
        let state = try #require(Samples.gearVariants["unmounting"])
        let zone = Samples.clock.kotlinZone
        let first = try #require(state.unmountFirstDay(zone: zone))
        let initial = try #require(state.unmountInitialDay(now: Samples.clock.now, zone: zone))
        assertAppSnapshot(
            of: UnmountSheet(firstDay: first, initialDay: initial, send: { _ in }),
            size: CGSize(width: 393, height: 560), named: "unmounting")
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.gear.keys) == Set(Self.samples))
        #expect(Set(Samples.gearVariants.keys) == Set(Self.variants))
    }
}
