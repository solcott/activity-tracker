import ImageIO
import OrgJetbrainsKotlinxKotlinxIoBytestring
import SwiftUI
import UIKit

/// A picked photo's bytes drawn filling a `size`-point square (a form's photo before it is saved), on `placeholder` when the bytes
/// aren't a picture. ImageIO makes a thumbnail just big enough for this screen the first time a photo is drawn at a size, and later
/// draws reuse it, so a redraw never bridges or decodes the whole photo again.
struct PickedPhotoThumbnail<Placeholder: View>: View {
    let bytes: KotlinByteString
    let size: CGFloat
    private let placeholder: Placeholder
    @Environment(\.displayScale) private var displayScale

    init(bytes: KotlinByteString, size: CGFloat, @ViewBuilder placeholder: () -> Placeholder) {
        self.bytes = bytes
        self.size = size
        self.placeholder = placeholder()
    }

    var body: some View {
        if let image = PickedPhotoThumbnails.thumbnail(of: bytes, filling: size * displayScale) {
            Image(uiImage: image).resizable().scaledToFill()
        } else {
            placeholder
        }
    }
}

enum PickedPhotoThumbnails {
    /// The thumbnails made so far, by photo and size. Kotlin's ByteString compares by content and keeps its hash, so a key is cheap.
    private static let cache = NSCache<Key, UIImage>()

    private nonisolated final class Key: NSObject {
        let bytes: KotlinByteString
        let pixels: CGFloat

        init(_ bytes: KotlinByteString, _ pixels: CGFloat) {
            self.bytes = bytes
            self.pixels = pixels
        }

        override var hash: Int { bytes.hash ^ pixels.hashValue }

        override func isEqual(_ object: Any?) -> Bool {
            guard let other = object as? Key else { return false }
            return pixels == other.pixels && bytes.isEqual(other.bytes)
        }
    }

    /// `bytes`' picture filling `pixels`, made once and kept; nil when it isn't a picture.
    static func thumbnail(of bytes: KotlinByteString, filling pixels: CGFloat) -> UIImage? {
        let key = Key(bytes, pixels)
        if let image = cache.object(forKey: key) { return image }
        guard let image = thumbnail(of: bytes.data, filling: pixels) else { return nil }
        cache.setObject(image, forKey: key)
        return image
    }

    /// `data`'s picture, upright, scaled down so its shorter side is `pixels` (never up); nil when it isn't a picture.
    nonisolated static func thumbnail(of data: Data, filling pixels: CGFloat) -> UIImage? {
        guard let source = CGImageSourceCreateWithData(data as CFData, [kCGImageSourceShouldCache: false] as CFDictionary),
            CGImageSourceGetCount(source) > 0
        else { return nil }
        let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any]
        let width = (properties?[kCGImagePropertyPixelWidth] as? NSNumber).map { CGFloat($0.doubleValue) } ?? pixels
        let height = (properties?[kCGImagePropertyPixelHeight] as? NSNumber).map { CGFloat($0.doubleValue) } ?? pixels
        // ImageIO bounds the longer side, so it is set to what leaves the shorter one filling the square.
        let shorter = max(min(width, height), 1)
        let longest = (pixels * max(width, height) / shorter).rounded(.up)
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: min(longest, max(width, height)),
            kCGImageSourceShouldCacheImmediately: true,
        ]
        return CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary).map { UIImage(cgImage: $0) }
    }
}
