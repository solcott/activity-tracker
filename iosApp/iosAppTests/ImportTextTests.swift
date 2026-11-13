import Foundation
import Testing

@testable import ActivityTracker

/// What the import screen says and offers (dev/shared-display-text). The rules are Kotlin's, with their own tests; these hold that Swift
/// reads each on the shared samples, and the catalog words each as the shared screen does.
@MainActor
struct ImportTextTests {
    private let texts = ImportActivityTexts.shared

    private func read(_ text: (any AppText)?) -> String? { text.map { AppStrings.string($0) } }

    /// A time of day as the device's clock style writes it, at a UTC offset.
    private func time(_ iso: String, offsetMinutes: Int = 0) -> String {
        let date = ISO8601DateFormatter().date(from: iso)!
        return date.formatted(Date.FormatStyle(date: .omitted, time: .shortened, timeZone: TimeZone(secondsFromGMT: offsetMinutes * 60)!))
    }

    @Test func pickingSaysWhatItRead() throws {
        let progress = try #require(Samples.importActivity["readingProgress"])
        #expect(read(texts.reading(progress: progress.readProgress)) == "812 files read, 790 activities found…")
        #expect(read(texts.pick(picked: false)) == "Choose files")
        let stopped = try #require(Samples.importActivity["stoppedImport"]?.stoppedImports.elements(as: StoppedImport.self).first)
        #expect(read(texts.stopped(stopped: stopped))?.hasPrefix("An import stopped after saving 120 of 400 activities.") == true)
    }

    @Test func aBatchCountsGroupsAndWordsItsRows() throws {
        let state = try #require(Samples.importActivity["batch"])
        let batch = try #require(state.batch)
        #expect(read(texts.found(batch: batch)) == "5 activities to import")
        #expect(read(texts.selected(batch: batch)) == "3 of 5 selected")
        let months = texts.months(batch: batch)
        #expect(months.map { read($0.heading) } == ["Apr 2024", "May 2024"])
        let rows = batch.rows.elements(as: ImportRow.self)
        #expect(read(texts.rowTitle(row: rows[1])) == "Morning Ride")
        let start = "Apr 28, 2024 at \(time("2024-04-28T07:02:00Z"))"
        #expect(read(texts.rowDetails(row: rows[0], units: .Metric)) == "\(start) · Run · 8.12 km · 52:00 · Garmin Forerunner 965")
        let logged = try #require(rows[3].duplicate)
        #expect(read(texts.duplicateWarning(info: logged, inFileNumber: nil))
            == "Already in your log: Lunch loop · May 14, 2024 at \(time("2024-05-14T12:01:00Z")) · Garmin Fenix 7")
        #expect(read(texts.saveBatch(state: state, batch: batch)) == "Import 3 activities")
        #expect(state.canSaveBatch)
        let copy = try #require(rows[2].duplicate)
        #expect(read(texts.duplicateWarning(info: copy, inFileNumber: texts.inFileNumber(batch: batch, row: rows[2]))) == "Exact copy of #2 in this import")
        let event = try #require(state.saveBatchEvent(source: CatalogText.shared) as? ImportActivityEvent.SaveBatch)
        #expect(event.defaultTitles.size == 2)
    }

    @Test func oneActivityOffersSaveAndItsChoices() throws {
        let preview = try #require(Samples.importActivity["preview"])
        #expect(preview.canSave)
        #expect(!(try #require(Samples.importActivityVariants["tooFar"])).canSave)
        let target = try #require(Samples.importActivityVariants["addsRecording"]?.addingTo)
        #expect(read(texts.addingTo(target: target))
            == "Adds this file as another recording of: Lunch loop · May 14, 2024 at \(time("2024-05-14T12:01:00Z")) · Garmin Fenix 7")
        let duplicate = try #require(Samples.importActivityVariants["duplicateUploading"]?.preview?.duplicate)
        #expect(texts.duplicateChoices(info: duplicate).map { read(texts.action(action: $0.action)) } == ["Ignore", "Import anyway"])
        let page = try #require(Samples.importActivityVariants["page"])
        let changes = page.thresholdChanges.elements(as: ThresholdChange.self)
        #expect(changes.map { read(texts.thresholdChange(change: $0)) } == ["Threshold power: 231 W → 240 W", "Run · Max heart rate: – → 188 bpm"])
    }

    @Test func zonesAreFoundByOffset() throws {
        let clock = try #require(Samples.importActivityVariants["diveZone"]?.preview?.clock)
        #expect(texts.zones(clock: clock, query: "utc+8").map(\.id) == ["Asia/Singapore"])
        #expect(read(texts.zone(zone: clock.zone)) == "America/Cancun (UTC-05:00)")
    }

    @Test func theZoneNoticeQuotesTheFilesClockWrittenOut() throws {
        let clock = try #require(Samples.importActivityVariants["diveZone"]?.preview?.clock)
        let notice = try #require(read(texts.zoneNotice(clock: clock, fileName: nil)))
        // The dive computer's 17:30, whatever the device's zone, on the device's clock.
        #expect(notice.contains("its start (Oct 16, 2024 at \(time("2024-10-16T17:30:00Z")))"))
    }
}
