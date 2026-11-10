import Testing

@testable import ActivityTracker

/// What the Gear list and the blocked and muted lists say and offer (ui/settings-layout, dev/shared-display-text, dev/ios-swift-interop).
/// The rules are Kotlin's, with their own tests; these hold that Swift reads each on the shared samples, and the catalog words each as
/// the shared pages do.
@MainActor
struct SettingsListsTextTests {
    private func read(_ text: (any AppText)?) -> String? { text.map { AppStrings.string($0) } }

    private var gear: GearLists { Samples.gearList["loaded"]!.content.data as! GearLists }

    private func row(_ name: String) -> GearRow {
        let active = gear.active.elements(as: GearGroup.self).flatMap { $0.items.elements(as: GearRow.self) }
        return (active + gear.retired.elements(as: GearRow.self)).first { $0.name == name }!
    }

    @Test func eachItemReadsItsKindVisibilityAndTotal() {
        let metric = UnitSystem.Metric
        #expect(read(GearListTexts.shared.detail(row: row("Gravel bike"), showRetired: false)) == "Bike · Public")
        #expect(read(GearListTexts.shared.total(row: row("Gravel bike"), units: metric)) == "3,200 km")
        #expect(read(GearListTexts.shared.detail(row: row("Trail shoes"), showRetired: false)) == "Shoes · Only me")
        #expect(read(GearListTexts.shared.total(row: row("Trail shoes"), units: metric)) == "No activities yet")
        #expect(read(GearListTexts.shared.total(row: row("Powder skis"), units: metric)) == "14 days · 41,000 m")
        #expect(read(GearListTexts.shared.total(row: row("Powder skis"), units: .Imperial)) == "14 days · 134,514 ft")
        #expect(read(GearListTexts.shared.total(row: row("Helmet"), units: metric)) == "120 h")
        // Totals not yet read: no figure.
        #expect(read(GearListTexts.shared.total(row: row("Old road bike"), units: metric)) == nil)
        #expect(read(GearListTexts.shared.detail(row: row("Old road bike"), showRetired: true)) == "Bike · Public · Retired")
    }

    @Test func theGroupsAreNamedByType() {
        let types = gear.active.elements(as: GearGroup.self).map { read(GearTexts.shared.type(type: $0.type)) }
        #expect(types == ["Bike", "Shoes", "Skis", "Helmet"])
        #expect(read(GearTexts.shared.type(type: "hoverboard")) == "Other")
        #expect(read(GearTexts.shared.kind(type: "bike", kind: "hoverboard")) == "Bike")
    }

    @Test func collectionsShowWithTheirCountsOnceThereIsGear() {
        let collections = gear.collections.elements(as: GearCollectionRow.self)
        #expect(collections.map { read(GearListTexts.shared.collectionCount(row: $0)) } == ["4 items", "3 items"])
        #expect(gear.showsCollections)
        #expect(!gear.explainsCollections)
        let empty = Samples.gearList["empty"]!.content.data as! GearLists
        #expect(empty.isEmpty)
        #expect(!empty.showsCollections)
    }

    @Test func theBlockedListsWords() {
        let blocked = Samples.blockList["loaded"]!
        let muted = Samples.blockListVariants["muted"]!
        #expect(read(BlockListTexts.shared.title(kind: blocked.kind)) == "Blocked accounts")
        #expect(read(BlockListTexts.shared.title(kind: muted.kind)) == "Muted accounts")
        #expect(read(BlockListTexts.shared.remove(kind: blocked.kind)) == "Unblock")
        #expect(read(BlockListTexts.shared.remove(kind: muted.kind)) == "Unmute")
        #expect(read(BlockListTexts.shared.empty(kind: blocked.kind)) == "You haven't blocked anyone.")
        #expect(read(BlockListTexts.shared.empty(kind: muted.kind)) == "You haven't muted anyone.")
        #expect(blocked.loadErrorKind == .LoadBlocks)
        #expect(muted.loadErrorKind == .LoadMutes)
    }

    @Test func accountsReachSwiftWithTheirNamesAndIds() {
        let accounts = Samples.blockList["loaded"]!.accounts.items.compactMap { $0 as? BlockListAccount }
        #expect(accounts.map { AppPackage.getShownName($0.profile) } == ["Bob", "Carol"])
        #expect(accounts.map { read(LabelText.shared.handle(handle: $0.profile.handle.value)) } == ["@bob.test", "@carol.test"])
        #expect(accounts.map(\.profile.did.value) == ["did:plc:bob", "did:plc:carol"])
    }

    @Test func anUnblockInFlightABlueskyBlockAndAFailure() {
        let state = Samples.blockListVariants["actions"]!
        let accounts = state.accounts.items.compactMap { $0 as? BlockListAccount }
        #expect(state.removing.elements(as: Did.self).map(\.value) == ["did:plc:bob"])
        #expect(accounts.map { state.isRemoving(account: $0) } == [true, false, false])
        #expect(accounts.map { read(BlockListTexts.shared.note(account: $0)) } == [nil, nil, "Blocked on Bluesky. Unblock them there."])
        #expect(accounts.map(\.removable) == [true, true, false])
        #expect(state.actionError.map { AppStrings.string(ErrorTexts.shared.of(error: $0)) }?.contains("reach the server") == true)
    }

    @Test func anAvatarShowsTheInitial() {
        #expect(read(AppPackage.avatarInitial(name: "Bob")) == "B")
        #expect(read(AppPackage.avatarInitial(name: "dave.test")) == "D")
    }
}
