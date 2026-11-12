import SwiftUI

extension View {
    /// The system's pull to refresh over a list whose refresh runs in Kotlin (ui/ios-design-system): `refresh` sends the page's refresh
    /// event, and the system's spinner stays until the page's state says the refresh has ended (`refreshing` turned on and off again),
    /// so it shows exactly as long as the refresh runs. A refresh that never shows as running (one answered before the next frame) lets
    /// the spinner go after a moment, and none holds it longer than half a minute.
    func pullToRefresh(refreshing: Bool, refresh: @escaping () -> Void) -> some View {
        modifier(PullToRefresh(refreshing: refreshing, refresh: refresh))
    }
}

private struct PullToRefresh: ViewModifier {
    let refreshing: Bool
    let refresh: () -> Void
    @State private var latest = RefreshFlag()

    func body(content: Content) -> some View {
        content
            .refreshable { await latest.run(refresh) }
            .onChange(of: refreshing, initial: true) { _, now in latest.refreshing = now }
    }
}

/// The page's latest refreshing flag, which the refresh's task reads as the states arrive: a view's own properties are the values it was
/// drawn with when the task began.
@MainActor
final class RefreshFlag {
    var refreshing = false

    /// Sends `refresh` and returns once the state has shown it running and then ended, or after `notStarted` if it never shows running,
    /// looking every `step`. Returns how long it waited, in steps' time. `sleep` is the wait between looks (a test's needn't take any).
    @discardableResult
    func run(
        _ refresh: () -> Void, notStarted: Duration = .milliseconds(800), atMost: Duration = .seconds(30),
        step: Duration = .milliseconds(100), sleep: (Duration) async -> Void = { try? await Task.sleep(for: $0) }
    ) async -> Duration {
        refresh()
        var waited = Duration.zero
        var started = refreshing
        while waited < atMost, !Task.isCancelled {
            await sleep(step)
            waited += step
            if refreshing {
                started = true
            } else if started || waited >= notStarted {
                return waited
            }
        }
        return waited
    }
}
