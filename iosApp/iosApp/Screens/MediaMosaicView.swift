import SwiftUI

/// An activity's route and photos as one mosaic (feed/activity-page-layout), as the shared `MediaMosaicUi` lays them out: the large tile
/// (the route's map, or the first photo without a route) two thirds wide, and the photos Kotlin's `MediaMosaic` picks stacked beside it,
/// the last saying how many more there are over a dark scrim. One tile alone fills the width. A photo under a media label is blurred and
/// offered to Show until the viewer reveals the activity's photos, a tap then revealing rather than opening it; the owner's taken-down
/// activity shows placeholders. Whether each applies is Kotlin's (`ActivityCardContent`).
struct MediaMosaicView<MapTile: View>: View {
    let mosaic: MediaMosaic
    let content: ActivityCardContent
    let revealed: Bool
    let reveal: () -> Void
    let open: (Int32) -> Void
    var height: CGFloat = MediaMosaicLayout.height
    /// The route, drawn at the size the mosaic gives it; used only when `mosaic.map` is set.
    @ViewBuilder let map: () -> MapTile

    var body: some View {
        let photos = content.media.elements(as: MediaPhoto.self)
        let beside = Int(mosaic.beside)
        let firstBeside = mosaic.map ? 0 : 1
        GeometryReader { geometry in
            let gap = MediaMosaicLayout.gap
            let largeWidth = beside > 0 ? (geometry.size.width - gap) * 2 / 3 : geometry.size.width
            let sideWidth = geometry.size.width - largeWidth - gap
            let sideHeight = beside > 0 ? (geometry.size.height - gap * CGFloat(beside - 1)) / CGFloat(beside) : 0
            HStack(spacing: gap) {
                if mosaic.map {
                    map().frame(width: largeWidth, height: geometry.size.height).clipShape(MediaMosaicLayout.tile)
                } else if let first = photos.first {
                    tile(first, index: 0, size: CGSize(width: largeWidth, height: geometry.size.height), last: beside == 0)
                }
                if beside > 0 {
                    VStack(spacing: gap) {
                        ForEach(0..<beside, id: \.self) { i in
                            let index = firstBeside + i
                            if index < photos.count {
                                tile(photos[index], index: index, size: CGSize(width: sideWidth, height: sideHeight), last: i == beside - 1)
                            }
                        }
                    }
                }
            }
        }
        .frame(height: height)
    }

    private func tile(_ photo: MediaPhoto, index: Int, size: CGSize, last: Bool) -> some View {
        let more = last ? mosaic.moreText : nil
        return MediaPhotoTile(
            photo: photo, content: content, size: size, corner: MediaMosaicLayout.corner, revealed: revealed, reveal: reveal,
            open: { open(Int32(index)) }, accessibilityValue: more.map { Text($0) }
        ) {
            if let more {
                // A neutral scrim, so the count reads on any photo; the tile still opens the gallery under it.
                ZStack {
                    Color.black.opacity(0.45)
                    Text(more).font(.title2.weight(.semibold)).foregroundStyle(.white)
                }
            }
        }
    }
}

/// The mosaic's measures, the shared mosaic's.
enum MediaMosaicLayout {
    static let height: CGFloat = 300
    static let gap: CGFloat = 8
    static let corner: CGFloat = 16
    static let tile = RoundedRectangle(cornerRadius: corner, style: .continuous)
}
