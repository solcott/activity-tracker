import SwiftUI
import Testing
import UIKit

@testable import ActivityTracker

/// When a native paged list asks for its next page (ui/ios-design-system, ui/content-loading): once the last item loaded is on screen
/// and more can be loaded, and not before. The list is drawn in a window of its own, as on screen.
@MainActor
struct PagedListTests {
    /// How many times the list asked for more.
    @Observable final class Asked {
        var count = 0
    }

    private func dids(_ count: Int) -> [Did] { (0..<count).map { Did(value: "did:plc:\($0)") } }

    /// `state` drawn as a phone-sized list until `until` holds or a second passes; what the list asked for by then.
    private func asked(
        _ state: KotlinPagedState, until: (Asked) -> Bool = { _ in false }, sections: (([Did]) -> [PagedSection<Did>])? = nil
    ) async throws -> Asked {
        let asked = Asked()
        let list = PagedList(
            state, of: Did.self, errorKind: .LoadBlocks, id: \.value, sections: sections, retry: {}, loadMore: { asked.count += 1 }
        ) {
            EmptyView()
        } row: { did in
            Text(verbatim: did.value).frame(height: 44)
        } empty: {
            EmptyView()
        }
        let scene = try #require(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(origin: .zero, size: Snapshots.screen)
        window.rootViewController = UIHostingController(rootView: list)
        window.isHidden = false
        defer { window.isHidden = true }
        for _ in 0..<20 where !until(asked) {
            try await Task.sleep(for: .milliseconds(50))
        }
        return asked
    }

    @Test func theEndOnScreenWithMoreToComeAsksForTheNextPage() async throws {
        let state = Samples.pagedStates.loaded(items: dids(2), next: Samples.pagedStates.NEXT)
        #expect(state.canLoadMore)
        let asked = try await asked(state) { $0.count > 0 }
        #expect(asked.count == 1)
    }

    @Test func theLastPageAsksForNothing() async throws {
        let asked = try await asked(Samples.pagedStates.end(items: dids(2), meta: nil))
        #expect(asked.count == 0)
    }

    @Test func aLongListWaitsUntilItsEndIsOnScreen() async throws {
        let asked = try await asked(Samples.pagedStates.loaded(items: dids(100), next: Samples.pagedStates.NEXT))
        #expect(asked.count == 0)
    }

    /// Two sections of one item each, the second headed: the end of the last one asks for more, as one section's does.
    @Test func theEndOfTheLastSectionAsksForTheNextPage() async throws {
        let state = Samples.pagedStates.loaded(items: dids(2), next: Samples.pagedStates.NEXT)
        let asked = try await asked(state, until: { $0.count > 0 }) { dids in
            dids.enumerated().map { index, did in PagedSection(id: did.value, header: index == 0 ? nil : "Later", items: [did]) }
        }
        #expect(asked.count == 1)
    }

    @Test func aLongSectionedListWaitsUntilItsEndIsOnScreen() async throws {
        let state = Samples.pagedStates.loaded(items: dids(100), next: Samples.pagedStates.NEXT)
        let asked = try await asked(state, sections: { dids in [PagedSection(id: "a", header: "All", items: dids)] })
        #expect(asked.count == 0)
    }
}
