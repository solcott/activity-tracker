import SwiftUI
import Testing

@testable import ActivityTracker

/// Settings' list in each of its sample states, and beside a page as a wide window lays it out, against their references
/// (ui/settings-layout, dev/ios-snapshot-tests).
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct SettingsSnapshotTests {
    /// A 13-inch iPad's width, upright: wide enough for the list beside its page.
    private let wide = CGSize(width: 1_032, height: 800)

    @Test(arguments: ["loaded", "loading", "moderator", "needsSignIn"])
    func list(sample: String) throws {
        let state = try #require(Samples.settings[sample])
        assertAppSnapshot(of: NavigationStack { SettingsView(state: state) }, named: sample)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.settings.keys) == ["loaded", "loading", "moderator", "needsSignIn"])
    }

    @Test func theListBesideAPage() throws {
        let state = try #require(Samples.settings["loaded"])
        let columns = NavigationStack {
            SettingsColumns(
                list: { SettingsView(state: state).environment(\.settingsLayout, .beside(SettingsRows.privacyZones)) },
                page: PageStandIn())
        }
        assertAppSnapshot(of: columns, size: wide, named: "besidePrivacyZones")
    }
}

/// Stands in for a settings page, which the shared UI draws from a record of the host: a snapshot has neither.
private struct PageStandIn: View {
    var body: some View {
        VStack(spacing: 12) {
            Text(.settingsPrivacyZones).font(.headline)
            Text(verbatim: "The page a row opened shows here.").foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .padding(.top, 16)
        .background(Color(.systemGroupedBackground))
    }
}
