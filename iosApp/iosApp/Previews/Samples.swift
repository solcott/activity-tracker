#if DEBUG
    import AppFixtures
    import SwiftUI

    /// The shared sample states (`:app-fixtures`), which the shared UI's tests and reference screenshots render too
    /// (dev/ios-snapshot-tests). Debug builds only: a release build doesn't export them, so nothing outside `#if DEBUG` may name one.
    enum Samples {
        /// Sign-in in each state it shows, by the sample's name.
        static var login: [String: LoginState] { LoginPackage.LoginSamples.shared.all }

        /// The feed's filter bar (ios-host-subcircuits): the shared samples, its picker open, and its longest words.
        static var feedFilter: [String: FeedFilterState] { FeedFilterPackage.FeedFilterSamples.shared.variants }

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

        /// Training zones, the default and sport zones forms and Privacy zones (ios-native-zones-pages), with their states beyond the
        /// shared samples; and threshold history, which the shared UI still draws.
        static var trainingZones: [String: TrainingZonesState] { SettingsPackage.TrainingZonesSamples.shared.all }
        static var trainingZonesVariants: [String: TrainingZonesState] { SettingsPackage.TrainingZonesSamples.shared.variants }
        static var zones: [String: ZonesState] { SettingsPackage.ZonesSamples.shared.all }
        static var zonesVariants: [String: ZonesState] { SettingsPackage.ZonesSamples.shared.variants }
        static var sportZones: [String: SportZonesState] { SettingsPackage.SportZonesSamples.shared.all }
        static var sportZonesVariants: [String: SportZonesState] { SettingsPackage.SportZonesSamples.shared.variants }
        static var privacyZones: [String: PrivacyZonesState] { SettingsPackage.PrivacyZonesSamples.shared.all }
        static var privacyZonesVariants: [String: PrivacyZonesState] { SettingsPackage.PrivacyZonesSamples.shared.variants }
        static var thresholdHistory: [String: ThresholdHistoryState] { SettingsPackage.ThresholdHistorySamples.shared.all }
        static var thresholdHistoryVariants: [String: ThresholdHistoryState] { SettingsPackage.ThresholdHistorySamples.shared.variants }

        static var uploads: [String: UploadsState] { UploadsPackage.UploadsSamples.shared.all }

        /// Starred segments, a segment's leaderboard and Mayors, and a dive site's Regulars; states beyond content loading apart.
        static var starredSegments: [String: StarredSegmentsState] { SegmentPackage.StarredSegmentsSamples.shared.all }
        static var segmentLeaderboard: [String: SegmentLeaderboardState] { SegmentPackage.SegmentLeaderboardSamples.shared.all }
        static var segmentMayors: [String: SegmentMayorsState] { SegmentPackage.SegmentMayorsSamples.shared.all }
        static var diveSiteRegulars: [String: DiveSiteRegularsState] { DiveSitePackage.DiveSiteRegularsSamples.shared.all }
        static var starredSegmentsVariants: [String: StarredSegmentsState] { SegmentPackage.StarredSegmentsSamples.shared.variants }
        static var segmentLeaderboardVariants: [String: SegmentLeaderboardState] {
            SegmentPackage.SegmentLeaderboardSamples.shared.variants
        }
        static var segmentMayorsVariants: [String: SegmentMayorsState] { SegmentPackage.SegmentMayorsSamples.shared.variants }
        static var diveSiteRegularsVariants: [String: DiveSiteRegularsState] { DiveSitePackage.DiveSiteRegularsSamples.shared.variants }
        /// Edit profile in each state it shows; states beyond content loading apart.
        static var editProfile: [String: EditProfileState] { ProfilePackage.EditProfileSamples.shared.all }
        static var editProfileVariants: [String: EditProfileState] { ProfilePackage.EditProfileSamples.shared.variants }

        /// One activity card in each case it draws differently, for `cardViewer`; a batch import's page in each state it shows.
        static var activityCards: [String: ActivityView] { ActivityListPackage.ActivityCardSamples.shared.all }
        static var cardViewer: String { ActivityListPackage.ActivityCardSamples.shared.viewer }
        static var importBatch: [String: ImportBatchState] { FeedPackage.ImportBatchSamples.shared.all }
        static var importBatchVariants: [String: ImportBatchState] { FeedPackage.ImportBatchSamples.shared.variants }
        /// The gear item page in each state it shows; states beyond content loading apart.
        static var gear: [String: GearState] { GearPackage.GearSamples.shared.all }
        static var gearVariants: [String: GearState] { GearPackage.GearSamples.shared.variants }

        /// The profile in each state it shows (ios-native-profile); states beyond content loading apart.
        static var profile: [String: ProfileState] { ProfilePackage.ProfileSamples.shared.all }
        static var profileVariants: [String: ProfileState] { ProfilePackage.ProfileSamples.shared.variants }

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
