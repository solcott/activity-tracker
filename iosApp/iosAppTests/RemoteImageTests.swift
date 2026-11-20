import CoreGraphics
import Nuke
import Testing

@testable import ActivityTracker

/// Remote pictures asked of Nuke at the size they're drawn.
struct RemoteImageTests {
    @Test func remoteRequestResizesToTheDrawnPixels() throws {
        let request = try #require(
            RemoteImages.request(for: "https://example.test/a.jpg", maxSize: CGSize(width: 40, height: 40), scale: 3))
        let resize = try #require(request.processors.first as? ImageProcessors.Resize)
        #expect(request.processors.count == 1)
        #expect(resize == ImageProcessors.Resize(size: CGSize(width: 120, height: 120), unit: .pixels, contentMode: .aspectFill))
    }

    @Test func heldPhotoRequestResizesToo() throws {
        let request = try #require(RemoteImages.request(for: "heldphoto:abc", maxSize: CGSize(width: 48, height: 48), scale: 2))
        #expect(request.processors.count == 1)
        #expect(request.imageId == "heldphoto:abc")
    }

    @Test func remoteRequestWithoutSizeKeepsFullSize() throws {
        let request = try #require(RemoteImages.request(for: "https://example.test/a.jpg"))
        #expect(request.processors.isEmpty)
    }
}
