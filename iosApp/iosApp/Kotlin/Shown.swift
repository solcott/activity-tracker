import SwiftUI

extension Binding where Value == Bool {
    /// A sheet's, alert's or dialog's `isPresented` that follows the presenter's state: shown while `isShown` (the state holds what it
    /// shows), and when the athlete dismisses it some other way than its buttons (a swipe down, a tap outside) it calls `onDismiss`, which
    /// tells the presenter, once, so the state lets it go.
    static func shown(_ isShown: Bool, onDismiss: @escaping () -> Void) -> Binding<Bool> {
        Binding(get: { isShown }, set: { if !$0 && isShown { onDismiss() } })
    }
}
