import SwiftUI
import Testing

@testable import ActivityTracker

/// The activity forms and the dive site form in each of their sample states, and the gear and type sheets, against their references
/// (feed/activity-visibility, dive-sites/site-creation, dev/ios-snapshot-tests). Each form is recorded tall enough for all of it. The
/// samples hold no reachable picture URL, so a saved photo is its placeholder, and outside a session the dive site's map is its sketch.
/// The alerts and sheets present over the form, so the forms' samples show the form under them; the alerts' words are
/// `ActivityFormsTextTests`', and the sheets are recorded on their own.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct ActivityFormsSnapshotTests {
    nonisolated private static let editSamples = ["loading", "failed", "loaded"]
    nonisolated private static let editVariants = [
        "full", "dive", "saving", "saveFailed", "confirmDiscard", "confirmNarrowing", "confirmCoreCharts",
    ]
    nonisolated private static let manualSamples = ["loaded", "invalid", "spacesUnsupported"]
    nonisolated private static let manualVariants = ["full", "imperial", "saving", "noSubSports"]
    nonisolated private static let siteSamples = ["loading", "failed", "loaded", "invalid", "insideZone", "duplicate"]
    nonisolated private static let siteVariants = ["namesDive", "publishing", "limitReached", "checkFailed", "noPosition"]

    private static let editSize = CGSize(width: 393, height: 1_900)
    private static let manualSize = CGSize(width: 393, height: 1_500)
    private static let siteSize = CGSize(width: 393, height: 1_000)

    @Test(arguments: editSamples + editVariants)
    func editActivity(sample: String) throws {
        let state = try #require(Samples.editActivity[sample] ?? Samples.editActivityVariants[sample])
        assertAppSnapshot(
            of: NavigationStack { EditActivityView(state: state) }, size: Self.editSize, named: sample, precision: Snapshots.barButtons)
    }

    @Test(arguments: manualSamples + manualVariants)
    func manualActivity(sample: String) throws {
        let state = try #require(Samples.manualActivity[sample] ?? Samples.manualActivityVariants[sample])
        assertAppSnapshot(
            of: NavigationStack { ManualActivityView(state: state) }, size: Self.manualSize, named: sample,
            precision: Snapshots.barButtons)
    }

    @Test(arguments: siteSamples + siteVariants)
    func createDiveSite(sample: String) throws {
        let state = try #require(Samples.createDiveSite[sample] ?? Samples.createDiveSiteVariants[sample])
        assertAppSnapshot(
            of: NavigationStack { CreateDiveSiteView(state: state) }, size: Self.siteSize, named: sample,
            precision: Snapshots.barButtons)
    }

    @Test func gearSheet() throws {
        let state = try #require(Samples.manualActivityVariants["full"])
        assertAppSnapshot(of: GearFieldSheet(field: state.gear), size: CGSize(width: 393, height: 500), named: "full",
            precision: Snapshots.barButtons)
    }

    @Test(arguments: ["", "gravel", "zzz"])
    func typeSheet(query: String) throws {
        let state = try #require(Samples.manualActivityVariants["full"])
        assertAppSnapshot(
            of: ActivityTypeSheet(field: state.type, query: query), named: query.isEmpty ? "all" : query,
            precision: Snapshots.barButtons)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.editActivity.keys) == Set(Self.editSamples))
        #expect(Set(Samples.editActivityVariants.keys) == Set(Self.editVariants))
        #expect(Set(Samples.manualActivity.keys) == Set(Self.manualSamples))
        #expect(Set(Samples.manualActivityVariants.keys) == Set(Self.manualVariants))
        #expect(Set(Samples.createDiveSite.keys) == Set(Self.siteSamples))
        #expect(Set(Samples.createDiveSiteVariants.keys) == Set(Self.siteVariants))
    }
}
