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
        // Edit profile (profile/private-details), pushed over the athlete's own profile.
        registry.register(EditProfileScreen.self, EditProfileState.self) { state in EditProfileView(state: state) }
        return registry
    }()
}
