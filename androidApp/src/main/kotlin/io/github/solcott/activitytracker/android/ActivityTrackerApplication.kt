package io.github.solcott.activitytracker.android

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.solcott.activitytracker.app.AndroidAppGraph
import io.github.solcott.activitytracker.app.createAndroidAppGraph
import io.github.solcott.activitytracker.data.importsave.ImportSaveLauncher

class ActivityTrackerApplication : Application() {
    val graph: AndroidAppGraph by lazy { createAndroidAppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        // Queued writes go on uploading after the app closes (data/outbox).
        ProcessLifecycleOwner.get().lifecycle.addObserver(ScheduleUploadsOnStop(this))
        // Batch saves run as a foreground job that outlives the app (feed/batch-import).
        graph.importSaves.launcher = ImportSaveLauncher { _, _ -> ImportSaveWorker.schedule(this) }
    }
}
