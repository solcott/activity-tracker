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

    @Test func theAppDrawsSignInAndSettingsNativelyAndTheRestThroughTheSharedUi() {
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "LoginScreen"))
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "SettingsScreen"))
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "ActivityDefaultsScreen"))
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "SegmentsAndGroupsScreen"))
        #expect(!ScreenRegistry.app.hasView(forScreenNamed: "FeedScreen"))
        // The other settings pages are still the shared UI's, beside the native list or over it.
        for page in ["AccountScreen", "PrivacyZonesScreen", "TrainingZonesScreen", "ZonesScreen", "SportZonesScreen", "LeaveScreen"] {
            #expect(!ScreenRegistry.app.hasView(forScreenNamed: page))
        }
    }

    @Test func theAppsEntriesAreNamedAsKotlinNamesTheirScreens() {
        // The registry takes a screen's name from its Swift type, and the host gives the Kotlin class's.
        #expect(String(describing: ActivityTracker.LoginScreen.self) == "LoginScreen")
        #expect(String(describing: ActivityTracker.SettingsScreen.self) == "SettingsScreen")
        #expect(String(describing: ActivityTracker.ActivityDefaultsScreen.self) == "ActivityDefaultsScreen")
        #expect(String(describing: ActivityTracker.SegmentsAndGroupsScreen.self) == "SegmentsAndGroupsScreen")
    }
}
