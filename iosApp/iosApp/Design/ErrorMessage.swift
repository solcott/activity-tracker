import SwiftUI

/// An error as every platform words it (ui/error-messages): what failed and, when the reason is one the athlete can act on or wait
/// out, why, in the error colour, with Retry when there is a way to try again. The wording is Kotlin's `ErrorTexts`.
struct ErrorMessage: View {
    /// Where Retry goes: under the text for a failure that has the screen to itself, beside it for one above content.
    enum Layout {
        case stacked
        case row
    }

    private let text: String
    private let layout: Layout
    private let retry: (() -> Void)?

    init(_ error: UiError, layout: Layout = .stacked, retry: (() -> Void)? = nil) {
        self.init(text: AppStrings.string(ErrorTexts.shared.of(error: error)), layout: layout, retry: retry)
    }

    /// For a failure whose text the screen has already built.
    init(text: String, layout: Layout = .stacked, retry: (() -> Void)? = nil) {
        self.text = text
        self.layout = layout
        self.retry = retry
    }

    var body: some View {
        switch layout {
        case .stacked:
            VStack(spacing: 8) {
                message.multilineTextAlignment(.center)
                retryButton
            }
        case .row:
            HStack(spacing: 8) {
                message.frame(maxWidth: .infinity, alignment: .leading)
                retryButton
            }
        }
    }

    private var message: some View {
        Text(verbatim: text).foregroundStyle(.red)
    }

    @ViewBuilder private var retryButton: some View {
        if let retry {
            AppTextButton(.actionRetry, action: retry)
        }
    }
}
