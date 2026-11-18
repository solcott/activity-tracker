import SwiftUI

/// A page's own action in its bar (ui/ios-design-system): Save or Publish, in the bar's semibold weight as the system draws a
/// confirming action, held back while `enabled` is false. While `busy`, the system's spinner takes its place, for a page whose action
/// has no word for its running (Publish); one whose label Kotlin words for it ("Saving…") passes that label and leaves `busy` out.
struct AppBarAction: View {
    private let title: Text
    private let busy: Bool
    private let enabled: Bool
    private let action: () -> Void

    init(_ title: LocalizedStringResource, busy: Bool = false, enabled: Bool = true, action: @escaping () -> Void) {
        self.init(text: Text(title), busy: busy, enabled: enabled, action: action)
    }

    /// An action whose label Kotlin chose (`LabelText.save(saving)`).
    init(_ title: any AppText, busy: Bool = false, enabled: Bool = true, action: @escaping () -> Void) {
        self.init(text: Text(title), busy: busy, enabled: enabled, action: action)
    }

    private init(text: Text, busy: Bool, enabled: Bool, action: @escaping () -> Void) {
        title = text
        self.busy = busy
        self.enabled = enabled
        self.action = action
    }

    var body: some View {
        if busy {
            AppProgress()
        } else {
            Button(action: action) { title }
                .fontWeight(.semibold)
                .disabled(!enabled)
        }
    }
}
