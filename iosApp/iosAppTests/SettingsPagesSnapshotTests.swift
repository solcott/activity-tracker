import SwiftUI
import Testing

@testable import ActivityTracker

/// Activity defaults and Segments & groups in each of their sample states, alone and beside Settings' list as a wide window lays them
/// out, against their references (ui/settings-layout, dev/ios-snapshot-tests).
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct SettingsPagesSnapshotTests {
    /// A 13-inch iPad's width, upright: wide enough for the list beside its page.
    private let wide = CGSize(width: 1_032, height: 800)
    /// A phone's width and the whole of Activity defaults' form, down to the note and a failure at its end.
    private let wholeForm = CGSize(width: Snapshots.screen.width, height: 1_500)

    @Test(arguments: ["loaded", "loading", "locked", "saveFailed"])
    func activityDefaults(sample: String) throws {
        let state = try #require(Samples.activityDefaults[sample])
        assertAppSnapshot(of: NavigationStack { ActivityDefaultsView(state: state) }, size: wholeForm, named: sample)
    }

    @Test(arguments: ["loaded", "loading", "signInAgain", "saveFailed"])
    func segmentsAndGroups(sample: String) throws {
        let state = try #require(Samples.segmentsAndGroups[sample])
        assertAppSnapshot(of: NavigationStack { SegmentsAndGroupsView(state: state) }, named: sample)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.activityDefaults.keys) == ["loaded", "loading", "locked", "saveFailed"])
        #expect(Set(Samples.segmentsAndGroups.keys) == ["loaded", "loading", "signInAgain", "saveFailed"])
    }

    @Test func activityDefaultsBesideTheList() throws {
        let page = ActivityDefaultsView(state: try #require(Samples.activityDefaults["loaded"]))
        assertAppSnapshot(of: beside(SettingsRows.activityDefaults, page), size: wide, named: "loaded")
    }

    @Test func segmentsAndGroupsBesideTheList() throws {
        let page = SegmentsAndGroupsView(state: try #require(Samples.segmentsAndGroups["loaded"]))
        assertAppSnapshot(of: beside(SettingsRows.segmentsAndGroups, page), size: wide, named: "loaded")
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
