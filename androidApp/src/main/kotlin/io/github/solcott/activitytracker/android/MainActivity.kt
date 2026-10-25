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
        handleOpenRecording(intent)
        setContent { App(graph, onRootPop = ::finish) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthRedirect(intent)
        handleOpenRecording(intent)
    }

    /** The recording notification was tapped: show the recording screen. */
    private fun handleOpenRecording(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_RECORDING) return
        intent.action = null
        graph.recordScreenRequests.request()
    }

    companion object {
        const val ACTION_OPEN_RECORDING = "io.github.solcott.activitytracker.OPEN_RECORDING"
    }

    private fun handleOAuthRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.path != "/oauth/callback") return
        intent.data = null
        lifecycleScope.launch { graph.authCoordinator.handleRedirect(parseRedirectParams(uri.toString())) }
    }
}
