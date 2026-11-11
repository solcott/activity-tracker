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

        /// Settings' pages, which the shared UI still draws. Their states hold the immutable collections `elements(as:)` is tested on.
        static var privacyZones: [String: SettingsPackage.PrivacyZonesState] { SettingsPackage.PrivacyZonesSamples.shared.all }
        static var trainingZones: [String: SettingsPackage.TrainingZonesState] { SettingsPackage.TrainingZonesSamples.shared.all }
        static var thresholdHistory: [String: SettingsPackage.ThresholdHistoryState] {
            SettingsPackage.ThresholdHistorySamples.shared.all
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
