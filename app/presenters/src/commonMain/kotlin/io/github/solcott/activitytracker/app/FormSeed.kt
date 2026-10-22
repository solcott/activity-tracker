package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.retained.rememberRetained
import io.github.solcott.uistate.ContentState

/**
 * The first value [content] loads, kept while the screen is, after [fill] has filled a form from it (ui/content-loading): null until then.
 * Later loads change neither it nor the form. [content]'s phase for the UI is [formContent] with `seeded` while this isn't null.
 */
@Composable
internal fun <T : Any> rememberFormSeed(content: ContentState<T?>, fill: (T) -> Unit): T? {
    var seed by rememberRetained { mutableStateOf<T?>(null) }
    val data = content.data
    SideEffect(data) {
        if (seed == null && data != null) {
            fill(data)
            seed = data
        }
    }
    return seed
}
