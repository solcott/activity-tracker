import AppFixtures
import Foundation
import OrgJetbrainsKotlinxKotlinxIoBytestring
import Testing
import UIKit

@testable import ActivityTracker

/// A calendar day between Kotlin and a date picker, in time zones either side of UTC (dev/ios-swift-interop).
@MainActor
struct CalendarDayTests {
    private func calendar(_ zone: String) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: zone)!
        return calendar
    }

    @Test(arguments: ["America/Denver", "Pacific/Auckland", "UTC"])
    func aDayIsTheSameDayInTheDateControl(zone: String) throws {
        let since = try #require(GearPackage.EditGearSamples.shared.mounted.mountedOn?.since)
        let day = CalendarDay(since)
        #expect(day == CalendarDay(year: 2026, month: 7, day: 1))
        let calendar = calendar(zone)
        let shown = day.date(in: calendar)
        #expect(calendar.component(.day, from: shown) == 1)
        #expect(calendar.component(.month, from: shown) == 7)
        #expect(CalendarDay(shown, in: calendar) == day)
    }

    @Test(arguments: ["America/Denver", "Pacific/Auckland"])
    func aChosenDayReachesKotlinAsThatDay(zone: String) {
        let calendar = calendar(zone)
        // Late in the evening of 2 July there, which is already 3 July in UTC west of it and still 2 July east of it.
        let chosen = calendar.date(from: DateComponents(year: 2026, month: 7, day: 2, hour: 23, minute: 30))!
        #expect(CalendarDay(chosen, in: calendar).localDate.description == "2026-07-02")
    }
}

/// SwiftUI's row moves as the collection form's `Move` takes them.
struct MemberMoveTests {
    @Test func movingDownLandsOneBeforeTheDestination() {
        // Tarmac (0) dropped below Aero helmet: SwiftUI says "before 2", Kotlin's final index is 1.
        #expect(MemberMove.of(source: [0], destination: 2).map { [$0.from, $0.to] } == [0, 1])
        #expect(MemberMove.of(source: [0], destination: 3).map { [$0.from, $0.to] } == [0, 2])
    }

    @Test func movingUpLandsAtTheDestination() {
        // Race shoes (2) dropped above Aero helmet (1).
        #expect(MemberMove.of(source: [2], destination: 1).map { [$0.from, $0.to] } == [2, 1])
        #expect(MemberMove.of(source: [2], destination: 0).map { [$0.from, $0.to] } == [2, 0])
    }

    @Test func droppingWhereItWasMovesNothing() {
        #expect(MemberMove.of(source: [1], destination: 1) == nil)
        #expect(MemberMove.of(source: [1], destination: 2) == nil)
    }
}

/// The order Add items adds in.
struct TickedItemsTests {
    @Test func itemsAreAddedInTheOrderTicked() {
        var ticked = TickedItems()
        ticked.toggle("3kpower")
        ticked.toggle("3kwetsuit")
        #expect(ticked.rkeys == ["3kpower", "3kwetsuit"])
        #expect(ticked.isTicked("3kpower"))
    }

    @Test func untickingAndTickingAgainPutsItLast() {
        var ticked = TickedItems()
        ticked.toggle("a")
        ticked.toggle("b")
        ticked.toggle("a")
        #expect(ticked.rkeys == ["b"])
        #expect(!ticked.isTicked("a"))
        ticked.toggle("a")
        #expect(ticked.rkeys == ["b", "a"])
    }
}

/// The gear form's photo pick: what reaches the presenter, with the compression stood in for.
@MainActor
struct PhotoPickTests {
    private let photo = AppPackage.edit.PickedPhoto(
        bytes: ExportedKotlinPackages.kotlinx.io.bytestring.toByteString(Data([1, 2, 3]) as NSData), mimeType: "image/webp", width: 1,
        height: 1)

    private func jpeg() -> Data {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: 8, height: 8), format: format).image { context in
            UIColor.systemTeal.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 8, height: 8))
        }.jpegData(compressionQuality: 0.9)!
    }

    @Test func aCompressedPictureIsSent() async {
        var sent: [PickedPhoto] = []
        let pick = PhotoPick { _ in photo }
        await pick.pick({ Data([9]) }) { sent.append($0) }
        #expect(sent.count == 1)
    }

    @Test func aPictureTheCompressionRefusesIsntTriedAgain() async {
        var tried = 0
        var sent: [PickedPhoto] = []
        let pick = PhotoPick { _ in
            tried += 1
            return nil
        }
        await pick.pick({ jpeg() }) { sent.append($0) }
        #expect(tried == 1)
        #expect(sent.isEmpty)
    }

    @Test func bytesThatArentAPictureSendNothing() async {
        var sent: [PickedPhoto] = []
        let pick = PhotoPick { _ in nil }
        await pick.pick({ Data([1, 2, 3, 4]) }) { sent.append($0) }
        await pick.pick({ nil }) { sent.append($0) }
        await pick.pick({ throw CancellationError() }) { sent.append($0) }
        #expect(sent.isEmpty)
    }

    @Test func theRealCompressionMakesAWebpOfAJpeg() async throws {
        var sent: [PickedPhoto] = []
        await PhotoPick.app.pick({ jpeg() }) { sent.append($0) }
        let picked = try #require(sent.first)
        #expect(picked.mimeType == "image/webp")
        #expect(picked.width == 8 && picked.height == 8)
        #expect(picked.bytes.data.starts(with: Array("RIFF".utf8)))
    }
}
