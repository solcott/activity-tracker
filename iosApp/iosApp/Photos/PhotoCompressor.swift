import Accelerate
import CoreGraphics
import Foundation
import ImageIO
import IosShell
import libwebp

/// A photo compressed for upload: WebP bytes and the size it came out at.
nonisolated struct CompressedPhoto: Sendable, Equatable {
    let bytes: Data
    let width: Int
    let height: Int
}

/// A picked activity or gear photo compressed for upload (ios-libwebp-photos), as the shared UI's `compressPhoto` compresses it on the
/// other platforms: Kotlin's steps (`PhotoCompression`), each scaling the photo upright to at most its pixels on the long edge and
/// encoding it as lossy WebP with libwebp at its quality, keeping the first under Kotlin's target, else the last. ImageIO decodes the
/// photo (a HEIC included), turns it by its EXIF orientation and scales it; only its pixels are encoded, so none of its metadata (the GPS
/// position included) is kept: records are public. iOS has no system WebP encoder, hence libwebp.
nonisolated enum PhotoCompressor {
    /// One step of Kotlin's, as Swift values.
    struct Step: Sendable, Equatable {
        let maxPixels: Int
        let quality: Int
    }

    /// Kotlin's steps, in order, the last being the fallback.
    static var steps: [Step] {
        PhotoCompression.shared.steps.map { Step(maxPixels: Int($0.maxPixels), quality: Int($0.quality)) }
    }

    /// Kotlin's target size in bytes.
    static var targetBytes: Int { Int(PhotoCompression.shared.targetBytes) }

    /// `image` compressed through Kotlin's steps, off the caller's actor; nil when it can't be read as a picture.
    @concurrent static func compress(_ image: Data) async -> CompressedPhoto? {
        compress(image, steps: steps, targetBytes: targetBytes, encode: encode)
    }

    /// The first of `steps`' photos `encode` makes no bigger than `targetBytes`, else the last step's; nil when `encode` gives none.
    static func compress(
        _ image: Data, steps: [Step], targetBytes: Int, encode: (Data, Step) -> CompressedPhoto?
    ) -> CompressedPhoto? {
        guard let last = steps.last else { return nil }
        for step in steps.dropLast() {
            guard let photo = encode(image, step) else { return nil }
            if photo.bytes.count <= targetBytes { return photo }
        }
        return encode(image, last)
    }

    /// `image` upright, scaled to at most `step.maxPixels` on its long edge (never up) and encoded as WebP at `step.quality`.
    static func encode(_ image: Data, step: Step) -> CompressedPhoto? {
        guard let upright = uprightImage(image, maxPixels: step.maxPixels), let rgba = rgbaPixels(upright) else { return nil }
        var output: UnsafeMutablePointer<UInt8>?
        let size = rgba.pixels.withUnsafeBufferPointer { pixels in
            WebPEncodeRGBA(
                pixels.baseAddress, Int32(rgba.width), Int32(rgba.height), Int32(rgba.bytesPerRow), Float(step.quality), &output)
        }
        guard size > 0, let output else { return nil }
        defer { WebPFree(output) }
        return CompressedPhoto(bytes: Data(bytes: output, count: size), width: rgba.width, height: rgba.height)
    }

    /// The photo decoded, turned by its orientation and scaled in one ImageIO call; it also decodes at a reduced size where it can.
    private static func uprightImage(_ image: Data, maxPixels: Int) -> CGImage? {
        guard let source = CGImageSourceCreateWithData(image as CFData, [kCGImageSourceShouldCache: false] as CFDictionary),
            CGImageSourceGetCount(source) > 0
        else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixels,
            kCGImageSourceShouldCacheImmediately: true,
        ]
        return CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary)
    }

    private struct RGBA {
        let pixels: [UInt8]
        let width: Int
        let height: Int
        let bytesPerRow: Int
    }

    /// `image`'s pixels as unpremultiplied 8-bit RGBA in sRGB, which libwebp takes: a wide-gamut photo is converted, since an untagged
    /// WebP is read as sRGB.
    private static func rgbaPixels(_ image: CGImage) -> RGBA? {
        let width = image.width
        let height = image.height
        let bytesPerRow = width * 4
        var pixels = [UInt8](repeating: 0, count: bytesPerRow * height)
        guard let space = CGColorSpace(name: CGColorSpace.sRGB) else { return nil }
        let drawn = pixels.withUnsafeMutableBytes { buffer -> Bool in
            guard
                let context = CGContext(
                    data: buffer.baseAddress, width: width, height: height, bitsPerComponent: 8, bytesPerRow: bytesPerRow, space: space,
                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue)
            else { return false }
            context.interpolationQuality = .high
            context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
            // Bitmap contexts hold premultiplied pixels; libwebp wants them straight. An opaque photo is unchanged.
            var vImage = vImage_Buffer(
                data: buffer.baseAddress, height: vImagePixelCount(height), width: vImagePixelCount(width), rowBytes: bytesPerRow)
            return vImageUnpremultiplyData_RGBA8888(&vImage, &vImage, vImage_Flags(kvImageNoFlags)) == kvImageNoError
        }
        return drawn ? RGBA(pixels: pixels, width: width, height: height, bytesPerRow: bytesPerRow) : nil
    }
}
