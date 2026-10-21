import ComposeApp
import FITSwiftSDK
import Foundation

/// The iOS FitDecoder: Kotlin/Native can't call Garmin's Swift FIT SDK, so this side translates its messages into the shared
/// DecodedActivity DTO and DecodedActivityMapper (Kotlin) does the rest, as on every other platform.
final class SwiftFitDecoder: FitDecoder {
    func decode(bytes: KotlinByteArray) throws -> DecodedActivity {
        let input = Self.data(from: bytes)

        let decoder = FITSwiftSDK.Decoder(stream: FITSwiftSDK.InputStream(data: input))
        guard (try? decoder.isFIT()) == true else { throw ActivityFileException(message: "Not a FIT file", cause: nil).asError() }
        let integrityDecoder = FITSwiftSDK.Decoder(stream: FITSwiftSDK.InputStream(data: input))
        guard (try? integrityDecoder.checkIntegrity()) == true else {
            throw ActivityFileException(message: "FIT file is corrupt or truncated", cause: nil).asError()
        }

        let listener = FitListener()
        decoder.addMesgListener(listener)
        do {
            try decoder.read()
        } catch {
            throw ActivityFileException(message: "Couldn't read FIT file: \(error)", cause: nil).asError()
        }
        let messages = listener.fitMessages
        let diveMessages = Self.diveMessages(messages)
        let session = messages.sessionMesgs.first

        return DecodedActivity(
            fileId: messages.fileIdMesgs.first.map(Self.fileId),
            sessions: messages.sessionMesgs.map(Self.summary),
            laps: messages.lapMesgs.map(Self.summary),
            records: messages.recordMesgs.map(Self.record),
            dive: FitDives.shared.interpret(
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
                DecodedJump(hangTimeSeconds: Self.double(m.getHangTime().map(Double.init)), distanceMeters: Self.double(m.getDistance().map(Double.init)))
            },
            athlete: Self.athlete(messages),
            gasSwitches: FitDives.shared.gasSwitches(messages: diveMessages),
            tankUpdates: FitDives.shared.tankUpdates(messages: diveMessages),
            diveAlerts: FitDives.shared.diveAlerts(messages: diveMessages),
            notes: nil,
            lengths: messages.lengthMesgs.map(Self.length),
            sets: messages.setMesgs.map(Self.set),
            splits: messages.splitMesgs.map(Self.split),
            hrMessages: messages.hrMesgs.map(Self.hrMessage),
            rrIntervalsSeconds: messages.hrvMesgs.flatMap { m in
                (m.getTime() ?? []).compactMap { $0 }.filter { FitHeart.shared.isValidRr(seconds: $0) }.map { KotlinDouble(value: $0) }
            },
            activityTimestampMillis: messages.activityMesgs.first?.getTimestamp().map { KotlinLong(value: Self.millis($0)) },
            activityLocalTimestampSeconds: messages.activityMesgs.first?.getLocalTimestamp().map { KotlinLong(value: Int64($0)) }
        )
    }

    /// An hr message's readings, padding (invalid values) dropped; HrMessages (Kotlin) anchors and merges them.
    private static func hrMessage(_ m: HrMesg) -> DecodedHrMessage {
        let readings = zip(m.getEventTimestamp() ?? [], m.getFilteredBpm() ?? []).compactMap { time, bpm -> (Double, Int32)? in
            guard let time, let bpm, FitHeart.shared.isValidReading(eventTimestamp: time, bpm: Int32(bpm)) else { return nil }
            return (time, Int32(bpm))
        }
        return DecodedHrMessage(
            timestampMillis: m.getTimestamp().map { KotlinLong(value: millis($0)) },
            fractionalSeconds: double(m.getFractionalTimestamp()),
            eventTimestamps: readings.map { KotlinDouble(value: $0.0) },
            filteredBpm: readings.map { KotlinInt(value: $0.1) }
        )
    }

