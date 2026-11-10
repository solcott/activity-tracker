import IosShell
import SwiftUI

@main
struct iOSApp: App {
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
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background {
                IosApp.shared.scheduleBackgroundTasks()
            }
        }
    }
}
