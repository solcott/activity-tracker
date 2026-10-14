package io.github.solcott.activitytracker.app.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.Visibility

/**
 * Who may see an activity being edited: as saved ([saved], once [seed]ed) and as the athlete has it now ([choice]), with the charts capped
 * at the activity. Without charts, only the activity's visibility is offered.
 */
@Stable
class EditVisibility internal constructor(private val canChoose: Boolean) {
    private var saved by mutableStateOf<VisibilityChoice?>(null)
    private var choice by mutableStateOf<VisibilityChoice?>(null)
    var hasCharts by mutableStateOf(false)
        private set

    var approvesFollowers by mutableStateOf(false)

    /** [view] as loaded, with its charts' visibility ([charts], null when it has none). */
    fun seed(view: ActivityView, charts: String?) {
        val visibility = view.visibilityOrPublic
        saved = VisibilityChoice(visibility, charts ?: visibility)
        choice = saved
        hasCharts = charts != null
    }

    /** What the form shows; null until loaded. */
    val fields: VisibilityFields?
        get() = choice?.let { VisibilityFields(it.activity, it.charts, canChoose, approvesFollowers) }

    /** The choice to save, if the athlete changed it. */
    val changed: VisibilityChoice?
        get() = choice?.takeIf { it != saved }

    /** Saving would make a Public activity less visible, which can't recall copies already published: the athlete is asked first. */
    var confirmingNarrowing by mutableStateOf(false)
        private set

    private val narrowsPublic: Boolean
        get() = saved?.activity == Visibility.PUBLIC && choice?.activity != Visibility.PUBLIC

    /** Runs [save], unless it would narrow a Public activity: then asks first ([confirmingNarrowing]). */
    fun save(save: () -> Unit) {
        if (narrowsPublic) confirmingNarrowing = true else save()
    }

    /** The athlete confirmed narrowing ([save]) or went back to editing. */
    fun narrowingAnswered(confirmed: Boolean, save: () -> Unit) {
        confirmingNarrowing = false
        if (confirmed) save()
    }

    fun activityChanged(visibility: String) {
        if (canChoose) choice = choice?.copy(activity = Visibility.known(visibility))?.capped()
    }

    fun chartsChanged(visibility: String) {
        if (canChoose) choice = choice?.copy(charts = Visibility.known(visibility))?.capped()
    }
}

@Composable internal fun rememberEditVisibility(canChoose: Boolean): EditVisibility = remember { EditVisibility(canChoose) }
