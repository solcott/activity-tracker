import Foundation
import IosShell
import OrgJetbrainsKotlinxKotlinxIoBytestring

/// Edit profile's photo pick: the picked image as the JPEG Kotlin's `compressAvatar` makes of it, as the shared pick does.
typealias AvatarPick = PhotoPickOf<KotlinByteString>

extension PhotoPickOf where Photo == KotlinByteString {
    /// Compresses with `IosApp.compressAvatar`.
    static var avatar: AvatarPick {
        AvatarPick { data in
            nonisolated(unsafe) let bytes = ExportedKotlinPackages.kotlinx.io.bytestring.toByteString(data as NSData)
            return try? await IosApp.shared.compressAvatar(image: bytes)
        }
    }
}

/// Edit profile's countries, named in `locale` and searched by Kotlin's rule (`CountrySearch`, which the Compose field asks too). The
/// names are the platform's, as the shared field's are (`countryName`, the same NSLocale lookup on iOS); the folding of case and accents
/// is the platform's too, applied to the names and to what was typed alike.
struct CountryNames {
    /// Every country, in Kotlin's order.
    let all: [CountryName]
    private let locale: Locale

    init(locale: Locale = .current) {
        self.locale = locale
        let names = CountrySearch.shared.codes.map { code in
            let name = locale.localizedString(forRegionCode: code) ?? code
            return CountryName(code: code, name: name, key: Self.fold(name))
        }
        all = CountrySearch.shared.sorted(names: names)
    }

    /// The name of the country with `code`, or nil for none.
    func name(of code: String) -> String? {
        code.isEmpty ? nil : (locale.localizedString(forRegionCode: code) ?? code)
    }

    /// What `typed` lists: every country for nothing, else the one whose code was typed first, then names containing it.
    func matches(_ typed: String) -> [CountryName] {
        CountrySearch.shared.matches(sorted: all, key: Self.fold(typed.trimmingCharacters(in: .whitespaces)))
    }

    /// `text` without accents and in lower case, as the shared field folds it (`stripDiacritics`, then lower case).
    static func fold(_ text: String) -> String {
        text.folding(options: .diacriticInsensitive, locale: nil).lowercased()
    }
}
