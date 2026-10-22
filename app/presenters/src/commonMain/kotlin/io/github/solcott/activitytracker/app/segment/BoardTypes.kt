package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.retained.rememberRetained
import io.github.solcott.activitytracker.data.segments.BoardMeta
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** A board screen's type control: the type shown and the types listed (segments/segment-leaderboards). */
internal class BoardTypes(val shown: String?, val listed: ImmutableList<String>) {
    /** Whether choosing [board] should load its board: it's listed and isn't the one shown. */
    fun admits(board: String): Boolean = board != shown && board in listed
}

/**
 * The board types for a page whose meta is [meta]: those the AppView served and listed, or, while a new board loads, the type [requested]
 * and the last ones it answered, so the control doesn't blink on every new choice.
 */
@Composable
internal fun rememberBoardTypes(meta: BoardMeta<*>?, requested: String?): BoardTypes {
    var last by rememberRetained { mutableStateOf<BoardTypes?>(null) }
    val answered = meta?.let { BoardTypes(it.board, it.boards.toImmutableList()) }
    SideEffect { if (answered != null) last = answered }
    return answered ?: BoardTypes(requested ?: last?.shown, last?.listed ?: persistentListOf())
}
