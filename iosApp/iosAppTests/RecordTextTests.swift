import Foundation
import Testing

@testable import ActivityTracker

/// What the recording screen says and offers (record/gps-recording, dev/shared-display-text). The rules are Kotlin's, with their own
/// tests; these hold that Swift reads each on the shared samples, and the catalog words each as the shared screen does.
@MainActor
struct RecordTextTests {
    private func read(_ text: any AppText) -> String { AppStrings.string(text) }

    @Test func eachStatusOffersItsControls() throws {
        let texts = RecordTexts.shared
        let idle = try #require(Samples.record["loaded"])
        #expect(texts.controls(state: idle).map(\.action) == [.Start])
        #expect(texts.typeChangeable(state: idle))
        let recording = try #require(Samples.record["recording"])
        #expect(texts.controls(state: recording).map(\.action) == [.Pause])
        #expect(!texts.typeChangeable(state: recording))
        let paused = try #require(Samples.record["paused"])
        #expect(texts.controls(state: paused).map { read($0.label) } == ["Resume", "Finish & save", "Discard"])
    }

    @Test func theFiguresReadInTheAthletesUnits() throws {
        let texts = RecordTexts.shared
        let ride = try #require(Samples.record["recording"])
        #expect(read(texts.elapsed(state: ride)) == "42:00")
        let metric = texts.stats(state: ride, units: .Metric).map { read($0.label) }
        #expect(metric == ["Distance", "Speed", "Elevation"])
        let run = try #require(Samples.recordVariants["run"])
        let imperial = texts.stats(state: run, units: .Imperial)
        #expect(read(imperial[0].value).hasSuffix("mi"))
        #expect(read(imperial[1].label) == "Pace")
        #expect(read(imperial[2].value).hasSuffix("ft"))
    }

    @Test func theNoticesAreWorded() {
        let texts = RecordTexts.shared
        #expect(read(texts.locationOff).contains("Settings"))
        #expect(read(texts.openSettings) == "Open Settings")
        #expect(read(texts.discardConfirm).hasPrefix("Discard this recording?"))
    }
}
