import Foundation
import SwiftUI
import KotlinRuntime

/// The String Catalog as Kotlin reads it: a string by its shared name, filled with arguments that are already text. Kotlin's
/// `resolve` walks a choice of text (its arguments first, its fallback when a name has no string) and asks this for each string, so
/// the order and the fallback are written once and this only looks a name up (dev/shared-display-text).
///
/// It holds nothing, so it is safe to use from anywhere Kotlin calls it.
nonisolated final class CatalogText: KotlinBase, TextSource, @unchecked Sendable {
    static let shared = CatalogText(zone: .autoupdatingCurrent)

    /// The time zone a time of day is formatted in: the device's for `shared`, or a clock's (`ShownClock`).
    private let zone: TimeZone

    init(zone: TimeZone) {
        self.zone = zone
        super.init()
    }

    /// What a lookup returns for a name the catalog doesn't have: no real string is this.
    private static let missing = "\u{1}"

    func string(name: String, args: [String]) -> String? {
        guard let format = Self.format(named: name) else { return nil }
        // An entry without placeholders isn't a format string: its percent signs are written once.
        return args.isEmpty ? format : String(format: format, arguments: args)
    }

    func plural(name: String, quantity: Int32, args: [String]) -> String? {
        guard let format = Self.format(named: name) else { return nil }
        // Argument 1 is the quantity, which selects the form; the plural's own placeholders start at 2.
        return String(format: format, locale: .current, arguments: [Int(quantity)] + args)
    }

    /// A moment as a time of day on the clock the device's settings choose ("7:05 PM", "19:05"), as the shared UI's `timeFormat` reads
    /// it: in the UTC offset given (an activity's own clock), else in this source's time zone.
    func time(epochSeconds: Int64, utcOffsetMinutes: Int32?) -> String {
        let shownZone = utcOffsetMinutes.flatMap { TimeZone(secondsFromGMT: Int($0) * 60) } ?? zone
        return Date(timeIntervalSince1970: TimeInterval(epochSeconds))
            .formatted(Date.FormatStyle(date: .omitted, time: .shortened, timeZone: shownZone))
    }

    private static func format(named name: String) -> String? {
        let found = Bundle.main.localizedString(forKey: name, value: missing, table: nil)
        return found == missing ? nil : found
    }
}

/// Text chosen in Kotlin, as the app's strings read it.
enum AppStrings {
    /// `text` in the catalog's words: a stat with its unit, a label, an error.
    static func string(_ text: any AppText) -> String {
        TextPackage.resolve(text, source: CatalogText.shared)
    }
}

extension Text {
    /// Text chosen in Kotlin.
    init(_ text: any AppText) {
        self.init(verbatim: AppStrings.string(text))
    }
}
