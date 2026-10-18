package io.github.solcott.activitytracker.app.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.app.visibility.fieldsOf
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.Visibility

/**
 * Who may see an activity being edited: as saved ([saved], once [seed]ed) and as the athlete has it now ([choice]), each category capped at
 * the activity. Only the categories it has data in are offered.
 */
@Stable
class EditVisibility internal constructor(private val canChoose: Boolean) {
    private var saved by mutableStateOf<VisibilityChoice?>(null)
    private var choice by mutableStateOf<VisibilityChoice?>(null)
    private var offered by mutableStateOf(emptyList<String>())

    var approvesFollowers by mutableStateOf(false)

    /**
     * [view] as loaded, with its [streams] (null when it has none or they can't load): each category with data at the tier it's served at
     * now, read from where it appears ([DetailCategory.currentTiers]).
     */
    fun seed(view: ActivityView, streams: GetActivityStreams.Output?) {
        val visibility = view.visibilityOrPublic
        val tiers = DetailCategory.currentTiers(visibility, view, streams)
        saved = VisibilityChoice(visibility, tiers)
        choice = saved
        offered = DetailCategory.all.filter { it in tiers }
    }

    /** What the form shows; null until loaded. */
    val fields: VisibilityFields?
        get() = choice?.let { fieldsOf(it, canChoose, approvesFollowers, offered) }

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

    /**
     * While the athlete is asked whether the core charts of an activity from before categories may become as visible as it, that
     * visibility; null otherwise.
     */
    var confirmingCoreCharts by mutableStateOf<String?>(null)
        private set

    private var coreChartsConfirmed = false

    /**
     * Whether saving the [changed] choice for [activity] must first ask ([confirmingCoreCharts]): it would widen the core charts of an
     * activity from before categories ([ActivityManager.coreChartsWiden]), and the athlete hasn't agreed yet.
     */
    suspend fun asksAboutCoreCharts(activity: ActivityView, manager: ActivityManager): Boolean {
        val widening = changed?.takeIf { !coreChartsConfirmed && manager.coreChartsWiden(activity, it) }
        confirmingCoreCharts = widening?.capped()?.activity
        return widening != null
    }

    /** The athlete agreed to widen the core charts ([asksAboutCoreCharts]), so [save] runs, or went back to editing. */
    fun coreChartsAnswered(confirmed: Boolean, save: () -> Unit) {
        confirmingCoreCharts = null
        coreChartsConfirmed = confirmed
        if (confirmed) save()
    }

    fun activityChanged(visibility: String) {
        if (canChoose) choice = choice?.withActivity(visibility)
    }

    fun categoryChanged(category: String, visibility: String) {
        if (canChoose && category in offered) choice = choice?.withCategory(category, visibility)
    }
}

@Composable internal fun rememberEditVisibility(canChoose: Boolean): EditVisibility = remember { EditVisibility(canChoose) }
