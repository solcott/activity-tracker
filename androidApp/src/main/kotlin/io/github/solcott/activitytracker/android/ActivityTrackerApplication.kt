package io.github.solcott.activitytracker.android

import android.app.Application
import io.github.solcott.activitytracker.app.AndroidAppGraph
import io.github.solcott.activitytracker.app.createAndroidAppGraph

class ActivityTrackerApplication : Application() {
    val graph: AndroidAppGraph by lazy { createAndroidAppGraph(this) }
}
