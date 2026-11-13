import SwiftUI

/// The default zones' form (ui/settings-layout), drawn from the states `ZonesPresenter` publishes: the thresholds, each with whether
/// imports may update it, and the heart rate and power models with the zones they give. What it says, limits and enables is Kotlin's
/// (`ZoneThreshold`, `ZoneTexts`, the state's getters), shared with the Compose form. Save is in the bar, a failed save shows at the
/// top, and a save that succeeds returns to Training zones.
///
/// Beside Settings' list it is the column's second page, with a back button to Training zones.
struct ZonesView: View {
    let state: ZonesState

    @State private var thresholdHeartRate: TextFieldModel
    @State private var maxHeartRate: TextFieldModel
    @State private var restingHeartRate: TextFieldModel
    @State private var power: TextFieldModel

    init(state: ZonesState) {
        self.state = state
        _thresholdHeartRate = State(initialValue: ThresholdFields.model(state.thresholdHeartRate, .ThresholdHeartRate))
        _maxHeartRate = State(initialValue: ThresholdFields.model(state.maxHeartRate, .MaxHeartRate))
        _restingHeartRate = State(initialValue: ThresholdFields.model(state.restingHeartRate, .RestingHeartRate))
        _power = State(initialValue: ThresholdFields.model(state.functionalThresholdPower, .Power))
    }

    var body: some View {
        // Read so that each keystroke redraws Save, whose condition reads the Kotlin fields SwiftUI doesn't observe.
        let _ = (thresholdHeartRate.text, maxHeartRate.text, restingHeartRate.text, power.text)
        ContentStateView(state.content, errorKind: .LoadProfile, retry: { send(ZonesEvent.Retry.shared) }) {
            form
        }
        .pageTitle(Text(.zonesDefaultTitle), back: { send(ZonesEvent.Back.shared) }) {
            if state.loaded {
                Button { send(ZonesEvent.Save.shared) } label: { Text(LabelText.shared.save(saving: state.saving)) }
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
                threshold(.ThresholdHeartRate, label: .zonesThresholdHeartRate, model: thresholdHeartRate, field: state.thresholdHeartRate)
            } header: {
                // What the defaults are for, over the thresholds, as the shared form opens with it.
                VStack(alignment: .leading, spacing: 12) {
                    Text(.zonesDefaultHelp).font(.subheadline.weight(.regular)).textCase(nil)
                    Text(.zonesThresholdsHeading)
                }
            }
            Section { threshold(.MaxHeartRate, label: .zonesMaxHeartRate, model: maxHeartRate, field: state.maxHeartRate) }
            Section { threshold(.RestingHeartRate, label: .zonesRestingHeartRate, model: restingHeartRate, field: state.restingHeartRate) }
            Section {
                threshold(.Power, label: .zonesFtp, model: power, field: state.functionalThresholdPower)
            } footer: {
                Text(.zonesAutoUpdateHelp)
            }
            Section {
                ZoneRows.modelPicker(
                    model: state.heartRateModel, models: state.heartRateModels.elements(as: ZoneModel.self), enabled: state.loaded
                ) { send(ZonesEvent.HeartRateModelChanged(token: $0)) }
                ZoneRows.zones(
                    state.heartRateZones?.elements(as: Zone.self), model: state.heartRateModel, kind: .HeartRate, paceUnit: .PerKilometer)
            } header: {
                Text(ZoneTexts.shared.heading(kind: .HeartRate))
            }
            Section {
                ZoneRows.modelPicker(model: state.powerModel, models: state.powerModels.elements(as: ZoneModel.self), enabled: state.loaded) {
                    send(ZonesEvent.PowerModelChanged(token: $0))
                }
                ZoneRows.zones(state.powerZones?.elements(as: Zone.self), model: state.powerModel, kind: .Power, paceUnit: .PerKilometer)
            } header: {
                Text(ZoneTexts.shared.heading(kind: .Power))
            }
        }
        .disabled(state.saving)
    }

    /// A threshold's row, in the error colour while its text isn't a value it takes, and whether imports may update it.
    @ViewBuilder private func threshold(
        _ threshold: ZoneThreshold, label: LocalizedStringResource, model: TextFieldModel, field: KotlinTextFieldState
    ) -> some View {
        AppTextField(
            label: label, model: model, state: field, layout: .row, options: ThresholdFields.options(threshold),
            isError: !state.isValid(threshold: threshold, text: model.text))
        Toggle(
            isOn: Binding(
                get: { state.autoUpdates(threshold: threshold) },
                set: { send(ZonesEvent.AutoUpdateChanged(threshold: threshold.token, enabled: $0)) })
        ) {
            Text(.zonesAutoUpdate)
        }
        .disabled(!state.loaded)
    }

    private func send(_ event: any ZonesEvent) {
        state.eventSink(event)
    }
}

/// How the zones forms' threshold fields take text, from Kotlin's `ZoneThreshold`: digits only and at most three or four for heart
/// rates and power, on the number pad; a pace as `m:ss` on the numbers-and-punctuation keyboard.
enum ThresholdFields {
    static func rules(_ threshold: ZoneThreshold) -> TextFieldRules {
        TextFieldRules(digitsOnly: threshold.digitsOnly, maxLength: Int(threshold.maxLength))
    }

    static func model(_ field: KotlinTextFieldState, _ threshold: ZoneThreshold) -> TextFieldModel {
        TextFieldModel(field, rules: rules(threshold))
    }

    static func options(_ threshold: ZoneThreshold) -> TextFieldOptions {
        TextFieldOptions(
            keyboard: threshold.digitsOnly ? .numberPad : .numbersAndPunctuation, capitalization: .never, autocorrection: false)
    }
}

#if DEBUG
    #Preview("Default zones") {
        SamplePages(Samples.zones) { state in NavigationStack { ZonesView(state: state) } }
    }

    #Preview("Default zones, beyond the shared samples") {
        SamplePages(Samples.zonesVariants) { state in NavigationStack { ZonesView(state: state) } }
    }
#endif
