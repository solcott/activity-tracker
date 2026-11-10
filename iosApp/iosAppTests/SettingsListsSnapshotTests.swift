import SwiftUI
import Testing

@testable import ActivityTracker

/// The Gear list and the blocked and muted lists in each of their sample states, alone and beside Settings' list as a wide window lays
/// them out, against their references (ui/settings-layout, dev/ios-snapshot-tests). The samples hold no picture URLs, so pictures are
/// their placeholders.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct SettingsListsSnapshotTests {
    /// A 13-inch iPad's width, upright: wide enough for the list beside its page.
    private let wide = CGSize(width: 1_032, height: 800)
    /// A phone's width and the whole of the loaded gear list, down to its retired item.
    private let wholeList = CGSize(width: Snapshots.screen.width, height: 1_300)

    nonisolated private static let gearSamples = ["loading", "failed", "loaded", "refreshing", "refreshFailed", "empty"]
    nonisolated private static let blockSamples = DesignGallery.pagedNames
    nonisolated private static let blockVariants = ["muted", "mutedEmpty", "actions"]

    @Test(arguments: gearSamples)
    func gearList(sample: String) throws {
        let state = try #require(Samples.gearList[sample])
        assertAppSnapshot(
            of: NavigationStack { GearListView(state: state) }, size: wholeList, named: sample, precision: Snapshots.barButtons)
    }

    @Test(arguments: blockSamples + blockVariants)
    func blockList(sample: String) throws {
        let state = try #require(Samples.blockList[sample] ?? Samples.blockListVariants[sample])
        assertAppSnapshot(of: NavigationStack { BlockListView(state: state) }, named: sample)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.gearList.keys) == Set(Self.gearSamples))
        #expect(Set(Samples.blockList.keys) == Set(Self.blockSamples))
        #expect(Set(Samples.blockListVariants.keys) == Set(Self.blockVariants))
    }

    @Test func gearBesideTheList() throws {
        let page = GearListView(state: try #require(Samples.gearList["loaded"]))
        assertAppSnapshot(of: beside(SettingsRows.gear, page), size: wide, named: "loaded")
    }

    @Test func blockedAccountsBesideTheList() throws {
        let page = BlockListView(state: try #require(Samples.blockListVariants["actions"]))
        assertAppSnapshot(of: beside(SettingsRows.blocked, page), size: wide, named: "actions")
    }

    /// The list with `row` marked, and `page` in the column beside it, as the shell draws them.
    private func beside(_ row: any SettingsPage, _ page: some View) -> some View {
        NavigationStack {
            SettingsColumns(
                list: {
                    SettingsView(state: Samples.settings["loaded"]!).environment(\.settingsLayout, .beside(row))
                },
                page: page.environment(\.inPane, true))
        }
    }
}
