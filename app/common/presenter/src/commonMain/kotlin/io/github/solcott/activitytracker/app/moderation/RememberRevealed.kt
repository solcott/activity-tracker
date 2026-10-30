package io.github.solcott.activitytracker.app.moderation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.persistentSetOf

/** A screen's [Revealed], in plain (not retained) state: it lasts for this view of the screen only. */
@Composable
fun rememberRevealed(): Revealed {
    var keys by remember { mutableStateOf(persistentSetOf<String>()) }
    return Revealed(keys) { keys = keys.adding(it) }
}
