import SwiftUI

/// An activity's route and photos side by side, as the shared `MediaRow` lays them out on a card: with no photos the route takes the
/// whole width; with photos, the route at four fifths of it (so the next tile peeks) and then each photo at its own shape, scrolled
/// sideways. A photo under a media label is blurred, named and offered to Show until the viewer reveals the card's photos, a tap then
/// revealing rather than opening it; the owner's taken-down activity shows placeholders. Whether each applies is Kotlin's
/// (`ActivityCardContent`).
struct MediaStrip: View {
    let content: ActivityCardContent
    let revealed: Bool
    let reveal: () -> Void
    let open: (Int32) -> Void

    static let height: CGFloat = 160
    private static let gap: CGFloat = 8
    private static let corner: CGFloat = 12

    var body: some View {
        let photos = content.media.elements(as: MediaPhoto.self)
        if photos.isEmpty {
            if content.hasRoute {
                route.frame(maxWidth: .infinity).frame(height: Self.height).clipShape(tile)
            }
        } else {
            GeometryReader { geometry in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: Self.gap) {
                        if content.hasRoute {
                            route.frame(width: geometry.size.width * 0.8).clipShape(tile)
                        }
                        ForEach(Array(photos.enumerated()), id: \.offset) { index, photo in
                            self.photo(photo, index: Int32(index))
                        }
                    }
                }
            }
            .frame(height: Self.height)
        }
    }

    private var tile: RoundedRectangle { RoundedRectangle(cornerRadius: Self.corner, style: .continuous) }

    private var route: some View {
        RouteSketch { size in content.routeShape(width: size.width, height: size.height, pad: 12) }
    }

    /// The shared clamp on a photo's shape, so a panorama or a tall photo stays a reasonable tile.
    private static func width(of photo: MediaPhoto) -> CGFloat {
        let ratio = photo.aspectRatio.map { CGFloat($0) } ?? 4.0 / 3.0
        return height * min(max(ratio, 0.6), 1.8)
    }

    @ViewBuilder private func photo(_ photo: MediaPhoto, index: Int32) -> some View {
        let frame = Self.width(of: photo)
        if content.takenDown {
            Text(.labelTakenDownPhoto)
                .font(.caption)
                .multilineTextAlignment(.center)
                .padding(8)
                .frame(width: frame, height: Self.height)
                .background(Color(.secondarySystemFill))
                .clipShape(tile)
        } else if let label = content.blurLabel(), !revealed {
            // Covered, the photo is neither seen nor reachable: a tap reveals rather than opens it.
            Button(action: reveal) {
                ZStack {
                    RemoteImage(photo.url) { Color(.secondarySystemFill) }
                        .frame(width: frame, height: Self.height)
                        .blur(radius: 24)
                        .accessibilityHidden(true)
                    Color(.secondarySystemBackground).opacity(0.85)
                    VStack(spacing: 4) {
                        Text(label).font(.subheadline.weight(.semibold)).multilineTextAlignment(.center)
                        Text(.labelShow).font(.footnote).foregroundStyle(.tint)
                    }
                    .padding(8)
                }
                .frame(width: frame, height: Self.height)
                .clipShape(tile)
            }
            .buttonStyle(.borderless)
        } else {
            Button {
                open(index)
            } label: {
                RemoteImage(photo.url) { Color(.secondarySystemFill) }
                    .frame(width: frame, height: Self.height)
                    .clipShape(tile)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(photo.alt.map { Text(verbatim: $0) } ?? Text(.detailsPhoto))
            .accessibilityAddTraits(.isImage)
        }
    }
}
