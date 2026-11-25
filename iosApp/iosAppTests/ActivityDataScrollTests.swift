import SwiftUI
import Testing
import UIKit

@testable import ActivityTracker

/// All data opened from a table's Show all (feed/activity-page-layout): the list scrolls to that table once it lays its rows out, which a
/// snapshot can't show, since it draws without turning the run loop.
@MainActor
struct ActivityDataScrollTests {
    @Test func openedAtTheLapsItScrollsToThem() async throws {
        let top = try await scrolled(Samples.activityDataAtLaps)
        let opened = try #require(Samples.activityData["loaded"])
        let unscrolled = try await scrolled(opened)
        #expect(unscrolled < 1, "opened at no section, it stays at the top, not \(unscrolled)")
        #expect(top > 200, "opened at the laps, it scrolls past the stats to them, not \(top)")
    }

    /// How far the page's list has scrolled once it settles, in a window of the app's scene.
    private func scrolled(_ state: ActivityDataState) async throws -> CGFloat {
        // A window in the app's scene, or it is never shown and lays nothing out.
        let scene = try #require(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(origin: .zero, size: Snapshots.screen)
        let hosting = UIHostingController(rootView: NavigationStack { ActivityDataView(state: state) }.environment(\.clock, Samples.clock))
        window.rootViewController = hosting
        // Shown but never key: the snapshot suites, which may run alongside, draw in the key window and replace its controller.
        window.isHidden = false
        defer { window.isHidden = true }
        var offset: CGFloat = 0
        var steady = 0
        var found = false
        // Until the offset holds for a few turns, or five seconds.
        for _ in 0..<100 {
            try await Task.sleep(for: .milliseconds(50))
            guard let list = Self.list(in: hosting.view) else { continue }
            found = true
            let now = list.contentOffset.y + list.adjustedContentInset.top
            steady = abs(now - offset) < 0.5 ? steady + 1 : 0
            offset = now
            if steady >= 5 && offset > 0 { break }
        }
        #expect(found, "the page's list was never laid out")
        return offset
    }

    /// The page's list: the tallest scroll view, since the chips scroll sideways in one of their own.
    private static func list(in view: UIView) -> UIScrollView? {
        scrollViews(in: view).filter { $0.contentSize.height > $0.bounds.height }.max { $0.contentSize.height < $1.contentSize.height }
    }

    private static func scrollViews(in view: UIView) -> [UIScrollView] {
        let own: [UIScrollView] = (view as? UIScrollView).map { [$0] } ?? []
        return own + view.subviews.flatMap { scrollViews(in: $0) }
    }
}
