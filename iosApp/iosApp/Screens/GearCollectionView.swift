import SwiftUI

/// One of the athlete's gear collections (ui/settings-layout), drawn from the states `GearCollectionPresenter` publishes: its notes, then
/// its items in the athlete's order, each with its photo, kind, who may see it and Retired where it is, and its one total. Edit and
/// Delete are in the bar, and Delete asks first in the system's alert. The title, the lines and when Edit and Delete show and can be
/// used are Kotlin's (`GearCollectionState`, `GearListTexts`), shared with the Compose page. It opens full width over Settings, and so
/// does an item opened from it.
struct GearCollectionView: View {
    let state: GearCollectionState
    @Environment(\.stats) private var stats

    var body: some View {
        ContentStateView(
            state.content, as: GearCollectionDetail.self, errorKind: .LoadGearCollection, retry: { send(GearCollectionEvent.Retry.shared) }
        ) { detail in
            list(detail)
        }
        .pageTitle(Text(state.title)) { actions }
        .alert(
            Text(.gearCollectionDeleteTitle),
            isPresented: Binding(get: { state.confirmingDelete }, set: { if !$0 { send(GearCollectionEvent.DismissDelete.shared) } })
        ) {
            Button(.gearCollectionDelete, role: .destructive) { send(GearCollectionEvent.ConfirmDelete.shared) }
            Button(.actionCancel, role: .cancel) {}
        } message: {
            Text(.gearCollectionDeleteMessage)
        }
    }

    private func list(_ detail: GearCollectionDetail) -> some View {
        List {
            RefreshStatusView(state: state.content, errorKind: .LoadGearCollection) { send(GearCollectionEvent.Retry.shared) }
                .listStatusRow()
            // Why the delete failed, over the items, which stay.
            if let error = state.error {
                ErrorMessage(error, layout: .row).listStatusRow()
            }
            if let notes = detail.notes {
                Section {
                    Text(verbatim: notes).textSelection(.enabled)
                }
            }
            let members = detail.members.elements(as: GearRow.self)
            if members.isEmpty {
                Text(.gearCollectionEmpty).foregroundStyle(.secondary).frame(maxWidth: .infinity).listStatusRow()
            } else {
                Section {
                    ForEach(members, id: \.rkey) { row in
                        AppItemRow(
                            Text(verbatim: row.name),
                            lines: [Text(GearListTexts.shared.detail(row: row, showRetired: true))],
                            summary: GearListTexts.shared.total(row: row, units: stats.units).map { Text($0) },
                            opens: { send(GearCollectionEvent.Open(rkey: row.rkey)) }
                        ) {
                            Thumbnail(url: row.photoUrl)
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    /// Edit and Delete, as the shared page's bar has them, once the collection's name is known; neither while it is being deleted.
    @ViewBuilder private var actions: some View {
        if state.showsActions {
            AppIconButton(.manualEntry, label: .gearCollectionEdit) { send(GearCollectionEvent.Edit.shared) }
                .disabled(!state.actionsEnabled)
            AppIconButton(.delete, label: .gearCollectionDelete) { send(GearCollectionEvent.Delete.shared) }
                .disabled(!state.actionsEnabled)
        }
    }

    private func send(_ event: any GearCollectionEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Gear collection") {
        SamplePages(Samples.gearCollection) { state in NavigationStack { GearCollectionView(state: state) } }
    }

    #Preview("Gear collection, beyond loading") {
        SamplePages(Samples.gearCollectionVariants) { state in NavigationStack { GearCollectionView(state: state) } }
    }
#endif
