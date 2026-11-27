package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.retained.rememberRetained

/** A screen's reload counter: pass [key] as a key of its load, and [retry] reads it again. */
@Stable
class Reload internal constructor() {
    var key: Int by mutableIntStateOf(0)
        private set

    fun retry() {
        key++
    }
}

/** A [Reload] retained with the screen, so coming back to it keeps the key and doesn't read again. */
@Composable fun rememberReload(): Reload = rememberRetained { Reload() }
