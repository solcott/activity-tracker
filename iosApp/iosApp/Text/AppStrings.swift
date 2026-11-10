import Foundation
import SwiftUI
import KotlinRuntime

/// The String Catalog as Kotlin reads it: a string by its shared name, filled with arguments that are already text. Kotlin's
/// `resolve` walks a choice of text (its arguments first, its fallback when a name has no string) and asks this for each string, so
/// the order and the fallback are written once and this only looks a name up (dev/shared-display-text).
///
/// It holds nothing, so it is safe to use from anywhere Kotlin calls it.
nonisolated final class CatalogText: KotlinBase, TextSource, @unchecked Sendable {
    static let shared = CatalogText()

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

    /// A moment as a time of day in the device's time zone, on the clock its settings choose ("7:05 PM", "19:05"), as the shared UI's
    /// `timeFormat` reads it.
    func time(epochSeconds: Int64) -> String {
        Date(timeIntervalSince1970: TimeInterval(epochSeconds)).formatted(date: .omitted, time: .shortened)
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
