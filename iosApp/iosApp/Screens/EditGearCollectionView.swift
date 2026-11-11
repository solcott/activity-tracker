import SwiftUI

/// The collection form (ui/settings-layout), drawn from the states `EditGearCollectionPresenter` publishes: its name and notes, then its
/// items in order, dragged to reorder and removed in place, and Add items. The form keeps the system's edit mode on, so each item has its
/// handle and its delete control; VoiceOver also has Move up, Move down and Remove in the shared form's words. What it says and offers is
/// Kotlin's (`EditGearCollectionState`'s getters, `EditGearTexts`, `EditGearLimits`, `GearListTexts`), shared with the Compose form. Save
/// is in the bar, and a failed save shows at the top.
struct EditGearCollectionView: View {
    let state: EditGearCollectionState

    @State private var name: TextFieldModel
    @State private var notes: TextFieldModel
    @State private var adding = false

    init(state: EditGearCollectionState) {
        self.state = state
        let limits = EditGearLimits.shared
        _name = State(initialValue: TextFieldModel(state.name, rules: TextFieldRules(maxLength: Int(limits.collectionName))))
        _notes = State(initialValue: TextFieldModel(state.notes, rules: TextFieldRules(maxLength: Int(limits.collectionNotes))))
    }

    var body: some View {
        ContentStateView(state.content, errorKind: .LoadGearCollection, retry: { send(EditGearCollectionEvent.Retry.shared) }) {
            form
        }
        .pageTitle(Text(state.title)) {
            if state.showsSave {
                Button(.gearCollectionSave) { send(EditGearCollectionEvent.Save.shared) }
                    .fontWeight(.semibold)
                    .disabled(!state.enabled)
            }
        }
        .sheet(isPresented: $adding) {
            AddItemsSheet(others: state.others.elements(as: GearRow.self)) { rkeys in
                send(EditGearCollectionEvent.Add(rkeys: rkeys))
            }
        }
    }

    private var form: some View {
        let members = state.members.elements(as: GearRow.self)
        return Form {
            if let error = state.error {
                Section { ErrorMessage(error, layout: .row) }
            }
            Section {
                AppTextField(
                    label: .gearCollectionName, model: name, state: state.name, layout: .row,
                    supportingText: state.nameMissing ? String(localized: .gearCollectionNameRequired) : nil, isError: state.nameMissing)
                AppTextField(label: .gearCollectionNotes, model: notes, state: state.notes, layout: .multiline)
            } footer: {
                Text(.gearCollectionsHelp)
            }
            Section {
                if members.isEmpty {
                    Text(.gearCollectionEmpty).foregroundStyle(.secondary)
                }
                ForEach(Array(members.enumerated()), id: \.element.rkey) { index, row in
                    member(row, at: index, of: members.count)
                }
                .onMove { source, destination in
                    if let move = MemberMove.of(source: source, destination: destination) {
                        send(EditGearCollectionEvent.Move(from: Int32(move.from), to: Int32(move.to)))
                    }
                }
                .onDelete { offsets in
                    offsets.map { members[$0].rkey }.forEach { send(EditGearCollectionEvent.Remove(rkey: $0)) }
                }
            } header: {
                Text(.gearCollectionItems)
            } footer: {
                if !state.canAdd { Text(.gearCollectionFull) }
            }
            if state.canAdd {
                Section {
                    Button(.gearCollectionAddItems) { adding = true }
                }
            }
        }
        .environment(\.editMode, .constant(state.enabled ? .active : .inactive))
        .disabled(!state.enabled)
    }

    private func member(_ row: GearRow, at index: Int, of count: Int) -> some View {
        AppItemRow(Text(verbatim: row.name), lines: [Text(GearListTexts.shared.detail(row: row, showRetired: true))]) {
            Thumbnail(url: row.photoUrl)
        }
        .accessibilityActions {
            if index > 0 {
                Button(.gearCollectionMoveUp(row.name)) {
                    send(EditGearCollectionEvent.Move(from: Int32(index), to: Int32(index - 1)))
                }
            }
            if index < count - 1 {
                Button(.gearCollectionMoveDown(row.name)) {
                    send(EditGearCollectionEvent.Move(from: Int32(index), to: Int32(index + 1)))
                }
            }
            Button(.gearCollectionRemove(row.name)) { send(EditGearCollectionEvent.Remove(rkey: row.rkey)) }
        }
    }

    private func send(_ event: any EditGearCollectionEvent) {
        state.eventSink(event)
    }
}

/// The collection form's Add items: the athlete's other items, ticked in any order and added at the end in the order ticked
/// (`TickedItems`), or "All your gear is in this collection." when there are none.
struct AddItemsSheet: View {
    let others: [GearRow]
    let add: ([String]) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var ticked = TickedItems()

    var body: some View {
        NavigationStack {
            AddItemsList(others: others, ticked: $ticked)
                .navigationTitle(Text(.gearCollectionAddItems))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button(.actionCancel) { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button(.gearCollectionAddItemsDone) {
                            add(ticked.rkeys)
                            dismiss()
                        }
                        .disabled(ticked.rkeys.isEmpty)
                    }
                }
        }
    }
}

/// The list in Add items, apart so it can be previewed and snapshotted.
struct AddItemsList: View {
    let others: [GearRow]
    @Binding var ticked: TickedItems

    var body: some View {
        List {
            if others.isEmpty {
                Text(.gearCollectionNoMoreItems).foregroundStyle(.secondary)
            }
            ForEach(others, id: \.rkey) { row in
                let isTicked = ticked.isTicked(row.rkey)
                Button { ticked.toggle(row.rkey) } label: {
                    AppItemRow(Text(verbatim: row.name), lines: [Text(GearListTexts.shared.detail(row: row, showRetired: true))]) {
                        Thumbnail(url: row.photoUrl)
                    } trailing: {
                        Image(systemName: "checkmark").foregroundStyle(.tint).opacity(isTicked ? 1 : 0).accessibilityHidden(true)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isTicked ? .isSelected : [])
            }
        }
    }
}

#if DEBUG
    #Preview("Collection form") {
        SamplePages(Samples.editGearCollection) { state in NavigationStack { EditGearCollectionView(state: state) } }
    }

    #Preview("Collection form, beyond loading") {
        SamplePages(Samples.editGearCollectionVariants) { state in NavigationStack { EditGearCollectionView(state: state) } }
    }

    #Preview("Add items") {
        AddItemsSheet(others: Samples.editGearCollection["loaded"]!.others.elements(as: GearRow.self)) { _ in }
    }
#endif
