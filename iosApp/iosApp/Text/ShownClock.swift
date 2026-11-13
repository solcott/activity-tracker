import Foundation
import SwiftUI

/// The clock that words about the day are read on ("Today", "until tomorrow at 1:10 AM"): the current time and the time zone, which Kotlin
/// is given to decide the day (`DayText`), and in which a time of day is formatted, so both are read on one clock. The device's by
/// default; previews and snapshots fix it, as the shared UI's previews fix `LocalClock` and `LocalTimeZone`.
struct ShownClock: @unchecked Sendable {
    // Unchecked because a function isn't Sendable; the device's reads the system clock and the fixed one a constant.
    let zone: TimeZone
    private let currentDate: () -> Date

    /// The device's clock, in its time zone as it is when asked.
    static var device: ShownClock { ShownClock(zone: .autoupdatingCurrent, now: Date.init) }

    init(zone: TimeZone, now: @escaping () -> Date) {
        self.zone = zone
        currentDate = now
    }

    /// A clock stopped at `epochSeconds`, in `zone`.
    static func fixed(atEpochSeconds epochSeconds: Int64, zone: TimeZone) -> ShownClock {
        ShownClock(zone: zone) { Date(timeIntervalSince1970: TimeInterval(epochSeconds)) }
    }

    /// The current time, for a date picker's bounds ("no later than today"), so a preview's fixed clock bounds it too.
    var date: Date { currentDate() }

    /// The current time, as Kotlin reads one.
    var now: KotlinInstant {
        KotlinInstant.Companion.shared.fromEpochMilliseconds(epochMilliseconds: Int64(currentDate().timeIntervalSince1970 * 1000))
    }

    /// The time zone, as Kotlin reads one.
    var kotlinZone: KotlinTimeZone { KotlinTimeZone.Companion.shared.of(zoneId: zone.identifier) }

    /// `text` in the catalog's words, with a time of day formatted in this clock's zone.
    func string(_ text: any AppText) -> String {
        TextPackage.resolve(text, source: CatalogText(zone: zone))
    }

    /// `text` as a `Text`, read on this clock.
    func text(_ text: any AppText) -> Text {
        Text(verbatim: string(text))
    }
}

extension EnvironmentValues {
    /// The clock words about the day are read on: the device's, until a preview or snapshot fixes one.
    @Entry var clock = ShownClock.device
}
