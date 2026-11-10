import ActivityFile
import FITSwiftSDK
import Foundation
import IosShell
import KotlinRuntime
import KotlinStdlib

// Swift export gives a Kotlin module no top-level names for its types when it is exported only through another module's signatures, as
// :core-activityfile is through :iosShell's. These name the ones this file uses.
typealias ActivityFilePackage = ExportedKotlinPackages.io.github.solcott.activitytracker.activityfile
typealias KotlinByteArray = ExportedKotlinPackages.kotlin.ByteArray
typealias DecodedActivity = ActivityFilePackage.DecodedActivity
typealias DecodedAthleteSettings = ActivityFilePackage.DecodedAthleteSettings
typealias DecodedDeveloperField = ActivityFilePackage.DecodedDeveloperField
typealias DecodedGearChange = ActivityFilePackage.DecodedGearChange
typealias DecodedHrMessage = ActivityFilePackage.DecodedHrMessage
typealias DecodedJump = ActivityFilePackage.DecodedJump
typealias DecodedLength = ActivityFilePackage.DecodedLength
typealias DecodedSample = ActivityFilePackage.DecodedSample
typealias DecodedSensor = ActivityFilePackage.DecodedSensor
typealias DecodedSet = ActivityFilePackage.DecodedSet
typealias DecodedSource = ActivityFilePackage.DecodedSource
typealias DecodedSplit = ActivityFilePackage.DecodedSplit
typealias DecodedSummary = ActivityFilePackage.DecodedSummary
typealias DivePosition = ActivityFilePackage.DivePosition
typealias FitDecoder = ActivityFilePackage.FitDecoder
typealias FitDiveAlert = ActivityFilePackage.FitDiveAlert
typealias FitDiveGas = ActivityFilePackage.FitDiveGas
typealias FitDiveMessages = ActivityFilePackage.FitDiveMessages
typealias FitDiveSummary = ActivityFilePackage.FitDiveSummary
typealias FitGasSwitch = ActivityFilePackage.FitGasSwitch
typealias FitTankSummary = ActivityFilePackage.FitTankSummary
typealias FitTankUpdate = ActivityFilePackage.FitTankUpdate
typealias SessionPhysiology = ActivityFilePackage.SessionPhysiology

