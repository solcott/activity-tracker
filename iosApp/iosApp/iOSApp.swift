import ComposeApp
import SwiftUI

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Before launch finishes, as BGTaskScheduler requires; queued writes upload in the background (data/outbox).
        IosBackgroundUploads.shared.register(fitDecoder: SwiftFitDecoder())
        // Batch saves go on in the background and resume after the app is stopped (feed/batch-import).
        IosImportSaves.shared.register(fitDecoder: SwiftFitDecoder())
        // Data exports too (auth/data-export).
        IosDataExports.shared.register(fitDecoder: SwiftFitDecoder())
    }

    var body: some Scene {
        WindowGroup {
            ContentView().ignoresSafeArea()
        }
        .onChange(of: scenePhase) { phase in
            if phase == .background {
                IosBackgroundUploads.shared.schedule(after: nil)
                IosImportSaves.shared.schedule()
                IosDataExports.shared.schedule()
            }
        }
    }
}
