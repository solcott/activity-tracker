import SwiftUI

/// The report sheet drawn natively (moderation/reports), from the report sub-screen's states the presenter host runs: the reasons as rows
/// with the chosen one checked, the note (required for Other, at most its limit), what moderators may see, and Cancel and Send in its own
/// bar, with the failure under the note; once sent, the thanks, Block offered when the author isn't blocked already, and Done. Every word
/// and condition is Kotlin's (`ReportTexts`, `ReportState`), shared with the Compose sheet.
///
/// A page shows it with `reporting(_:)`, which asks the host for the sub-screen.
struct ReportSheet: View {
    let state: ReportState
    @State private var note: TextFieldModel

    init(state: ReportState) {
        self.state = state
        _note = State(initialValue: TextFieldModel(state.note, rules: TextFieldRules(maxLength: Int(ReportTexts.shared.noteLimit))))
    }

    var body: some View {
        NavigationStack {
            Group {
                if state.sent {
                    thanks
                } else {
                    form
                }
            }
            .navigationTitle(Text(ReportTexts.shared.title(authorName: state.authorName)))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { bar }
        }
    }

    // MARK: Choosing

    private var form: some View {
        Form {
            Section {
                ForEach(ReportTexts.shared.reasons.elements(as: ReportReasonChoice.self), id: \.reason) { choice in
                    reasonRow(choice)
                }
            } header: {
                Text(ReportTexts.shared.reasonPrompt)
            }
            Section {
                AppTextField(label: ReportTexts.shared.note(state: state), model: note, state: state.note, layout: .multiline)
                    .disabled(state.sending)
            } footer: {
                VStack(alignment: .leading, spacing: 8) {
                    if let error = state.error {
                        ErrorMessage(error, layout: .row)
                    }
                    Text(ReportTexts.shared.privateNote)
                }
            }
        }
    }

    /// A reason, checked once chosen; a tap chooses it, as the shared sheet's chip does.
    private func reasonRow(_ choice: ReportReasonChoice) -> some View {
        let chosen = state.chose(choice: choice)
        return Button { send(ReportEvent.PickReason(reason: choice.reason)) } label: {
            HStack {
                Text(choice.label).foregroundStyle(Color.primary)
                Spacer()
                if chosen {
                    Image(systemName: "checkmark").font(.body.weight(.semibold)).foregroundStyle(Color.accentColor)
                }
            }
            .contentShape(Rectangle())
        }
        .disabled(state.sending)
        .accessibilityAddTraits(chosen ? .isSelected : [])
    }

    @ToolbarContentBuilder private var bar: some ToolbarContent {
        if !state.sent {
            ToolbarItem(placement: .cancellationAction) {
                Button(.actionCancel) { send(ReportEvent.Close.shared) }
            }
            ToolbarItem(placement: .confirmationAction) {
                if state.sending {
                    AppProgress(small: true)
                } else {
                    Button { send(ReportEvent.Send.shared) } label: { Text(ReportTexts.shared.send) }
                        .disabled(!state.canSend)
                }
            }
        }
    }

    // MARK: Sent

    private var thanks: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(ReportTexts.shared.thanksTitle).font(.title2.weight(.semibold))
            Text(ReportTexts.shared.thanksMessage).foregroundStyle(.secondary)
            Spacer().frame(height: 8)
            if state.offerBlock {
                AppSecondaryButton(ReportTexts.shared.blockOffer(authorName: state.authorName)) { send(ReportEvent.Block.shared) }
                    .frame(maxWidth: .infinity)
            }
            AppButton(.actionDone) { send(ReportEvent.Close.shared) }.frame(maxWidth: .infinity)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }

    private func send(_ event: any ReportEvent) {
        state.eventSink(event)
    }
}

extension View {
    /// A page's `Reporting` drawn natively (moderation/reports): the report sheet while it is open, run by the presenter host as the page's
    /// sub-screen, and the Block confirmation once its offer is taken. Any native page carrying `Reporting` adds it (the profile, a post's
    /// page or card), and opens the sheet by sending `ReportingEvent.Open`, which Kotlin builds.
    func reporting(_ reporting: Reporting) -> some View {
        modifier(ReportingModifier(reporting: reporting))
    }
}

/// Shows a `Reporting`'s sheet and confirmation. The sheet keeps drawing the screen it opened with while it slides away after the page
/// cleared it, and once it has gone the sub-screen is closed in the host, so the next one opens afresh (dev/presenter-host). The Block
/// confirmation waits for the sheet to go: an alert doesn't present over a sheet that is still dismissing.
private struct ReportingModifier: ViewModifier {
    let reporting: Reporting
    @Environment(\.hostRecord) private var record
    /// The screen the sheet shows, from when the page opens it until the sheet has gone.
    @State private var shown: ReportScreen?

    func body(content: Content) -> some View {
        content
            .onChange(of: reporting.sheet != nil, initial: true) {
                if let sheet = reporting.sheet { shown = sheet }
            }
            .sheet(isPresented: presented, onDismiss: closed) {
                if let record, let screen = shown ?? reporting.sheet {
                    SubScreenView(record, screen: screen, outerEvent: outer) { (state: ReportState) in
                        ReportSheet(state: state)
                    }
                }
            }
            .alert(
                Text(reporting.confirmBlock.map { ReportTexts.shared.blockConfirmTitle(offer: $0) } ?? ReportTexts.shared.block),
                isPresented: confirming
            ) {
                Button(role: .destructive) { reporting.eventSink(ReportingEvent.ConfirmBlock(confirmed: true)) } label: {
                    Text(ReportTexts.shared.block)
                }
                Button(.actionCancel, role: .cancel) { reporting.eventSink(ReportingEvent.ConfirmBlock(confirmed: false)) }
            } message: {
                Text(ReportTexts.shared.blockConfirmMessage)
            }
    }

    /// Open while the page holds a sheet, in a page the host draws. A swipe down tells the page the sheet closed, as the shared sheet does.
    private var presented: Binding<Bool> {
        Binding(
            get: { record != nil && reporting.sheet != nil },
            set: { isShown in
                if !isShown && reporting.sheet != nil {
                    reporting.eventSink(ReportingEvent.Sheet(event: ReportOuterEvent.Closed.shared))
                }
            })
    }

    private var confirming: Binding<Bool> {
        Binding(
            get: { reporting.confirmBlock != nil && shown == nil },
            set: { isShown in
                // A tap on either button answers already; this is for the alert going some other way.
                if !isShown && reporting.confirmBlock != nil {
                    reporting.eventSink(ReportingEvent.ConfirmBlock(confirmed: false))
                }
            })
    }

    private func outer(_ event: any SubCircuitOuterEvent) {
        if let event = event as? ReportOuterEvent {
            reporting.eventSink(ReportingEvent.Sheet(event: event))
        }
    }

    private func closed() {
        if let record, let shown {
            record.sub(screen: shown, outerEventSink: { _ in }).close()
        }
        shown = nil
    }
}

#if DEBUG
    #Preview("Report sheet") {
        SamplePages(Samples.report) { state in ReportSheet(state: state) }
    }
#endif
