import Nuke
import SwiftUI
import UIKit

/// An activity's photos full screen on black (feed/activity-media, moderation/labels), drawn from the states `MediaGalleryPresenter`
/// publishes: swiped a photo at a time; a tap zooms the photo showing, for pinch, double tap and pan, and swiping waits until a tap lets
/// it go; Close (which leaves zoom first), the counter over more than one photo, and Report on someone else's photo in a menu; a labelled
/// photo covered until the viewer reveals it, a tap revealing rather than zooming; and the owner's taken-down photos as placeholders.
/// Every word and condition is Kotlin's (`MediaGalleryTexts`), as the shared gallery's are. The host hides the tabs over it.
struct MediaGalleryView: View {
    let state: MediaGalleryState

    var body: some View {
        let texts = MediaGalleryTexts.shared
        ZStack {
            Color.black.ignoresSafeArea()
            ContentStateView(state.content, errorKind: .LoadActivity, retry: retry) {
                let photos = state.items.elements(as: MediaPhoto.self)
                if !photos.isEmpty {
                    pager(photos)
                }
            }
            VStack(spacing: 8) {
                bar
                Spacer()
                RefreshStatusView(state: state.content, errorKind: .LoadActivity, retry: retry)
                if let error = state.error { ErrorMessage(error, layout: .row) }
            }
            .padding(12)
        }
        .environment(\.colorScheme, .dark)
        .toolbar(.hidden, for: .navigationBar)
        .statusBarHidden(state.zoomed)
        .accessibilityAction(.escape) { send(MediaGalleryEvent.Back.shared) }
        .reporting(state.reporting)
    }

    /// Close, and the counter and photo menu at the other end.
    private var bar: some View {
        let texts = MediaGalleryTexts.shared
        return HStack(spacing: 12) {
            Button { send(MediaGalleryEvent.Back.shared) } label: {
                Image(.close).frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
            }
            .accessibilityLabel(Text(texts.close))
            Spacer(minLength: 8)
            if let counter = texts.counter(state: state) {
                Text(counter).font(.subheadline.monospacedDigit())
            }
            if texts.offersReport(state: state) {
                Menu {
                    Button { send(MediaGalleryEvent.ReportPhoto.shared) } label: { Text(texts.reportPhoto) }
                } label: {
                    Image(.more).frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
                }
                .accessibilityLabel(Text(texts.more))
            }
        }
        .foregroundStyle(.white)
    }

