import SwiftUI
import UIKit

/// Recording an activity (record/gps-recording), a grouped form over the states `RecordPresenter` publishes: a failed finish and the
/// recording's notices first, then the elapsed time large with its figures in the athlete's units, then the type (outdoor types only,
/// fixed once recording starts), the title, the gear and who sees it, as the native forms draw them. The controls for the recording's
/// status sit in a bar at the bottom, where a phone's thumb finds them mid-ride: Start; Pause; Resume, Finish & save and Discard, which
/// asks first. Recording is Kotlin's (`ActivityRecorder`); this draws the state and sends events. Start without location access asks
/// Core Location (`LocationAuthorization`) and sends the answer; a refused access says so, with Open Settings. What follows a rule is
/// Kotlin's (`RecordTexts`), shared with the Compose screen.
struct RecordView: View {
    let state: RecordState

    @State private var title: TextFieldModel
    @State private var location = LocationAuthorization()
    @State private var refused: Bool
    @State private var confirmingDiscard = false
    /// Set for a preview or snapshot, which can't choose the simulator's own answer; nil reads Core Location's.
    private let refusedOverride: Bool?
    @Environment(\.stats) private var stats
    @Environment(\.scenePhase) private var scenePhase

    init(state: RecordState, locationRefused: Bool? = nil) {
        self.state = state
        refusedOverride = locationRefused
        _title = State(initialValue: TextFieldModel(state.title))
        _refused = State(initialValue: locationRefused ?? false)
    }

    var body: some View {
        let texts = RecordTexts.shared
        let enabled = !state.saving
        Form {
            if let error = state.error {
                Section { ErrorMessage(error, layout: .row) }
            }
            notices
            Section { clock }
            Section {
                ActivityTypeRow(field: state.type, enabled: texts.typeChangeable(state: state), outdoorOnly: true)
                AppTextField(label: .fieldTitle, model: title, state: state.title, layout: .row)
                    .disabled(!enabled)
            }
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
        }
        .safeAreaInset(edge: .bottom, spacing: 0) { controls }
        .pageTitle(Text(texts.title), back: { send(RecordEvent.Back.shared) })
        .confirmationDialog(Text(texts.discardConfirm), isPresented: $confirmingDiscard, titleVisibility: .visible) {
            Button(role: .destructive) { send(RecordEvent.Discard.shared) } label: { Text(texts.label(action: .Discard)) }
        }
        .onAppear(perform: readAccess)
        .onChange(of: scenePhase) { _, phase in
            // Back from Settings, where the athlete may have allowed it.
            if phase == .active { readAccess() }
        }
    }

    /// What the recording is or needs before it goes on: offered back after the app stopped, not saved on the device, location off.
    @ViewBuilder private var notices: some View {
        let texts = RecordTexts.shared
        if state.recovered {
            Section { Text(texts.recovered) }
        }
        if state.saveFailed {
            Section { Text(texts.saveFailed).foregroundStyle(.red) }
        }
        if refused && !state.hasPermission {
            Section {
                Text(texts.locationOff)
                Button {
                    if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                } label: {
                    Text(texts.openSettings)
                }
            }
        }
    }

    /// The time recorded, large enough to read at arm's length, and the figures under it.
    private var clock: some View {
        VStack(spacing: 12) {
            Text(RecordTexts.shared.elapsed(state: state))
                .font(.system(size: 64, weight: .semibold, design: .rounded))
                .monospacedDigit()
                .lineLimit(1)
                .minimumScaleFactor(0.5)
                .frame(maxWidth: .infinity)
                .accessibilityLabel(Text(.statTime))
                .accessibilityValue(Text(RecordTexts.shared.elapsed(state: state)))
            CardStats(stats: RecordTexts.shared.stats(state: state, units: stats.units))
        }
        .padding(.vertical, 8)
    }

    /// The controls for the recording's status, in Kotlin's order, sharing the bar's width; Finish & save, which ends the recording,
    /// on a row of its own over the others. Progress takes their place while finishing.
    private var controls: some View {
        let all = RecordTexts.shared.controls(state: state)
        let finish = all.first { $0.action == .Finish }
        let others = all.filter { $0.action != .Finish }
        return VStack(spacing: 12) {
            if let finish { button(finish) }
            HStack(spacing: 12) {
                ForEach(others, id: \.action) { button($0) }
            }
        }
        .disabled(state.saving)
        // While finishing, the progress alone, where the controls were.
        .opacity(state.saving ? 0 : 1)
        .overlay {
            if state.saving { AppProgress() }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(.bar)
    }

    @ViewBuilder private func button(_ control: RecordControl) -> some View {
        if control.action == .Discard {
            AppSecondaryButton(control.label, wide: true) { perform(control.action) }
        } else {
            AppButton(control.label, wide: true) { perform(control.action) }
        }
    }

    private func perform(_ action: RecordAction) {
        switch action {
        case .Start:
            if state.hasPermission {
                send(RecordEvent.Start.shared)
            } else {
                Task {
                    let granted = await location.request()
                    refused = refusedOverride ?? location.refused
                    send(RecordEvent.PermissionResult(granted: granted))
                }
            }
        case .Pause: send(RecordEvent.Pause.shared)
        case .Resume: send(RecordEvent.Resume.shared)
        case .Finish: send(RecordEvent.Finish.shared)
        case .Discard: confirmingDiscard = true
        }
    }

    private func readAccess() {
        refused = refusedOverride ?? location.refused
    }

    private func send(_ event: any RecordEvent) {
        state.eventSink(event)
    }
}

#if DEBUG
    #Preview("Record") {
        SamplePages(Samples.record) { state in NavigationStack { RecordView(state: state) } }
    }

    #Preview("Record, beyond these") {
        SamplePages(Samples.recordVariants) { state in NavigationStack { RecordView(state: state) } }
    }

    #Preview("Record, location off") {
        if let state = Samples.recordVariants["noPermission"] {
            NavigationStack { RecordView(state: state, locationRefused: true) }
        }
    }
#endif