    /// A record's developer fields, named and united by their field descriptions, for DeveloperFields (Kotlin) to recognize.
    private static func developerFields(_ m: RecordMesg) -> [DecodedDeveloperField] {
        m.developerFields.map { field in
            DecodedDeveloperField(
                name: field.getName().isEmpty ? nil : field.getName(),
                units: field.getUnits().isEmpty ? nil : field.getUnits(),
                value: double(number(field.getValue()))
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
            startTimeMillis: m.getStartTime().map { KotlinLong(value: millis($0)) },
            totalElapsedSeconds: double(m.getTotalElapsedTime()),
            swimStroke: m.getSwimStroke().map { String(describing: $0) },
            totalStrokes: int(m.getTotalStrokes().map(Int32.init)),
            lengthType: m.getLengthType().map { String(describing: $0) }
        )
    }

    private static func set(_ m: SetMesg) -> DecodedSet {
        // The SDK reads a value newer than its profile as .invalid.
        let category = m.getCategory()?.first.flatMap { $0 }.flatMap { $0 == .invalid ? nil : $0 }
        let subtype = m.getCategorySubtype()?.first.flatMap { $0 }
        return DecodedSet(
            startTimeMillis: m.getStartTime().map { KotlinLong(value: millis($0)) },
            timestampMillis: m.getTimestamp().map { KotlinLong(value: millis($0)) },
            durationSeconds: double(m.getDuration()),
            setType: m.getSetType().map { String(describing: $0) },
            category: category.map { String(describing: $0) },
            exercise: category.flatMap { c in subtype.flatMap { exerciseName(c, $0) } },
            repetitions: int(m.getRepetitions().map(Int32.init)),
            weightKg: double(m.getWeight())
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
            startTimeMillis: m.getStartTime().map { KotlinLong(value: millis($0)) },
            totalElapsedSeconds: double(m.getTotalElapsedTime()),
            totalTimerSeconds: double(m.getTotalTimerTime()),
            totalDistanceMeters: double(m.getTotalDistance()),
            totalAscentMeters: double(m.getTotalAscent().map(Double.init)),
            totalDescentMeters: double(m.getTotalDescent().map(Double.init)),
            avgSpeedMps: double(m.getAvgSpeed()),
            maxSpeedMps: double(m.getMaxSpeed()),
            avgHeartRate: int(m.getAvgHeartRate().map(Int32.init)),
            maxHeartRate: int(m.getMaxHeartRate().map(Int32.init)),
            totalCalories: int(m.getTotalCalories().map(Int32.init(truncatingIfNeeded:)))
        )
    }

    /// The athlete's settings on the device: zones_target (max HR, LTHR, FTP) and user_profile (resting HR).
    private static func athlete(_ messages: FitMessages) -> DecodedAthleteSettings? {
        let zones = messages.zonesTargetMesgs.first
        let profile = messages.userProfileMesgs.first
        if zones == nil && profile == nil { return nil }
        return DecodedAthleteSettings(
            maxHeartRate: int(zones?.getMaxHeartRate().map(Int32.init)),
            thresholdHeartRate: int(zones?.getThresholdHeartRate().map(Int32.init)),
            functionalThresholdPower: int(zones?.getFunctionalThresholdPower().map(Int32.init)),
            restingHeartRate: int(profile?.getRestingHeartRate().map(Int32.init))
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
            deviceIndex: int(m.getDeviceIndex().map(Int32.init)),
            sourceType: source.map { String(describing: $0) },
            deviceType: deviceType,
            manufacturer: m.getManufacturer().map { String(describing: $0) },
            product: product(m.getManufacturer(), garmin: try? m.getGarminProduct(), name: m.getProductName(), number: m.getProduct()),
            batteryStatus: m.getBatteryStatus().map { String(describing: $0) },
            batteryLevelPercent: int(m.getBatteryLevel().map(Int32.init)),
            serialNumber: m.getSerialNumber().flatMap { $0 == 0 ? nil : KotlinLong(value: Int64($0)) },
            antDeviceNumber: m.getAntDeviceNumber().flatMap { $0 == 0 ? nil : KotlinInt(value: Int32($0)) },
            antTransmissionType: int(m.getAntTransmissionType().map(Int32.init)),
            softwareVersion: double(m.getSoftwareVersion()),
            batteryVolts: double(m.getBatteryVoltage()),
            cumulativeOperatingSeconds: long(m.getCumOperatingTime())
        )
    }

    private static func gearChange(_ m: EventMesg) -> DecodedGearChange? {
        let event = m.getEvent()
        guard event == .rearGearChange || event == .frontGearChange else { return nil }
        return DecodedGearChange(
            timestampMillis: m.getTimestamp().map { KotlinLong(value: millis($0)) },
            rear: event == .rearGearChange,
            frontGear: int(m.getFrontGearNum().map(Int32.init)),
            frontTeeth: int(m.getFrontGear().map(Int32.init)),
            rearGear: int(m.getRearGearNum().map(Int32.init)),
            rearTeeth: int(m.getRearGear().map(Int32.init))
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
                    avgDepthMeters: double(m.getAvgDepth()),
                    maxDepthMeters: double(m.getMaxDepth()),
                    surfaceIntervalSeconds: double(m.getSurfaceInterval().map(Double.init)),
                    diveNumber: int(m.getDiveNumber().map(Int32.init(truncatingIfNeeded:))),
                    bottomTimeSeconds: double(m.getBottomTime()),
                    startCns: int(m.getStartCns().map(Int32.init)),
                    endCns: int(m.getEndCns().map(Int32.init)),
                    oxygenToxicityOtu: int(m.getO2Toxicity().map(Int32.init)),
                    avgRmv: double(m.getAvgRmv()),
                    avgVolumeSac: double(m.getAvgVolumeSac()),
                    avgPressureSac: double(m.getAvgPressureSac()),
                    startN2: int(m.getStartN2().map(Int32.init)),
                    endN2: int(m.getEndN2().map(Int32.init)),
                    avgAscentRate: double(m.getAvgAscentRate()),
                    maxAscentRate: double(m.getMaxAscentRate()),
                    avgDescentRate: double(m.getAvgDescentRate()),
                    maxDescentRate: double(m.getMaxDescentRate()),
                    descentTimeSeconds: double(m.getDescentTime())
                )
            },
            gradientFactorLow: int(settings?.getGfLow().map(Int32.init)),
            gradientFactorHigh: int(settings?.getGfHigh().map(Int32.init)),
            gases: messages.diveGasMesgs.map { m in
                FitDiveGas(
                    index: int(m.getMessageIndex().map(Int32.init)),
                    oxygenPercent: int(m.getOxygenContent().map(Int32.init)),
                    heliumPercent: int(m.getHeliumContent().map(Int32.init)),
                    status: m.getStatus().map { String(describing: $0) },
                    mode: m.getMode().map { String(describing: $0) }
                )
            },
            gasSwitches: messages.eventMesgs.compactMap { m in
                guard m.getEvent() == .diveGasSwitched, let data = m.getData() else { return nil }
                return FitGasSwitch(
                    timestampMillis: m.getTimestamp().map { KotlinLong(value: millis($0)) },
                    gasIndex: Int32(truncatingIfNeeded: data)
                )
            },
            tanks: messages.tankSummaryMesgs.map { m in
                FitTankSummary(
                    startPressureBar: double(m.getStartPressure()),
                    endPressureBar: double(m.getEndPressure()),
                    volumeUsedLiters: double(m.getVolumeUsed()),
                    sensor: m.getSensor().map { KotlinLong(value: Int64($0)) }
                )
            },
            entry: position(session?.getStartPositionLat(), session?.getStartPositionLong()),
            exit: position(session?.getEndPositionLat(), session?.getEndPositionLong()),
            sessionMaxDepthMeters: double(session?.getMaxDepth()),
            sessionAvgDepthMeters: double(session?.getAvgDepth()),
            sessionDiveNumber: int(session?.getDiveNumber().map(Int32.init(truncatingIfNeeded:))),
            waterType: settings?.getWaterType().map { String(describing: $0) },
            waterDensityKgPerM3: double(settings?.getWaterDensity().map(Double.init)),
            tankUpdates: messages.tankUpdateMesgs.map { m in
                FitTankUpdate(
                    timestampMillis: m.getTimestamp().map { KotlinLong(value: millis($0)) },
                    sensor: m.getSensor().map { KotlinLong(value: Int64($0)) },
                    pressureBar: double(m.getPressure())
                )
            },
            // The raw data, whatever the SDK names it: FitDives resolves it by FIT value.
            diveAlerts: messages.eventMesgs.compactMap { m in
                guard m.getEvent() == .diveAlert, let data = m.getData() else { return nil }
                return FitDiveAlert(
                    timestampMillis: m.getTimestamp().map { KotlinLong(value: millis($0)) },
                    value: Int32(truncatingIfNeeded: data),
                    sdkName: DiveAlert(rawValue: UInt8(truncatingIfNeeded: data)).map { String(describing: $0) }
                )
            },
            decoModel: settings?.getModel().map { String(describing: $0) },
            ppO2WarnBar: double(settings?.getPo2Warn()),
            ppO2CriticalBar: double(settings?.getPo2Critical()),
            ppO2DecoBar: double(settings?.getPo2Deco()),
            safetyStopEnabled: settings?.getSafetyStopEnabled().map { KotlinBoolean(value: $0) },
            safetyStopTimeSeconds: int(settings?.getSafetyStopTime().map(Int32.init)),
            lowSetpointBar: double(settings?.getCcrLowSetpoint()),
            highSetpointBar: double(settings?.getCcrHighSetpoint())
        )
    }

    private static func position(_ lat: Int32?, _ lon: Int32?) -> DivePosition? {
        guard let lat, let lon else { return nil }
        return DivePosition(
            latitude: FitUnits.shared.semicirclesToDegrees(semicircles: lat),
            longitude: FitUnits.shared.semicirclesToDegrees(semicircles: lon)
        )
    }

    private static func data(from bytes: KotlinByteArray) -> Data {
        Data((0..<bytes.size).map { UInt8(bitPattern: bytes.get(index: $0)) })
    }

    private static func fileId(_ m: FileIdMesg) -> DecodedSource {
        let manufacturer = m.getManufacturer()
        return DecodedSource(
            type: m.getType().map { String(describing: $0) },
            manufacturer: manufacturer.map { String(describing: $0) },
            product: product(manufacturer, garmin: try? m.getGarminProduct(), name: m.getProductName(), number: m.getProduct()),
            timeCreatedMillis: m.getTimeCreated().map { KotlinLong(value: millis($0)) },
            serialNumber: m.getSerialNumber().flatMap { $0 == 0 ? nil : String($0) },
            softwareVersion: nil
        )
    }

    private static func summary(_ m: SessionMesg) -> DecodedSummary {
        DecodedSummary(
            sport: m.getSport().map { String(describing: $0) },
            subSport: m.getSubSport().map { String(describing: $0) },
            startTimeMillis: m.getStartTime().map { KotlinLong(value: millis($0)) },
            totalElapsedSeconds: double(m.getTotalElapsedTime()),
            totalTimerSeconds: double(m.getTotalTimerTime()),
            totalDistanceMeters: double(m.getTotalDistance()),
            totalAscentMeters: double(m.getTotalAscent().map(Double.init)),
            avgSpeedMps: double(m.getEnhancedAvgSpeed() ?? m.getAvgSpeed()),
            maxSpeedMps: double(m.getEnhancedMaxSpeed() ?? m.getMaxSpeed()),
            avgHeartRate: int(m.getAvgHeartRate().map(Int32.init)),
            maxHeartRate: int(m.getMaxHeartRate().map(Int32.init)),
            avgPowerWatts: int(m.getAvgPower().map(Int32.init)),
            avgCadence: int(m.getAvgCadence().map(Int32.init)),
            avgTemperatureCelsius: double(m.getAvgTemperature().map(Double.init)),
            maxPowerWatts: int(m.getMaxPower().map(Int32.init)),
            normalizedPowerWatts: int(m.getNormalizedPower().map(Int32.init)),
            maxCadence: int(m.getMaxCadence().map(Int32.init)),
            totalDescentMeters: double(m.getTotalDescent().map(Double.init)),
            totalWorkJoules: double(m.getTotalWork().map(Double.init)),
            thresholdPowerWatts: int(m.getThresholdPower().map(Int32.init)),
            intensityFactor: double(m.getIntensityFactor()),
            trainingStressScore: double(m.getTrainingStressScore()),
            frontShiftCount: int(m.getFrontGearShiftCount().map(Int32.init)),
            rearShiftCount: int(m.getRearGearShiftCount().map(Int32.init)),
            jumpCount: int(m.getJumpCount().map(Int32.init)),
            totalGrit: double(m.getTotalGrit().map(Double.init)),
            avgFlow: double(m.getAvgFlow().map(Double.init)),
            totalCalories: int(m.getTotalCalories().map(Int32.init)),
            aerobicTrainingEffect: double(m.getTotalTrainingEffect()),
            anaerobicTrainingEffect: double(m.getTotalAnaerobicTrainingEffect()),
            poolLengthMeters: double(m.getPoolLength()),
            intensity: nil,
            lapTrigger: nil,
            totalCycles: m.getTotalCycles().map { KotlinLong(value: Int64($0)) },
            avgStrokeDistanceMeters: double(m.getAvgStrokeDistance()),
            sportProfileName: m.getSportProfileName().flatMap { $0.isEmpty ? nil : $0 },
            // The SDK keeps fields its profile doesn't name in the message, so Garmin's own are read by number.
            physiology: SessionPhysiology.companion.read(exerciseLoad: double(m.getTrainingLoadPeak())) { number in
                rawLong(m.getFieldValue(fieldNum: UInt8(number.int32Value)))
            }
        )
    }

    private static func summary(_ m: LapMesg) -> DecodedSummary {
        DecodedSummary(
            sport: m.getSport().map { String(describing: $0) },
            subSport: m.getSubSport().map { String(describing: $0) },
            startTimeMillis: m.getStartTime().map { KotlinLong(value: millis($0)) },
            totalElapsedSeconds: double(m.getTotalElapsedTime()),
            totalTimerSeconds: double(m.getTotalTimerTime()),
            totalDistanceMeters: double(m.getTotalDistance()),
            totalAscentMeters: double(m.getTotalAscent().map(Double.init)),
            avgSpeedMps: double(m.getEnhancedAvgSpeed() ?? m.getAvgSpeed()),
            maxSpeedMps: double(m.getEnhancedMaxSpeed() ?? m.getMaxSpeed()),
            avgHeartRate: int(m.getAvgHeartRate().map(Int32.init)),
            maxHeartRate: int(m.getMaxHeartRate().map(Int32.init)),
            avgPowerWatts: int(m.getAvgPower().map(Int32.init)),
            avgCadence: int(m.getAvgCadence().map(Int32.init)),
            avgTemperatureCelsius: double(m.getAvgTemperature().map(Double.init)),
            maxPowerWatts: int(m.getMaxPower().map(Int32.init)),
            normalizedPowerWatts: int(m.getNormalizedPower().map(Int32.init)),
            maxCadence: int(m.getMaxCadence().map(Int32.init)),
            totalDescentMeters: double(m.getTotalDescent().map(Double.init)),
            totalWorkJoules: double(m.getTotalWork().map(Double.init)),
            thresholdPowerWatts: nil,
            intensityFactor: nil,
            trainingStressScore: nil,
            frontShiftCount: int(m.getFrontGearShiftCount().map(Int32.init)),
            rearShiftCount: int(m.getRearGearShiftCount().map(Int32.init)),
            jumpCount: int(m.getJumpCount().map(Int32.init)),
            totalGrit: double(m.getTotalGrit().map(Double.init)),
            avgFlow: double(m.getAvgFlow().map(Double.init)),
            totalCalories: int(m.getTotalCalories().map(Int32.init)),
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
            timestampMillis: m.getTimestamp().map { KotlinLong(value: millis($0)) },
            latitude: double(m.getPositionLat().map { FitUnits.shared.semicirclesToDegrees(semicircles: $0) }),
            longitude: double(m.getPositionLong().map { FitUnits.shared.semicirclesToDegrees(semicircles: $0) }),
            altitudeMeters: double(m.getEnhancedAltitude() ?? m.getAltitude()),
            heartRate: int(m.getHeartRate().map(Int32.init)),
            cadence: int(m.getCadence().map(Int32.init)),
            powerWatts: int(m.getPower().map(Int32.init)),
            speedMps: double(m.getEnhancedSpeed() ?? m.getSpeed()),
            distanceMeters: double(m.getDistance()),
            temperatureCelsius: double(m.getTemperature().map(Double.init)),
            depthMeters: double(m.getDepth()),
            ppO2Bar: double(m.getPo2()),
            measuredPpO2Bars: [],
            cnsPercent: int(m.getCnsLoad().map(Int32.init)),
            n2LoadPercent: int(m.getN2Load().map(Int32.init)),
            ndlSeconds: long(m.getNdlTime()),
            nextStopDepthMeters: double(m.getNextStopDepth()),
            nextStopSeconds: long(m.getNextStopTime()),
            ttsSeconds: long(m.getTimeToSurface()),
            respirationRate: double(m.getEnhancedRespirationRate() ?? m.getRespirationRate().map(Double.init)),
            verticalOscillationMm: double(m.getVerticalOscillation()),
            stanceTimeMs: double(m.getStanceTime()),
            stepLengthMm: double(m.getStepLength()),
            verticalRatioPercent: double(m.getVerticalRatio()),
            stanceTimeBalancePercent: double(m.getStanceTimeBalance()),
            leftRightBalance: int(m.getLeftRightBalance().map(Int32.init)),
            leftTorqueEffectiveness: double(m.getLeftTorqueEffectiveness()),
            rightTorqueEffectiveness: double(m.getRightTorqueEffectiveness()),
            leftPedalSmoothness: double(m.getLeftPedalSmoothness()),
            rightPedalSmoothness: double(m.getRightPedalSmoothness()),
            leftPcoMm: int(m.getLeftPco().map(Int32.init)),
            rightPcoMm: int(m.getRightPco().map(Int32.init)),
            coreTemperatureCelsius: double(m.getCoreTemperature()),
            saturatedHemoglobinPercent: double(m.getSaturatedHemoglobinPercent()),
            totalHemoglobinConc: double(m.getTotalHemoglobinConc()),
            ebikeBatteryPercent: int(m.getEbikeBatteryLevel().map(Int32.init)),
            ebikeAssistMode: int(m.getEbikeAssistMode().map(Int32.init)),
            ebikeAssistLevelPercent: int(m.getEbikeAssistLevelPercent().map(Int32.init)),
            developerFields: developerFields(m),
            formPowerWatts: nil,
            legSpringStiffnessKnPerM: nil
        )
    }

    private static func millis(_ dateTime: DateTime) -> Int64 {
        Int64(dateTime.date.timeIntervalSince1970 * 1000)
    }

    private static func double(_ value: Double?) -> KotlinDouble? { value.map { KotlinDouble(value: $0) } }

    private static func int(_ value: Int32?) -> KotlinInt? { value.map { KotlinInt(value: $0) } }

    private static func long(_ value: UInt32?) -> KotlinLong? { value.map { KotlinLong(value: Int64($0)) } }

    /** A field's raw value as a Kotlin long, whatever integer type the SDK decoded it to; null for anything else. */
    private static func rawLong(_ value: Any?) -> KotlinLong? {
        switch value {
        case let v as UInt8: return KotlinLong(value: Int64(v))
        case let v as UInt16: return KotlinLong(value: Int64(v))
        case let v as UInt32: return KotlinLong(value: Int64(v))
        case let v as Int8: return KotlinLong(value: Int64(v))
        case let v as Int16: return KotlinLong(value: Int64(v))
        case let v as Int32: return KotlinLong(value: Int64(v))
        case let v as Int64: return KotlinLong(value: v)
        case let v as Float64: return v.isFinite ? KotlinLong(value: Int64(v)) : nil
        default: return nil
        }
    }
}
