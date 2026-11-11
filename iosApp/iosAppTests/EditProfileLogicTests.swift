import Foundation
import OrgJetbrainsKotlinxKotlinxIoBytestring
import Testing
import UIKit

@testable import ActivityTracker

/// Edit profile's country search, through Swift's names and folding and Kotlin's rule (dev/shared-display-text).
struct CountryNamesTests {
    private let countries = CountryNames(locale: Locale(identifier: "en_US"))

    @Test func everyCountryIsListedByNameForNothingTyped() {
        #expect(countries.all.count == 249)
        #expect(countries.matches("").count == 249)
        #expect(countries.matches("  ").count == 249)
        #expect(countries.all.first?.name == "Afghanistan")
    }

    @Test func aTypedCodeComesFirst() {
        let found = countries.matches("us")
        #expect(found.first?.code == "US")
        #expect(found.contains { $0.code == "AU" })
        #expect(found.contains { $0.code == "BY" })
    }

    @Test func accentsAndCaseAreIgnored() {
        #expect(countries.matches("COTE").map(\.code) == ["CI"])
        #expect(countries.matches("åland").contains { $0.code == "AX" })
        #expect(countries.matches("zzz").isEmpty)
    }

    @Test func aCodeIsNamedInTheLocale() {
        #expect(countries.name(of: "US") == "United States")
        #expect(countries.name(of: "") == nil)
    }
}

/// Edit profile's photo pick: what reaches the presenter is Kotlin's `compressAvatar` JPEG, at most 512 pixels on its long edge.
@MainActor
struct AvatarPickTests {
    private func picture(width: Int, height: Int) -> Data {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format).image { context in
            UIColor.systemTeal.setFill()
            context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        }.jpegData(compressionQuality: 0.9)!
    }

    @Test func aLargePhotoBecomesASmallJpeg() async throws {
        var sent: [KotlinByteString] = []
        await AvatarPick.avatar.pick({ picture(width: 2_000, height: 1_500) }) { sent.append($0) }
        let jpeg = try #require(sent.first).data
        #expect(jpeg.starts(with: [0xFF, 0xD8]))
        let image = try #require(UIImage(data: jpeg))
        #expect(image.size == CGSize(width: 512, height: 384))
    }

    @Test func bytesThatArentAPictureSendNothing() async {
        var sent: [KotlinByteString] = []
        await AvatarPick.avatar.pick({ Data([1, 2, 3, 4]) }) { sent.append($0) }
        #expect(sent.isEmpty)
    }
}
