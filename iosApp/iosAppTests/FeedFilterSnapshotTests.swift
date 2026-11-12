import SwiftUI
import Testing

@testable import ActivityTracker

/// The feed's native filter bar in each sample state, and its type picker (collapsed, a sport disclosed, searching, nothing matching), against their references (feed/feed-filter,
/// dev/ios-snapshot-tests). The bar is laid out at the top of a phone's width, as the feed puts it over its list; the picker is drawn on
/// its own, since a sheet doesn't render in a snapshot of the view that presents it.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct FeedFilterSnapshotTests {
    nonisolated private static let samples = ["loaded", "narrowed", "picking", "custom"]

    @Test(arguments: samples)
    func filterBar(sample: String) throws {
        let state = try #require(Samples.feedFilter[sample])
        assertAppSnapshot(
            of: FeedFilterBar(state: state).padding().frame(maxHeight: .infinity, alignment: .top),
            size: CGSize(width: Snapshots.screen.width, height: 120), named: sample)
    }

    @Test func typePicker() throws {
        let picker = try #require(Samples.feedFilter["picking"]?.typePicker)
        assertAppSnapshot(of: TypePickerSheet(picker: picker) { _ in }, named: "picking", precision: Snapshots.barButtons)
    }

    /// Run disclosed: its sub-sports under it, Trail Run picked and Run part-picked.
    @Test func typePickerExpanded() throws {
        let picker = try #require(Samples.feedFilter["picking"]?.typePicker)
        assertAppSnapshot(
            of: TypePickerSheet(picker: picker, expanded: ["run"]) { _ in }, named: "expanded", precision: Snapshots.barButtons)
    }

    /// "tra" typed: Trail Run and Track Run, no quick types, a plain category header.
    @Test func typePickerSearching() throws {
        let picker = try #require(Samples.feedFilter["picking"]?.typePicker)
        assertAppSnapshot(
            of: TypePickerSheet(picker: picker, query: "tra") { _ in }, named: "searching", precision: Snapshots.barButtons)
    }

    /// Nothing matches: the sheet says so.
    @Test func typePickerNoMatches() throws {
        let picker = try #require(Samples.feedFilter["picking"]?.typePicker)
        assertAppSnapshot(
            of: TypePickerSheet(picker: picker, query: "zzz") { _ in }, named: "noMatches", precision: Snapshots.barButtons)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.feedFilter.keys) == Set(Self.samples))
    }
}
