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
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "UploadsScreen"))
        for screen in ["StarredSegmentsScreen", "SegmentLeaderboardScreen", "SegmentMayorsScreen", "DiveSiteRegularsScreen"] {
            #expect(ScreenRegistry.app.hasView(forScreenNamed: screen))
        }
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "EditProfileScreen"))
        // The zones pages (ios-native-zones-pages).
        for page in ["TrainingZonesScreen", "ZonesScreen", "SportZonesScreen", "PrivacyZonesScreen"] {
            #expect(ScreenRegistry.app.hasView(forScreenNamed: page))
        }
        // A batch import's page, with the native activity card (ios-native-activity-card).
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "ImportBatchScreen"))
        // The gear item page, with its activities as native cards (ios-native-gear-item).
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "GearScreen"))
        // The feed, the Feed tab's root (ios-native-feed).
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "FeedScreen"))
        // The profile (ios-native-profile), which opens the native follow lists, requests and edit profile.
        #expect(ScreenRegistry.app.hasView(forScreenNamed: "ProfileScreen"))
        // A post's page and its quotes, with the native post card (ios-native-post-card).
        for screen in ["PostPageScreen", "PostQuotesScreen"] {
            #expect(ScreenRegistry.app.hasView(forScreenNamed: screen))
        }
        // The Search tab's root and the choice after leaving on another device (ios-native-search).
        for screen in ["AthleteSearchScreen", "LeftElsewhereScreen"] {
            #expect(ScreenRegistry.app.hasView(forScreenNamed: screen))
        }
        // The rest of what the lists open is still the shared UI's.
        // Accepting an invite waits for the native route preview and visibility form.
        // The segment and dive site pages, their map and segment browse draw maps or charts, so they wait for native ones (the native
        // Search tab embeds the shared browse screen).
        for screen in [
            "AcceptInviteScreen", "SegmentScreen", "SegmentMapScreen", "SegmentBrowseScreen",
            "DiveSiteScreen",
        ] {
            #expect(!ScreenRegistry.app.hasView(forScreenNamed: screen))
        }
        // The other settings pages are still the shared UI's, beside the native list or over it: threshold history draws charts.
        #expect(!ScreenRegistry.app.hasView(forScreenNamed: "ThresholdHistoryScreen"))
    }

    @Test func theAppsEntriesAreNamedAsKotlinNamesTheirScreens() {
        // The registry takes a screen's name from its Swift type, and the host gives the Kotlin class's.
        #expect(String(describing: ActivityTracker.LoginScreen.self) == "LoginScreen")
        #expect(String(describing: ActivityTracker.SettingsScreen.self) == "SettingsScreen")
        #expect(String(describing: ActivityTracker.ActivityDefaultsScreen.self) == "ActivityDefaultsScreen")
        #expect(String(describing: ActivityTracker.SegmentsAndGroupsScreen.self) == "SegmentsAndGroupsScreen")
        #expect(String(describing: ActivityTracker.GearListScreen.self) == "GearListScreen")
        #expect(String(describing: ActivityTracker.GearScreen.self) == "GearScreen")
        #expect(String(describing: ActivityTracker.PostPageScreen.self) == "PostPageScreen")
        #expect(String(describing: ActivityTracker.PostQuotesScreen.self) == "PostQuotesScreen")
        #expect(String(describing: ActivityTracker.GearCollectionScreen.self) == "GearCollectionScreen")
        #expect(String(describing: ActivityTracker.IgnoredSensorsScreen.self) == "IgnoredSensorsScreen")
        #expect(String(describing: ActivityTracker.BlockListScreen.self) == "BlockListScreen")
        #expect(String(describing: ActivityTracker.FollowListScreen.self) == "FollowListScreen")
        #expect(String(describing: ActivityTracker.FollowRequestsScreen.self) == "FollowRequestsScreen")
        #expect(String(describing: ActivityTracker.ActivityInvitesScreen.self) == "ActivityInvitesScreen")
        #expect(String(describing: ActivityTracker.AddPeopleScreen.self) == "AddPeopleScreen")
        #expect(String(describing: ActivityTracker.AccountScreen.self) == "AccountScreen")
        #expect(String(describing: ActivityTracker.LeaveScreen.self) == "LeaveScreen")
        #expect(String(describing: ActivityTracker.AthleteSearchScreen.self) == "AthleteSearchScreen")
        #expect(String(describing: ActivityTracker.LeftElsewhereScreen.self) == "LeftElsewhereScreen")
        #expect(String(describing: ActivityTracker.EditGearScreen.self) == "EditGearScreen")
        #expect(String(describing: ActivityTracker.EditGearCollectionScreen.self) == "EditGearCollectionScreen")
        #expect(String(describing: ActivityTracker.UploadsScreen.self) == "UploadsScreen")
        #expect(String(describing: ActivityTracker.StarredSegmentsScreen.self) == "StarredSegmentsScreen")
        #expect(String(describing: ActivityTracker.SegmentLeaderboardScreen.self) == "SegmentLeaderboardScreen")
        #expect(String(describing: ActivityTracker.SegmentMayorsScreen.self) == "SegmentMayorsScreen")
        #expect(String(describing: ActivityTracker.DiveSiteRegularsScreen.self) == "DiveSiteRegularsScreen")
        #expect(String(describing: ActivityTracker.EditProfileScreen.self) == "EditProfileScreen")
        #expect(String(describing: ActivityTracker.TrainingZonesScreen.self) == "TrainingZonesScreen")
        #expect(String(describing: ActivityTracker.ZonesScreen.self) == "ZonesScreen")
        #expect(String(describing: ActivityTracker.SportZonesScreen.self) == "SportZonesScreen")
        #expect(String(describing: ActivityTracker.PrivacyZonesScreen.self) == "PrivacyZonesScreen")
    }
}
