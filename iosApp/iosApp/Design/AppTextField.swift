import SwiftUI

/// How a field's keyboard behaves.
struct TextFieldOptions {
    var keyboard: UIKeyboardType = .default
    var capitalization: TextInputAutocapitalization = .sentences
    var autocorrection = true
    var contentType: UITextContentType?
    var submitLabel: SubmitLabel = .done
}

/// A one-line text field over a Kotlin text field state, with its label above it and, when there is some, the text under it that
/// explains it (dev/ios-swift-interop, ui/ios-design-system). A field the screen marks as wrong shows that text in the error colour.
///
/// The screen holds the model, so the rest of it can read the text as it is typed (a button enabled only once there is some). The
/// field keeps the model on the state it is given: when a presenter replaces the state object, the model moves to the new one.
struct AppTextField: View {
    let label: LocalizedStringResource
    let model: TextFieldModel
    let state: KotlinTextFieldState
    var options = TextFieldOptions()
    var supportingText: String?
    var isError = false
    var onSubmit: () -> Void = {}

    var body: some View {
        // Read here so a refused edit redraws the field, which otherwise keeps showing what was typed.
        let _ = model.refusals
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption)
                .foregroundStyle(isError ? AnyShapeStyle(.red) : AnyShapeStyle(.secondary))
                .padding(.leading, 4)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityHidden(true)
            TextField(text: Binding(get: { model.text }, set: { model.edit($0) }), prompt: Text(verbatim: "")) { Text(label) }
                .keyboardType(options.keyboard)
                .textInputAutocapitalization(options.capitalization)
                .autocorrectionDisabled(!options.autocorrection)
                .textContentType(options.contentType)
                .submitLabel(options.submitLabel)
                .onSubmit(onSubmit)
                // Whatever the screen around it does: a field's text starts at its leading edge.
                .multilineTextAlignment(.leading)
                .padding(.horizontal, 12)
                .padding(.vertical, 11)
                .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 10))
                .overlay {
                    if isError { RoundedRectangle(cornerRadius: 10).strokeBorder(.red, lineWidth: 1) }
                }
            if let supportingText {
                Text(supportingText)
                    .font(.caption)
                    .foregroundStyle(isError ? AnyShapeStyle(.red) : AnyShapeStyle(.secondary))
                    .padding(.leading, 4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .multilineTextAlignment(.leading)
            }
        }
        .task(id: ObjectIdentifier(state)) {
            model.bind(to: state)
            await model.follow(state.values.asAsyncSequence())
        }
    }
}
