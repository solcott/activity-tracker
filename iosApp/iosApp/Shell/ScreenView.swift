import SwiftUI

/// One of the host's screens, drawn by its native view. Every screen has one (`checkIosScreenRegistry`).
struct ScreenView: View {
    let record: HostRecord
    /// Drawn in a column beside another screen, under a navigation bar that isn't its own (`SettingsColumns`).
    var inPane = false

    var body: some View {
        screen
            // A flow that takes the whole window (recording, an import, a full-screen map) hides the tabs. Nothing is said for the
            // screens that keep them: asking for them to show, on a covered screen, would win over the screen on top asking to hide.
            .toolbarVisibility(record.showsTabs ? .automatic : .hidden, for: .tabBar)
            // Tells the host this screen is showing even when it isn't on top, as beneath a back swipe, so its presenter runs.
            .onAppear { record.setVisible(visible: true) }
            .onDisappear { record.setVisible(visible: false) }
    }

    private var screen: some View {
        // A native page in a column titles itself in a header there (`pageTitle`), since the bar is the other screen's.
        // Its header there has a back button once it is deeper than the column's first page.
        Following(record.paneRoot) { paneRoot in
            NativeScreen(record: record).environment(\.inPane, inPane).environment(\.paneRoot, paneRoot)
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
                // A sheet in the page asks the host for its sub-screen through the record (`reporting(_:)`, `SubScreenView`).
                view.environment(\.hostRecord, record)
            }
        }
    }
}

/// A screen shown on its own, with no stack around it: sign-in, a leave in progress, the choice after leaving elsewhere.
struct SingleScreen: View {
    let record: HostRecord

    var body: some View {
        ScreenView(record: record)
    }
}

extension EnvironmentValues {
    /// The host record of the native page a view is drawn in, for a sheet that asks it for a sub-screen; nil outside the host, as in a
    /// preview or a snapshot.
    @Entry var hostRecord: HostRecord?
}
