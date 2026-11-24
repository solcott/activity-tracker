import CoreGraphics
import Foundation
import ImageIO
import OrgJetbrainsKotlinxKotlinxIoBytestring
import Testing
import UniformTypeIdentifiers

@testable import ActivityTracker

/// A picked photo's preview, decoded by ImageIO only as large as it is drawn.
struct PickedPhotoThumbnailTests {
    private func jpeg(width: Int, height: Int) throws -> Data {
        let sRGB = try #require(CGColorSpace(name: CGColorSpace.sRGB))
        let context = try #require(
            CGContext(
                data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                space: sRGB, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        context.setFillColor(red: 0.2, green: 0.5, blue: 0.8, alpha: 1)
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        let image = try #require(context.makeImage())
        let data = NSMutableData()
        let destination = try #require(CGImageDestinationCreateWithData(data, UTType.jpeg.identifier as CFString, 1, nil))
        CGImageDestinationAddImage(destination, image, nil)
        #expect(CGImageDestinationFinalize(destination))
        return data as Data
    }

    @Test func pickedThumbnailFillsTheSquareWithItsShorterSide() throws {
        let image = try #require(PickedPhotoThumbnails.thumbnail(of: try jpeg(width: 2000, height: 1000), filling: 216))
        #expect(image.size == CGSize(width: 432, height: 216))
    }

    @Test func pickedThumbnailNeverScalesUp() throws {
        let image = try #require(PickedPhotoThumbnails.thumbnail(of: try jpeg(width: 100, height: 50), filling: 216))
        #expect(image.size == CGSize(width: 100, height: 50))
    }

    @Test func pickedThumbnailOfBytesThatAreNotAPicture() {
        #expect(PickedPhotoThumbnails.thumbnail(of: Data("not a picture".utf8), filling: 216) == nil)
    }

    @Test func pickedThumbnailIsMadeOncePerPhotoAndSize() throws {
        let data = try jpeg(width: 640, height: 480)
        // Two Kotlin byte strings of the same bytes, as a state rebuilt by the presenter hands over.
        let first = data.kotlinByteString
        let second = data.kotlinByteString
        let made = try #require(PickedPhotoThumbnails.thumbnail(of: first, filling: 216))
        #expect(PickedPhotoThumbnails.thumbnail(of: second, filling: 216) === made)
        #expect(PickedPhotoThumbnails.thumbnail(of: first, filling: 264) !== made)
    }
}
