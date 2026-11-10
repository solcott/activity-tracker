import ObjectiveC
import UIKit

/// Swipe back from the leading edge on screens whose navigation bar is hidden.
///
/// A fallback screen draws its own Compose bar, so its destination hides the native one, and UIKit then switches the navigation
/// controller's pop gesture off. This puts it back, with the one rule that makes that safe: it may begin only when there is a screen to
/// go back to and no transition is running. The gesture beginning on a stack of one screen freezes the interface.
///
/// On iOS 18 Compose keeps following the touch while UIKit drives the pop, so a swipe that begins over a pager turns its page (the
/// roadmap's phase 2 probes). While the pop gesture runs, the fallback screen's own gesture recognisers are therefore switched off,
/// which cancels the touch Compose is following, and switched back on when it ends, completed or cancelled.
final class BackSwipe: NSObject, UIGestureRecognizerDelegate {
    private weak var navigationController: UINavigationController?
    private var paused: [UIGestureRecognizer] = []

    /// Turns the pop gesture on for `navigationController`, once: its screens, native or fallback, share the one recogniser.
    static func enable(on navigationController: UINavigationController) {
        guard objc_getAssociatedObject(navigationController, &key) == nil,
            let recognizer = navigationController.interactivePopGestureRecognizer
        else { return }
        let backSwipe = BackSwipe()
        backSwipe.navigationController = navigationController
        // The navigation controller keeps it alive: the recogniser's delegate is a weak reference.
        objc_setAssociatedObject(navigationController, &key, backSwipe, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        recognizer.delegate = backSwipe
        recognizer.addTarget(backSwipe, action: #selector(changed(_:)))
    }

    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard let navigationController, Self.mayBegin(screens: navigationController.viewControllers.count, inTransition: navigationController.transitionCoordinator != nil)
        else { return false }
        if let top = navigationController.topViewController {
            paused = Self.fallbackRecognizers(in: top).filter(\.isEnabled)
            paused.forEach { $0.isEnabled = false }
        }
        return true
    }

    /// Whether a back swipe may begin on a stack of `screens` screens.
    static func mayBegin(screens: Int, inTransition: Bool) -> Bool {
        screens > 1 && !inTransition
    }

    @objc private func changed(_ recognizer: UIGestureRecognizer) {
        guard [.ended, .cancelled, .failed].contains(recognizer.state) else { return }
        paused.forEach { $0.isEnabled = true }
        paused = []
    }

    /// The gesture recognisers of the fallback screens under `controller`: Compose's, and those of the native views it embeds. A native
    /// screen has none, so nothing of SwiftUI's is touched.
    private static func fallbackRecognizers(in controller: UIViewController) -> [UIGestureRecognizer] {
        let own = controller is FallbackContainerController ? recognizers(in: controller.view) : []
        return own + controller.children.flatMap { fallbackRecognizers(in: $0) }
    }

    private static func recognizers(in view: UIView) -> [UIGestureRecognizer] {
        (view.gestureRecognizers ?? []) + view.subviews.flatMap { recognizers(in: $0) }
    }
}

private nonisolated(unsafe) var key: UInt8 = 0
