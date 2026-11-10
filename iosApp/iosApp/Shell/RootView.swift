import SwiftUI

/// The app's root: follows the presenter host between sign-in and a signed-in session (dev/presenter-host, ui/ios-native-shell).
struct RootView: View {
    let host: AppHost

    var body: some View {
        Following(host.root) { root in
            switch root.sealedType() {
            // Not yet known whether an account is signed in: nothing, for the moment the stored session takes to read.
            case .restoring:
                Color(.systemGroupedBackground).ignoresSafeArea()
            case let .signedOut(signedOut):
                // A new record each time the athlete signs out, so the screen starts afresh.
                SingleScreen(record: signedOut.value.login).id(signedOut.value.login.key)
            case let .signedIn(signedIn):
                SessionView(session: signedIn.value.session).id(ObjectIdentifier(signedIn.value.session))
            }
        }
    }
}
