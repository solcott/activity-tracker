import Foundation
import Testing

@testable import ActivityTracker

/// What the profile says and offers, read from Swift over its samples (graph/follow-requests, graph/blocks, graph/mutes,
/// dev/shared-display-text, dev/ios-swift-interop).
@MainActor
struct ProfileTextTests {
    private func read(_ text: (any AppText)?) -> String? { text.map { AppStrings.string($0) } }

    @Test func bobSeesAlice() throws {
        let state = try #require(Samples.profile["loaded"])
        let profile = try #require(state.profile)
        #expect(read(ProfileTexts.shared.title(profile: profile)) == "Alice")
        #expect(read(LabelText.shared.handle(handle: profile.handle.value)) == "@alice.test")
        #expect(state.bio == "Trail runner and weekend rider.")
        #expect(read(ProfileTexts.shared.activities(count: profile.activitiesCount)) == "42 activities")
        #expect(read(ProfileTexts.shared.followers(count: profile.followersCount)) == "12 followers")
        #expect(read(ProfileTexts.shared.following(count: profile.followsCount)) == "8 following")
        #expect(state.actions == .Follow)
        #expect(state.followLabel == .Follow && state.followPrimary)
        #expect(read(ProfileTexts.shared.follow(label: state.followLabel)) == "Follow")
        #expect(!state.activityList.elements(as: ActivityView.self).isEmpty)
        #expect(state.showsActivities && !state.activitiesHidden && !state.activitiesEmpty)
    }

    @Test func beforeItLoadsTheTitleIsProfile() throws {
        let state = try #require(Samples.profile["loading"])
        #expect(state.profile == nil)
        #expect(read(ProfileTexts.shared.title(profile: state.profile)) == "Profile")
        #expect(state.activityList.elements(as: ActivityView.self).isEmpty)
    }

    @Test func noActivities() throws {
        let state = try #require(Samples.profile["empty"])
        #expect(state.activitiesEmpty)
        #expect(read(ProfileTexts.shared.activitiesEmpty) == "No activities yet.")
    }

    @Test func followingAndRequested() throws {
        let following = try #require(Samples.profileVariants["following"])
        #expect(read(ProfileTexts.shared.follow(label: following.followLabel)) == "Following")
        #expect(!following.followPrimary)
        let requested = try #require(Samples.profileVariants["requested"])
        #expect(read(ProfileTexts.shared.follow(label: requested.followLabel)) == "Requested")
        #expect(try #require(Samples.profileVariants["followPending"]).followPending)
    }

    @Test func theMenuAndTheBlock() throws {
        let muted = try #require(Samples.profileVariants["muted"])
        #expect(read(ProfileTexts.shared.mute(muting: muted.muting)) == "Unmute")
        let blocked = try #require(Samples.profileVariants["blocked"])
        #expect(blocked.actions == .Unblock && blocked.offersBlock && !blocked.showsActivities)
        #expect(read(ProfileTexts.shared.block(blocking: blocked.blocking)) == "Unblock")
        let bluesky = try #require(Samples.profileVariants["blockedOnBluesky"])
        #expect(bluesky.actions == .BlockedOnBluesky && !bluesky.offersBlock)
        #expect(read(ProfileTexts.shared.blockedOnBluesky) == "Blocked on Bluesky. Unblock them there.")
        let alice = try #require(Samples.profile["loaded"]?.profile)
        #expect(read(ProfileTexts.shared.blockConfirmTitle(name: ProfileTexts.shared.name(profile: alice))) == "Block Alice?")
    }

    @Test func theOwnProfilesEntries() throws {
        let state = try #require(Samples.profileVariants["ownApproving"])
        #expect(state.actions == .Own)
        #expect(
            ProfileTexts.shared.ownEntries(state: state).map { read($0.label) } == [
                "Edit profile", "Starred segments", "2 activity invites", "Follow requests (500+)",
            ])
    }

    @Test func theDetailsInBothUnits() throws {
        let state = try #require(Samples.profileVariants["details"])
        let details = try #require(state.profile?.details)
        #expect(read(ProfileTexts.shared.location(details: details, countryName: "United States")) == "Boulder, CO, United States")
        let metric = read(ProfileTexts.shared.facts(details: details, now: Samples.clock.now, units: .Metric))
        #expect(metric == "36 years old · Female · 168 cm · 61.5 kg")
        let imperial = read(ProfileTexts.shared.facts(details: details, now: Samples.clock.now, units: .Imperial))
        #expect(imperial?.contains("5′ 6″") == true)
    }

    @Test func labelsBlurTheAvatarAndHideTheActivities() throws {
        let labeled = try #require(Samples.profileVariants["labeled"])
        #expect(labeled.avatarBlur != nil)
        // A media label blurs the picture and says nothing more to others, as on the shared page.
        #expect(labeled.notices().isEmpty)
        let warned = try #require(Samples.profileVariants["warned"])
        #expect(warned.notices().count == 1)
        let hidden = try #require(Samples.profileVariants["hidden"])
        #expect(hidden.activitiesHidden)
    }
}
