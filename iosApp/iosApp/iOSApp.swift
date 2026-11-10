import IosShell
import SwiftUI

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Before launch finishes, as BGTaskScheduler requires. Queued writes upload in the background (data/outbox), batch saves go on
        // and resume after the app is stopped (feed/batch-import), and data exports too (auth/data-export).
        IosApp.shared.registerBackgroundTasks(fitDecoder: SwiftFitDecoder())
    }

    var body: some Scene {
        WindowGroup {
            ContentView().ignoresSafeArea()
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background {
                IosApp.shared.scheduleBackgroundTasks()
            }
        }
    }
}
