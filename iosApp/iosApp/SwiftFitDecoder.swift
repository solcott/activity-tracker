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

        return DecodedActivity(
            fileId: messages.fileIdMesgs.first.map(Self.fileId),
            sessions: messages.sessionMesgs.map(Self.summary),
            laps: messages.lapMesgs.map(Self.summary),
            records: messages.recordMesgs.map(Self.record),
            dive: Self.dive(messages),
            // FIT times are UTC.
            localStart: nil,
            title: nil,
            sensors: messages.deviceInfoMesgs.map(Self.sensor),
            gearChanges: messages.eventMesgs.compactMap(Self.gearChange),
            jumps: messages.jumpMesgs.map { m in
                DecodedJump(hangTimeSeconds: Self.double(m.getHangTime().map(Double.init)), distanceMeters: Self.double(m.getDistance().map(Double.init)))
            },
            athlete: Self.athlete(messages)
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
            batteryLevelPercent: int(m.getBatteryLevel().map(Int32.init))
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
    private static func dive(_ messages: FitMessages) -> DecodedDive? {
        let session = messages.sessionMesgs.first
        let settings = messages.diveSettingsMesgs.first
        let diveMessages = FitDiveMessages(
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
                    avgPressureSac: double(m.getAvgPressureSac())
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
                m.getEvent() == .diveGasSwitched ? m.getData().map { KotlinInt(value: Int32(truncatingIfNeeded: $0)) } : nil
            },
            tanks: messages.tankSummaryMesgs.map { m in
                FitTankSummary(
                    startPressureBar: double(m.getStartPressure()),
                    endPressureBar: double(m.getEndPressure()),
                    volumeUsedLiters: double(m.getVolumeUsed())
                )
            },
            entry: position(session?.getStartPositionLat(), session?.getStartPositionLong()),
            exit: position(session?.getEndPositionLat(), session?.getEndPositionLong()),
            sessionMaxDepthMeters: double(session?.getMaxDepth()),
            sessionAvgDepthMeters: double(session?.getAvgDepth()),
            sessionDiveNumber: int(session?.getDiveNumber().map(Int32.init(truncatingIfNeeded:)))
        )
        return FitDives.shared.interpret(
            messages: diveMessages,
            sport: session?.getSport().map { String(describing: $0) },
            subSport: session?.getSubSport().map { String(describing: $0) }
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
            timeCreatedMillis: m.getTimeCreated().map { KotlinLong(value: millis($0)) }
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
            avgFlow: double(m.getAvgFlow().map(Double.init))
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
            avgFlow: double(m.getAvgFlow().map(Double.init))
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
            depthMeters: double(m.getDepth())
        )
    }

    private static func millis(_ dateTime: DateTime) -> Int64 {
        Int64(dateTime.date.timeIntervalSince1970 * 1000)
    }

    private static func double(_ value: Double?) -> KotlinDouble? { value.map { KotlinDouble(value: $0) } }

    private static func int(_ value: Int32?) -> KotlinInt? { value.map { KotlinInt(value: $0) } }
}
