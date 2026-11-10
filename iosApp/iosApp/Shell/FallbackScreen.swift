import IosShell
import SwiftUI
import UIKit

/// A screen drawn by the shared Compose UI, for one with no native view: a Compose view controller that draws the states the host
/// publishes for the record. It fills the window and draws its own navigation bar, as it did when Compose drew the whole app.
struct FallbackScreen: UIViewControllerRepresentable {
    let record: HostRecord
    let controllers: FallbackControllers

    func makeUIViewController(context: Context) -> FallbackContainerController {
        let content = controllers.controller(forKey: record.key) { IosApp.shared.screenViewController(record: record) }
        return FallbackContainerController(content: content)
    }

    func updateUIViewController(_ container: FallbackContainerController, context: Context) {}
}

/// The Compose view controllers of one stack's fallback screens, one per record for as long as the record is in the stack. Keeping the
/// controller keeps the screen's composition alive while it is covered, which is what holds its scroll position and the state of the
/// sub-screens it embeds; SwiftUI is free to rebuild the representable around it.
final class FallbackControllers {
    private var controllers: [String: UIViewController] = [:]

    /// The controller of the record with this key, made with `make` the first time it is asked for.
    func controller(forKey key: String, make: () -> UIViewController) -> UIViewController {
        if let known = controllers[key] { return known }
        let made = make()
        controllers[key] = made
        return made
    }

    /// Lets go of every controller whose record has left the stack. A screen still sliding away keeps its own until it is gone.
    func keep(only keys: [String]) {
        controllers = controllers.filter { keys.contains($0.key) }
    }
}

/// Holds a fallback screen's Compose view controller, and lets the athlete swipe back from it.
final class FallbackContainerController: UIViewController {
    private let content: UIViewController

    init(content: UIViewController) {
        self.content = content
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("FallbackContainerController is made in code")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        // The controller comes from the stack's cache, so an earlier container may still hold it.
        content.willMove(toParent: nil)
        content.view.removeFromSuperview()
        content.removeFromParent()
        addChild(content)
        content.view.frame = view.bounds
        content.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(content.view)
        content.didMove(toParent: self)
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        if let navigationController { BackSwipe.enable(on: navigationController) }
    }
}
