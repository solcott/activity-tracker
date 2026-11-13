import PhotosUI
import SwiftUI

/// The gear form (ui/settings-layout), drawn from the states `EditGearPresenter` publishes: what the item is, what it is for, its photo,
/// who may see it, and for a saved item what it is mounted on, retiring and deleting it. What it says, limits and offers is Kotlin's
/// (`EditGearState`'s getters, `EditGearTexts`, `EditGearLimits`, `SportTypeGroups`), shared with the Compose form. Save is in the bar,
/// and a failed save shows at the top. It opens full width over Settings.
struct EditGearView: View {
    let state: EditGearState
    @Environment(\.clock) private var clock

    @State private var name: TextFieldModel
    @State private var brand: TextFieldModel
    @State private var model: TextFieldModel
    @State private var weight: TextFieldModel
    @State private var startingDistance: TextFieldModel
    @State private var startingTime: TextFieldModel
    @State private var notes: TextFieldModel
    @State private var choosing: TypeChoice?
    @State private var pickingDate = false
    @State private var photoItem: PhotosPickerItem?
    @Environment(\.calendar) private var calendar

    init(state: EditGearState) {
        self.state = state
        let limits = EditGearLimits.shared
        _name = State(initialValue: TextFieldModel(state.name, rules: TextFieldRules(maxLength: Int(limits.name))))
        _brand = State(initialValue: TextFieldModel(state.brand, rules: TextFieldRules(maxLength: Int(limits.brand))))
        _model = State(initialValue: TextFieldModel(state.model, rules: TextFieldRules(maxLength: Int(limits.brand))))
        _weight = State(
            initialValue: TextFieldModel(state.weight, rules: TextFieldRules(digitsOnly: true, maxLength: Int(limits.weightDigits))))
        _startingDistance = State(
            initialValue: TextFieldModel(state.startingDistance, rules: TextFieldRules(maxLength: Int(limits.starting))))
        _startingTime = State(initialValue: TextFieldModel(state.startingTime, rules: TextFieldRules(maxLength: Int(limits.starting))))
        _notes = State(initialValue: TextFieldModel(state.notes, rules: TextFieldRules(maxLength: Int(limits.notes))))
    }

