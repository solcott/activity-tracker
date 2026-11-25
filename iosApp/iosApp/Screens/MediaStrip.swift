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
    /// False where the page draws the route on its own map, as the activity page does: then only the photos show.
    var showsRoute = true
    let open: (Int32) -> Void

    @Environment(\.liveMaps) private var liveMaps

    static let height: CGFloat = 160
    private static let gap: CGFloat = 8
    private static let corner: CGFloat = 12

    var body: some View {
        let photos = content.media.elements(as: MediaPhoto.self)
        if photos.isEmpty {
            if showsRoute && content.hasRoute {
                route.frame(maxWidth: .infinity).frame(height: Self.height).clipShape(tile)
            }
        } else {
            GeometryReader { geometry in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: Self.gap) {
                        if showsRoute && content.hasRoute {
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

    /// The route on a map image in a signed-in session (`\.liveMaps`); elsewhere, as previews and snapshots draw it, the line preview.
    @ViewBuilder private var route: some View {
        if liveMaps, let map = content.routeMap {
            RouteMapImage(map: map)
        } else {
            RouteSketch { size in content.routeShape(width: size.width, height: size.height, pad: 12) }
        }
    }

    /// The shared clamp on a photo's shape, so a panorama or a tall photo stays a reasonable tile.
    private static func width(of photo: MediaPhoto) -> CGFloat {
        let ratio = photo.aspectRatio.map { CGFloat($0) } ?? 4.0 / 3.0
        return height * min(max(ratio, 0.6), 1.8)
    }

    private func photo(_ photo: MediaPhoto, index: Int32) -> some View {
        MediaPhotoTile(
            photo: photo, content: content, size: CGSize(width: Self.width(of: photo), height: Self.height), corner: Self.corner,
            revealed: revealed, reveal: reveal, open: { open(index) }
        )
    }
}
