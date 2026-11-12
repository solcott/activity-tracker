import CoreLocation

/// The app's location access as Core Location keeps it (record/gps-recording): whether it was refused, and asking for it while in use.
/// The recording screen asks before Start, so the athlete's answer reaches the presenter as `RecordEvent.PermissionResult`; the iOS
/// `LocationTracker` then finds it given. Never access all the time.
@MainActor
final class LocationAuthorization: NSObject, @preconcurrency CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var waiting: CheckedContinuation<Bool, Never>?

    override init() {
        super.init()
        manager.delegate = self
    }

    /// Refused, or restricted by the device's owner: asking again shows nothing, so only Settings can allow it.
    var refused: Bool {
        manager.authorizationStatus == .denied || manager.authorizationStatus == .restricted
    }

    /// Asks for access while in use when it hasn't been asked, and answers whether the app has it. A refused status answers false at
    /// once, with no prompt.
    func request() async -> Bool {
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            return true
        case .notDetermined:
            waiting?.resume(returning: false)
            return await withCheckedContinuation { continuation in
                waiting = continuation
                manager.requestWhenInUseAuthorization()
            }
        default:
            return false
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        // The delegate is told the status once when it's set, before anything is asked.
        let status = manager.authorizationStatus
        guard status != .notDetermined, let waiting else { return }
        self.waiting = nil
        waiting.resume(returning: status == .authorizedWhenInUse || status == .authorizedAlways)
    }
}
