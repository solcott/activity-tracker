import SwiftUI

/// How stats read for one athlete: each value with its unit, in their unit system. The rules and the numbers are Kotlin's `StatText`
/// (dev/shared-display-text, ui/number-format); this passes the units and reads the strings. A view takes it from the environment,
/// where the signed-in session puts the athlete's.
struct Stats: @unchecked Sendable {
    // Unchecked because Kotlin's enum isn't declared Sendable in Swift; it is a plain value.
    let units: UnitSystem

    func distance(_ meters: Double?) -> String { read(StatText.shared.distance(meters: meters, units: units)) }

    /// Minutes per kilometer, or per mile.
    func pace(meters: Double?, seconds: Int64?) -> String { read(StatText.shared.pace(meters: meters, seconds: seconds, units: units)) }

    /// km/h, or mph.
    func speed(mmPerSecond: Int64?) -> String { read(StatText.shared.speed(mmPerSecond: mmPerSecond, units: units)) }

    /// Minutes per 100 meters, or per 100 yards.
    func swimPace(meters: Double?, seconds: Int64?) -> String {
        read(StatText.shared.swimPace(meters: meters, seconds: seconds, units: units))
    }

    /// The sport's average rate with its name (speed or pace), or nil for a sport where distance means little.
    func rate(sport: String, meters: Double?, seconds: Int64?) -> (label: String, value: String)? {
        StatText.shared.rate(sport: sport, meters: meters, seconds: seconds, units: units).map { (read($0.label), read($0.value)) }
    }

    /// Meters, or feet.
    func elevation(_ meters: Double?) -> String { read(StatText.shared.elevation(meters: meters, units: units)) }

    /// A dive depth, or another short length, to one decimal.
    func depth(_ meters: Double?) -> String { read(StatText.shared.depth(meters: meters, units: units)) }

    /// A record temperature, given in tenths of °C.
    func temperature(tenthsCelsius: Int64?) -> String { read(StatText.shared.temperature(tenthsCelsius: tenthsCelsius, units: units)) }

    /// A profile weight, given in grams.
    func weight(grams: Int64) -> String { read(StatText.shared.weight(grams: grams, units: units)) }

    /// A length of time as `m:ss`, or `h:mm:ss` from an hour up.
    static func duration(seconds: Int64) -> String { StatText.shared.duration(seconds: seconds) }

    private func read(_ text: any AppText) -> String { AppStrings.string(text) }
}

extension EnvironmentValues {
    /// The signed-in athlete's stats formatting. Metric until a session provides theirs, as in the shared UI.
    @Entry var stats = Stats(units: .Metric)
}

/// The names of things the app shows by a token or a kind, each with what it shows for one the app has no string for. Kotlin's
/// `LabelText` chooses; this reads the strings.
enum Labels {
    static func sport(_ sport: String) -> String { AppStrings.string(LabelText.shared.sport(sport: sport)) }

    static func activityType(_ type: KotlinActivityType) -> String { AppStrings.string(LabelText.shared.activityType(type: type)) }

    static func diveAlert(_ kind: String) -> String { AppStrings.string(LabelText.shared.diveAlert(kind: kind)) }

    static func sensorType(_ kind: String) -> String { AppStrings.string(LabelText.shared.sensorType(kind: kind)) }

    static func handle(_ handle: String) -> String { AppStrings.string(LabelText.shared.handle(handle: handle)) }

    static func gender(_ gender: String) -> String { AppStrings.string(LabelText.shared.gender(gender: gender)) }
}
