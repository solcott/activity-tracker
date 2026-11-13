import SwiftUI

/// Leave Activity Tracker (auth/leaving, ui/settings-layout), drawn from the states `LeavePresenter` publishes. Before the athlete
/// confirms: what is deleted and what stays, the shared places that stay without their name, what this device would lose, the export,
/// the grant, and the typed-handle confirmation. Once the leave runs, only its progress, with no way back: whether opened from Account or
/// shown in place of the app while a leave resumes. What it says that follows a rule, and when Leave is on, is Kotlin's (`LeaveTexts`,
/// `canLeave`), shared with the Compose page.
///
/// It is opened from Account, so beside Settings' list it is a deeper page, with a back button in its header that returns to Account.
struct LeaveView: View {
    let state: LeaveState
    /// The handle as typed, held here so Leave follows each keystroke. It starts on the state's field and the field moves it when the
    /// presenter replaces that.
    @State private var typed: TextFieldModel

    init(state: LeaveState) {
        self.state = state
        _typed = State(initialValue: TextFieldModel(state.confirmHandle))
    }

    var body: some View {
        List {
            if let run = state.run {
                progress(run)
            } else {
                summary
            }
        }
        .listStyle(.insetGrouped)
        .pageTitle(Text(.leaveTitle), back: { state.eventSink(LeaveEvent.Back.shared) }, canGoBack: state.canGoBack)
    }

    @ViewBuilder private var summary: some View {
        Section {
            Text(.leaveIntro(state.handle))
        }
        Section {
            bullets([
                Text(.leaveDeletedActivities), Text(.leaveDeletedProfile), Text(.leaveDeletedGear), Text(.leaveDeletedFollows),
                Text(.leaveDeletedInteractions), Text(.leaveDeletedSegments), Text(.leaveDeletedSettings),
            ])
        } header: {
            Text(.leaveSectionDeleted)
        }
        Section {
            bullets([Text(.leaveStaysAccount(state.handle)), Text(.leaveStaysOtherApps)])
        } header: {
            Text(.leaveSectionStays)
        }
        Section {
            places
        } header: {
            Text(.leaveSectionPlaces)
        }
        if state.showsDeviceWork {
            Section {
                bullets(LeaveTexts.shared.deviceLines(state: state).map { Text($0) })
            } header: {
                Text(.leaveSectionDevice)
            }
        }
        Section {
            export
        } header: {
            Text(.leaveSectionExport)
        }
        Section {
            confirm
        } header: {
            Text(.leaveSectionConfirm)
        }
    }

    /// Lines of text, each as a bullet, in one row.
    private func bullets(_ lines: [Text]) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(verbatim: "•")
                    line
                }
            }
        }
        .padding(.vertical, 4)
    }

    private var places: some View {
        let lines = LeaveTexts.shared.places(places: state.places)
        return VStack(alignment: .leading, spacing: 4) {
            if state.places is SharedPlacesSummary.Loading {
                HStack(spacing: 12) {
                    ProgressView()
                    ForEach(Array(lines.enumerated()), id: \.offset) { _, line in Text(line) }
                }
            } else {
                if let failed = state.places as? SharedPlacesSummary.Failed {
                    ErrorMessage(failed.error, layout: .row)
                }
                ForEach(Array(lines.enumerated()), id: \.offset) { _, line in Text(line) }
            }
        }
        .padding(.vertical, 4)
    }

    private var export: some View {
        VStack(alignment: .leading, spacing: 8) {
            if state.export == .Running {
                HStack(spacing: 12) {
                    ProgressView()
                    Text(LeaveTexts.shared.exportLine(export: state.export))
                }
            } else {
                Text(LeaveTexts.shared.exportLine(export: state.export))
            }
            ForEach(Array(LeaveTexts.shared.exportControls(state: state).enumerated()), id: \.offset) { _, control in
                ExportControlButton(control: control) {
                    switch control.action {
                    case .Export: state.eventSink(LeaveEvent.Export.shared)
                    case .Cancel: state.eventSink(LeaveEvent.CancelExport.shared)
                    case .Save: state.eventSink(LeaveEvent.SaveExport.shared)
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }

    /// Signing in again, when the grant can't delete spaces; otherwise the handle to type and Leave, which is on only once it matches.
    private var confirm: some View {
        VStack(alignment: .leading, spacing: 12) {
            if state.needsSignInAgain {
                Text(.leaveSignInAgain)
                AppButton(.actionSignInAgain) { state.eventSink(LeaveEvent.SignInAgain.shared) }
                    .disabled(state.starting)
            } else {
                AppTextField(
                    label: .leaveConfirmLabel(state.handle),
                    model: typed,
                    state: state.confirmHandle,
                    options: TextFieldOptions(keyboard: .URL, capitalization: .never, autocorrection: false, submitLabel: .done)
                )
                .disabled(state.starting)
            }
            if let error = state.error {
                ErrorMessage(error, layout: .row)
            }
            if state.starting {
                ProgressView()
            } else if !state.needsSignInAgain {
                Button(role: .destructive) {
                    state.eventSink(LeaveEvent.Leave.shared)
                } label: {
                    Text(.leaveConfirm).frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                // Asked with the text as typed, which reaches the state a keystroke later.
                .disabled(!state.canLeave(typed: typed.text))
            }
            AppSecondaryButton(.actionCancel) { state.eventSink(LeaveEvent.Back.shared) }
                .disabled(state.starting)
        }
        .padding(.vertical, 4)
    }

    /// A running leave: its step, or the refusal that stopped it; how many records are gone; until when the server asked it to wait;
    /// and to keep the app open, or Retry.
    private func progress(_ run: LeaveRun) -> some View {
        Section {
            VStack(alignment: .leading, spacing: 12) {
                if let error = run.error {
                    ErrorMessage(error, layout: .row)
                } else {
                    HStack(spacing: 12) {
                        ProgressView()
                        Text(LeaveTexts.shared.step(run: run)).font(.headline)
                    }
                }
                if let deleted = LeaveTexts.shared.recordsDeleted(run: run) {
                    Text(deleted)
                }
                if let paused = LeaveTexts.shared.paused(run: run) {
                    Text(paused)
                }
                Text(LeaveTexts.shared.runNote(run: run))
                if run.error != nil {
                    AppButton(.actionRetry) { state.eventSink(LeaveEvent.Retry.shared) }
                }
            }
            .padding(.vertical, 4)
        }
    }
}

#if DEBUG
    #Preview("Leave") {
        SamplePages(Samples.leave) { state in NavigationStack { LeaveView(state: state) } }
    }

    #Preview("Leave beside the list", traits: .landscapeLeft) {
        NavigationStack {
            SettingsColumns(
                list: {
                    SettingsView(state: Samples.settings["loaded"]!)
                        .environment(\.settingsLayout, .beside(SettingsRows.account))
                },
                page: LeaveView(state: Samples.leave["loaded"]!).environment(\.inPane, true).environment(\.paneRoot, false))
        }
    }
#endif
