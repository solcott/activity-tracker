import Foundation

/// The text for a failure: what failed, followed by why when the reason is one the athlete can act on or wait out
/// (ui/error-messages). Which failures those are is Kotlin's `FailureReason`, the rule the shared UI follows too.
enum FailureText {
    static func text(what: String, reason: FailureReason?) -> String {
        guard let reason else { return what }
        return String(format: String(localized: "error_with_reason"), what, why(reason))
    }

    private static func why(_ reason: FailureReason) -> String {
        switch reason {
        case .Unreachable: String(localized: "error_reason_unreachable")
        case .Server: String(localized: "error_reason_server")
        case .Unreadable: String(localized: "error_reason_unreadable")
        }
    }
}
