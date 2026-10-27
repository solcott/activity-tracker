package io.github.solcott.activitytracker.app.feed

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.data.activity.RejectedChange
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.dataresult.DataError
import kotlin.time.Duration.Companion.hours
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

object UnsentChangesSamples {
    val changes: ImmutableList<RejectedChange> =
        persistentListOf(
            RejectedChange(
                "3kride",
                RejectedChange.Kind.Activity,
                Fixed.NOW,
                title = "Morning Ride",
                sport = Sport.RIDE,
                startedAt = Fixed.NOW - 3.hours,
            ),
            RejectedChange("3krun", RejectedChange.Kind.Activity, Fixed.NOW, sport = Sport.RUN, startedAt = Fixed.NOW - 26.hours),
            RejectedChange("3klike", RejectedChange.Kind.Like, Fixed.NOW),
        )

    private fun state(changes: ImmutableList<RejectedChange>) = UnsentChangesState(changes, busy = persistentListOf())

    val empty: UnsentChangesState
        get() = state(persistentListOf())

    val loaded: UnsentChangesState
        get() = state(changes)

    /** A retry of the first change in flight. */
    val retrying: UnsentChangesState
        get() = state(changes).copy(busy = persistentListOf(changes.first().rkey))

    /** Asking to confirm discarding the first change. */
    val confirmingDiscard: UnsentChangesState
        get() = state(changes).copy(confirmingDiscard = changes.first())

    /** A retry that failed because the device is offline. */
    val retryFailed: UnsentChangesState
        get() = state(changes).copy(error = UiError(UiError.Kind.RetryChange, DataError.Network))

    val all: Map<String, UnsentChangesState>
        get() =
            mapOf(
                SampleKind.EMPTY to empty,
                SampleKind.LOADED to loaded,
                "retrying" to retrying,
                "confirmingDiscard" to confirmingDiscard,
                "retryFailed" to retryFailed,
            )
}
