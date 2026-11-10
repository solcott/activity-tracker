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
            as: .image(
                drawHierarchyInKeyWindow: true, precision: precision, perceptualPrecision: 0.98, size: size, traits: traits),
            named: "\(name).\(appearance)", fileID: fileID, file: filePath, testName: testName, line: line, column: column)
    }
}
