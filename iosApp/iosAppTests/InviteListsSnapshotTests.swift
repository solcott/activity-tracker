import SwiftUI
import Testing

@testable import ActivityTracker

/// The invites list and Add people in each of their sample states, against their references (feed/activity-invites,
/// dev/ios-snapshot-tests). The samples hold no picture URLs, so avatars are their initials.
@MainActor
@Suite(.enabled(if: Snapshots.comparesHere, Snapshots.skipped))
struct InviteListsSnapshotTests {
    nonisolated private static let contentSamples = ["loading", "failed", "empty", "loaded", "refreshing", "refreshFailed"]
    nonisolated private static let inviteVariants = ["declined", "declinedEmpty", "answering", "needsSignIn", "noDistance"]
    nonisolated private static let peopleVariants = ["pending", "removing", "full", "noFollowers", "needsSignIn"]

    @Test(arguments: contentSamples + inviteVariants)
    func activityInvites(sample: String) throws {
        let state = try #require(Samples.activityInvites[sample] ?? Samples.activityInvitesVariants[sample])
        assertAppSnapshot(of: NavigationStack { ActivityInvitesView(state: state) }, named: sample)
    }

    @Test(arguments: contentSamples + peopleVariants)
    func addPeople(sample: String) throws {
        let state = try #require(Samples.addPeople[sample] ?? Samples.addPeopleVariants[sample])
        // A full activity lists 20 people, so its page is drawn tall enough to show the note and the follower under them.
        let size = sample == "full" ? CGSize(width: Snapshots.screen.width, height: 2_000) : Snapshots.screen
        assertAppSnapshot(of: NavigationStack { AddPeopleView(state: state) }, size: size, named: sample)
    }

    @Test func everySampleHasASnapshot() {
        #expect(Set(Samples.activityInvites.keys) == Set(Self.contentSamples))
        #expect(Set(Samples.activityInvitesVariants.keys) == Set(Self.inviteVariants))
        #expect(Set(Samples.addPeople.keys) == Set(Self.contentSamples))
        #expect(Set(Samples.addPeopleVariants.keys) == Set(Self.peopleVariants))
    }
}
