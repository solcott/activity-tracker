import Testing

@testable import ActivityTracker

/// A card's tap shows at once (feed/activity-page-layout): the page draws the card opened or closed in the same update as the tap, rather
/// than a few frames later when the presenter's state arrives.
@MainActor
struct CardOpeningsTests {
    @Test func aTapShowsAtOnceUntilTheStateAgrees() throws {
        let page = try #require(Samples.activityDetailVariants["ownRide"]?.content.data as? ActivityDetailPage)
        let card = try #require(page.page.cards.elements(as: MetricCard.self).first { !page.isOpen(card: $0) })
        let openings = CardOpenings()
        openings.toggle(card, on: page)
        #expect(openings.isOpen(card, on: page), "opened before the presenter answers")
        openings.settle(page)
        #expect(openings.isOpen(card, on: page), "kept while the state still has it closed")
        openings.toggle(card, on: page)
        #expect(!openings.isOpen(card, on: page), "a second tap closes it again")
    }
}
