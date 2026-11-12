import Foundation
import Testing

@testable import ActivityTracker

/// What the native activity card and the batch page say and decide, read from Swift on the samples' clock (ui/ios-design-system,
/// feed/import-cards, dev/shared-display-text, dev/ios-swift-interop).
@MainActor
struct ActivityCardTextTests {
    private let clock = Samples.clock

    private func read(_ text: (any AppText)?) -> String? { text.map { clock.string($0) } }

    private func card(_ name: String) throws -> ActivityCardContent {
        let activity = try #require(Samples.activityCards[name])
        return ActivityCardContent.Companion.shared.of(activity: activity, viewerDid: Samples.cardViewer)
    }

    /// A time of day as the device's clock style writes it, at a UTC offset.
    private func time(_ iso: String, offsetMinutes: Int) -> String {
        let date = ISO8601DateFormatter().date(from: iso)!
        return date.formatted(Date.FormatStyle(date: .omitted, time: .shortened, timeZone: TimeZone(secondsFromGMT: offsetMinutes * 60)!))
    }

    @Test func everyCardSampleReads() throws {
        #expect(Samples.activityCards.count == 14)
        for name in Samples.activityCards.keys {
            let content = try card(name)
            #expect(!(read(content.title()) ?? "").isEmpty, "\(name) has no title")
            #expect(read(content.start(now: clock.now, zone: clock.kotlinZone)) != nil)
        }
    }

    @Test func aFollowersRide() throws {
        let content = try card("ride")
        #expect(read(content.author()) == "Bob")
        #expect(read(content.title()) == "Morning Ride")
        #expect(read(content.typeDescription()) == "Ride")
        // Started at 07:00 UTC on May 31 at UTC-6, seen at noon UTC on June 1: yesterday at 1:00 AM on the activity's clock.
        #expect(read(content.start(now: clock.now, zone: clock.kotlinZone)) == "Yesterday at \(time("2026-05-31T07:00:00Z", offsetMinutes: -360))")
        let stats = content.stats(units: .Metric).map { (read($0.label) ?? "", read($0.value) ?? "") }
        #expect(stats.map(\.0) == ["Distance", "Time", "Speed"])
        #expect(stats[1].1 == "1:00:00")
        #expect(read(content.counts()) == "3 likes · 1 comment")
        #expect(content.offersLike && !content.liked)
        #expect(content.notices().isEmpty && content.locks().isEmpty)
    }

    @Test func likedAndOwn() throws {
        #expect(try card("liked").liked)
        let own = try card("own")
        #expect(!own.offersLike)
        #expect(read(own.badge()) == "Followers")
        #expect(read(own.badgeDescription()) == "Visible to: Followers")
        let lock = try #require(own.locks().first)
        #expect(read(lock.text) == "Heart rate · Only me")
        #expect(read(lock.spoken) == "Heart rate: visible to Only me")
    }

    @Test func untitledAndGroup() throws {
        let untitled = try card("untitled")
        #expect(read(untitled.title()) == "Run")
        #expect(untitled.typeDescription() == nil)
        #expect(!untitled.hasRoute)
        #expect(read(try card("group").together()) == "with Carol and 2 others")
    }

    @Test func labelsAndTakedowns() throws {
        let blurred = try card("blurred")
        #expect(read(blurred.blurLabel()) == "Graphic media")
        #expect(blurred.media.elements(as: MediaPhoto.self).count == 2)
        #expect(read(try card("warned").notices().first?.text) == "Spam")
        let takenDown = try card("takenDown")
        #expect(takenDown.takenDown)
        let notice = try #require(takenDown.notices().first)
        #expect(notice.isError)
    }

    @Test func aDiveAStrengthWorkoutAndADayOnly() throws {
        let dive = try card("dive")
        #expect(dive.diveSite() == "Blue Hole")
        #expect(dive.stats(units: .Metric).map { read($0.label) } == ["Max depth", "Dive time", "Water temp"])
        #expect(try card("strength").stats(units: .Metric).map { read($0.label) } == ["Time"])
        #expect(read(try card("dateOnly").start(now: clock.now, zone: clock.kotlinZone)) == "Yesterday")
    }

    @Test func aBrokenRouteIsPlacedInsideItsTile() throws {
        let shape = try #require(try card("broken").routeShape(width: 300, height: 160, pad: 12))
        let pieces = shape.pieces.elements(as: RoutePiece.self)
        #expect(pieces.count == 2)
        #expect(shape.breaks.elements(as: RouteBreak.self).count == 1)
        let points = pieces.flatMap { $0.points.elements(as: ShapePoint.self) } + [shape.start, shape.end].compactMap { $0 }
        #expect(points.allSatisfy { (11.9...288.1).contains($0.x) && (11.9...148.1).contains($0.y) })
        #expect(try card("ride").routeShape(width: 300, height: 160, pad: 12).map { $0.breaks.elements(as: RouteBreak.self).isEmpty } == true)
    }

    @Test func anOffsetTimeReadsOnItsOwnClock() {
        // 16:00 UTC at UTC-7, whatever the clock's zone.
        let text = AppText.Time(epochSeconds: 1_794_326_400, utcOffsetMinutes: -420)
        #expect(clock.string(text) == time("2026-11-10T16:00:00Z", offsetMinutes: -420))
        #expect(clock.string(text).hasPrefix("9:00"))
    }

    @Test func theBatchPagesTitleAndMonths() throws {
        let state = try #require(Samples.importBatchVariants["threeMonths"])
        #expect(read(ImportBatchTexts.shared.title(state: state)) == "Imported by Bob")
        let activities = state.activities.items.compactMap { $0 as? ActivityView }
        let months = ImportBatchTexts.shared.months(activities: activities, zone: clock.kotlinZone)
        #expect(months.map { read($0.heading) } == ["May 2025", "Apr 2025", "Mar 2025"])
        #expect(months.map { $0.activities.elements(as: ActivityView.self).count } == [1, 1, 1])
    }
}
