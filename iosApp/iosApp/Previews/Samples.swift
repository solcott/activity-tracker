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

        /// Settings' pages, which the shared UI still draws. Their states hold the immutable collections `elements(as:)` is tested on.
        static var privacyZones: [String: SettingsPackage.PrivacyZonesState] { SettingsPackage.PrivacyZonesSamples.shared.all }
        static var trainingZones: [String: SettingsPackage.TrainingZonesState] { SettingsPackage.TrainingZonesSamples.shared.all }
        static var thresholdHistory: [String: SettingsPackage.ThresholdHistoryState] {
            SettingsPackage.ThresholdHistorySamples.shared.all
        }

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
