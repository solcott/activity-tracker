import Foundation
import IosShell
import OrgJetbrainsKotlinxKotlinxIoBytestring
import PhotosUI
import SwiftUI

/// A native form's photo pick (dev/ios-swift-interop): the picked image's bytes compressed for upload, then sent in the form's event.
/// Bytes that aren't a picture send nothing. `Photo` is what the compression gives: an activity or gear photo (`PhotoPick`, compressed
/// in Swift with libwebp through Kotlin's steps) or a profile photo's JPEG (`AvatarPick`, Kotlin's).
struct PhotoPickOf<Photo> {
    let compress: (Data) async -> Photo?

    /// Loads the picked image with `load`, compresses it, and sends it with `send`; nothing when there is none or it isn't a picture.
    func pick(_ load: () async throws -> Data?, send: (Photo) -> Void) async {
        guard let data = try? await load(), let photo = await compress(data) else { return }
        send(photo)
    }

    /// Each of the library picker's `items` loaded and compressed in turn, for a form that sends what was picked together; the ones that
    /// aren't pictures left out.
    func pickAll(_ items: [PhotosPickerItem]) async -> [Photo] {
        var photos: [Photo] = []
        for item in items {
            await pick({ try await item.loadTransferable(type: Data.self) }) { photos.append($0) }
        }
        return photos
    }
}

/// An activity or gear photo's pick, compressed by `PhotoCompressor`, which reads a HEIC itself (ios-libwebp-photos).
typealias PhotoPick = PhotoPickOf<PickedPhoto>

extension PhotoPickOf where Photo == PickedPhoto {
    /// Compresses with `PhotoCompressor`, off the main actor, as WebP.
    static var app: PhotoPick {
        PhotoPick { data in
            guard let photo = await PhotoCompressor.compress(data) else { return nil }
            return PickedPhoto(
                bytes: photo.bytes.kotlinByteString, mimeType: "image/webp",
                width: Int32(photo.width), height: Int32(photo.height))
        }
    }
}
