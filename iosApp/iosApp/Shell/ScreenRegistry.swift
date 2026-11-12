import SwiftUI

/// Which screens have a native view. A screen with no entry is drawn by the shared Compose UI (`FallbackScreen`), so every screen works
/// whether or not it has been written in SwiftUI, and writing one is a view and an entry here.
///
/// A screen is found by its class name (`HostRecord.screenName`), never by reading the Kotlin screen object. Swift export hands Swift an
/// interface-typed value through the Swift class generated for its Kotlin class, and Xcode links only the generated modules this
/// target's Swift refers to: reading `HostRecord.screen` for a screen no Swift code names stops the app. Registering a screen names its
/// type and its state's, which is what links their modules.
struct ScreenRegistry {
    private struct Entry {
        let screenName: String
        let view: (Any) -> AnyView?
    }

    private var entries: [Entry] = []

    /// Draws screens of type `screen` with `content`, from their state of type `state`. The Swift type's name is the Kotlin class's.
    mutating func register<S, State, Content: View>(
        _ screen: S.Type, _ state: State.Type, @ViewBuilder content: @escaping (State) -> Content
    ) {
        entries.append(
            Entry(
                screenName: String(describing: screen),
                view: { anyState in (anyState as? State).map { AnyView(content($0)) } }
            ))
    }

    func hasView(forScreenNamed name: String) -> Bool {
        entries.contains { $0.screenName == name }
    }

    /// The native view of the screen named `name` showing `state`, or nil when the screen has none or the state isn't the type its
    /// view takes.
    func view(forScreenNamed name: String, state: Any) -> AnyView? {
        entries.first { $0.screenName == name }?.view(state)
    }
}

