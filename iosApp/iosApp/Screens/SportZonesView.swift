import SwiftUI

/// A sport's own zones form (ui/settings-layout), drawn from the states `SportZonesPresenter` publishes: the thresholds its zone kinds
/// use, each blank one showing the default it falls back to, each kind's model with its zones, and Remove for a sport that has its
/// own. What it says, limits and enables is Kotlin's (`SportZonesTexts`, `ZoneThreshold`, `ZoneTexts`, the state's getters), shared
/// with the Compose form. Save is in the bar, a failed save shows at the top, and saving or removing returns to Training zones.
struct SportZonesView: View {
    let state: SportZonesState

    @State private var pace: TextFieldModel
    @State private var thresholdHeartRate: TextFieldModel
    @State private var maxHeartRate: TextFieldModel
    @State private var power: TextFieldModel

    init(state: SportZonesState) {
        self.state = state
        _pace = State(initialValue: ThresholdFields.model(state.thresholdPace, .Pace))
        _thresholdHeartRate = State(initialValue: ThresholdFields.model(state.thresholdHeartRate, .ThresholdHeartRate))
        _maxHeartRate = State(initialValue: ThresholdFields.model(state.maxHeartRate, .MaxHeartRate))
        _power = State(initialValue: ThresholdFields.model(state.functionalThresholdPower, .Power))
    }

    var body: some View {
        // Read so that each keystroke redraws Save, whose condition reads the Kotlin fields SwiftUI doesn't observe.
        let _ = (pace.text, thresholdHeartRate.text, maxHeartRate.text, power.text)
        ContentStateView(state.content, errorKind: .LoadProfile, retry: { send(SportZonesEvent.Retry.shared) }) {
            form
        }
        .pageTitle(Text(SportZonesTexts.shared.title(sport: state.sport)), back: { send(SportZonesEvent.Back.shared) }) {
            if state.loaded {
                Button { send(SportZonesEvent.Save.shared) } label: { Text(LabelText.shared.save(saving: state.saving)) }
                    .fontWeight(.semibold)
                    .disabled(!state.canSave)
            }
        }
    }

    private var form: some View {
        Form {
            if let error = state.error {
                Section { ErrorMessage(error, layout: .row) }
            }
            Section {
                if state.offers(kind: .Speed) {
                    AppTextField(
                        label: SportZonesTexts.shared.paceLabel(unit: state.paceUnit), model: pace, state: state.thresholdPace,
                        layout: .row, options: ThresholdFields.options(.Pace),
                        isError: !state.isValid(threshold: .Pace, text: pace.text))
                }
                field(.ThresholdHeartRate, label: .zonesThresholdHeartRate, model: thresholdHeartRate, state: state.thresholdHeartRate)
                field(.MaxHeartRate, label: .zonesMaxHeartRate, model: maxHeartRate, state: state.maxHeartRate)
                if state.offers(kind: .Power) {
                    field(.Power, label: .sportZonesThresholdPower, model: power, state: state.functionalThresholdPower)
                }
            } header: {
                Text(.zonesThresholdsHeading)
            } footer: {
                Text(SportZonesTexts.shared.help(sport: state.sport))
            }
            if state.offers(kind: .Speed) {
                kind(.Speed, model: state.speedModel, models: state.speedModels.elements(as: ZoneModel.self), zones: state.speedZones) {
                    send(SportZonesEvent.SpeedModelChanged(token: $0))
                }
            }
            kind(
                .HeartRate, model: state.heartRateModel, models: state.heartRateModels.elements(as: ZoneModel.self),
                zones: state.heartRateZones
            ) { send(SportZonesEvent.HeartRateModelChanged(token: $0)) }
            if state.offers(kind: .Power) {
                kind(.Power, model: state.powerModel, models: state.powerModels.elements(as: ZoneModel.self), zones: state.powerZones) {
                    send(SportZonesEvent.PowerModelChanged(token: $0))
                }
            }
            if state.saved {
                Section {
                    Button(role: .destructive) { send(SportZonesEvent.Remove.shared) } label: {
                        Text(SportZonesTexts.shared.remove(sport: state.sport))
                    }
                }
            }
        }
        .disabled(state.saving)
    }

    /// A heart rate or power row, blank showing the default it falls back to, in the error colour while its text isn't a value it takes.
    private func field(
        _ threshold: ZoneThreshold, label: LocalizedStringResource, model: TextFieldModel, state field: KotlinTextFieldState
    ) -> some View {
        AppTextField(
            label: label, model: model, state: field, layout: .row, options: ThresholdFields.options(threshold), hint: hint(threshold),
            isError: !state.isValid(threshold: threshold, text: model.text))
    }

    /// "Default: 190", what a blank `threshold` falls back to.
    private func hint(_ threshold: ZoneThreshold) -> Text? {
        SportZonesTexts.shared.defaultHint(state: state, threshold: threshold).map { Text($0) }
    }

    /// A kind of zone: its model, and the zones it gives or what it needs, pace zones per the sport's pace unit.
    private func kind(
        _ kind: ZoneKind, model: ZoneModel, models: [ZoneModel], zones: (any KotlinCollection)?, onPick: @escaping (String) -> Void
    ) -> some View {
        Section {
            ZoneRows.modelPicker(model: model, models: models, enabled: state.loaded, onPick: onPick)
            ZoneRows.zones(zones?.elements(as: Zone.self), model: model, kind: kind, paceUnit: state.paceUnit)
        } header: {
            Text(ZoneTexts.shared.heading(kind: kind))
        }
    }

    private func send(_ event: any SportZonesEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("A sport's zones") {
        SamplePages(Samples.sportZones) { state in NavigationStack { SportZonesView(state: state) } }
    }

    #Preview("A sport's zones, beyond the shared samples") {
        SamplePages(Samples.sportZonesVariants) { state in NavigationStack { SportZonesView(state: state) } }
    }
#endif
