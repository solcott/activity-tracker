package io.github.solcott.activitytracker.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import io.github.solcott.activitytracker.app.auth.AuthBrowser
import io.github.solcott.activitytracker.app.di.AppGraph
import io.github.solcott.activitytracker.atproto.AtprotoConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

@DependencyGraph(AppScope::class)
interface AndroidAppGraph : AppGraph {
    @Provides
    fun atprotoConfig(): AtprotoConfig = NetworkConfigs.native()

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides context: Context,
        ): AndroidAppGraph
    }
}

fun createAndroidAppGraph(context: Context): AndroidAppGraph =
    createGraphFactory<AndroidAppGraph.Factory>().create(context.applicationContext)

/** Opens OAuth in a Custom Tab; the redirect returns via MainActivity's intent filter. */
@ContributesBinding(AppScope::class)
@Inject
class AndroidAuthBrowser(private val context: Context) : AuthBrowser {
    override fun open(url: String) {
        val intent = CustomTabsIntent.Builder().build()
        intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.launchUrl(context, Uri.parse(url))
    }
}
