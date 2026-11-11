#if DEBUG
    import AppFixtures
    import SwiftUI

    /// The shared sample states (`:app-fixtures`), which the shared UI's tests and reference screenshots render too
    /// (dev/ios-snapshot-tests). Debug builds only: a release build doesn't export them, so nothing outside `#if DEBUG` may name one.
    enum Samples {
        /// Sign-in in each state it shows, by the sample's name.
        static var login: [String: LoginState] { LoginPackage.LoginSamples.shared.all }

        /// Settings' list in each state it shows.
        static var settings: [String: SettingsState] { SettingsPackage.SettingsSamples.shared.all }

        /// Activity defaults and Segments & groups, the settings pages drawn natively.
        static var activityDefaults: [String: ActivityDefaultsState] { SettingsPackage.ActivityDefaultsSamples.shared.all }
        static var segmentsAndGroups: [String: SegmentsAndGroupsState] { SettingsPackage.SegmentsAndGroupsSamples.shared.all }
        static var account: [String: AccountState] { SettingsPackage.AccountSamples.shared.all }
        static var accountVariants: [String: AccountState] { SettingsPackage.AccountSamples.shared.variants }
        static var leave: [String: LeaveState] { SettingsPackage.LeaveSamples.shared.all }

        /// Gear, a collection, Ignored devices, and the blocked and muted lists, in each state they show; states beyond content loading apart.
        static var gearList: [String: GearListState] { GearPackage.GearListSamples.shared.all }
        static var gearCollection: [String: GearCollectionState] { GearPackage.GearCollectionSamples.shared.all }
        static var gearCollectionVariants: [String: GearCollectionState] { GearPackage.GearCollectionSamples.shared.variants }
        static var ignoredSensors: [String: IgnoredSensorsState] { GearPackage.IgnoredSensorsSamples.shared.all }
        static var ignoredSensorsVariants: [String: IgnoredSensorsState] { GearPackage.IgnoredSensorsSamples.shared.variants }
        /// The gear and collection forms in each state they show; states beyond content loading apart.
        static var editGear: [String: EditGearState] { GearPackage.EditGearSamples.shared.all }
        static var editGearVariants: [String: EditGearState] { GearPackage.EditGearSamples.shared.variants }
        static var editGearCollection: [String: EditGearCollectionState] { GearPackage.EditGearCollectionSamples.shared.all }
        static var editGearCollectionVariants: [String: EditGearCollectionState] {
            GearPackage.EditGearCollectionSamples.shared.variants
        }
        static var blockList: [String: BlockListState] { ProfilePackage.BlockListSamples.shared.all }
        static var blockListVariants: [String: BlockListState] { ProfilePackage.BlockListSamples.shared.variants }

        /// The follow lists and follow requests a profile opens; states beyond content loading apart.
        static var followList: [String: FollowListState] { ProfilePackage.FollowListSamples.shared.all }
        static var followListVariants: [String: FollowListState] { ProfilePackage.FollowListSamples.shared.variants }
        static var followRequests: [String: FollowRequestsState] { ProfilePackage.FollowRequestsSamples.shared.all }
        static var followRequestsVariants: [String: FollowRequestsState] { ProfilePackage.FollowRequestsSamples.shared.variants }

        /// The invites list a profile opens and Add people an activity opens; states beyond content loading apart.
        static var activityInvites: [String: ActivityInvitesState] { InvitesPackage.ActivityInvitesSamples.shared.all }
        static var activityInvitesVariants: [String: ActivityInvitesState] { InvitesPackage.ActivityInvitesSamples.shared.variants }
        static var addPeople: [String: AddPeopleState] { InvitesPackage.AddPeopleSamples.shared.all }
        static var addPeopleVariants: [String: AddPeopleState] { InvitesPackage.AddPeopleSamples.shared.variants }

        /// Settings' pages, which the shared UI still draws. Their states hold the immutable collections `elements(as:)` is tested on.
        static var privacyZones: [String: PrivacyZonesState] { SettingsPackage.PrivacyZonesSamples.shared.all }
        static var trainingZones: [String: TrainingZonesState] { SettingsPackage.TrainingZonesSamples.shared.all }
        static var thresholdHistory: [String: ThresholdHistoryState] { SettingsPackage.ThresholdHistorySamples.shared.all }
        static var thresholdHistoryVariants: [String: ThresholdHistoryState] { SettingsPackage.ThresholdHistorySamples.shared.variants }
        static var sportZones: [String: SportZonesState] { SettingsPackage.SportZonesSamples.shared.all }

        static var uploads: [String: UploadsState] { UploadsPackage.UploadsSamples.shared.all }

        /// Edit profile in each state it shows; states beyond content loading apart.
        static var editProfile: [String: EditProfileState] { ProfilePackage.EditProfileSamples.shared.all }
        static var editProfileVariants: [String: EditProfileState] { ProfilePackage.EditProfileSamples.shared.variants }

        /// The samples' clock, stopped at `Fixed.NOW` in UTC, as the shared previews read them.
        static var clock: ShownClock {
            ShownClock.fixed(
                atEpochSeconds: ExportedKotlinPackages.io.github.solcott.activitytracker.app.fixtures.Fixed.shared.NOW.epochSeconds,
                zone: TimeZone(identifier: "UTC")!)
        }

        /// A paged list in each state of ui/content-loading, around items a test gives.
        static let pagedStates = ExportedKotlinPackages.io.github.solcott.activitytracker.app.fixtures.PagedStates.shared

        /// Loaded content in each state of ui/content-loading, holding `data`.
        static let content = ExportedKotlinPackages.io.github.solcott.activitytracker.app.fixtures.ContentStates.shared
    }

    /// Every sample of one screen, a page each, with the sample's name over it: what a screen's preview shows.
    struct SamplePages<State, Content: View>: View {
        private let samples: [(name: String, state: State)]
        private let content: (State) -> Content

        init(_ samples: [String: State], @ViewBuilder content: @escaping (State) -> Content) {
            self.samples = samples.sorted { $0.key < $1.key }.map { ($0.key, $0.value) }
            self.content = content
        }

        var body: some View {
            TabView {
                ForEach(samples, id: \.name) { sample in
                    content(sample.state)
                        .overlay(alignment: .top) {
                            Text(verbatim: sample.name).font(.caption.monospaced()).padding(6).background(.thinMaterial, in: Capsule())
                        }
                }
            }
            .tabViewStyle(.page)
        }
    }
#endif
