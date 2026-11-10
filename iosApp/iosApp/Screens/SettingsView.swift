import SwiftUI

/// Settings' list (ui/settings-layout), drawn from the states `SettingsPresenter` publishes: the units choice, saved in place, and a
/// row for each page, grouped, each summarizing its page where it has been read. What the summaries say is Kotlin's `SettingsTexts`,
/// shared with the Compose list. On a wide window the shell shows the chosen page beside it (`SettingsColumns`), and the list marks
/// that page's row.
///
/// Moderation isn't offered: moderators review on desktop and web.
struct SettingsView: View {
    let state: SettingsState
    @Environment(\.settingsLayout) private var layout

    var body: some View {
        List {
            Section {
                AppListRow(
                    Text(verbatim: state.handle),
                    summary: state.needsSignIn ? Text(.settingsAccountNeedsSignIn) : nil,
                    selected: layout.marks(SettingsRows.account),
                    opens: { open(SettingsRows.account) })
            } header: {
                Text(.settingsSectionAccount)
            }
            Section {
                units
            } header: {
                Text(.settingsSectionGeneral)
            }
            Section {
                row(
                    .settingsActivityDefaults,
                    summary: SettingsTexts.shared.defaultVisibility(visibility: state.defaultActivityVisibility),
                    opens: SettingsRows.activityDefaults)
                row(
                    .settingsPrivacyZones,
                    summary: SettingsTexts.shared.privacyZones(count: state.privacyZoneCount, hidesRouteEnds: state.hidesRouteEnds),
                    opens: SettingsRows.privacyZones)
                row(
                    .settingsSegmentsAndGroups,
                    summary: SettingsTexts.shared.segmentMatching(on: state.segmentMatching),
                    opens: SettingsRows.segmentsAndGroups)
            } header: {
                Text(.settingsSectionPrivacy)
            }
            Section {
                row(.settingsZones, opens: SettingsRows.trainingZones)
                row(.settingsGear, opens: SettingsRows.gear)
            } header: {
                Text(.settingsSectionTraining)
            }
            Section {
                row(.settingsBlockedAccounts, opens: SettingsRows.blocked)
                row(.settingsMutedAccounts, opens: SettingsRows.muted)
            } header: {
                Text(.settingsSectionSafety)
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(Text(.settingsTitle))
        .navigationBarTitleDisplayMode(.inline)
    }

    /// The units choice, saved as soon as it changes, with what it means under it.
    private var units: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(.settingsUnitsHeading)
            Picker(selection: unitsChoice) {
                Text(.unitsMetric).tag(UnitSystem.Metric)
                Text(.unitsImperial).tag(UnitSystem.Imperial)
            } label: {
                Text(.settingsUnitsHeading)
            }
            .pickerStyle(.segmented)
            .labelsHidden()
            .disabled(state.saving)
            Text(SettingsTexts.shared.unitsNote(units: state.units, saving: state.saving))
                .font(.footnote)
                .foregroundStyle(.secondary)
            if let error = state.error {
                ErrorMessage(error, layout: .row).font(.footnote)
            }
        }
        .padding(.vertical, 4)
    }

    /// The presenter's choice, which it changes at once and puts back if the save fails.
    private var unitsChoice: Binding<UnitSystem> {
        Binding(get: { state.units }, set: { state.eventSink(SettingsEvent.UnitsChanged(units: $0)) })
    }

    private func row(_ title: LocalizedStringResource, summary: (any AppText)? = nil, opens page: any SettingsPage) -> some View {
        AppListRow(Text(title), summary: summary.map { Text($0) }, selected: layout.marks(page), opens: { open(page) })
    }

    private func open(_ page: any SettingsPage) {
        state.eventSink(SettingsEvent.Open(page: page))
    }
}

/// The page each of Settings' rows opens. Three contract modules hold them: settings' own, gear's and the profile's.
enum SettingsRows {
    static var account: any SettingsPage { SettingsPackage.AccountScreen.shared }
    static var activityDefaults: any SettingsPage { SettingsPackage.ActivityDefaultsScreen.shared }
    static var privacyZones: any SettingsPage { SettingsPackage.PrivacyZonesScreen.shared }
    static var segmentsAndGroups: any SettingsPage { SettingsPackage.SegmentsAndGroupsScreen.shared }
    static var trainingZones: any SettingsPage { SettingsPackage.TrainingZonesScreen.shared }
    static var gear: any SettingsPage { GearListScreen.shared }
    static var blocked: any SettingsPage { BlockListScreen(kind: .Blocked) }
    static var muted: any SettingsPage { BlockListScreen(kind: .Muted) }
}

/// How Settings is laid out around its list: one page at a time, or the list beside a page, whose row it marks. Kotlin's `SettingsPane`
/// holds it and decides which row is marked (`marks`), as it does for the Compose list.
struct SettingsLayout: @unchecked Sendable {
    // Unchecked because a Kotlin class isn't declared Sendable in Swift; this one is an immutable value.
    let pane: SettingsPane

    /// One page at a time: no row is marked.
    static var single: SettingsLayout { SettingsLayout(pane: SettingsPane.Companion.shared.Single) }

    /// The list beside `page`.
    static func beside(_ page: any SettingsPage) -> SettingsLayout {
        SettingsLayout(pane: SettingsPane(twoPane: true, shownPage: page))
    }

    func marks(_ page: any SettingsPage) -> Bool { pane.marks(page: page) }
}

extension EnvironmentValues {
    /// How Settings is laid out, for its list. The shell sets it when it draws a page beside the list.
    @Entry var settingsLayout = SettingsLayout.single
}

/// Settings' list with, on a wide window, the page it opened beside it (ui/settings-layout). The list is the same view narrow and wide,
/// so crossing the width keeps its scroll position. There is no navigation container in here: the bar over both columns is the stack's
/// own, with its back button, and a page draws whatever bar it has under it. (A `NavigationStack` inside a stack's destination is never
/// shown, and a `NavigationSplitView` there chooses for itself which column shows; the roadmap's phase 4 probes.)
struct SettingsColumns<ListContent: View, Page: View>: View {
    private let list: ListContent
    private let page: Page?

    /// `page` is nil for one page at a time.
    init(@ViewBuilder list: () -> ListContent, page: Page?) {
        self.list = list()
        self.page = page
    }

    var body: some View {
        HStack(spacing: 0) {
            list.frame(maxWidth: page == nil ? .infinity : SettingsColumnsMetrics.listWidth)
            if let page {
                Divider().ignoresSafeArea()
                page.frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
    }
}

enum SettingsColumnsMetrics {
    /// The list's width beside a page, as on the other platforms.
    static let listWidth: CGFloat = 360
}

#if DEBUG
    #Preview("Settings") {
        SamplePages(Samples.settings) { state in NavigationStack { SettingsView(state: state) } }
    }

    #Preview("Settings beside a page", traits: .landscapeLeft) {
        NavigationStack {
            SettingsColumns(
                list: {
                    SettingsView(state: Samples.settings["loaded"]!)
                        .environment(\.settingsLayout, .beside(SettingsRows.privacyZones))
                },
                page: Text(verbatim: "Privacy zones"))
        }
    }
#endif