    var body: some View {
        ContentStateView(state.content, errorKind: .LoadGear, retry: { send(EditGearEvent.Retry.shared) }) {
            form
        }
        .pageTitle(Text(state.title)) {
            if state.showsSave {
                Button(.gearSave) { send(EditGearEvent.Save.shared) }
                    .fontWeight(.semibold)
                    .disabled(!state.enabled)
            }
        }
        .alert(
            Text(.gearDeleteTitle),
            isPresented: Binding(get: { state.confirmingDelete }, set: { if !$0 { send(EditGearEvent.DismissDelete.shared) } })
        ) {
            Button(.gearRetire) { send(EditGearEvent.Retire.shared) }
            Button(.gearDelete, role: .destructive) { send(EditGearEvent.ConfirmDelete.shared) }
            Button(.actionCancel, role: .cancel) {}
        } message: {
            Text(.gearDeleteMessage)
        }
        .sheet(item: $choosing) { choice in
            TypeChoiceSheet(choice: choice, state: state)
        }
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            photoItem = nil
            Task {
                await PhotoPick.app.pick({ try await item.loadTransferable(type: Data.self) }) { photo in
                    send(EditGearEvent.PhotoPicked(photo: photo))
                }
            }
        }
    }

    private var form: some View {
        Form {
            // Why the save failed, where it is seen with Save in the bar.
            if let error = state.error {
                Section { ErrorMessage(error, layout: .row) }
            }
            Section {
                AppTextField(
                    label: .gearName, model: name, state: state.name, layout: .row,
                    supportingText: state.nameMissing ? String(localized: .gearNameRequired) : nil, isError: state.nameMissing)
                AppPickerRow(
                    String(localized: .gearType),
                    selection: Binding(get: { state.type }, set: { send(EditGearEvent.TypeChanged(type: $0)) }),
                    options: state.types.elements(as: String.self),
                    optionLabel: { AppStrings.string(GearTexts.shared.type(type: $0)) })
                if state.showsKind {
                    let options = state.kindOptions.elements(as: GearKindOption.self)
                    AppPickerRow(
                        String(localized: .gearKind),
                        selection: Binding(get: { state.kind }, set: { send(EditGearEvent.KindChanged(kind: $0)) }),
                        options: options.map(\.kind),
                        optionLabel: { kind in options.first { $0.kind == kind }.map { AppStrings.string($0.label) } ?? "" })
                }
                AppTextField(label: .gearBrand, model: brand, state: state.brand, layout: .row)
                AppTextField(label: .gearModel, model: model, state: state.model, layout: .row)
            }
            Section {
                AppListRow(Text(.gearSports), summary: Text(state.sportsLabel), opens: { choosing = .sports })
                AppListRow(Text(.gearDefaultFor), summary: Text(state.defaultForLabel), opens: { choosing = .defaultFor })
            } footer: {
                Text(.gearDefaultForHint)
            }
            collections
            Section {
                AppTextField(
                    label: .gearWeight, model: weight, state: state.weight, layout: .row,
                    options: TextFieldOptions(keyboard: .numberPad))
                acquired
            }
            Section {
                AppTextField(
                    label: state.startingDistanceLabel, model: startingDistance, state: state.startingDistance, layout: .row,
                    options: TextFieldOptions(keyboard: .decimalPad))
                AppTextField(
                    label: .gearStartingTime, model: startingTime, state: state.startingTime, layout: .row,
                    options: TextFieldOptions(keyboard: .decimalPad))
            } footer: {
                Text(.gearStartingHelp)
            }
            Section { photo }
            Section {
                AppTextField(label: .gearNotes, model: notes, state: state.notes, layout: .multiline)
            }
            Section {
                AppPickerRow(
                    String(localized: .visibilityLabel),
                    selection: Binding(get: { state.visibility }, set: { send(EditGearEvent.VisibilityChanged(visibility: $0)) }),
                    options: state.visibilityOptions.elements(as: String.self),
                    optionLabel: { AppStrings.string(LabelText.shared.visibility(visibility: $0)) })
            } footer: {
                VStack(alignment: .leading, spacing: 8) {
                    if let note = state.followersNote { Text(note) }
                    Text(.gearVisibilityHelp)
                }
            }
            if state.showsManagement { management }
        }
        .disabled(!state.enabled)
    }

    /// The athlete's collections, each ticked when the item is in it; none when they have no collection.
    @ViewBuilder private var collections: some View {
        let choices = state.collections.elements(as: GearCollectionChoice.self)
        if !choices.isEmpty {
            Section {
                ForEach(choices, id: \.rkey) { choice in
                    Button { send(EditGearEvent.ToggleCollection(rkey: choice.rkey)) } label: {
                        HStack {
                            Text(verbatim: choice.name).foregroundStyle(.primary)
                            Spacer()
                            if choice.member {
                                Image(systemName: "checkmark").foregroundStyle(.tint).accessibilityHidden(true)
                            }
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(choice.member ? .isSelected : [])
                }
            } header: {
                Text(.gearCollections)
            }
        }
    }

    /// The day the athlete got the item, from a calendar of past days in a popover, and clearing it.
    private var acquired: some View {
        HStack(spacing: 12) {
            Text(.gearAcquired).frame(maxWidth: .infinity, alignment: .leading)
            if let date = state.acquiredOn {
                Button { pickingDate = true } label: { Text(EditGearTexts.shared.date(date: date)) }
                    .accessibilityHint(Text(.gearAcquiredChoose))
                AppIconButton(.clear, label: .gearAcquiredClear) { send(EditGearEvent.AcquiredChanged(date: nil)) }
                    .foregroundStyle(.secondary)
            } else {
                AppIconButton(.calendar, label: .gearAcquiredChoose) { pickingDate = true }
            }
        }
        .popover(isPresented: $pickingDate) {
            DatePicker(
                selection: Binding(
                    get: { state.acquiredOn.map { CalendarDay($0).date(in: calendar) } ?? clock.date },
                    set: { picked in
                        pickingDate = false
                        send(EditGearEvent.AcquiredChanged(date: CalendarDay(picked, in: calendar).localDate))
                    }),
                in: ...clock.date, displayedComponents: .date
            ) {
                Text(.gearAcquired)
            }
            .datePickerStyle(.graphical)
            .padding()
            .frame(minWidth: 320)
            .presentationCompactAdaptation(.popover)
        }
    }

    /// The photo as it is now, picked from the library, changed or removed.
    private var photo: some View {
        HStack(spacing: 16) {
            GearPhotoView(photo: state.photo)
            VStack(alignment: .leading, spacing: 8) {
                PhotosPicker(selection: $photoItem, matching: .images, preferredItemEncoding: .current) {
                    Text(state.photoAction)
                }
                if state.photo != nil {
                    Button(.gearPhotoRemove, role: .destructive) { send(EditGearEvent.RemovePhoto.shared) }
                }
            }
            .buttonStyle(.borderless)
        }
    }

    /// A saved item's mount, retirement and deletion.
    @ViewBuilder private var management: some View {
        Section {
            if let mounted = state.mountedOnLabel {
                Button { send(EditGearEvent.OpenMountedOn.shared) } label: { Text(mounted) }
            }
            if let retired = state.retiredOnLabel {
                Text(retired)
            }
            if state.isRetired {
                Button(.gearUnretire) { send(EditGearEvent.Unretire.shared) }
            } else {
                Button(.gearRetire) { send(EditGearEvent.Retire.shared) }
            }
        }
        Section {
            Button(.gearDelete, role: .destructive) { send(EditGearEvent.Delete.shared) }
        }
    }

    private func send(_ event: any EditGearEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Gear form") {
        SamplePages(Samples.editGear) { state in NavigationStack { EditGearView(state: state) } }
    }

    #Preview("Gear form, beyond loading") {
        SamplePages(Samples.editGearVariants) { state in NavigationStack { EditGearView(state: state) } }
    }
#endif
