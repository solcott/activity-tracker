import SwiftUI

/// Logging an activity by hand (feed/activity-visibility), a grouped form over the states `ManualActivityPresenter` publishes: the type
/// (`ActivityTypeRow`), the title, the units, distance, duration and temperature with the units in their labels, the gear and who sees
/// it, with a failed save at the top and Save in the bar, held back until a duration is entered (`ManualActivityState.valid`). What
/// follows a rule is Kotlin's (`ManualActivityTexts`), shared with the Compose form.
struct ManualActivityView: View {
    let state: ManualActivityState

    @State private var title: TextFieldModel
    @State private var distance: TextFieldModel
    @State private var duration: TextFieldModel
    @State private var temperature: TextFieldModel

    init(state: ManualActivityState) {
        self.state = state
        _title = State(initialValue: TextFieldModel(state.title))
        _distance = State(initialValue: TextFieldModel(state.distance))
        _duration = State(initialValue: TextFieldModel(state.durationMinutes))
        _temperature = State(initialValue: TextFieldModel(state.temperature))
    }

    var body: some View {
        let enabled = !state.saving
        Form {
            if let error = state.error {
                Section { ErrorMessage(error, layout: .row) }
            }
            Section {
                ActivityTypeRow(field: state.type, enabled: enabled)
                AppTextField(label: .fieldTitle, model: title, state: state.title, layout: .row)
            }
            .disabled(!enabled)
            Section {
                Picker(
                    selection: Binding(get: { state.units }, set: { send(ManualActivityEvent.UnitsChanged(value: $0)) })
                ) {
                    Text(.unitsMetric).tag(UnitSystem.Metric)
                    Text(.unitsImperial).tag(UnitSystem.Imperial)
                } label: {
                    EmptyView()
                }
                .pickerStyle(.segmented)
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
            }
            .disabled(!enabled)
            Section {
                AppTextField(
                    label: ManualActivityTexts.shared.distanceLabel(units: state.units), model: distance, state: state.distance,
                    layout: .row, options: TextFieldOptions(keyboard: .decimalPad))
                AppTextField(
                    label: .manualDurationLabel, model: duration, state: state.durationMinutes, layout: .row,
                    options: TextFieldOptions(keyboard: .decimalPad))
                // Not the decimal pad: some have no minus key.
                AppTextField(
                    label: ManualActivityTexts.shared.temperatureLabel(units: state.units), model: temperature,
                    state: state.temperature, layout: .row, options: TextFieldOptions(keyboard: .numbersAndPunctuation))
            }
            .disabled(!enabled)
            Section { GearFieldRow(field: state.gear, enabled: enabled) }
            VisibilityFormSections(
                fields: state.visibility, heading: Text(.activityVisibilityHeading),
                activityEnabled: enabled && state.visibility.canChoose, categoriesEnabled: enabled
            ) {
                if state.visibility.canChoose {
                    Text(.activityVisibilityHelp)
                } else {
                    Text(VisibilityTexts.shared.lockedNote(fields: state.visibility))
                }
            } detailsNote: {
                EmptyView()
            }
            if state.saving {
                Section { AppProgress().frame(maxWidth: .infinity) }
            }
        }
        .pageTitle(Text(.manualTitle)) {
            Button(.actionSave) { send(ManualActivityEvent.Save.shared) }
                .fontWeight(.semibold)
                .disabled(!state.valid || state.saving)
        }
    }

    private func send(_ event: any ManualActivityEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Manual entry") {
        SamplePages(Samples.manualActivity) { state in NavigationStack { ManualActivityView(state: state) } }
    }

    #Preview("Manual entry, beyond these") {
        SamplePages(Samples.manualActivityVariants) { state in NavigationStack { ManualActivityView(state: state) } }
    }
#endif