extension ScreenRegistry {
    /// The app's native screens.
    static let app: ScreenRegistry = {
        var registry = ScreenRegistry()
        registry.register(LoginScreen.self, LoginState.self) { state in LoginView(state: state) }
        registry.register(SettingsScreen.self, SettingsState.self) { state in SettingsView(state: state) }
        registry.register(ActivityDefaultsScreen.self, ActivityDefaultsState.self) { state in ActivityDefaultsView(state: state) }
        registry.register(SegmentsAndGroupsScreen.self, SegmentsAndGroupsState.self) { state in SegmentsAndGroupsView(state: state) }
        registry.register(GearListScreen.self, GearListState.self) { state in GearListView(state: state) }
        registry.register(GearCollectionScreen.self, GearCollectionState.self) { state in GearCollectionView(state: state) }
        registry.register(IgnoredSensorsScreen.self, IgnoredSensorsState.self) { state in IgnoredSensorsView(state: state) }
        registry.register(BlockListScreen.self, BlockListState.self) { state in BlockListView(state: state) }
        registry.register(FollowListScreen.self, FollowListState.self) { state in FollowListView(state: state) }
        registry.register(FollowRequestsScreen.self, FollowRequestsState.self) { state in FollowRequestsView(state: state) }
        registry.register(ActivityInvitesScreen.self, ActivityInvitesState.self) { state in ActivityInvitesView(state: state) }
        registry.register(AddPeopleScreen.self, AddPeopleState.self) { state in AddPeopleView(state: state) }
        registry.register(AccountScreen.self, AccountState.self) { state in AccountView(state: state) }
        registry.register(LeaveScreen.self, LeaveState.self) { state in LeaveView(state: state) }
        // The gear editors (ios-native-gear-editors).
        registry.register(EditGearScreen.self, EditGearState.self) { state in EditGearView(state: state) }
        registry.register(EditGearCollectionScreen.self, EditGearCollectionState.self) { state in
            EditGearCollectionView(state: state)
        }
        // The upload queue (data/upload-queue), pushed over the feed.
        registry.register(UploadsScreen.self, UploadsState.self) { state in UploadsView(state: state) }
        // The segment lists (ios-native-segment-lists): Starred segments over the profile, a segment's leaderboard and Mayors over its
        // page, and a dive site's Regulars over its page.
        registry.register(StarredSegmentsScreen.self, StarredSegmentsState.self) { state in StarredSegmentsView(state: state) }
        registry.register(SegmentLeaderboardScreen.self, SegmentLeaderboardState.self) { state in SegmentLeaderboardView(state: state) }
        registry.register(SegmentMayorsScreen.self, SegmentMayorsState.self) { state in SegmentMayorsView(state: state) }
        registry.register(DiveSiteRegularsScreen.self, DiveSiteRegularsState.self) { state in DiveSiteRegularsView(state: state) }
        // Edit profile (profile/private-details), pushed over the athlete's own profile.
        registry.register(EditProfileScreen.self, EditProfileState.self) { state in EditProfileView(state: state) }
        // The zones pages (ios-native-zones-pages).
        registry.register(TrainingZonesScreen.self, TrainingZonesState.self) { state in TrainingZonesView(state: state) }
        registry.register(ZonesScreen.self, ZonesState.self) { state in ZonesView(state: state) }
        registry.register(SportZonesScreen.self, SportZonesState.self) { state in SportZonesView(state: state) }
        registry.register(PrivacyZonesScreen.self, PrivacyZonesState.self) { state in PrivacyZonesView(state: state) }
        // A batch import's page (feed/import-cards), pushed over the feed: the first drawn with the native activity card.
        registry.register(ImportBatchScreen.self, ImportBatchState.self) { state in ImportBatchView(state: state) }
        // The gear item page (ios-native-gear-item), opened from Gear, a collection or an activity's gear.
        registry.register(GearScreen.self, GearState.self) { state in GearItemView(state: state) }

        // The profile (ios-native-profile), drawn with the native activity card; Report and Posts wait.
        registry.register(ProfileScreen.self, ProfileState.self) { state in ProfilePage(state: state) }

        // Posts (ios-native-post-card): a post's page and its quotes, drawn with the native post card; Report waits.
        registry.register(PostPageScreen.self, PostPageState.self) { state in PostPage(state: state) }
        registry.register(PostQuotesScreen.self, PostQuotesState.self) { state in PostQuotesView(state: state) }

        // The feed (ios-native-feed), the Feed tab's root, with the native filter bar, activity, import and post cards.
        registry.register(FeedScreen.self, FeedState.self) { state in FeedPage(state: state) }

        // Search (ios-native-search): the Search tab's root, with the shared segment browse embedded in Segments mode, and the choice
        // after leaving on another device, shown in place of the app.
        registry.register(AthleteSearchScreen.self, AthleteSearchState.self) { state in AthleteSearchView(state: state) }
        registry.register(LeftElsewhereScreen.self, LeftElsewhereState.self) { state in LeftElsewhereView(state: state) }

        // The dive site page (ios-native-map), the first drawn with the native route map, with its names and the name sheet.
        registry.register(DiveSiteScreen.self, DiveSiteState.self) { state in DiveSitePage(state: state) }

        // The interactive maps (ios-native-interactive-maps): a segment's enlarged map, segment browse (which the Search tab embeds, and
        // which draws natively too if anything pushes it), and accepting an invite.
        registry.register(SegmentMapScreen.self, SegmentMapState.self) { state in SegmentMapView(state: state) }
        registry.register(SegmentBrowseScreen.self, SegmentBrowseState.self) { state in
            SegmentBrowseView(state: state, memory: SegmentsMapMemory())
        }
        registry.register(AcceptInviteScreen.self, AcceptInviteState.self) { state in AcceptInviteView(state: state) }

        // The segment page (ios-native-charts), with the native chart, map and names.
        registry.register(SegmentScreen.self, SegmentState.self) { state in SegmentPage(state: state) }

        // The activity page (ios-native-activity-page), with the native map, charts, comments and menus.
        registry.register(ActivityDetailScreen.self, ActivityDetailState.self) { state in ActivityPage(state: state) }

        // Threshold history (ios-native-leftovers), opened from Training zones, with the native step charts.
        registry.register(ThresholdHistoryScreen.self, ThresholdHistoryState.self) { state in ThresholdHistoryView(state: state) }

        // The rest of the activity page (ios-native-activity-rest): the screens it opens, the enlarged route map, the media gallery and
        // the combine picker.
        registry.register(RouteMapScreen.self, RouteMapState.self) { state in ActivityRouteMapView(state: state) }
        registry.register(MediaGalleryScreen.self, MediaGalleryState.self) { state in MediaGalleryView(state: state) }
        registry.register(CombinePickerScreen.self, CombinePickerState.self) { state in CombinePickerView(state: state) }
        // The activity forms (ios-native-forms): Edit, over the activity page; entering one by hand, over the feed; and creating a dive
        // site from a dive, over its page.
        registry.register(EditActivityScreen.self, EditActivityState.self) { state in EditActivityView(state: state) }
        registry.register(ManualActivityScreen.self, ManualActivityState.self) { state in ManualActivityView(state: state) }
        registry.register(CreateDiveSiteScreen.self, CreateDiveSiteState.self) { state in CreateDiveSiteView(state: state) }
        // Create segment (ios-native-create-segment), over the activity page.
        registry.register(CreateSegmentScreen.self, CreateSegmentState.self) { state in CreateSegmentView(state: state) }
        // The import screen (ios-native-import), over the feed and, adding a recording, over the activity page.
        registry.register(ImportActivityScreen.self, ImportActivityState.self) { state in ImportActivityView(state: state) }
        // Record (ios-native-record), over the feed and from the recording's notification.
        registry.register(RecordScreen.self, RecordState.self) { state in RecordView(state: state) }
        // Moderation (ios-native-moderation), the last screen to go native: Settings offers it only off mobile, so nothing opens it yet.
        registry.register(ModerationScreen.self, ModerationState.self) { state in ModerationView(state: state) }
        return registry
    }()
}
