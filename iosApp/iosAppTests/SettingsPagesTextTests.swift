import Testing

@testable import ActivityTracker

/// What Activity defaults and Segments & groups say and offer (ui/settings-layout, dev/ios-swift-interop). The rules are Kotlin's, with
/// their own tests; these hold that Swift reads each on the shared samples, and the catalog words each as the shared pages do.
@MainActor
struct SettingsPagesTextTests {
    private func read(_ text: (any AppText)?) -> String? { text.map { AppStrings.string($0) } }

    private var defaults: VisibilityFields { Samples.activityDefaults["loaded"]!.defaults! }
    private var segments: SegmentsAndGroupsState { Samples.segmentsAndGroups["loaded"]! }

    @Test func aCategoryIsOfferedOnlyTheActivitysVisibilityOrNarrower() {
        #expect(defaults.activity == "followers")
        #expect(defaults.activityOptions.elements(as: String.self) == ["public", "followers", "mutuals", "self"])
        #expect(defaults.categoryOptions.elements(as: String.self) == ["followers", "mutuals", "self"])
        #expect(
            defaults.categoryOptions.elements(as: String.self).map { read(LabelText.shared.visibility(visibility: $0)) }
                == ["Followers", "Mutuals", "Only me"])
    }

    @Test func categoriesAreNamedWithTheirHelp() {
        let categories = defaults.categories.elements(as: CategoryVisibility.self)
        #expect(categories.count == 10)
        #expect(categories.first.map { read(VisibilityTexts.shared.categoryName(fields: defaults, category: $0.category)) } == "Start time")
        #expect(read(VisibilityTexts.shared.categoryHelp(category: "startTime")) == "Others see only the date.")
        #expect(read(VisibilityTexts.shared.categoryHelp(category: "heartRate")) == nil)
        #expect(read(VisibilityTexts.shared.categoryName(fields: defaults, category: "cadence")) == "Cadence and running form")
        #expect(read(VisibilityTexts.shared.categoryName(fields: defaults, category: "unknownCategory")) == "unknownCategory")
    }

    @Test func followersWithoutApprovalSaysAnyoneCanFollow() {
        #expect(
            read(VisibilityTexts.shared.followersNote(selected: defaults.activity, approvesFollowers: false))
                == "Anyone who follows you can see what you share with Followers. Turn on Approve followers in your profile to choose who.")
        #expect(read(VisibilityTexts.shared.followersNote(selected: defaults.activity, approvesFollowers: true)) == nil)
    }

    @Test func theDefaultsNote() {
        let loaded = Samples.activityDefaults["loaded"]!
        let locked = Samples.activityDefaults["locked"]!
        #expect(loaded.canChange)
        #expect(
            read(SettingsTexts.shared.defaultsNote(defaults: defaults, saving: false))
                == "Where you record, enter or import an activity, you can change these for that activity.")
        #expect(read(SettingsTexts.shared.defaultsNote(defaults: defaults, saving: true)) == "Saving…")
        #expect(!locked.canChange)
        #expect(
            read(SettingsTexts.shared.defaultsNote(defaults: locked.defaults!, saving: false))
                == "Sign in again to choose who sees your activities. Until then they're Public.")
        #expect(locked.defaults!.activity == "public")
        #expect(Samples.activityDefaults["loading"]!.defaults == nil)
    }

    @Test func theGroupingChoicesReachSwiftAsTheirOwnEnum() {
        let options = segments.groupOptions.elements(as: GroupOption.self)
        #expect(options.map(\.choice) == [.OFF, .CONNECTED, .ANYONE])
        #expect(options.map { read($0.label) } == ["Off", "People I follow", "Anyone"])
        #expect(segments.shownGroupChoice == .CONNECTED)
    }

    @Test func whatSegmentsAndGroupsOffers() {
        #expect(segments.canChangeMatching)
        #expect(segments.offersLeaderboards && segments.canChangeLeaderboards)
        #expect(segments.offersBracketBoards && segments.canChangeBracketBoards)
        #expect(segments.canChangeGroups)

        let loading = Samples.segmentsAndGroups["loading"]!
        #expect(!loading.canChangeMatching && !loading.offersLeaderboards && !loading.offersBracketBoards)
        #expect(loading.shownGroupChoice == .OFF, "a grouping not read yet shows as Off")
    }

    @Test func aSessionThatCantUseThemSaysToSignInAgain() {
        let signInAgain = Samples.segmentsAndGroups["signInAgain"]!
        #expect(!signInAgain.canChangeMatching && !signInAgain.canChangeGroups)
        #expect(read(SettingsTexts.shared.segmentsNote(state: signInAgain)) == "Sign in again to use segments.")
        #expect(read(SettingsTexts.shared.groupsNote(state: signInAgain)) == "Sign in again to use group activities.")
        #expect(read(SettingsTexts.shared.segmentsNote(state: segments))?.hasPrefix("Shares a copy of your routes") == true)
    }
}
