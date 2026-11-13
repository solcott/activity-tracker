import Foundation
import SnapshotTesting
import SwiftUI
import Testing
import UIKit

/// How the app's views are compared with their reference images (dev/ios-snapshot-tests).
nonisolated enum Snapshots {
    /// The simulator OS the references under `__Snapshots__` were recorded on. A reference is true for one OS version only: fonts,
    /// materials and control shapes change between releases. Rerecord on a newer one by changing this, deleting the references and
    /// running the tests twice.
    static let recordedOn = (major: 26, minor: 0)

    /// Whether this simulator is the kind the references were recorded on. Suites of snapshot tests are enabled only where it is.
    static var comparesHere: Bool {
        let version = ProcessInfo.processInfo.operatingSystemVersion
        return version.majorVersion == recordedOn.major && version.minorVersion == recordedOn.minor
    }

    static let skipped: Comment =
        "Snapshot references were recorded on iOS 26.0, and compare only there: run the tests on an iOS 26.0 simulator."

    /// The `precision` of a screen with buttons in its bar: all but one pixel in a thousand, which is ten times what the glass behind
    /// them was seen to vary by.
    static let barButtons: Float = 0.999

    /// An iPhone's width and height in points, for a whole screen.
    static let screen = CGSize(width: 393, height: 852)

    /// How many draws in a row must be the same for a view to count as settled. Two aren't enough: a system transition that steps once
    /// a draw can start a draw late, so the two draws before it agree (a search field's clear button, dev/ios-snapshot-tests).
    static let settledDraws = 3

    /// The most times a view is drawn waiting for `settledDraws` in a row to agree.
    static let settleDraws = 9
}

extension Snapshotting where Value == UIViewController, Format == UIImage {
    /// The key window's drawing of a controller, as the library's `.image(drawHierarchyInKeyWindow: true)` makes it, drawn again until
    /// `Snapshots.settledDraws` draws in a row are the same (at most `Snapshots.settleDraws`), and compared as the library compares that
    /// strategy's images.
    ///
    /// One draw straight after the view is laid out can catch what the system renders on its own time, half done: the glass and soft
    /// scroll edge under a bar on iOS 26 blur a backdrop the render server captures apart from the draw, and while a full test run keeps
    /// the process busy that backdrop can lag the first draw, so a comparison failed only under a full run's load (dev/ios-snapshot-tests).
    /// A search field's clear button fades and grows in over three or four draws, whatever the time between them and though the view's
    /// layer time stands still: its symbol is a RenderBox layer that steps its appearing once a rendered frame. Under load that start can
    /// come a draw late, so the first two draws both lack the button and agree; a third has to agree too, and by then it has begun.
    /// The draws are synchronous, with a short pause between them and no turn of the run loop, so no other test runs in between and
    /// changes the key window.
    static func settledImage(precision: Float, perceptualPrecision: Float, size: CGSize, traits: UITraitCollection) -> Snapshotting {
        let base = Snapshotting.image(
            drawHierarchyInKeyWindow: true, precision: precision, perceptualPrecision: perceptualPrecision, size: size, traits: traits)
        return Snapshotting(pathExtension: base.pathExtension, diffing: base.diffing, asyncSnapshot: { controller in
            var last: (image: UIImage, png: Data?)?
            var agreeing = 0
            for _ in 0..<Snapshots.settleDraws {
                var drawn: UIImage?
                base.snapshot(controller).run { drawn = $0 }
                // A draw the library finishes later (a web view's) isn't one this app has: leave it to the library.
                guard let image = drawn else { return base.snapshot(controller) }
                let png = image.pngData()
                agreeing = last?.png == png ? agreeing + 1 : 1
                if agreeing == Snapshots.settledDraws { return Async(value: image) }
                last = (image, png)
                usleep(50_000)
            }
            return Async(value: last!.image)
        })
    }
}

/// Compares `view`, drawn at `size` in the light and the dark appearance, with its two references. A missing reference is recorded and
/// fails the run that recorded it; one that exists is never rewritten.
///
/// `precision` is the share of pixels that must match. Every pixel must, except where a view draws something the system renders a
/// little differently on each run: the glass behind a bar's buttons on iOS 26 differs by a few levels over a few hundred pixels, so a
/// screen with buttons in its bar passes `Snapshots.barButtons`.
@MainActor
func assertAppSnapshot(
    of view: some View, size: CGSize = Snapshots.screen, named name: String, precision: Float = 1,
    fileID: StaticString = #fileID, file filePath: StaticString = #filePath, testName: String = #function, line: UInt = #line,
    column: UInt = #column
) {
    for (style, appearance) in [(UIUserInterfaceStyle.light, "light"), (UIUserInterfaceStyle.dark, "dark")] {
        // Three pixels a point whatever the simulator, so the same references serve any device of the recorded OS.
        let traits = UITraitCollection { mutable in
            mutable.userInterfaceStyle = style
            mutable.displayScale = 3
        }
        let controller = UIHostingController(rootView: view.frame(width: size.width, height: size.height))
        // Time stands still in the view, so a spinner is drawn at the same turn on every run.
        controller.view.layer.speed = 0
        assertSnapshot(
            of: controller,
            // Drawn in the app's window, where lists and materials look as they do on screen; off screen a list's rows draw apart.
            as: .settledImage(precision: precision, perceptualPrecision: 0.98, size: size, traits: traits),
            named: "\(name).\(appearance)", fileID: fileID, file: filePath, testName: testName, line: line, column: column)
    }
}
