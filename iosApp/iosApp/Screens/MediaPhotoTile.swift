import SwiftUI

/// One activity photo as a tile, as the shared `MediaPhoto` draws it in both the media strip and the mosaic: a placeholder on the owner's
/// taken-down activity, the photo covered (`CoveredPhoto`) under a media label until the viewer reveals it, a tap then revealing rather
/// than opening it, and otherwise the photo, opening the gallery at it. `overlay` lays something over an open photo (the mosaic's "+2").
/// Whether each applies is Kotlin's (`ActivityCardContent`).
struct MediaPhotoTile<Overlay: View>: View {
    let photo: MediaPhoto
    let content: ActivityCardContent
    let size: CGSize
    let corner: CGFloat
    let revealed: Bool
    let reveal: () -> Void
    let open: () -> Void
    /// Read out after the photo's label, for what `overlay` says.
    var accessibilityValue: Text?
    @ViewBuilder var overlay: () -> Overlay

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: corner, style: .continuous)
        if content.takenDown {
            Text(.labelTakenDownPhoto)
                .font(.caption)
                .multilineTextAlignment(.center)
                .padding(8)
                .frame(width: size.width, height: size.height)
                .background(Color(.secondarySystemFill))
                .clipShape(shape)
        } else if let label = content.blurLabel(), !revealed {
            // Covered, the photo is neither seen nor reachable: a tap reveals rather than opens it.
            CoveredPhoto(reason: Text(label), show: Text(.labelShow), style: .tile, reveal: reveal) { image }
                .frame(width: size.width, height: size.height)
                .clipShape(shape)
        } else {
            Button(action: open) {
                image.overlay { overlay() }.clipShape(shape)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(photo.alt.map { Text(verbatim: $0) } ?? Text(.detailsPhoto))
            .accessibilityValue(accessibilityValue ?? Text(verbatim: ""))
            .accessibilityAddTraits(.isImage)
        }
    }

    private var image: some View {
        RemoteImage(photo.url, maxSize: size) { Color(.secondarySystemFill) }
            .frame(width: size.width, height: size.height)
            .clipped()
    }
}

extension MediaPhotoTile where Overlay == EmptyView {
    init(
        photo: MediaPhoto, content: ActivityCardContent, size: CGSize, corner: CGFloat, revealed: Bool, reveal: @escaping () -> Void,
        open: @escaping () -> Void
    ) {
        self.init(
            photo: photo, content: content, size: size, corner: corner, revealed: revealed, reveal: reveal, open: open
        ) { EmptyView() }
    }
}
