import Foundation
import Testing

@testable import ActivityTracker

/// What the upload queue says and offers, read from Swift on the samples' clock (data/upload-queue, dev/shared-display-text,
/// dev/ios-swift-interop).
@MainActor
struct UploadsTextTests {
    private let clock = Samples.clock

    private func read(_ text: (any AppText)?) -> String? { text.map { clock.string($0) } }

    private func queue(_ sample: String) throws -> UploadQueueView {
        try #require(Samples.uploads[sample]?.content.data as? UploadQueueView)
    }

    /// A time of day as the device's clock style writes it, in the samples' zone.
    private func time(_ iso: String) -> String {
        let date = ISO8601DateFormatter().date(from: iso)!
        return date.formatted(Date.FormatStyle(date: .omitted, time: .shortened, timeZone: TimeZone(identifier: "UTC")!))
    }

    @Test func theEmptyQueueReadsAsEmpty() throws {
        let state = try #require(Samples.uploads["empty"])
        let view = try queue("empty")
        #expect(view.isEmpty)
        #expect(view.own.isEmpty && view.imports.isEmpty && view.finished.isEmpty && view.refusedOther.isEmpty)
        #expect(state.showsClear)
        #expect(!state.canClear)
        #expect(!state.showsPauseImports)
    }

    @Test func anImportPartWay() throws {
        let state = try #require(Samples.uploads["importPartWay"])
        let group = try #require(try queue("importPartWay").imports.first)
        #expect(read(UploadImportTexts.shared.source(source: group.source)) == "Strava export")
        #expect(read(UploadImportTexts.shared.saved(group: group, now: clock.now, zone: clock.kotlinZone)) == "Saved Today at \(time("2026-06-01T07:00:00Z"))")
        #expect(read(UploadImportTexts.shared.progress(group: group)) == "412 of 1,362 uploaded")
        #expect(read(UploadImportTexts.shared.counts(group: group)) == "1 refused · 3 skipped")
        #expect(state.shownLive(group: group).count == 20)
        #expect(state.showsShowAll(group: group))
        #expect(read(UploadImportTexts.shared.showAll(group: group)) == "Show all (26)")
        #expect(state.offersCancelRest(group: group))
        #expect(read(UploadImportTexts.shared.cancelRest(group: group)) == "Cancel the rest of Strava export")
        #expect(read(UploadsTexts.shared.timeLeft(state: state)) == "About 1 day left")
        #expect(state.timeLeft?.inWholeSeconds == Int64(26 * 3600))
        #expect(state.showsPauseImports)

        let expanded = try #require(Samples.uploads["importExpanded"])
        #expect(expanded.shownLive(group: group).count == 26)
        #expect(!expanded.showsShowAll(group: group))
    }

    @Test func bothPauses() throws {
        let state = try #require(Samples.uploads["paused"])
        #expect(state.importsPaused)
        #expect(!state.showsPauseImports)
        #expect(
            read(UploadsTexts.shared.serverPaused(state: state, now: clock.now, zone: clock.kotlinZone))
                == "Uploads paused by the server until \(time("2026-06-01T15:40:00Z"))")
        let group = try #require(try queue("paused").imports.first)
        #expect(read(UploadImportTexts.shared.source(source: group.source)) == "Strava and Garmin exports")
    }

    @Test func itemsAndTheirActions() throws {
        let state = try #require(Samples.uploads["ownOnly"])
        let view = try queue("ownOnly")
        let current = try #require(view.current)
        #expect(read(UploadsTexts.shared.itemName(item: current, zone: clock.kotlinZone)) == "Evening Run")
        #expect(read(UploadsTexts.shared.state(item: current)) == "Uploading, 2 of 5")
        #expect(!state.offersCancel(item: current))
        let ride = view.own[0]
        #expect(state.offersCancel(item: ride))
        #expect(read(UploadsTexts.shared.cancel(name: UploadsTexts.shared.itemName(item: ride, zone: clock.kotlinZone))) == "Cancel Morning Ride")
        #expect(read(UploadsTexts.shared.itemName(item: view.own[1], zone: clock.kotlinZone)) == "Updating Lunch Swim")

        let finished = try #require(try queue("loaded").finished.first)
        #expect(Samples.uploads["loaded"]!.opens(item: finished))
        #expect(Samples.uploads["loaded"]!.canClear)
    }

    @Test func refusedChanges() throws {
        let state = try #require(Samples.uploads["refused"])
        let view = try queue("refused")
        let run = view.own[0]
        #expect(read(UploadsTexts.shared.itemName(item: run, zone: clock.kotlinZone)) == "Run, 2026-05-31")
        #expect(state.offersSendAgain(item: run))
        let like = try #require(view.refusedOther.first)
        let name = UploadsTexts.shared.changeName(change: like, zone: clock.kotlinZone)
        #expect(read(name) == "A like")
        #expect(read(UploadsTexts.shared.sendAgain(name: name)) == "Send A like again")
        #expect(!state.isBusy(key: like.rkey))
        #expect(Samples.uploads["sendingAgain"]!.isBusy(key: like.rkey))
    }

    @Test func savingAndStoppedImports() throws {
        let saving = try #require(try queue("saving").imports.first)
        #expect(read(UploadImportTexts.shared.saveStatus(group: saving)) == "Saving 500 of 1,362")
        let stopped = try #require(try queue("stopped").imports.first)
        #expect(stopped.save is ImportSaveStopped)
        #expect(
            read(UploadImportTexts.shared.saveStatus(group: stopped))
                == "Stopped after 120 of 400 · pick the files again to import the rest")
        #expect(read(UploadImportTexts.shared.source(source: stopped.source)) == "Morning_Ride.fit and 399 more")
    }

    @Test func theQuestions() throws {
        #expect(Samples.uploads["confirmingCancel"]?.confirmingCancel == "3k2")
        let rest = try #require(Samples.uploads["confirmingCancelRest"])
        #expect(rest.confirmingCancelRest == "batch1")
        #expect(
            read(UploadsTexts.shared.cancelRestMessage(state: rest))
                == "Activities waiting or refused are removed from this device and not sent. Those uploaded or uploading are kept.")
        let saving = try #require(Samples.uploads["confirmingCancelRestSaving"])
        #expect(read(UploadsTexts.shared.cancelRestMessage(state: saving))?.hasPrefix("Activities not saved yet won't be saved") == true)
    }

    @Test func aLengthOfTime() {
        #expect(read(DurationText.shared.approximate(duration: Samples.uploads["importPartWay"]!.timeLeft!)) == "1 day")
    }
}