/// The iOS FitDecoder: Kotlin/Native can't call Garmin's Swift FIT SDK, so this side translates its messages into the shared
/// DecodedActivity DTO and DecodedActivityMapper (Kotlin) does the rest, as on every other platform.
nonisolated final class SwiftFitDecoder: KotlinBase, FitDecoder {
    func decode(bytes: KotlinByteArray) throws -> DecodedActivity {
        let input = Self.data(from: bytes)

        let decoder = FITSwiftSDK.Decoder(stream: FITSwiftSDK.InputStream(data: input))
        guard (try? decoder.isFIT()) == true else { throw FitSupport.shared.unreadable(message: "Not a FIT file") }
        let integrityDecoder = FITSwiftSDK.Decoder(stream: FITSwiftSDK.InputStream(data: input))
        guard (try? integrityDecoder.checkIntegrity()) == true else {
            throw FitSupport.shared.unreadable(message: "FIT file is corrupt or truncated")
        }

        let listener = FitListener()
        decoder.addMesgListener(listener)
        do {
            try decoder.read()
        } catch {
            throw FitSupport.shared.unreadable(message: "Couldn't read FIT file: \(error)")
        }
        let messages = listener.fitMessages
        let diveMessages = Self.diveMessages(messages)
        let session = messages.sessionMesgs.first

        return DecodedActivity(
            fileId: messages.fileIdMesgs.first.map(Self.fileId),
            sessions: messages.sessionMesgs.map(Self.summary),
            laps: messages.lapMesgs.map(Self.summary),
            records: messages.recordMesgs.map(Self.record),
            dive: FitSupport.shared.dive(
                messages: diveMessages,
                sport: session?.getSport().map { String(describing: $0) },
                subSport: session?.getSubSport().map { String(describing: $0) }
            ),
            // FIT times are UTC.
            localStart: nil,
            title: nil,
            sensors: messages.deviceInfoMesgs.map(Self.sensor),
            gearChanges: messages.eventMesgs.compactMap(Self.gearChange),
            jumps: messages.jumpMesgs.map { m in
                DecodedJump(hangTimeSeconds: m.getHangTime().map(Double.init), distanceMeters: m.getDistance().map(Double.init))
            },
            athlete: Self.athlete(messages),
            gasSwitches: FitSupport.shared.gasSwitches(messages: diveMessages),
            tankUpdates: FitSupport.shared.tankUpdates(messages: diveMessages),
            diveAlerts: FitSupport.shared.diveAlerts(messages: diveMessages),
            notes: nil,
            lengths: messages.lengthMesgs.map(Self.length),
            sets: messages.setMesgs.map(Self.set),
            splits: messages.splitMesgs.map(Self.split),
            hrMessages: messages.hrMesgs.map(Self.hrMessage),
            rrIntervalsSeconds: messages.hrvMesgs.flatMap { m in
                (m.getTime() ?? []).compactMap { $0 }.filter { FitSupport.shared.isValidRr(seconds: $0) }
            },
            activityTimestampMillis: messages.activityMesgs.first?.getTimestamp().map { Self.millis($0) },
            activityLocalTimestampSeconds: messages.activityMesgs.first?.getLocalTimestamp().map { Int64($0) }
        )
    }

    /// An hr message's readings, padding (invalid values) dropped; HrMessages (Kotlin) anchors and merges them.
    private static func hrMessage(_ m: HrMesg) -> DecodedHrMessage {
        let readings = zip(m.getEventTimestamp() ?? [], m.getFilteredBpm() ?? []).compactMap { time, bpm -> (Double, Int32)? in
            guard let time, let bpm, FitSupport.shared.isValidHeartRateReading(eventTimestamp: time, bpm: Int32(bpm)) else { return nil }
            return (time, Int32(bpm))
        }
        return DecodedHrMessage(
            timestampMillis: m.getTimestamp().map { millis($0) },
            fractionalSeconds: m.getFractionalTimestamp(),
            eventTimestamps: readings.map { $0.0 },
            filteredBpm: readings.map { $0.1 }
        )
    }

    /// A record's developer fields, named and united by their field descriptions, for DeveloperFields (Kotlin) to recognize.
    private static func developerFields(_ m: RecordMesg) -> [DecodedDeveloperField] {
        m.developerFields.map { field in
            DecodedDeveloperField(
                name: field.getName().isEmpty ? nil : field.getName(),
                units: field.getUnits().isEmpty ? nil : field.getUnits(),
                value: number(field.getValue())
            )
        }
    }

    /// A field value of any numeric type the SDK returns, as a Double.
    private static func number(_ value: Any?) -> Double? {
        switch value {
        case let v as Double: v
        case let v as Float: Double(v)
        case let v as any BinaryInteger: Double(v)
        default: nil
        }
    }

    private static func length(_ m: LengthMesg) -> DecodedLength {
        DecodedLength(
            startTimeMillis: m.getStartTime().map { millis($0) },
            totalElapsedSeconds: m.getTotalElapsedTime(),
            swimStroke: m.getSwimStroke().map { String(describing: $0) },
            totalStrokes: m.getTotalStrokes().map(Int32.init),
            lengthType: m.getLengthType().map { String(describing: $0) }
        )
    }

    private static func set(_ m: SetMesg) -> DecodedSet {
        // The SDK reads a value newer than its profile as .invalid.
        let category = m.getCategory()?.first.flatMap { $0 }.flatMap { $0 == .invalid ? nil : $0 }
        let subtype = m.getCategorySubtype()?.first.flatMap { $0 }
        return DecodedSet(
            startTimeMillis: m.getStartTime().map { millis($0) },
            timestampMillis: m.getTimestamp().map { millis($0) },
            durationSeconds: m.getDuration(),
            setType: m.getSetType().map { String(describing: $0) },
            category: category.map { String(describing: $0) },
            exercise: category.flatMap { c in subtype.flatMap { exerciseName(c, $0) } },
            repetitions: m.getRepetitions().map(Int32.init),
            weightKg: m.getWeight()
        )
    }

    /// The name of exercise subtype in its category's exercise-name enum (a benchPress set's subtype is a BenchPressExerciseName).
    private static func exerciseName(_ category: ExerciseCategory, _ subtype: UInt16) -> String? {
        let name: Any? =
            switch category {
            case .bandedExercises: BandedExercisesExerciseName(rawValue: subtype)
            case .battleRope: BattleRopeExerciseName(rawValue: subtype)
            case .benchPress: BenchPressExerciseName(rawValue: subtype)
            case .bike: BikeExerciseName(rawValue: subtype)
            case .bikeOutdoor: BikeOutdoorExerciseName(rawValue: subtype)
            case .calfRaise: CalfRaiseExerciseName(rawValue: subtype)
            case .cardio: CardioExerciseName(rawValue: subtype)
            case .carry: CarryExerciseName(rawValue: subtype)
            case .chop: ChopExerciseName(rawValue: subtype)
            case .core: CoreExerciseName(rawValue: subtype)
            case .crunch: CrunchExerciseName(rawValue: subtype)
            case .curl: CurlExerciseName(rawValue: subtype)
            case .deadlift: DeadliftExerciseName(rawValue: subtype)
            case .elliptical: EllipticalExerciseName(rawValue: subtype)
            case .floorClimb: FloorClimbExerciseName(rawValue: subtype)
            case .flye: FlyeExerciseName(rawValue: subtype)
            case .hipRaise: HipRaiseExerciseName(rawValue: subtype)
            case .hipStability: HipStabilityExerciseName(rawValue: subtype)
            case .hipSwing: HipSwingExerciseName(rawValue: subtype)
            case .hyperextension: HyperextensionExerciseName(rawValue: subtype)
            case .indoorBike: IndoorBikeExerciseName(rawValue: subtype)
            case .indoorRow: IndoorRowExerciseName(rawValue: subtype)
            case .ladder: LadderExerciseName(rawValue: subtype)
            case .lateralRaise: LateralRaiseExerciseName(rawValue: subtype)
            case .legCurl: LegCurlExerciseName(rawValue: subtype)
            case .legRaise: LegRaiseExerciseName(rawValue: subtype)
            case .lunge: LungeExerciseName(rawValue: subtype)
            case .move: MoveExerciseName(rawValue: subtype)
            case .olympicLift: OlympicLiftExerciseName(rawValue: subtype)
            case .plank: PlankExerciseName(rawValue: subtype)
            case .plyo: PlyoExerciseName(rawValue: subtype)
            case .pose: PoseExerciseName(rawValue: subtype)
            case .pullUp: PullUpExerciseName(rawValue: subtype)
            case .pushUp: PushUpExerciseName(rawValue: subtype)
            case .row: RowExerciseName(rawValue: subtype)
            case .run: RunExerciseName(rawValue: subtype)
            case .runIndoor: RunIndoorExerciseName(rawValue: subtype)
            case .sandbag: SandbagExerciseName(rawValue: subtype)
            case .shoulderPress: ShoulderPressExerciseName(rawValue: subtype)
            case .shoulderStability: ShoulderStabilityExerciseName(rawValue: subtype)
            case .shrug: ShrugExerciseName(rawValue: subtype)
            case .sitUp: SitUpExerciseName(rawValue: subtype)
            case .sled: SledExerciseName(rawValue: subtype)
            case .sledgeHammer: SledgeHammerExerciseName(rawValue: subtype)
            case .squat: SquatExerciseName(rawValue: subtype)
            case .stairStepper: StairStepperExerciseName(rawValue: subtype)
            case .suspension: SuspensionExerciseName(rawValue: subtype)
            case .tire: TireExerciseName(rawValue: subtype)
            case .totalBody: TotalBodyExerciseName(rawValue: subtype)
            case .tricepsExtension: TricepsExtensionExerciseName(rawValue: subtype)
            case .warmUp: WarmUpExerciseName(rawValue: subtype)
            default: nil
            }
        return name.map { String(describing: $0) }
    }

    private static func split(_ m: SplitMesg) -> DecodedSplit {
        DecodedSplit(
            splitType: m.getSplitType().map { String(describing: $0) },
            startTimeMillis: m.getStartTime().map { millis($0) },
            totalElapsedSeconds: m.getTotalElapsedTime(),
            totalTimerSeconds: m.getTotalTimerTime(),
            totalDistanceMeters: m.getTotalDistance(),
            totalAscentMeters: m.getTotalAscent().map(Double.init),
            totalDescentMeters: m.getTotalDescent().map(Double.init),
            avgSpeedMps: m.getAvgSpeed(),
            maxSpeedMps: m.getMaxSpeed(),
            avgHeartRate: m.getAvgHeartRate().map(Int32.init),
            maxHeartRate: m.getMaxHeartRate().map(Int32.init),
            totalCalories: m.getTotalCalories().map(Int32.init(truncatingIfNeeded:))
        )
    }

    /// The athlete's settings on the device: zones_target (max HR, LTHR, FTP) and user_profile (resting HR).
    private static func athlete(_ messages: FitMessages) -> DecodedAthleteSettings? {
        let zones = messages.zonesTargetMesgs.first
        let profile = messages.userProfileMesgs.first
        if zones == nil && profile == nil { return nil }
        return DecodedAthleteSettings(
            maxHeartRate: zones?.getMaxHeartRate().map(Int32.init),
            thresholdHeartRate: zones?.getThresholdHeartRate().map(Int32.init),
            functionalThresholdPower: zones?.getFunctionalThresholdPower().map(Int32.init),
            restingHeartRate: profile?.getRestingHeartRate().map(Int32.init)
        )
    }

    private static func sensor(_ m: DeviceInfoMesg) -> DecodedSensor {
        let source = m.getSourceType()
        let deviceType: String? =
            switch source {
            case .antplus: (try? m.getAntplusDeviceType()).map { String(describing: $0) }
            case .bluetoothLowEnergy: (try? m.getBleDeviceType()).map { String(describing: $0) }
            case .local: (try? m.getLocalDeviceType()).map { String(describing: $0) }
            default: nil
            }
        return DecodedSensor(
            deviceIndex: m.getDeviceIndex().map(Int32.init),
            sourceType: source.map { String(describing: $0) },
            deviceType: deviceType,
            manufacturer: m.getManufacturer().map { String(describing: $0) },
            product: product(m.getManufacturer(), garmin: try? m.getGarminProduct(), name: m.getProductName(), number: m.getProduct()),
            batteryStatus: m.getBatteryStatus().map { String(describing: $0) },
            batteryLevelPercent: m.getBatteryLevel().map(Int32.init),
            serialNumber: m.getSerialNumber().flatMap { $0 == 0 ? nil : Int64($0) },
            antDeviceNumber: m.getAntDeviceNumber().flatMap { $0 == 0 ? nil : Int32($0) },
            antTransmissionType: m.getAntTransmissionType().map(Int32.init),
            softwareVersion: m.getSoftwareVersion(),
            batteryVolts: m.getBatteryVoltage(),
            cumulativeOperatingSeconds: long(m.getCumOperatingTime())
        )
    }

    private static func gearChange(_ m: EventMesg) -> DecodedGearChange? {
        let event = m.getEvent()
        guard event == .rearGearChange || event == .frontGearChange else { return nil }
        return DecodedGearChange(
            timestampMillis: m.getTimestamp().map { millis($0) },
            rear: event == .rearGearChange,
            frontGear: m.getFrontGearNum().map(Int32.init),
            frontTeeth: m.getFrontGear().map(Int32.init),
            rearGear: m.getRearGearNum().map(Int32.init),
            rearTeeth: m.getRearGear().map(Int32.init)
        )
    }

    /// The SDK's name for a Garmin product, else the device's own product name, else the product number.
    private static func product(_ manufacturer: Manufacturer?, garmin: GarminProduct?, name: String?, number: UInt16?) -> String? {
        if manufacturer == .garmin { return garmin.map { String(describing: $0) } }
        return name ?? number.map { String($0) }
    }

    /// The dive messages as they are; FitDives (Kotlin) interprets them, as for the Java and JS decoders.
    private static func diveMessages(_ messages: FitMessages) -> FitDiveMessages {
        let session = messages.sessionMesgs.first
        let settings = messages.diveSettingsMesgs.first
        return FitDiveMessages(
            summaries: messages.diveSummaryMesgs.map { m in
                FitDiveSummary(
                    referenceMesg: m.getReferenceMesg().map { String(describing: $0) },
                    avgDepthMeters: m.getAvgDepth(),
                    maxDepthMeters: m.getMaxDepth(),
                    surfaceIntervalSeconds: m.getSurfaceInterval().map(Double.init),
                    diveNumber: m.getDiveNumber().map(Int32.init(truncatingIfNeeded:)),
                    bottomTimeSeconds: m.getBottomTime(),
                    startCns: m.getStartCns().map(Int32.init),
                    endCns: m.getEndCns().map(Int32.init),
                    oxygenToxicityOtu: m.getO2Toxicity().map(Int32.init),
                    avgRmv: m.getAvgRmv(),
                    avgVolumeSac: m.getAvgVolumeSac(),
                    avgPressureSac: m.getAvgPressureSac(),
                    startN2: m.getStartN2().map(Int32.init),
                    endN2: m.getEndN2().map(Int32.init),
                    avgAscentRate: m.getAvgAscentRate(),
                    maxAscentRate: m.getMaxAscentRate(),
                    avgDescentRate: m.getAvgDescentRate(),
                    maxDescentRate: m.getMaxDescentRate(),
                    descentTimeSeconds: m.getDescentTime()
                )
            },
            gradientFactorLow: settings?.getGfLow().map(Int32.init),
            gradientFactorHigh: settings?.getGfHigh().map(Int32.init),
            gases: messages.diveGasMesgs.map { m in
                FitDiveGas(
                    index: m.getMessageIndex().map(Int32.init),
                    oxygenPercent: m.getOxygenContent().map(Int32.init),
                    heliumPercent: m.getHeliumContent().map(Int32.init),
                    status: m.getStatus().map { String(describing: $0) },
                    mode: m.getMode().map { String(describing: $0) }
                )
            },
            gasSwitches: messages.eventMesgs.compactMap { m in
                guard m.getEvent() == .diveGasSwitched, let data = m.getData() else { return nil }
                return FitGasSwitch(
                    timestampMillis: m.getTimestamp().map { millis($0) },
                    gasIndex: Int32(truncatingIfNeeded: data)
                )
            },
            tanks: messages.tankSummaryMesgs.map { m in
                FitTankSummary(
                    startPressureBar: m.getStartPressure(),
                    endPressureBar: m.getEndPressure(),
                    volumeUsedLiters: m.getVolumeUsed(),
                    sensor: m.getSensor().map { Int64($0) }
                )
            },
            entry: position(session?.getStartPositionLat(), session?.getStartPositionLong()),
            exit: position(session?.getEndPositionLat(), session?.getEndPositionLong()),
            sessionMaxDepthMeters: session?.getMaxDepth(),
            sessionAvgDepthMeters: session?.getAvgDepth(),
            sessionDiveNumber: session?.getDiveNumber().map(Int32.init(truncatingIfNeeded:)),
            waterType: settings?.getWaterType().map { String(describing: $0) },
            waterDensityKgPerM3: settings?.getWaterDensity().map(Double.init),
            tankUpdates: messages.tankUpdateMesgs.map { m in
                FitTankUpdate(
                    timestampMillis: m.getTimestamp().map { millis($0) },
                    sensor: m.getSensor().map { Int64($0) },
                    pressureBar: m.getPressure()
                )
            },
            // The raw data, whatever the SDK names it: FitDives resolves it by FIT value.
            diveAlerts: messages.eventMesgs.compactMap { m in
                guard m.getEvent() == .diveAlert, let data = m.getData() else { return nil }
                return FitDiveAlert(
                    timestampMillis: m.getTimestamp().map { millis($0) },
                    value: Int32(truncatingIfNeeded: data),
                    sdkName: DiveAlert(rawValue: UInt8(truncatingIfNeeded: data)).map { String(describing: $0) }
                )
            },
            decoModel: settings?.getModel().map { String(describing: $0) },
            ppO2WarnBar: settings?.getPo2Warn(),
            ppO2CriticalBar: settings?.getPo2Critical(),
            ppO2DecoBar: settings?.getPo2Deco(),
            safetyStopEnabled: settings?.getSafetyStopEnabled(),
            safetyStopTimeSeconds: settings?.getSafetyStopTime().map(Int32.init),
            lowSetpointBar: settings?.getCcrLowSetpoint(),
            highSetpointBar: settings?.getCcrHighSetpoint()
        )
    }

    private static func position(_ lat: Int32?, _ lon: Int32?) -> DivePosition? {
        guard let lat, let lon else { return nil }
        return DivePosition(
            latitude: FitSupport.shared.semicirclesToDegrees(semicircles: lat),
            longitude: FitSupport.shared.semicirclesToDegrees(semicircles: lon)
        )
    }

    private static func data(from bytes: KotlinByteArray) -> Data {
        Data((0..<bytes.size).map { UInt8(bitPattern: bytes[$0]) })
    }

    private static func fileId(_ m: FileIdMesg) -> DecodedSource {
        let manufacturer = m.getManufacturer()
        return DecodedSource(
            type: m.getType().map { String(describing: $0) },
            manufacturer: manufacturer.map { String(describing: $0) },
            product: product(manufacturer, garmin: try? m.getGarminProduct(), name: m.getProductName(), number: m.getProduct()),
            timeCreatedMillis: m.getTimeCreated().map { millis($0) },
            serialNumber: m.getSerialNumber().flatMap { $0 == 0 ? nil : String($0) },
            softwareVersion: nil
        )
    }

    private static func summary(_ m: SessionMesg) -> DecodedSummary {
        DecodedSummary(
            sport: m.getSport().map { String(describing: $0) },
            subSport: m.getSubSport().map { String(describing: $0) },
            startTimeMillis: m.getStartTime().map { millis($0) },
            totalElapsedSeconds: m.getTotalElapsedTime(),
            totalTimerSeconds: m.getTotalTimerTime(),
            totalDistanceMeters: m.getTotalDistance(),
            totalAscentMeters: m.getTotalAscent().map(Double.init),
            avgSpeedMps: m.getEnhancedAvgSpeed() ?? m.getAvgSpeed(),
            maxSpeedMps: m.getEnhancedMaxSpeed() ?? m.getMaxSpeed(),
            avgHeartRate: m.getAvgHeartRate().map(Int32.init),
            maxHeartRate: m.getMaxHeartRate().map(Int32.init),
            avgPowerWatts: m.getAvgPower().map(Int32.init),
            avgCadence: m.getAvgCadence().map(Int32.init),
            avgTemperatureCelsius: m.getAvgTemperature().map(Double.init),
            maxPowerWatts: m.getMaxPower().map(Int32.init),
            normalizedPowerWatts: m.getNormalizedPower().map(Int32.init),
            maxCadence: m.getMaxCadence().map(Int32.init),
            totalDescentMeters: m.getTotalDescent().map(Double.init),
            totalWorkJoules: m.getTotalWork().map(Double.init),
            thresholdPowerWatts: m.getThresholdPower().map(Int32.init),
            intensityFactor: m.getIntensityFactor(),
            trainingStressScore: m.getTrainingStressScore(),
            frontShiftCount: m.getFrontGearShiftCount().map(Int32.init),
            rearShiftCount: m.getRearGearShiftCount().map(Int32.init),
            jumpCount: m.getJumpCount().map(Int32.init),
            totalGrit: m.getTotalGrit().map(Double.init),
            avgFlow: m.getAvgFlow().map(Double.init),
            totalCalories: m.getTotalCalories().map(Int32.init),
            aerobicTrainingEffect: m.getTotalTrainingEffect(),
            anaerobicTrainingEffect: m.getTotalAnaerobicTrainingEffect(),
            poolLengthMeters: m.getPoolLength(),
            intensity: nil,
            lapTrigger: nil,
            totalCycles: m.getTotalCycles().map { Int64($0) },
            avgStrokeDistanceMeters: m.getAvgStrokeDistance(),
            sportProfileName: m.getSportProfileName().flatMap { $0.isEmpty ? nil : $0 },
            // The SDK keeps fields its profile doesn't name in the message, so Garmin's own are read by number.
            physiology: SessionPhysiology.Companion.shared.read(exerciseLoad: m.getTrainingLoadPeak()) { number in
                rawLong(m.getFieldValue(fieldNum: UInt8(number)))
            }
        )
    }

    private static func summary(_ m: LapMesg) -> DecodedSummary {
        DecodedSummary(
            sport: m.getSport().map { String(describing: $0) },
            subSport: m.getSubSport().map { String(describing: $0) },
            startTimeMillis: m.getStartTime().map { millis($0) },
            totalElapsedSeconds: m.getTotalElapsedTime(),
            totalTimerSeconds: m.getTotalTimerTime(),
            totalDistanceMeters: m.getTotalDistance(),
            totalAscentMeters: m.getTotalAscent().map(Double.init),
            avgSpeedMps: m.getEnhancedAvgSpeed() ?? m.getAvgSpeed(),
            maxSpeedMps: m.getEnhancedMaxSpeed() ?? m.getMaxSpeed(),
            avgHeartRate: m.getAvgHeartRate().map(Int32.init),
            maxHeartRate: m.getMaxHeartRate().map(Int32.init),
            avgPowerWatts: m.getAvgPower().map(Int32.init),
            avgCadence: m.getAvgCadence().map(Int32.init),
            avgTemperatureCelsius: m.getAvgTemperature().map(Double.init),
            maxPowerWatts: m.getMaxPower().map(Int32.init),
            normalizedPowerWatts: m.getNormalizedPower().map(Int32.init),
            maxCadence: m.getMaxCadence().map(Int32.init),
            totalDescentMeters: m.getTotalDescent().map(Double.init),
            totalWorkJoules: m.getTotalWork().map(Double.init),
            thresholdPowerWatts: nil,
            intensityFactor: nil,
            trainingStressScore: nil,
            frontShiftCount: m.getFrontGearShiftCount().map(Int32.init),
            rearShiftCount: m.getRearGearShiftCount().map(Int32.init),
            jumpCount: m.getJumpCount().map(Int32.init),
            totalGrit: m.getTotalGrit().map(Double.init),
            avgFlow: m.getAvgFlow().map(Double.init),
            totalCalories: m.getTotalCalories().map(Int32.init),
            aerobicTrainingEffect: nil,
            anaerobicTrainingEffect: nil,
            poolLengthMeters: nil,
            intensity: m.getIntensity().map { String(describing: $0) },
            lapTrigger: m.getLapTrigger().map { String(describing: $0) },
            totalCycles: nil,
            avgStrokeDistanceMeters: nil,
            sportProfileName: nil,
            physiology: nil
        )
    }

    private static func record(_ m: RecordMesg) -> DecodedSample {
        DecodedSample(
            timestampMillis: m.getTimestamp().map { millis($0) },
            latitude: m.getPositionLat().map { FitSupport.shared.semicirclesToDegrees(semicircles: $0) },
            longitude: m.getPositionLong().map { FitSupport.shared.semicirclesToDegrees(semicircles: $0) },
            altitudeMeters: m.getEnhancedAltitude() ?? m.getAltitude(),
            heartRate: m.getHeartRate().map(Int32.init),
            cadence: m.getCadence().map(Int32.init),
            powerWatts: m.getPower().map(Int32.init),
            speedMps: m.getEnhancedSpeed() ?? m.getSpeed(),
            distanceMeters: m.getDistance(),
            temperatureCelsius: m.getTemperature().map(Double.init),
            depthMeters: m.getDepth(),
            ppO2Bar: m.getPo2(),
            measuredPpO2Bars: [],
            cnsPercent: m.getCnsLoad().map(Int32.init),
            n2LoadPercent: m.getN2Load().map(Int32.init),
            ndlSeconds: long(m.getNdlTime()),
            nextStopDepthMeters: m.getNextStopDepth(),
            nextStopSeconds: long(m.getNextStopTime()),
            ttsSeconds: long(m.getTimeToSurface()),
            respirationRate: m.getEnhancedRespirationRate() ?? m.getRespirationRate().map(Double.init),
            verticalOscillationMm: m.getVerticalOscillation(),
            stanceTimeMs: m.getStanceTime(),
            stepLengthMm: m.getStepLength(),
            verticalRatioPercent: m.getVerticalRatio(),
            stanceTimeBalancePercent: m.getStanceTimeBalance(),
            leftRightBalance: m.getLeftRightBalance().map(Int32.init),
            leftTorqueEffectiveness: m.getLeftTorqueEffectiveness(),
            rightTorqueEffectiveness: m.getRightTorqueEffectiveness(),
            leftPedalSmoothness: m.getLeftPedalSmoothness(),
            rightPedalSmoothness: m.getRightPedalSmoothness(),
            leftPcoMm: m.getLeftPco().map(Int32.init),
            rightPcoMm: m.getRightPco().map(Int32.init),
            coreTemperatureCelsius: m.getCoreTemperature(),
            saturatedHemoglobinPercent: m.getSaturatedHemoglobinPercent(),
            totalHemoglobinConc: m.getTotalHemoglobinConc(),
            ebikeBatteryPercent: m.getEbikeBatteryLevel().map(Int32.init),
            ebikeAssistMode: m.getEbikeAssistMode().map(Int32.init),
            ebikeAssistLevelPercent: m.getEbikeAssistLevelPercent().map(Int32.init),
            developerFields: developerFields(m),
            formPowerWatts: nil,
            legSpringStiffnessKnPerM: nil
        )
    }

    private static func millis(_ dateTime: DateTime) -> Int64 {
        Int64(dateTime.date.timeIntervalSince1970 * 1000)
    }

    private static func long(_ value: UInt32?) -> Int64? { value.map { Int64($0) } }

    /// A field's raw value as a 64-bit integer, whatever integer type the SDK decoded it to; nil for anything else.
    private static func rawLong(_ value: Any?) -> Int64? {
        switch value {
        case let v as UInt8: return Int64(v)
        case let v as UInt16: return Int64(v)
        case let v as UInt32: return Int64(v)
        case let v as Int8: return Int64(v)
        case let v as Int16: return Int64(v)
        case let v as Int32: return Int64(v)
        case let v as Int64: return v
        case let v as Float64: return v.isFinite ? Int64(v) : nil
        default: return nil
        }
    }
}
