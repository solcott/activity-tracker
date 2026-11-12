import CoreGraphics
import Foundation
import ImageIO
import Testing
import UniformTypeIdentifiers

@testable import ActivityTracker

/// A picked photo's compression (ios-libwebp-photos): WebP from libwebp through Kotlin's steps, upright and without metadata.
struct PhotoCompressorTests {
    /// A `width` × `height` picture drawn by `paint` into sRGB pixels, written as `type` with `properties` (an orientation, GPS).
    private func picture(
        width: Int, height: Int, type: UTType = .jpeg, properties: [CFString: Any] = [:], paint: (CGContext) -> Void
    ) throws -> Data {
        let context = try #require(
            CGContext(
                data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        paint(context)
        let image = try #require(context.makeImage())
        let data = NSMutableData()
        let destination = try #require(CGImageDestinationCreateWithData(data, type.identifier as CFString, 1, nil))
        var all = properties
        all[kCGImageDestinationLossyCompressionQuality] = 0.95
        CGImageDestinationAddImage(destination, image, all as CFDictionary)
        #expect(CGImageDestinationFinalize(destination))
        return data as Data
    }

    /// A camera-sized picture of coloured 24-pixel blocks, which compresses like a photo.
    private func cameraPhoto() throws -> Data {
        try picture(width: 4_032, height: 3_024) { context in
            var seed: UInt32 = 1
            for x in stride(from: 0, to: 4_032, by: 24) {
                for y in stride(from: 0, to: 3_024, by: 24) {
                    seed = seed &* 1_103_515_245 &+ 12_345
                    context.setFillColor(
                        red: CGFloat(seed >> 16 & 0xFF) / 255, green: CGFloat(seed >> 8 & 0xFF) / 255, blue: CGFloat(seed & 0xFF) / 255,
                        alpha: 1)
                    context.fill(CGRect(x: x, y: y, width: 24, height: 24))
                }
            }
        }
    }

    /// The RIFF chunks' four-character names after the `WEBP` form type.
    private func chunks(_ webp: Data) -> [String] {
        let bytes = [UInt8](webp)
        var names: [String] = []
        var offset = 12
        while offset + 8 <= bytes.count {
            names.append(String(decoding: bytes[offset..<offset + 4], as: UTF8.self))
            let size = Int(bytes[offset + 4]) | Int(bytes[offset + 5]) << 8 | Int(bytes[offset + 6]) << 16 | Int(bytes[offset + 7]) << 24
            offset += 8 + size + size % 2
        }
        return names
    }

    private func isWebP(_ data: Data) -> Bool {
        data.count > 12 && data.prefix(4) == Data("RIFF".utf8) && data[8..<12] == Data("WEBP".utf8)
    }

    /// The colour of the decoded `webp`'s pixel at `x`, `y` (from the top left), as red, green and blue.
    private func pixel(_ webp: Data, x: Int, y: Int) throws -> (Int, Int, Int) {
        let source = try #require(CGImageSourceCreateWithData(webp as CFData, nil))
        let image = try #require(CGImageSourceCreateImageAtIndex(source, 0, nil))
        var rgba = [UInt8](repeating: 0, count: 4)
        let context = try #require(
            CGContext(
                data: &rgba, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4, space: CGColorSpace(name: CGColorSpace.sRGB)!,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        context.draw(image, in: CGRect(x: -x, y: y - image.height + 1, width: image.width, height: image.height))
        return (Int(rgba[0]), Int(rgba[1]), Int(rgba[2]))
    }

    @Test func kotlinsStepsReachSwift() {
        #expect(PhotoCompressor.steps.first == PhotoCompressor.Step(maxPixels: 2_048, quality: 80))
        #expect(PhotoCompressor.steps.last == PhotoCompressor.Step(maxPixels: 1_280, quality: 60))
        #expect(PhotoCompressor.steps.count == 4)
        #expect(PhotoCompressor.targetBytes == 1_500_000)
    }

    @Test func aLargePhotoBecomesAWebpUnderTheTarget() async throws {
        let photo = try #require(await PhotoCompressor.compress(try cameraPhoto()))
        #expect(isWebP(photo.bytes))
        #expect(photo.width == 2_048 && photo.height == 1_536)
        #expect(photo.bytes.count <= 1_500_000, "\(photo.bytes.count) bytes")
        let source = try #require(CGImageSourceCreateWithData(photo.bytes as CFData, nil))
        let decoded = try #require(CGImageSourceCreateImageAtIndex(source, 0, nil))
        #expect(decoded.width == 2_048 && decoded.height == 1_536)
    }

    @Test func aSmallPhotoKeepsItsSize() throws {
        let small = try picture(width: 300, height: 400) { context in
            context.setFillColor(gray: 0.5, alpha: 1)
            context.fill(CGRect(x: 0, y: 0, width: 300, height: 400))
        }
        let photo = try #require(PhotoCompressor.encode(small, step: .init(maxPixels: 2_048, quality: 80)))
        #expect(photo.width == 300 && photo.height == 400)
    }

    @Test func aPhotoTakenOnItsSideComesOutUpright() throws {
        // Stored 400 × 300, red on top and blue below; orientation 6 says its top row is the right of the picture as taken.
        let sideways = try picture(width: 400, height: 300, properties: [kCGImagePropertyOrientation: 6]) { context in
            context.setFillColor(red: 0, green: 0, blue: 1, alpha: 1)
            context.fill(CGRect(x: 0, y: 0, width: 400, height: 150))
            context.setFillColor(red: 1, green: 0, blue: 0, alpha: 1)
            context.fill(CGRect(x: 0, y: 150, width: 400, height: 150))
        }
        let photo = try #require(PhotoCompressor.encode(sideways, step: .init(maxPixels: 2_048, quality: 80)))
        #expect(photo.width == 300 && photo.height == 400)
        let (leftRed, _, leftBlue) = try pixel(photo.bytes, x: 60, y: 200)
        let (rightRed, _, rightBlue) = try pixel(photo.bytes, x: 240, y: 200)
        #expect(leftBlue > 200 && leftRed < 60, "left \(leftRed), \(leftBlue)")
        #expect(rightRed > 200 && rightBlue < 60, "right \(rightRed), \(rightBlue)")
    }

    @Test func theMetadataIsLeftOut() throws {
        let gps: [CFString: Any] = [
            kCGImagePropertyGPSLatitude: 44.26, kCGImagePropertyGPSLatitudeRef: "N", kCGImagePropertyGPSLongitude: 72.58,
            kCGImagePropertyGPSLongitudeRef: "W",
        ]
        let tagged = try picture(
            width: 640, height: 480,
            properties: [kCGImagePropertyGPSDictionary: gps, kCGImagePropertyExifDictionary: [kCGImagePropertyExifUserComment: "alice"]]
        ) { $0.setFillColor(red: 0.2, green: 0.6, blue: 0.4, alpha: 1); $0.fill(CGRect(x: 0, y: 0, width: 640, height: 480)) }
        let source = try #require(CGImageSourceCreateWithData(tagged as CFData, nil))
        let original = try #require(CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any])
        #expect(original[kCGImagePropertyGPSDictionary] != nil)

        let photo = try #require(PhotoCompressor.encode(tagged, step: .init(maxPixels: 2_048, quality: 80)))
        #expect(chunks(photo.bytes) == ["VP8 "])
        let webp = try #require(CGImageSourceCreateWithData(photo.bytes as CFData, nil))
        let written = try #require(CGImageSourceCopyPropertiesAtIndex(webp, 0, nil) as? [CFString: Any])
        #expect(written[kCGImagePropertyGPSDictionary] == nil)
    }

    @Test func aHeicIsReadDirectly() async throws {
        let heic = try picture(width: 1_200, height: 900, type: .heic) {
            $0.setFillColor(red: 0.9, green: 0.4, blue: 0.1, alpha: 1); $0.fill(CGRect(x: 0, y: 0, width: 1_200, height: 900))
        }
        let photo = try #require(await PhotoCompressor.compress(heic))
        #expect(isWebP(photo.bytes))
        #expect(photo.width == 1_200 && photo.height == 900)
    }

    @Test func bytesThatArentAPictureGiveNothing() async {
        #expect(await PhotoCompressor.compress(Data([1, 2, 3, 4])) == nil)
        #expect(await PhotoCompressor.compress(Data()) == nil)
    }

    @Test func theFirstStepUnderTheTargetIsKept() {
        let steps = [PhotoCompressor.Step(maxPixels: 3, quality: 1), .init(maxPixels: 2, quality: 1), .init(maxPixels: 1, quality: 1)]
        var tried: [Int] = []
        // A photo's size in bytes is its step's pixels here.
        let photo = PhotoCompressor.compress(Data(), steps: steps, targetBytes: 2) { _, step in
            tried.append(step.maxPixels)
            return CompressedPhoto(bytes: Data(count: step.maxPixels), width: step.maxPixels, height: step.maxPixels)
        }
        #expect(photo?.width == 2)
        #expect(tried == [3, 2])
    }

    @Test func theLastStepIsKeptWhenNothingFits() {
        let steps = [PhotoCompressor.Step(maxPixels: 3, quality: 1), .init(maxPixels: 2, quality: 1)]
        let photo = PhotoCompressor.compress(Data(), steps: steps, targetBytes: 0) { _, step in
            CompressedPhoto(bytes: Data(count: step.maxPixels), width: step.maxPixels, height: step.maxPixels)
        }
        #expect(photo?.width == 2)
    }
}
