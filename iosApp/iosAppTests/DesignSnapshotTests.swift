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

    @Test func pageBarActions() { assertAppSnapshot(of: DesignGallery.pageBarActions, size: panel, named: "pageBarActions") }

    @Test func pageBarBack() { assertAppSnapshot(of: DesignGallery.pageBarBack, size: panel, named: "pageBarBack") }

    @Test func pageBarNoBack() { assertAppSnapshot(of: DesignGallery.pageBarNoBack, size: panel, named: "pageBarNoBack") }

    @Test func images() { assertAppSnapshot(of: DesignGallery.images, size: CGSize(width: 393, height: 120), named: "images") }

    @Test func itemRows() { assertAppSnapshot(of: DesignGallery.itemRows, size: panel, named: "itemRows") }

    @Test(arguments: DesignGallery.pagedNames)
    func pagedList(state: String) {
        assertAppSnapshot(of: DesignGallery.paged(state), size: panel, named: state)
    }

    @Test func cards() { assertAppSnapshot(of: DesignGallery.cards, size: panel, named: "cards") }

    @Test func icons() { assertAppSnapshot(of: DesignGallery.icons, size: panel, named: "icons") }

    @Test func messages() { assertAppSnapshot(of: DesignGallery.messages, size: panel, named: "messages") }

    @Test(arguments: DesignGallery.contentNames)
    func loadedContent(state: String) {
        assertAppSnapshot(of: DesignGallery.content(state), size: panel, named: state)
    }
}
