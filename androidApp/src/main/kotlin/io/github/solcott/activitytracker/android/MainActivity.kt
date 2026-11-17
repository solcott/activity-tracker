package io.github.solcott.activitytracker.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import io.github.solcott.activitytracker.app.App
import io.github.solcott.activitytracker.data.auth.parseRedirectParams
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val graph
        get() = (application as ActivityTrackerApplication).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOAuthRedirect(intent)
        handleLink(intent)
        handleOpenRecording(intent)
        handleOpenAccount(intent)
        setContent { App(graph, onRootPop = ::finish) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthRedirect(intent)
        handleLink(intent)
        handleOpenRecording(intent)
        handleOpenAccount(intent)
    }

    /**
     * An https link on the link host or an `activitytracker://` one was opened (ui/deep-links): the app opens its page once a session is
     * open. Not when the activity is relaunched from Recents, which hands back the intent it was first started with.
     */
    private fun handleLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        if (graph.linkRequests.request(uri.toString())) intent.data = null
    }

    /** The "export ready" notification was tapped: show the Account page, where it's saved (auth/data-export). */
    private fun handleOpenAccount(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_ACCOUNT) return
        intent.action = null
        graph.linkRequests.openAccount()
    }

    /** The recording notification was tapped: show the recording screen. */
    private fun handleOpenRecording(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_RECORDING) return
        intent.action = null
        graph.linkRequests.openRecord()
    }

    companion object {
        const val ACTION_OPEN_RECORDING = "io.github.solcott.activitytracker.OPEN_RECORDING"
        const val ACTION_OPEN_ACCOUNT = "io.github.solcott.activitytracker.OPEN_ACCOUNT"
    }

    private fun handleOAuthRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.path != "/oauth/callback") return
        intent.data = null
        lifecycleScope.launch { graph.authCoordinator.handleRedirect(parseRedirectParams(uri.toString())) }
    }
}
