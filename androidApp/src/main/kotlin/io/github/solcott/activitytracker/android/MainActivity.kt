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
        setContent { App(graph, onRootPop = ::finish) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthRedirect(intent)
    }

    private fun handleOAuthRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.path != "/oauth/callback") return
        intent.data = null
        lifecycleScope.launch { graph.authCoordinator.handleRedirect(parseRedirectParams(uri.toString())) }
    }
}
