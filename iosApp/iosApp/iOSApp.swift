import IosShell
import SwiftUI

// The name Xcode gave the app; the target and file are named after it.
// swift-format-ignore: TypeNamesShouldBeCapitalized
@main
struct iOSApp: App {  // swiftlint:disable:this type_name
    @Environment(\.scenePhase) private var scenePhase

    /// The app's screens, run in Kotlin with no UI. Whichever of this and a background task comes first creates the app's graph, so both
    /// are given the FIT decoder.
    private let host: AppHost

    init() {
        // Before launch finishes, as BGTaskScheduler requires. Queued writes upload in the background (data/outbox), batch saves go on
        // and resume after the app is stopped (feed/batch-import), and data exports too (auth/data-export).
        IosApp.shared.registerBackgroundTasks(fitDecoder: SwiftFitDecoder())
        host = IosApp.shared.host(fitDecoder: SwiftFitDecoder())
    }

    var body: some Scene {
        WindowGroup {
            RootView(host: host)
                // The app's links (ui/deep-links): activitytracker:// URLs, and https ones on the AppView's host when the build has
                // associated domains. Kotlin opens each once a session is open. Sign-in's callback never comes here:
                // ASWebAuthenticationSession takes it.
                .onOpenURL { url in
                    _ = IosApp.shared.openLink(url: url.absoluteString, fitDecoder: SwiftFitDecoder())
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL {
                        _ = IosApp.shared.openLink(url: url.absoluteString, fitDecoder: SwiftFitDecoder())
                    }
                }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background {
                IosApp.shared.scheduleBackgroundTasks()
            }
        }
    }
}
