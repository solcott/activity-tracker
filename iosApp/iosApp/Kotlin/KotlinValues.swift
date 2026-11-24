import Foundation
import OrgJetbrainsKotlinxKotlinxIoBytestring

// Kotlin values as Swift takes them and back (dev/ios-swift-interop).

extension KotlinByteString {
    /// The bytes as Swift's `Data`, copied in bulk through kotlinx-io.
    nonisolated var data: Data { ExportedKotlinPackages.kotlinx.io.bytestring.toNSData(self) as Data }
}

extension Data {
    /// The bytes as Kotlin's `ByteString`, copied in bulk through kotlinx-io.
    nonisolated var kotlinByteString: KotlinByteString { ExportedKotlinPackages.kotlinx.io.bytestring.toByteString(self as NSData) }
}

extension KotlinActivityType {
    /// What tells one type from another in a list.
    var key: String { "\(sport)/\(subSport ?? "")" }
}

/// A calendar day as a contract holds it (`LocalDate`) and as a SwiftUI date picker takes it (a `Date`), the same day either way in any
/// time zone (dev/ios-swift-interop). A day becomes the start of that day in `calendar`'s zone; a `Date` becomes the day it falls on there.
struct CalendarDay: Equatable {
    let year: Int
    let month: Int
    let day: Int

    init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    init(_ date: KotlinLocalDate) {
        // The month is kotlinx-datetime's `Month`, whose raw value is its ordinal.
        self.init(year: Int(date.year), month: Int(date.month.rawValue) + 1, day: Int(date.day))
    }

    /// The day `date` falls on in `calendar`.
    init(_ date: Date, in calendar: Calendar) {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        self.init(year: parts.year ?? 1970, month: parts.month ?? 1, day: parts.day ?? 1)
    }

    /// The start of the day in `calendar`, which is what a date picker shows as this day.
    func date(in calendar: Calendar) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day)) ?? Date(timeIntervalSince1970: 0)
    }

    var localDate: KotlinLocalDate {
        KotlinLocalDate(year: Int32(year), month: Int32(month), day: Int32(day))
    }
}
