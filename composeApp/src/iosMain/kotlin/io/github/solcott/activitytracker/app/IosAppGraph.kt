package io.github.solcott.activitytracker.app

import androidx.compose.ui.window.ComposeUIViewController
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import io.github.solcott.activitytracker.activityfile.FitDecoder
import io.github.solcott.activitytracker.app.di.AppGraph
import io.github.solcott.activitytracker.atproto.AtprotoConfig
import io.github.solcott.activitytracker.data.auth.AuthBrowser
import io.github.solcott.activitytracker.data.auth.AuthCoordinator
import io.github.solcott.activitytracker.data.auth.parseRedirectParams
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject

@DependencyGraph(AppScope::class)
interface IosAppGraph : AppGraph {
    @Provides fun atprotoConfig(): AtprotoConfig = NetworkConfigs.native()

    @DependencyGraph.Factory
    fun interface Factory {
        /** [fitDecoder] is `SwiftFitDecoder`, implemented in iosApp with Garmin's Swift FIT SDK. */
        fun create(@Provides fitDecoder: FitDecoder): IosAppGraph
    }
}

private var graph: IosAppGraph? = null

/** Entry point used by `iosApp/iosApp/ContentView.swift`. The graph is created once, by the first call. */
@Suppress("FunctionName", "unused")
fun MainViewController(fitDecoder: FitDecoder): UIViewController {
    val appGraph = graph ?: createGraphFactory<IosAppGraph.Factory>().create(fitDecoder).also { graph = it }
    return ComposeUIViewController { App(appGraph) }
}

/** OAuth via ASWebAuthenticationSession, which hands the redirect URL straight back to us. */
@ContributesBinding(AppScope::class)
@Inject
class IosAuthBrowser(private val coordinator: () -> AuthCoordinator) : AuthBrowser {
    private val scope = MainScope()
    private var session: ASWebAuthenticationSession? = null

    private val anchor =
        object : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
            override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor {
                val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull { it is UIWindowScene } as? UIWindowScene
                return scene?.windows?.filterIsInstance<UIWindow>()?.firstOrNull { it.isKeyWindow() } ?: UIWindow()
            }
        }

    override fun open(url: String) {
        val authSession =
            ASWebAuthenticationSession(
                uRL = requireNotNull(NSURL.URLWithString(url)),
                callbackURLScheme = NetworkConfigs.NATIVE_REDIRECT_SCHEME,
            ) { callback, error ->
                scope.launch {
                    val redirect = callback?.absoluteString
                    if (redirect != null) {
                        coordinator().handleRedirect(parseRedirectParams(redirect))
                    } else {
                        if (error != null) coordinator().cancelled()
                    }
                }
                session = null
            }
        authSession.presentationContextProvider = anchor
        authSession.prefersEphemeralWebBrowserSession = false
        session = authSession
        authSession.start()
    }
}
