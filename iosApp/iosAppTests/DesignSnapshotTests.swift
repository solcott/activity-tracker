import SwiftUI
import Testing

@testable import ActivityTracker

/// Each piece of the design system against its references (ui/ios-design-system, dev/ios-snapshot-tests).
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct DesignSnapshotTests {
    private let panel = CGSize(width: 393, height: 420)

    @Test func buttons() { assertAppSnapshot(of: DesignGallery.buttons, size: panel, named: "buttons") }

    @Test func fields() { assertAppSnapshot(of: DesignGallery.fields, size: panel, named: "fields") }

    @Test func rows() { assertAppSnapshot(of: DesignGallery.rows, size: panel, named: "rows") }

    @Test func pageBar() { assertAppSnapshot(of: DesignGallery.pageBar, size: panel, named: "pageBar") }

    @Test func cards() { assertAppSnapshot(of: DesignGallery.cards, size: panel, named: "cards") }

    @Test func icons() { assertAppSnapshot(of: DesignGallery.icons, size: panel, named: "icons") }

    @Test func messages() { assertAppSnapshot(of: DesignGallery.messages, size: panel, named: "messages") }

    @Test(arguments: DesignGallery.contentNames)
    func loadedContent(state: String) {
        assertAppSnapshot(of: DesignGallery.content(state), size: panel, named: state)
    }
}
