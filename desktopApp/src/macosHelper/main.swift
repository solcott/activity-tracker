// ActivityTrackerLocation: the desktop app's macOS location helper (segments/segment-browse). macOS grants location only to an app
// bundle that says why it wants it, so the JVM app launches this one through LaunchServices (`open -W -n … --args --out <file>`).
// It asks for location while in use, writes one answer to <file> as {"lat":…,"lng":…} or {"error":"…"}, and quits. The app creates
// <file> and deletes it once it stops waiting, so the helper only writes into it and never creates it: an answer after the app gave
// up leaves no location on disk.
import AppKit
import CoreLocation
import Foundation

/** How long the helper waits for the athlete's answer and a fix before giving up, in seconds, unless `--give-up <seconds>` says. */
let defaultGiveUpAfter: TimeInterval = 30

final class OneLocation: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private let out: String
    private var done = false

    init(out: String) {
        self.out = out
        super.init()
        manager.delegate = self
        // A town's worth is enough to open a map, and answers fastest.
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
    }

    func start() { decide(manager.authorizationStatus) }

    private func decide(_ status: CLAuthorizationStatus) {
        switch status {
        case .notDetermined: manager.requestWhenInUseAuthorization()
        case .authorizedAlways, .authorizedWhenInUse: manager.requestLocation()
        default: finish(error: "denied")
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) { decide(manager.authorizationStatus) }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let at = locations.last?.coordinate else { return }
        finish(json: "{\"lat\":\(at.latitude),\"lng\":\(at.longitude)}")
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Not an answer yet while the athlete is still deciding.
        if manager.authorizationStatus == .notDetermined { return }
        finish(error: "failed")
    }

    func finish(error: String) { finish(json: "{\"error\":\"\(error)\"}") }

    private func finish(json: String) {
        if done { return }
        done = true
        // Into the app's file only: nil once the app has deleted it. Opened before that, the bytes go to the unlinked file and vanish.
        if let file = FileHandle(forWritingAtPath: out) {
            try? file.truncate(atOffset: 0)
            try? file.write(contentsOf: Data(json.utf8))
            try? file.close()
        }
        exit(0)
    }
}

let arguments = CommandLine.arguments
guard let flag = arguments.firstIndex(of: "--out"), flag + 1 < arguments.count else { exit(64) }
let finder = OneLocation(out: arguments[flag + 1])
// The app passes how long it still listens, so the helper's answer, a timeout too, reaches it in time.
let giveUpAfter = arguments.firstIndex(of: "--give-up").flatMap { $0 + 1 < arguments.count ? TimeInterval(arguments[$0 + 1]) : nil }
    .flatMap { $0 > 0 ? $0 : nil } ?? defaultGiveUpAfter
DispatchQueue.main.async { finder.start() }
DispatchQueue.main.asyncAfter(deadline: .now() + giveUpAfter) { finder.finish(error: "timeout") }
// An app's run loop, not a bare one: without it macOS never shows the location prompt.
let app = NSApplication.shared
app.setActivationPolicy(.accessory)
// In front, so the prompt shows over the app that launched it.
app.activate(ignoringOtherApps: true)
app.run()
