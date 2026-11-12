import Foundation
import Testing

@testable import ActivityTracker

/// What the feed says and offers, read from Swift on the samples' clock (feed/feed-filter, feed/import-cards, data/outbox,
/// dev/shared-display-text, dev/ios-swift-interop).
@MainActor
struct FeedTextTests {
    private let clock = Samples.clock

    private func read(_ text: (any AppText)?) -> String? { text.map { clock.string($0) } }

    private func sample(_ name: String) throws -> FeedState {
        try #require(Samples.feed[name] ?? Samples.feedVariants[name])
    }

    @Test func theTitleNamesTheScopeAndItsMenuMarksIt() throws {
        let mine = try sample("mine")
        #expect(read(FeedTexts.shared.title(state: mine)) == "Mine")
        let options = FeedTexts.shared.scopes(state: mine)
        #expect(options.map { read($0.label) } == ["Following", "Mine"])
        #expect(options.map(\.chosen) == [false, true])
        #expect(options.last?.scope == .Mine)
    }

    @Test func addOffersRecordOnlyWhereTheDeviceCanRecord() throws {
        let labels = { (state: FeedState) in FeedTexts.shared.addChoices(state: state, recordInMenu: true).map { self.read($0.label) } }
        #expect(labels(try sample("loaded")) == ["Record", "Add manual activity", "Import activity file"])
        #expect(labels(try sample("emptyManualOnly")) == ["Add manual activity", "Import activity file"])
    }

    @Test func theUploadRowsReadAsTheSharedFeedsDo() throws {
        let unsent = try sample("unsent").uploads
        #expect(read(FeedUploadTexts.shared.refused(uploads: unsent)) == "2 changes couldn't be published")
        #expect(read(FeedUploadTexts.shared.uploading(uploads: unsent)) == "Uploading 1 activity and 950 imported · 3 other changes")
        let paused = try sample("paused").uploads
        #expect(read(FeedUploadTexts.shared.uploading(uploads: paused)) == "Uploading 3,000 imported activities")
        let until = try #require(read(FeedUploadTexts.shared.pausedUntil(uploads: paused, now: clock.now, zone: clock.kotlinZone)))
        #expect(until.hasPrefix("Paused by the server until "))
        let tomorrow = try sample("pausedTomorrow").uploads
        let later = try #require(read(FeedUploadTexts.shared.pausedUntil(uploads: tomorrow, now: clock.now, zone: clock.kotlinZone)))
        #expect(later.hasPrefix("Paused by the server until tomorrow at "))
        #expect(FeedUploadTexts.shared.importsPaused(uploads: tomorrow) == nil)
        #expect(FeedUploadTexts.shared.refused(uploads: try sample("loaded").uploads) == nil)
    }

    @Test func theEmptyFeedSaysWhy() throws {
        #expect(read(FeedTexts.shared.empty(state: try sample("empty"))) == "No activities yet. Record one or follow other athletes.")
        #expect(!FeedTexts.shared.emptyOffersClear(state: try sample("empty")))
        let filtered = try sample("emptyFiltered")
        #expect(read(FeedTexts.shared.empty(state: filtered)) == "No activities match these filters.")
        #expect(FeedTexts.shared.emptyOffersClear(state: filtered))
        #expect(FeedTexts.shared.empty(state: try sample("loading")) == nil)
        #expect(FeedTexts.shared.empty(state: try sample("loaded")) == nil)
    }

    @Test func theTimelineReachesSwiftWithItsKeys() throws {
        let imported = try sample("imported")
        let items = imported.items.elements(as: Any.self)
        #expect(items.count == 3)
        let card = try #require(items.first as? TimelineItem.ImportCard)
        #expect(imported.keyOf(item: card).hasPrefix("importCard:"))
        #expect(items[1] is TimelineItem.Activity)
        #expect(try sample("reposted").items.elements(as: Any.self).first is TimelineItem.Repost)
        #expect(try sample("posts").items.elements(as: Any.self).first is TimelineItem.Post)
    }

    @Test func anImportCardReadsItsLines() throws {
        let card = try #require(try sample("imported").items.elements(as: Any.self).first as? TimelineItem.ImportCard).card
        #expect(read(ImportCardTexts.shared.title(card: card)) == "Imported 312 activities")
        #expect(read(ImportCardTexts.shared.range(card: card, zone: clock.kotlinZone)) == "Jun 2019 – May 2026")
        #expect(read(ImportCardTexts.shared.summary(card: card, units: .Metric))?.hasPrefix("Ride 201 · Run 98 · Swim 13 · ") == true)
        #expect(ImportCardTexts.shared.routes(card: card).count == 4)
    }
}
