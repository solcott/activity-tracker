import AppCommonApi
import Foundation
import IosShell
import Nuke
import NukeUI
import OrgJetbrainsKotlinxKotlinxIoBytestring
import SwiftUI

/// A picture from a URL, filling its frame (ui/ios-design-system): `placeholder` shows until it loads, when it fails, and when there
/// is no URL. Nuke loads and caches it. A `heldphoto:` URL, a photo this device uploaded that the session holds (a gear item that isn't
/// Public shows its photo by one), is answered from those bytes, which Kotlin keeps, as the shared UI's image loader answers it.
struct RemoteImage<Placeholder: View>: View {
    private let url: String?
    private let placeholder: Placeholder

    init(_ url: String?, @ViewBuilder placeholder: () -> Placeholder) {
        self.url = url
        self.placeholder = placeholder()
    }

    var body: some View {
        if let request = RemoteImages.request(for: url) {
            LazyImage(request: request) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill()
                } else {
                    placeholder
                }
            }
        } else {
            placeholder
        }
    }
}

/// How a URL is asked for.
enum RemoteImages {
    /// The scheme of a photo the session holds (Kotlin's `HeldPhotos.SCHEME`).
    static let heldScheme = "heldphoto:"

    /// What Nuke is asked for to show `url`: the held bytes for a held photo, the URL otherwise; nil for no URL or one that isn't one.
    static func request(for url: String?) -> ImageRequest? {
        guard let url, !url.isEmpty else { return nil }
        if url.hasPrefix(heldScheme) {
            // Kotlin's session state is the main thread's, so the bytes are read there; the request's id keys Nuke's cache.
            return ImageRequest(id: url, data: { try await MainActor.run { try heldBytes(url) } })
        }
        return URL(string: url).map { ImageRequest(url: $0) }
    }

    /// The bytes of the held photo at `url`, or an error when the session doesn't hold it (or no one is signed in).
    static func heldBytes(_ url: String) throws -> Data {
        guard let bytes = IosApp.shared.heldPhoto(url: url) else { throw NotHeld() }
        return ExportedKotlinPackages.kotlinx.io.bytestring.toNSData(bytes) as Data
    }

    struct NotHeld: Error {}
}

/// An athlete's picture in a circle, with their initial under it until it loads and when they have none, as on the other platforms.
/// The initial is Kotlin's (`avatarInitial`).
struct Avatar: View {
    let url: String?
    let name: String
    var size: CGFloat = 40

    var body: some View {
        ZStack {
            Circle().fill(.tint.opacity(0.18))
            Text(AppPackage.avatarInitial(name: name))
                .font(.system(size: size * 0.42, weight: .semibold))
                .foregroundStyle(.tint)
            RemoteImage(url) { Color.clear }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityHidden(true)
    }
}

/// A thing's photo in a small rounded square, such as a gear item's, on the system's fill until it loads and when there is none.
struct Thumbnail: View {
    let url: String?
    var size: CGFloat = 48

    var body: some View {
        RemoteImage(url) { Color.clear }
            .frame(width: size, height: size)
            .background(Color(.secondarySystemFill))
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            .accessibilityHidden(true)
    }
}
