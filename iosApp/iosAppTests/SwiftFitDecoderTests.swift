import Foundation
import OrgJetbrainsKotlinxKotlinxIoBytestring
import Testing

@testable import ActivityTracker

/// The Swift FIT decoder's handling of the bytes Kotlin hands it.
struct SwiftFitDecoderTests {
    /// `data` as a Kotlin `ByteArray`, as `FitDecoder.decode` receives it.
    private func kotlinBytes(_ data: Data) -> KotlinByteArray {
        ExportedKotlinPackages.kotlinx.io.bytestring.toByteString(data as NSData)
            .toByteArray(startIndex: 0, endIndex: Int32(data.count))
    }

    @Test func copiesEveryByteUnchanged() {
        let data = Data((0..<1024).map { UInt8(truncatingIfNeeded: $0 &* 37) })
        #expect(SwiftFitDecoder.data(from: kotlinBytes(data)) == data)
    }

    @Test func copiesNoBytes() {
        #expect(SwiftFitDecoder.data(from: kotlinBytes(Data())).isEmpty)
    }

    @Test func rejectsBytesThatAreNotFit() {
        #expect(throws: (any Error).self) {
            try SwiftFitDecoder().decode(bytes: kotlinBytes(Data("not a fit file".utf8)))
        }
    }
}
