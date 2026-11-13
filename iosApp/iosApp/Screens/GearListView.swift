import SwiftUI

/// The athlete's gear (ui/settings-layout), drawn from the states `GearListPresenter` publishes: their collections, then their active
/// items by type, then the retired ones, each item with its photo, kind, who may see it and its one total in their units. The words, the
/// figures and when the collections show are Kotlin's (`GearTexts`, `GearListTexts`, `GearLists`), shared with the Compose page.
/// Everything it opens (an item, a collection, a new one, Ignored devices) opens full width over Settings.
struct GearListView: View {
    let state: GearListState
    @Environment(\.stats) private var stats

    var body: some View {
        ContentStateView(state.content, as: GearLists.self, errorKind: .LoadGear, retry: { send(GearListEvent.Retry.shared) }) { lists in
            list(lists)
        }
        .pageTitle(Text(.gearTitle)) { actions }
    }

    private func list(_ lists: GearLists) -> some View {
        List {
            RefreshStatusView(state: state.content, errorKind: .LoadGear) { send(GearListEvent.Retry.shared) }.listStatusRow()
            if lists.isEmpty {
                VStack(spacing: 16) {
                    Text(.gearEmpty).multilineTextAlignment(.center)
                    AppButton(.gearAdd) { send(GearListEvent.Add.shared) }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 24)
                .listStatusRow()
            }
            if lists.showsCollections {
                collections(lists)
            }
            ForEach(lists.active.elements(as: GearGroup.self), id: \.type) { group in
                Section {
                    items(group.items.elements(as: GearRow.self))
                } header: {
                    Text(GearListTexts.shared.section(type: group.type))
                }
            }
            let retired = lists.retired.elements(as: GearRow.self)
            if !retired.isEmpty {
                Section {
                    items(retired)
                } header: {
                    Text(GearListTexts.shared.section(type: nil))
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    /// The athlete's collections with their counts, then New collection; while there are none, what collections are.
    private func collections(_ lists: GearLists) -> some View {
        Section {
            ForEach(lists.collections.elements(as: GearCollectionRow.self), id: \.rkey) { row in
                AppListRow(
                    Text(verbatim: row.name), summary: Text(GearListTexts.shared.collectionCount(row: row)),
                    opens: { send(GearListEvent.OpenCollection(rkey: row.rkey)) })
            }
            Button { send(GearListEvent.AddCollection.shared) } label: { Text(.gearCollectionNew) }
        } header: {
            Text(.gearCollections)
        } footer: {
            if lists.explainsCollections {
                Text(.gearCollectionsHelp)
            }
        }
    }

    private func items(_ rows: [GearRow]) -> some View {
        ForEach(rows, id: \.rkey) { row in
            AppItemRow(
                Text(verbatim: row.name),
                lines: [Text(GearListTexts.shared.detail(row: row, showRetired: false))],
                summary: GearListTexts.shared.total(row: row, units: stats.units).map { Text($0) },
                opens: { send(GearListEvent.Open(rkey: row.rkey)) }
            ) {
                Thumbnail(url: row.photoUrl)
            }
        }
    }

    /// Add, and a menu holding Ignored devices, as the shared page's bar has them.
    @ViewBuilder private var actions: some View {
        AppIconButton(.add, label: .gearAdd) { send(GearListEvent.Add.shared) }
        Menu {
            Button { send(GearListEvent.OpenIgnoredSensors.shared) } label: { Text(.gearIgnoredSensors) }
        } label: {
            Image(.more).frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
        }
        .accessibilityLabel(Text(.gearMore))
    }

    private func send(_ event: any GearListEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Gear") {
        SamplePages(Samples.gearList) { state in NavigationStack { GearListView(state: state) } }
    }

    #Preview("Gear beside the list", traits: .landscapeLeft) {
        NavigationStack {
            SettingsColumns(
                list: {
                    SettingsView(state: Samples.settings["loaded"]!).environment(\.settingsLayout, .beside(SettingsRows.gear))
                },
                page: GearListView(state: Samples.gearList["loaded"]!).environment(\.inPane, true))
        }
    }
#endif
