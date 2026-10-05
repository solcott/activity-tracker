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
            records: messages.recordMesgs.map(Self.record)
        )
    }

    private static func data(from bytes: KotlinByteArray) -> Data {
        Data((0..<bytes.size).map { UInt8(bitPattern: bytes.get(index: $0)) })
    }

    private static func fileId(_ m: FileIdMesg) -> DecodedSource {
        let manufacturer = m.getManufacturer()
        let product: String? =
            if manufacturer == .garmin, let garmin = try? m.getGarminProduct() {
                String(describing: garmin)
            } else {
                m.getProductName() ?? m.getProduct().map { String($0) }
            }
        return DecodedSource(
            type: m.getType().map { String(describing: $0) },
            manufacturer: manufacturer.map { String(describing: $0) },
            product: product,
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
            avgTemperatureCelsius: double(m.getAvgTemperature().map(Double.init))
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
            avgTemperatureCelsius: double(m.getAvgTemperature().map(Double.init))
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
            temperatureCelsius: double(m.getTemperature().map(Double.init))
        )
    }

    private static func millis(_ dateTime: DateTime) -> Int64 {
        Int64(dateTime.date.timeIntervalSince1970 * 1000)
    }

    private static func double(_ value: Double?) -> KotlinDouble? { value.map { KotlinDouble(value: $0) } }

    private static func int(_ value: Int32?) -> KotlinInt? { value.map { KotlinInt(value: $0) } }
}
