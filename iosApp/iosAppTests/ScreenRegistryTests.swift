import SwiftUI
import Testing

@testable import ActivityTracker

/// Which screens are drawn natively, and that every other screen falls back to the shared UI (ui/ios-native-shell).
@MainActor
struct ScreenRegistryTests {
    private struct LoginScreen {}
    private struct LoginState { let handle: String }
    private struct OtherState {}

    private var registry: ScreenRegistry = {
        var registry = ScreenRegistry()
        registry.register(LoginScreen.self, LoginState.self) { state in Text(state.handle) }
        return registry
    }()

    @Test func aRegisteredScreenIsFoundByItsClassName() {
        #expect(registry.hasView(forScreenNamed: "LoginScreen"))
        #expect(registry.view(forScreenNamed: "LoginScreen", state: LoginState(handle: "alice.test")) != nil)
    }

    @Test func anyOtherScreenFallsBack() {
        #expect(!registry.hasView(forScreenNamed: "FeedScreen"))
        #expect(registry.view(forScreenNamed: "FeedScreen", state: LoginState(handle: "alice.test")) == nil)
    }

    @Test func aStateOfAnotherTypeDrawsNothing() {
        #expect(registry.view(forScreenNamed: "LoginScreen", state: OtherState()) == nil)
    }

    @Test func theAppDrawsSignInNativelyAndTheRestThroughTheSharedUi() {
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "LoginScreen"))
        #expect(!ScreenRegistry.app.hasView(forScreenNamed: "FeedScreen"))
    }

    @Test func theAppsEntriesAreNamedAsKotlinNamesTheirScreens() {
        // The registry takes a screen's name from its Swift type, and the host gives the Kotlin class's.
        #expect(String(describing: ActivityTracker.LoginScreen.self) == "LoginScreen")
    }
}
