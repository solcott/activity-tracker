import Testing
import UIKit

@testable import ActivityTracker

/// One Compose view controller per fallback screen for as long as its record is in the stack: what keeps a covered screen as it was
/// left (ui/ios-native-shell).
@MainActor
struct FallbackControllersTests {
    private let controllers = FallbackControllers()

    @Test func aRecordKeepsItsControllerWhileItIsInTheStack() {
        var made = 0
        let first = controllers.controller(forKey: "activity") { made += 1; return UIViewController() }
        let again = controllers.controller(forKey: "activity") { made += 1; return UIViewController() }

        #expect(first === again)
        #expect(made == 1)
    }

    @Test func aRecordThatLeftTheStackGetsANewControllerIfItsKeyIsEverAskedForAgain() {
        let first = controllers.controller(forKey: "activity") { UIViewController() }

        controllers.keep(only: ["feed"])
        let later = controllers.controller(forKey: "activity") { UIViewController() }

        #expect(first !== later)
    }

    @Test func recordsStillInTheStackKeepTheirs() {
        let feed = controllers.controller(forKey: "feed") { UIViewController() }
        _ = controllers.controller(forKey: "activity") { UIViewController() }

        controllers.keep(only: ["feed"])

        #expect(controllers.controller(forKey: "feed") { UIViewController() } === feed)
    }

    @Test func thePageBesideARecordKeepsItsControllerWhileThatRecordIsInTheStack() {
        // Activity defaults shows beside Settings without being on the stack, so the stack's own keys don't name it.
        let defaults = controllers.controller(forKey: "defaults") { UIViewController() }

        controllers.keep(only: StackPath.drawable([("profile", nil), ("settings", "defaults")]))
        #expect(controllers.controller(forKey: "defaults") { UIViewController() } === defaults)

        controllers.keep(only: StackPath.drawable([("profile", nil)]))
        #expect(controllers.controller(forKey: "defaults") { UIViewController() } !== defaults)
    }
}

/// When a back swipe may begin on a screen whose navigation bar is hidden.
struct BackSwipeTests {
    @Test func itBeginsOnlyWithAScreenToGoBackToAndNoTransitionRunning() {
        #expect(BackSwipe.mayBegin(screens: 2, inTransition: false))
        // On a tab's first screen it would freeze the interface.
        #expect(!BackSwipe.mayBegin(screens: 1, inTransition: false))
        #expect(!BackSwipe.mayBegin(screens: 3, inTransition: true))
    }
}
