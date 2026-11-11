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
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "GearListScreen"))
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "GearCollectionScreen"))
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "IgnoredSensorsScreen"))
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "BlockListScreen"))
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "FollowListScreen"))
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "FollowRequestsScreen"))
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "ActivityInvitesScreen"))
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "AddPeopleScreen"))
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "AccountScreen"))
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "LeaveScreen"))
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "EditGearScreen"))
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "EditGearCollectionScreen"))
        #expect(!ScreenRegistry.app.hasView(forScreenNamed: "FeedScreen"))
        // The rest of what the lists open is still the shared UI's: the gear item page waits for the native activity card, and a
        // profile, which opens the follow lists, for the feed's.
        // Accepting an invite waits for the native route preview and visibility form.
        for screen in ["GearScreen", "ProfileScreen", "AcceptInviteScreen"] {
            #expect(!ScreenRegistry.app.hasView(forScreenNamed: screen))
        }
        // The other settings pages are still the shared UI's, beside the native list or over it.
        for page in ["PrivacyZonesScreen", "TrainingZonesScreen", "ZonesScreen", "SportZonesScreen", "LeftElsewhereScreen"] {
            #expect(!ScreenRegistry.app.hasView(forScreenNamed: page))
        }
    }

    @Test func theAppsEntriesAreNamedAsKotlinNamesTheirScreens() {
        // The registry takes a screen's name from its Swift type, and the host gives the Kotlin class's.
        #expect(String(describing: ActivityTracker.LoginScreen.self) == "LoginScreen")
        #expect(String(describing: ActivityTracker.SettingsScreen.self) == "SettingsScreen")
        #expect(String(describing: ActivityTracker.ActivityDefaultsScreen.self) == "ActivityDefaultsScreen")
        #expect(String(describing: ActivityTracker.SegmentsAndGroupsScreen.self) == "SegmentsAndGroupsScreen")
        #expect(String(describing: ActivityTracker.GearListScreen.self) == "GearListScreen")
        #expect(String(describing: ActivityTracker.GearCollectionScreen.self) == "GearCollectionScreen")
        #expect(String(describing: ActivityTracker.IgnoredSensorsScreen.self) == "IgnoredSensorsScreen")
        #expect(String(describing: ActivityTracker.BlockListScreen.self) == "BlockListScreen")
        #expect(String(describing: ActivityTracker.FollowListScreen.self) == "FollowListScreen")
        #expect(String(describing: ActivityTracker.FollowRequestsScreen.self) == "FollowRequestsScreen")
        #expect(String(describing: ActivityTracker.ActivityInvitesScreen.self) == "ActivityInvitesScreen")
        #expect(String(describing: ActivityTracker.AddPeopleScreen.self) == "AddPeopleScreen")
        #expect(String(describing: ActivityTracker.AccountScreen.self) == "AccountScreen")
        #expect(String(describing: ActivityTracker.LeaveScreen.self) == "LeaveScreen")
        #expect(String(describing: ActivityTracker.EditGearScreen.self) == "EditGearScreen")
        #expect(String(describing: ActivityTracker.EditGearCollectionScreen.self) == "EditGearCollectionScreen")
    }
}
