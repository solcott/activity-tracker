import SwiftUI
import Testing

@testable import ActivityTracker

/// Edit profile in each of its sample states, and its country sheet, against their references (profile/private-details,
/// dev/ios-snapshot-tests). The form is recorded tall enough for all of it. The samples hold no reachable picture URL, so a saved photo is
/// the initial; a picked one is the sample's bytes. The calendar and the sheet present over the form, so the sheet is recorded on its own.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct EditProfileSnapshotTests {
    nonisolated private static let samples = ["loading", "failed", "loaded", "invalid"]
    nonisolated private static let variants = [
        "imperial", "savedPhoto", "pickedPhoto", "nowHidden", "approving", "saving", "saveFailed", "needsSignIn",
    ]

    private static let size = CGSize(width: 393, height: 2_000)

    @Test(arguments: samples + variants)
    func editProfile(sample: String) throws {
        let state = try #require(Samples.editProfile[sample] ?? Samples.editProfileVariants[sample])
        assertAppSnapshot(
            of: NavigationStack { EditProfileView(state: state) }, size: Self.size, named: sample, precision: Snapshots.barButtons)
    }

    @Test(arguments: ["", "us"])
    func countrySheet(search: String) {
        assertAppSnapshot(
            of: CountrySheet(countries: CountryNames(locale: Locale(identifier: "en_US")), chosen: "US", search: search) { _ in },
            size: CGSize(width: 393, height: 700), named: search.isEmpty ? "all" : search, precision: Snapshots.barButtons)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.editProfile.keys) == Set(Self.samples))
        #expect(Set(Samples.editProfileVariants.keys) == Set(Self.variants))
    }
}
