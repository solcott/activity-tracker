import Foundation

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

/// SwiftUI's move of a list's row, as the collection form's `Move` takes it (`EditGearCollectionEvent.Move`).
enum MemberMove {
    /// The move of the row at `source` to before the row at `destination` in the old order (`onMove`'s offsets), as Kotlin's `from` and
    /// `to`, both indexes into the list, `to` the row's index once moved. Nil when the row lands where it was.
    static func of(source: IndexSet, destination: Int) -> (from: Int, to: Int)? {
        guard source.count == 1, let from = source.first else { return nil }
        let to = destination > from ? destination - 1 : destination
        return to == from ? nil : (from, to)
    }
}

/// The items ticked in the collection form's Add items, in the order ticked: Add adds them in that order, as the shared sheet does.
struct TickedItems: Equatable {
    private(set) var rkeys: [String] = []

    func isTicked(_ rkey: String) -> Bool { rkeys.contains(rkey) }

    /// Ticks `rkey` last, or unticks it; ticking it again puts it last.
    mutating func toggle(_ rkey: String) {
        if let index = rkeys.firstIndex(of: rkey) {
            rkeys.remove(at: index)
        } else {
            rkeys.append(rkey)
        }
    }
}
