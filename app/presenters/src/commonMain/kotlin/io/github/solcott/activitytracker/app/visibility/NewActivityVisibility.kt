package io.github.solcott.activitytracker.app.visibility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.activity.ActivityDefaults
import io.github.solcott.activitytracker.data.activity.PendingWritesException
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList

/**
 * The visibility choice of a form for a new activity (record, manual entry, import). It starts at the athlete's defaults once they load,
 * unless the athlete chose already, and narrowing the activity's visibility narrows wider categories with it. Changing it affects only this
 * activity (or import), never the defaults. Only the categories the activity has data in are [offered]; the others keep their tier in
 * [current], so one offered again shows as the athlete left it, and publish nothing.
 */
@Stable
class NewActivityVisibility
internal constructor(
    private val canChoose: Boolean,
    private val approvesFollowers: () -> Boolean,
    private val offered: () -> Set<String> = { DetailCategory.all.toSet() },
    private val runningForm: () -> Boolean = { true },
) {
    internal var choice by mutableStateOf(VisibilityChoice.PUBLIC)
    internal var chosen by mutableStateOf(false)

    /** What the form shows. */
    val fields: VisibilityFields
        get() =
            fieldsOf(choice, canChoose, ::onEvent, approvesFollowers(), DetailCategory.all.filter { it in offered() })
                .copy(runningForm = runningForm())

    /** What to publish with. */
    val current: VisibilityChoice
        get() = choice

    fun onEvent(event: VisibilityEvent) {
        if (!canChoose) return
        if (event is VisibilityEvent.CategoryChanged && event.category !in offered()) return
        chosen = true
        choice =
            when (event) {
                is VisibilityEvent.ActivityChanged -> choice.withActivity(event.visibility)
                is VisibilityEvent.CategoryChanged -> choice.withCategory(event.category, event.visibility)
            }
    }
}

/**
 * A [NewActivityVisibility] offering [offered] (the categories the activity has data in, `ActivityDraft.heldCategories`), its cadence named
 * for running form when [runningForm].
 */
@Composable
fun rememberNewActivityVisibility(
    defaults: ActivityDefaults,
    offered: ImmutableSet<String>,
    runningForm: Boolean = false,
    approvesFollowers: () -> Boolean = { false },
): NewActivityVisibility {
    val currentOffered by rememberUpdatedState(offered)
    val currentRunningForm by rememberUpdatedState(runningForm)
    val state = remember { NewActivityVisibility(defaults.canChoose, approvesFollowers, { currentOffered }, { currentRunningForm }) }
    LaunchedEffect(state) {
        val loaded = defaults.load()
        if (!state.chosen) state.choice = loaded
    }
    return state
}

/**
 * [choice] as a form shows it: each of [offered] (every category unless said) at its visibility, capped at the activity's. Changes go to
 * [eventSink].
 */
fun fieldsOf(
    choice: VisibilityChoice,
    canChoose: Boolean,
    eventSink: (VisibilityEvent) -> Unit,
    approvesFollowers: Boolean = false,
    offered: List<String> = DetailCategory.all,
): VisibilityFields {
    val tiers = choice.tiers
    return VisibilityFields(
        activity = Visibility.known(choice.activity),
        categories = offered.map { CategoryVisibility(it, tiers.getValue(it)) }.toImmutableList(),
        canChoose = canChoose,
        approvesFollowers = approvesFollowers,
        eventSink = eventSink,
    )
}

/**
 * [cause] as a [UiError] of [kind], except that a session without access to the athlete's spaces is told to sign in again, and a save
 * waiting on unsent writes says so.
 */
fun uiErrorOf(kind: UiError.Kind, cause: Throwable): UiError =
    when {
        cause is XrpcException && cause.error == SpaceErrors.SCOPE_MISSING -> UiError(UiError.Kind.SignInAgain)
        cause is PendingWritesException -> UiError(UiError.Kind.WritesPending)
        else -> UiError.of(kind, cause)
    }
