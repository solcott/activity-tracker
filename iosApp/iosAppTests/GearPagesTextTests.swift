import Testing

@testable import ActivityTracker

/// What a gear collection and Ignored devices say and offer (ui/settings-layout, dev/shared-display-text, dev/ios-swift-interop). The
/// rules are Kotlin's, with their own tests; these hold that Swift reads each on the shared samples, and the catalog words each as the
/// shared pages do.
@MainActor
struct GearPagesTextTests {
    private func read(_ text: (any AppText)?) -> String? { text.map { AppStrings.string($0) } }

    private func detail(_ state: GearCollectionState?) -> GearCollectionDetail? { state?.content.data as? GearCollectionDetail }

    private var sensors: [LinkedSensor] { Samples.ignoredSensors["loaded"]!.sensors.elements(as: LinkedSensor.self) }

    @Test func aCollectionListsItsItemsInOrderWithRetiredMarked() throws {
        let state = try #require(Samples.gearCollection["loaded"])
        let collection = try #require(detail(state))
        #expect(read(state.title) == "Dive kit")
        #expect(collection.notes == "Cold water setup")
        let members = collection.members.elements(as: GearRow.self)
        #expect(members.map { read(GearListTexts.shared.detail(row: $0, showRetired: true)) } == [
            "Regulator · Public", "Fullsuit · Only me · Retired", "Fins · Public",
        ])
        #expect(members.map(\.rkey) == ["3kreg", "3ksuit", "3kfins"])
    }

    @Test func anEmptyCollectionSaysSo() throws {
        let state = try #require(Samples.gearCollection["empty"])
        #expect(read(state.title) == "Winter training")
        #expect(try #require(detail(state)).members.elements(as: GearRow.self).isEmpty)
        #expect(String(localized: .gearCollectionEmpty) == "No items yet")
    }

    @Test func beforeItLoadsItIsCollectionsWithNoActions() throws {
        for sample in ["loading", "failed"] {
            let state = try #require(Samples.gearCollection[sample])
            #expect(read(state.title) == "Collections")
            #expect(!state.showsActions)
        }
        let loaded = try #require(Samples.gearCollection["loaded"])
        #expect(loaded.showsActions)
        #expect(loaded.actionsEnabled)
    }

    @Test func whileDeletingEditAndDeleteShowButCantBeUsed() throws {
        let state = try #require(Samples.gearCollectionVariants["deleting"])
        #expect(state.showsActions)
        #expect(!state.actionsEnabled)
    }

    @Test func deletingAsksFirstInTheSharedWords() throws {
        #expect(try #require(Samples.gearCollectionVariants["confirmingDelete"]).confirmingDelete)
        #expect(String(localized: .gearCollectionDeleteTitle) == "Delete this collection?")
        #expect(String(localized: .gearCollectionDeleteMessage) == "Its items, and the gear on your activities, stay as they are.")
        #expect(String(localized: .gearCollectionDelete) == "Delete collection")
        #expect(String(localized: .actionCancel) == "Cancel")
        #expect(String(localized: .gearCollectionEdit) == "Edit collection")
    }

    @Test func aFailedDeleteSaysTheServerCouldntBeReached() throws {
        let state = try #require(Samples.gearCollectionVariants["deleteFailed"])
        #expect(state.error.map { AppStrings.string(ErrorTexts.shared.of(error: $0)) }?.contains("reach the server") == true)
        #expect(state.actionsEnabled)
        #expect(try #require(detail(state)).members.size == 3)
    }

    @Test func ignoredDevicesReadByMakerModelAndSerial() {
        #expect(sensors.map { read(LinkedSensorTexts.shared.name(sensor: $0)) } == ["Garmin Edge 840", "Polar H10"])
        #expect(sensors.map { read(LinkedSensorTexts.shared.detail(sensor: $0)) } == [
            "Serial …2071", "Known only by its model, so it may match other units",
        ])
        #expect(sensors.map(\.serialTail) == ["2071", nil])
        #expect(sensors.map(\.weak) == [false, true])
        #expect(String(localized: .gearUnignore) == "Un-ignore")
        #expect(read(LinkedSensorTexts.shared.unignore(sensor: sensors[0])) == "Un-ignore Garmin Edge 840")
    }

    @Test func aModelNamingItsMakerAndUnlinkingReadAloud() {
        let perdix = LinkedSensor(id: "perdix", manufacturer: "Shearwater", model: "Shearwater Perdix AI", serialTail: nil, weak: false)
        #expect(read(LinkedSensorTexts.shared.name(sensor: perdix)) == "Shearwater Perdix AI")
        #expect(read(LinkedSensorTexts.shared.detail(sensor: perdix)) == nil)
        let power = LinkedSensor(id: "power", manufacturer: "SRAM", model: "1052", serialTail: nil, weak: false)
        #expect(read(LinkedSensorTexts.shared.unlink(sensor: power)) == "Unlink SRAM 1052")
    }

    @Test func everyIgnoredSampleReadsItsDevicesTyped() {
        for (name, state) in Samples.ignoredSensors.merging(Samples.ignoredSensorsVariants, uniquingKeysWith: { a, _ in a }) {
            let count = state.sensors.elements(as: LinkedSensor.self).count
            #expect(count == (["loading", "failed", "nothingIgnored"].contains(name) ? 0 : 2), "\(name)")
        }
    }

    @Test func nothingIgnoredExplainsTheList() {
        #expect(
            String(localized: .gearIgnoredEmpty)
                == "No ignored devices. Devices you mark as not gear when importing are listed here.")
        #expect(String(localized: .gearIgnoredSensors) == "Ignored devices")
    }

    @Test func aFailedUnignoreSaysTheServerCouldntBeReached() throws {
        let state = try #require(Samples.ignoredSensorsVariants["unignoreFailed"])
        #expect(state.error.map { AppStrings.string(ErrorTexts.shared.of(error: $0)) }?.contains("reach the server") == true)
        #expect(state.sensors.elements(as: LinkedSensor.self).count == 2)
    }
}
