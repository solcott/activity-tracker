package io.github.solcott.activitytracker.app.visibility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.activity.ActivityDefaults
import io.github.solcott.activitytracker.data.activity.PendingWritesException
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors

/**
 * The visibility choice of a form for a new activity (record, manual entry, import). It starts at the athlete's defaults once they load,
 * unless the athlete chose already, and changing the activity's visibility narrows the charts with it. Changing it affects only this
 * activity (or import), never the defaults.
 */
@Stable
class NewActivityVisibility internal constructor(private val canChoose: Boolean, private val approvesFollowers: () -> Boolean) {
    internal var choice by mutableStateOf(VisibilityChoice.PUBLIC)
    internal var chosen by mutableStateOf(false)

    /** What the form shows. */
    val fields: VisibilityFields
        get() = VisibilityFields(choice.activity, choice.charts, canChoose, approvesFollowers())

    /** What to publish with. */
    val current: VisibilityChoice
        get() = choice

    fun activityChanged(visibility: String) {
        if (!canChoose) return
        chosen = true
        choice = choice.copy(activity = Visibility.known(visibility)).capped()
    }

    fun chartsChanged(visibility: String) {
        if (!canChoose) return
        chosen = true
        choice = choice.copy(charts = Visibility.known(visibility)).capped()
    }
}

@Composable
fun rememberNewActivityVisibility(defaults: ActivityDefaults, approvesFollowers: () -> Boolean = { false }): NewActivityVisibility {
    val state = remember { NewActivityVisibility(defaults.canChoose, approvesFollowers) }
    LaunchedEffect(state) {
        val loaded = defaults.load()
        if (!state.chosen) state.choice = loaded
    }
    return state
}

/**
 * [cause] as a [UiError] of [kind], except that a session without access to the athlete's spaces is told to sign in again, and a save
 * waiting on unsent writes says so.
 */
fun uiErrorOf(kind: UiError.Kind, cause: Throwable): UiError =
    when {
        cause is XrpcException && cause.error == SpaceErrors.SCOPE_MISSING -> UiError(UiError.Kind.SignInAgain, null)
        cause is PendingWritesException -> UiError(UiError.Kind.WritesPending, null)
        else -> UiError.of(kind, cause)
    }
