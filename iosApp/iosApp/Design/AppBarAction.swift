import SwiftUI

/// A page's own action in its bar (ui/ios-design-system): Save or Publish, in the bar's semibold weight as the system draws a
/// confirming action, held back while `enabled` is false. While `busy`, the system's spinner takes its place, for a page whose action
/// has no word for its running (Publish); one whose label Kotlin words for it ("Saving…") passes that label and leaves `busy` out.
/// `smallSpinner` draws that spinner at the small size, as a sheet's bar does (Report's Send, a quote's Post).
struct AppBarAction: View {
    private let title: Text
    private let busy: Bool
    private let smallSpinner: Bool
    private let enabled: Bool
    private let action: () -> Void

    init(
        _ title: LocalizedStringResource,
        busy: Bool = false,
        smallSpinner: Bool = false,
        enabled: Bool = true,
        action: @escaping () -> Void
    ) {
        self.init(text: Text(title), busy: busy, smallSpinner: smallSpinner, enabled: enabled, action: action)
    }

    /// An action whose label Kotlin chose (`LabelText.save(saving)`).
    init(
        _ title: any AppText,
        busy: Bool = false,
        smallSpinner: Bool = false,
        enabled: Bool = true,
        action: @escaping () -> Void
    ) {
        self.init(text: Text(title), busy: busy, smallSpinner: smallSpinner, enabled: enabled, action: action)
    }

    private init(text: Text, busy: Bool, smallSpinner: Bool, enabled: Bool, action: @escaping () -> Void) {
        title = text
        self.busy = busy
        self.smallSpinner = smallSpinner
        self.enabled = enabled
        self.action = action
    }

    var body: some View {
        if busy {
            AppProgress(small: smallSpinner)
        } else {
            Button(action: action) { title }
                .fontWeight(.semibold)
                .disabled(!enabled)
        }
    }
}
