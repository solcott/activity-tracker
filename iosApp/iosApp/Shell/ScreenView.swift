import SwiftUI

/// One of the host's screens: its native view where the registry has one, and the shared Compose UI otherwise.
struct ScreenView: View {
    let record: HostRecord
    let controllers: FallbackControllers

    var body: some View {
        screen
            // A flow that takes the whole window (recording, an import, a full-screen map) hides the tabs. Nothing is said for the
            // screens that keep them: asking for them to show, on a covered screen, would win over the screen on top asking to hide.
            .toolbarVisibility(record.showsTabs ? .automatic : .hidden, for: .tabBar)
            // Tells the host this screen is showing even when it isn't on top, as beneath a back swipe, so its presenter runs.
            .onAppear { record.setVisible(visible: true) }
            .onDisappear { record.setVisible(visible: false) }
    }

    @ViewBuilder private var screen: some View {
        if ScreenRegistry.app.hasView(forScreenNamed: record.screenName) {
            NativeScreen(record: record)
        } else {
            // Compose lays the screen out against the whole window and pads for the bars and the keyboard itself, and draws its own
            // navigation bar.
            FallbackScreen(record: record, controllers: controllers)
                .ignoresSafeArea()
                .toolbar(.hidden, for: .navigationBar)
        }
    }
}

/// A screen with a native view, drawn from each state its presenter publishes.
private struct NativeScreen: View {
    let record: HostRecord

    var body: some View {
        Following(record.state) { state in
            // Nothing until the presenter's first state, which arrives within a frame.
            if let state, let view = ScreenRegistry.app.view(forScreenNamed: record.screenName, state: state) {
                view
            }
        }
    }
}

/// A screen shown on its own, with no stack around it: sign-in, a leave in progress, the choice after leaving elsewhere.
struct SingleScreen: View {
    let record: HostRecord
    @State private var controllers = FallbackControllers()

    var body: some View {
        ScreenView(record: record, controllers: controllers)
    }
}
