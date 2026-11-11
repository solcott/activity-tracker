import SwiftUI

extension View {
    /// A host's quote sheet and delete confirmation (feed/quote-posts), from its `QuoteFields`, a plain part of its state: a sheet while a
    /// post is being quoted, sending `Cancel` when swiped away, and an alert while Delete asks. Every word is `QuoteSheetTexts`'.
    func quoteSheet(_ fields: QuoteFields?) -> some View {
        modifier(QuoteSheetModifier(fields: fields))
    }
}

private struct QuoteSheetModifier: ViewModifier {
    /// Nil before the host's content loads: nothing to show.
    let fields: QuoteFields?

    func body(content: Content) -> some View {
        if let fields {
            sheets(content, fields)
        } else {
            content
        }
    }

    private func sheets(_ content: Content, _ fields: QuoteFields) -> some View {
        content
            .sheet(
                isPresented: Binding(
                    get: { fields.quoting != nil },
                    set: { if !$0 && fields.quoting != nil { fields.eventSink(QuoteEvent.Cancel.shared) } })
            ) {
                if fields.quoting != nil {
                    QuoteSheet(fields: fields)
                }
            }
            .alert(
                Text(QuoteSheetTexts.shared.deleteTitle),
                isPresented: Binding(
                    get: { fields.confirmDelete != nil },
                    set: { if !$0 && fields.confirmDelete != nil { fields.eventSink(QuoteEvent.ConfirmDelete(confirmed: false)) } })
            ) {
                Button(role: .destructive) { fields.eventSink(QuoteEvent.ConfirmDelete(confirmed: true)) } label: {
                    Text(QuoteSheetTexts.shared.deleteConfirm)
                }
                Button(role: .cancel) { fields.eventSink(QuoteEvent.ConfirmDelete(confirmed: false)) } label: {
                    Text(QuoteSheetTexts.shared.cancel)
                }
            } message: {
                Text(QuoteSheetTexts.shared.deleteMessage)
            }
    }
}

/// The quote sheet's content: the post being quoted, the athlete's words, who may see the quote, and why it couldn't be posted, with
/// Cancel and Post in its bar.
struct QuoteSheet: View {
    let fields: QuoteFields
    @State private var text: TextFieldModel

    init(fields: QuoteFields) {
        self.fields = fields
        _text = State(initialValue: TextFieldModel(fields.text))
    }

    var body: some View {
        // Read so Post and the too-long note follow each keystroke: both ask Kotlin, which reads the same text.
        let _ = text.text
        NavigationStack {
            Form {
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(QuoteSheetTexts.shared.quotedAuthor(fields: fields)).font(.footnote).foregroundStyle(.secondary)
                        if let quoted = fields.quoting?.text {
                            Text(verbatim: quoted).font(.subheadline).lineLimit(3)
                        }
                    }
                }
                Section {
                    AppTextField(
                        label: QuoteSheetTexts.shared.field, model: text, state: fields.text, layout: .multiline,
                        supportingText: fields.textValid ? nil : AppStrings.string(QuoteSheetTexts.shared.tooLong),
                        isError: !fields.textValid)
                    .disabled(fields.posting)
                    AppPickerRow(
                        AppStrings.string(QuoteSheetTexts.shared.audienceName),
                        selection: Binding(
                            get: { fields.audience }, set: { fields.eventSink(QuoteEvent.AudienceChanged(audience: $0)) }),
                        options: fields.audiences.elements(as: String.self),
                        optionLabel: { AppStrings.string(QuoteSheetTexts.shared.audience(visibility: $0)) }
                    )
                    .disabled(fields.posting)
                } footer: {
                    if fields.publicOnly {
                        Text(QuoteSheetTexts.shared.publicOnly)
                    }
                }
                if let error = fields.error {
                    ErrorMessage(error, layout: .row).listStatusRow()
                }
            }
            .navigationTitle(Text(QuoteSheetTexts.shared.title))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button { fields.eventSink(QuoteEvent.Cancel.shared) } label: { Text(QuoteSheetTexts.shared.cancel) }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if fields.posting {
                        AppProgress(small: true)
                    } else {
                        Button { fields.eventSink(QuoteEvent.Post.shared) } label: { Text(QuoteSheetTexts.shared.post) }
                            .disabled(!fields.canPost)
                    }
                }
            }
        }
    }
}

#if DEBUG
    #Preview("Quote sheet") {
        SamplePages(Samples.quoteSheet) { fields in QuoteSheet(fields: fields) }
    }
#endif
