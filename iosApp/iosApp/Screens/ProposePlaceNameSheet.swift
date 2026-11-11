import SwiftUI

/// The place-name sheet drawn natively (segments/segment-names, feed/posts), from the name sub-screen's states the presenter host runs:
/// what proposing shows others (or, for an edit, that saving clears the votes), the name with its invalid note, and Share to feed, with
/// its audience, why only Public may be offered and the post's text, then the failure; Cancel and Send (Save for an edit) in its own
/// bar. Every word and condition is Kotlin's (`ProposePlaceNameTexts`, `ProposePlaceNameState`), shared with the Compose sheet.
///
/// A page shows it with `placeNames(_:)`. No native page does yet: the segment and dive site pages are still the shared UI's.
struct ProposePlaceNameSheet: View {
    let state: ProposePlaceNameState
    @State private var name: TextFieldModel
    @State private var shareText: TextFieldModel

    init(state: ProposePlaceNameState) {
        self.state = state
        // Neither field refuses a long edit, as the shared sheet's don't: the notes under them say why it can't be sent.
        _name = State(initialValue: TextFieldModel(state.name))
        _shareText = State(initialValue: TextFieldModel(state.shareText))
    }

    var body: some View {
        NavigationStack {
            form
                .navigationTitle(Text(ProposePlaceNameTexts.shared.title(state: state)))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(.actionCancel) { send(ProposePlaceNameEvent.Cancel.shared) }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        if state.sending {
                            AppProgress(small: true)
                        } else {
                            Button { send(ProposePlaceNameEvent.Send.shared) } label: {
                                Text(ProposePlaceNameTexts.shared.confirm(state: state))
                            }
                            .disabled(!state.canSend)
                        }
                    }
                }
        }
    }

    private var form: some View {
        let texts = ProposePlaceNameTexts.shared
        return Form {
            Section {
                AppTextField(
                    label: texts.field, model: name, state: state.name, layout: .row,
                    options: TextFieldOptions(capitalization: .words, submitLabel: .done),
                    supportingText: state.showsInvalid ? AppStrings.string(texts.invalid) : nil, isError: state.showsInvalid)
                    .disabled(state.sending)
            } footer: {
                // What proposing shows others, or that saving clears the votes: said before it's sent, as the shared sheet does above it.
                Text(texts.lead(state: state))
            }
            share
            if let error = state.error {
                ErrorMessage(error, layout: .row).listStatusRow()
            }
        }
    }

    /// Share to feed: the switch, then with it on the audience, why only Public is offered, and the post's text.
    @ViewBuilder private var share: some View {
        let texts = ProposePlaceNameTexts.shared
        Section {
            Toggle(isOn: Binding(get: { state.sharing }, set: { send(ProposePlaceNameEvent.SharingChanged(sharing: $0)) })) {
                Text(texts.shareToFeed)
                Text(texts.shareToFeedDetail)
            }
            if state.sharing {
                AppPickerRow(
                    name: AppStrings.string(texts.audience),
                    selection: Binding(get: { state.audience }, set: { send(ProposePlaceNameEvent.AudienceChanged(audience: $0)) }),
                    options: state.audiences.elements(as: String.self),
                    optionLabel: { AppStrings.string(texts.audience(visibility: $0)) }
                ) {
                    Text(texts.audience)
                }
                AppTextField(
                    label: texts.shareText, model: shareText, state: state.shareText, layout: .multiline,
                    supportingText: state.shareTextValid ? nil : AppStrings.string(texts.shareTextTooLong),
                    isError: !state.shareTextValid)
            }
        } footer: {
            if state.sharing, let limit = texts.shareLimit(state: state) {
                Text(limit)
            }
        }
        .disabled(!state.shareEnabled)
    }

    private func send(_ event: any ProposePlaceNameEvent) {
        state.eventSink(event)
    }
}

extension View {
    /// A place's `PlaceNameFields` drawn natively (segments/segment-names): the name sheet while it is open, run by the presenter host as
    /// the page's sub-screen, and the confirmation before the viewer's own proposal is deleted. A native segment or dive site page adds it.
    func placeNames(_ fields: PlaceNameFields) -> some View {
        modifier(PlaceNamesModifier(fields: fields))
    }
}

/// Shows `PlaceNameFields`' sheet and delete confirmation, the sheet kept and closed in the host as `reporting(_:)` keeps and closes its.
private struct PlaceNamesModifier: ViewModifier {
    let fields: PlaceNameFields
    @Environment(\.hostRecord) private var record
    @State private var shown: ProposePlaceNameScreen?

    func body(content: Content) -> some View {
        content
            .onChange(of: fields.sheet != nil, initial: true) {
                if let sheet = fields.sheet { shown = sheet }
            }
            .sheet(isPresented: presented, onDismiss: closed) {
                if let record, let screen = shown ?? fields.sheet {
                    SubScreenView(record, screen: screen, outerEvent: outer) { (state: ProposePlaceNameState) in
                        ProposePlaceNameSheet(state: state)
                    }
                }
            }
            .alert(Text(ProposePlaceNameTexts.shared.deleteConfirmTitle), isPresented: confirming) {
                Button(role: .destructive) { fields.eventSink(PlaceNameEvent.ConfirmDelete(confirmed: true)) } label: {
                    Text(ProposePlaceNameTexts.shared.delete)
                }
                Button(.actionCancel, role: .cancel) { fields.eventSink(PlaceNameEvent.ConfirmDelete(confirmed: false)) }
            } message: {
                Text(ProposePlaceNameTexts.shared.deleteConfirmMessage)
            }
    }

    private var presented: Binding<Bool> {
        Binding(
            get: { record != nil && fields.sheet != nil },
            set: { isShown in
                if !isShown && fields.sheet != nil {
                    fields.eventSink(PlaceNameEvent.Sheet(event: ProposePlaceNameOuterEvent.Closed.shared))
                }
            })
    }

    private var confirming: Binding<Bool> {
        Binding(
            get: { fields.confirmDelete != nil && shown == nil },
            set: { isShown in
                if !isShown && fields.confirmDelete != nil {
                    fields.eventSink(PlaceNameEvent.ConfirmDelete(confirmed: false))
                }
            })
    }

    private func outer(_ event: any SubCircuitOuterEvent) {
        if let event = event as? ProposePlaceNameOuterEvent {
            fields.eventSink(PlaceNameEvent.Sheet(event: event))
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
    #Preview("Place-name sheet") {
        SamplePages(Samples.proposePlaceName) { state in ProposePlaceNameSheet(state: state) }
    }
#endif