    /// A page per photo, swiped, bound to the state's page; while one is zoomed it alone shows, so a pan moves the photo, not the pager.
    @ViewBuilder private func pager(_ photos: [MediaPhoto]) -> some View {
        let page = min(Int(state.page), photos.count - 1)
        if state.zoomed, !state.takenDown {
            ZoomablePhoto(url: photos[page].url) { send(MediaGalleryEvent.ToggleZoom.shared) }
                .ignoresSafeArea()
                .accessibilityLabel(Text(MediaGalleryTexts.shared.description(photo: photos[page])))
        } else {
            TabView(selection: Binding(get: { page }, set: { send(MediaGalleryEvent.PageChanged(page: Int32($0))) })) {
                ForEach(Array(photos.enumerated()), id: \.offset) { index, photo in
                    GalleryPhoto(photo: photo, state: state) { send(MediaGalleryEvent.ToggleZoom.shared) }
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .ignoresSafeArea()
        }
    }

    private func send(_ event: any MediaGalleryEvent) {
        state.eventSink(event)
    }

    private func retry() {
        send(MediaGalleryEvent.Retry.shared)
    }
}

/// One page of the gallery: the taken-down placeholder, the label's cover with Show, or the photo fitted to the screen, which a tap zooms.
private struct GalleryPhoto: View {
    let photo: MediaPhoto
    let state: MediaGalleryState
    let tap: () -> Void

    var body: some View {
        let texts = MediaGalleryTexts.shared
        if state.takenDown {
            Text(texts.takenDown).foregroundStyle(.white).multilineTextAlignment(.center).padding(24)
        } else if let cover = texts.cover(state: state) {
            // Covered, the photo is neither seen nor reachable: a tap reveals it.
            Button(action: tap) {
                ZStack {
                    RemoteImage(photo.url, fits: true) { Color.clear }.blur(radius: 40).accessibilityHidden(true)
                    Color.black.opacity(0.6)
                    VStack(spacing: 6) {
                        Text(cover).font(.headline).multilineTextAlignment(.center)
                        Text(texts.show).font(.subheadline).foregroundStyle(.tint)
                    }
                    .foregroundStyle(.white)
                    .padding(24)
                }
            }
            .buttonStyle(.plain)
        } else {
            RemoteImage(photo.url, fits: true) { Color.clear }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .contentShape(Rectangle())
                .onTapGesture(perform: tap)
                .accessibilityElement()
                .accessibilityLabel(Text(texts.description(photo: photo)))
                .accessibilityHint(Text(texts.zoomHint))
                .accessibilityAddTraits([.isImage, .isButton])
                .accessibilityAction { tap() }
        }
    }
}

/// A photo in a scroll view that pinches, double taps and pans, as the shared gallery's zoomed photo does; a single tap (`tap`) leaves
/// zoom. A fresh view each time, so leaving zoom resets the scale. Its picture is the same request `RemoteImage` makes.
private struct ZoomablePhoto: UIViewRepresentable {
    let url: String
    let tap: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(tap: tap) }

    func makeUIView(context: Context) -> ZoomScrollView {
        let scroll = ZoomScrollView()
        scroll.backgroundColor = .black
        scroll.minimumZoomScale = 1
        scroll.maximumZoomScale = 5
        scroll.showsHorizontalScrollIndicator = false
        scroll.showsVerticalScrollIndicator = false
        scroll.contentInsetAdjustmentBehavior = .never
        scroll.delegate = context.coordinator
        let image = UIImageView()
        image.contentMode = .scaleAspectFit
        image.isUserInteractionEnabled = true
        scroll.addSubview(image)
        scroll.image = image
        context.coordinator.image = image
        let double = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.doubleTapped(_:)))
        double.numberOfTapsRequired = 2
        let single = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.tapped))
        single.require(toFail: double)
        scroll.addGestureRecognizer(double)
        scroll.addGestureRecognizer(single)
        if let request = RemoteImages.request(for: url) {
            Task { @MainActor in image.image = try? await ImagePipeline.shared.image(for: request) }
        }
        return scroll
    }

    func updateUIView(_ scroll: ZoomScrollView, context: Context) {
        context.coordinator.tap = tap
    }

    /// A scroll view that fits its photo to its bounds whenever it lays out unzoomed, since it has no size when it's made.
    final class ZoomScrollView: UIScrollView {
        weak var image: UIImageView?

        override func layoutSubviews() {
            super.layoutSubviews()
            if zoomScale == 1, let image, image.frame.size != bounds.size {
                image.frame = CGRect(origin: .zero, size: bounds.size)
                contentSize = bounds.size
            }
        }
    }

    final class Coordinator: NSObject, UIScrollViewDelegate {
        var tap: () -> Void
        weak var image: UIImageView?

        init(tap: @escaping () -> Void) {
            self.tap = tap
        }

        func viewForZooming(in scrollView: UIScrollView) -> UIView? { image }

        @objc func tapped() { tap() }

        @objc func doubleTapped(_ recognizer: UITapGestureRecognizer) {
            guard let scroll = recognizer.view as? UIScrollView else { return }
            if scroll.zoomScale > 1 {
                scroll.setZoomScale(1, animated: true)
            } else {
                let point = recognizer.location(in: image)
                let size = CGSize(width: scroll.bounds.width / 2.5, height: scroll.bounds.height / 2.5)
                scroll.zoom(to: CGRect(x: point.x - size.width / 2, y: point.y - size.height / 2, width: size.width, height: size.height), animated: true)
            }
        }
    }
}

#if DEBUG
    #Preview("Media gallery") {
        SamplePages(Samples.mediaGallery) { state in NavigationStack { MediaGalleryView(state: state) } }
    }
#endif
